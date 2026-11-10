package io.flagship.security;

import java.io.IOException;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import io.flagship.ratelimit.RateLimitProperties;
import io.flagship.ratelimit.RateLimiter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Three policies, each counted separately:
 * <ul>
 *   <li>auth: login/register, per client IP (slows down password guessing)</li>
 *   <li>evaluate: evaluation by an application, per API key</li>
 *   <li>api: everything else a logged-in person does, per user</li>
 * </ul>
 * Runs after authentication so it knows who is calling. Not a Spring bean (see {@link JwtAuthenticationFilter}).
 *
 * <p>The client IP is the direct connection address. Behind a reverse proxy, configure the proxy and
 * server.forward-headers-strategy so that address is the real client, never trust X-Forwarded-For blindly.
 */
public class RateLimitFilter extends OncePerRequestFilter {

    private static final String AUTH_PREFIX = "/api/v1/auth/";
    private static final String EVALUATE_PATH = "/api/v1/evaluate";

    private final RateLimiter limiter;
    private final RateLimitProperties properties;
    private final SecurityProblemWriter problems;
    private final MeterRegistry meters;

    public RateLimitFilter(RateLimiter limiter, RateLimitProperties properties, SecurityProblemWriter problems,
                           MeterRegistry meters) {
        this.limiter = limiter;
        this.properties = properties;
        this.problems = problems;
        this.meters = meters;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Policy policy = policyOf(request);
        if (policy == null) {
            chain.doFilter(request, response);
            return;
        }
        RateLimiter.Decision decision = limiter.tryAcquire(policy.key(), policy.limit());
        response.setHeader("X-RateLimit-Limit", String.valueOf(decision.limit()));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(decision.remaining()));
        if (!decision.allowed()) {
            meters.counter("flagship.ratelimit.rejected", "policy", policy.name()).increment();
            response.setHeader("Retry-After", String.valueOf(decision.retryAfterSeconds()));
            problems.write(response, HttpStatus.TOO_MANY_REQUESTS, "Too many requests",
                    "Rate limit exceeded; retry in " + decision.retryAfterSeconds() + " seconds");
            return;
        }
        chain.doFilter(request, response);
    }

    private Policy policyOf(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (path.startsWith(AUTH_PREFIX)) {
            return new Policy("auth", "auth:" + request.getRemoteAddr(), properties.authLimit());
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return null;   // not authenticated: the request is rejected with 401 further down and touches nothing
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof ApiKeyPrincipal key) {
            return new Policy("evaluate", "eval:" + key.apiKeyId(), properties.evaluateLimit());
        }
        if (principal instanceof AuthenticatedUser user) {
            return EVALUATE_PATH.equals(path)
                    ? new Policy("evaluate", "eval-user:" + user.userId(), properties.evaluateLimit())
                    : new Policy("api", "api:" + user.userId(), properties.apiLimit());
        }
        return null;
    }

    /** name is the metric tag (a small fixed set); key identifies who is being counted. */
    private record Policy(String name, String key, int limit) {
    }
}
