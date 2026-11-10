package io.flagship.security;

import java.io.IOException;
import java.util.List;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import io.flagship.apikey.ApiKeyService;
import io.flagship.common.tenant.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Authenticates applications calling the evaluation endpoint with the "X-API-Key" header.
 * It only looks at that one path, and only when no user is authenticated yet.
 * Not a Spring bean, for the same reason as {@link JwtAuthenticationFilter}.
 */
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-API-Key";
    private static final String EVALUATE_PATH = "/api/v1/evaluate";

    private final ApiKeyService apiKeys;

    public ApiKeyAuthenticationFilter(ApiKeyService apiKeys) {
        this.apiKeys = apiKeys;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !EVALUATE_PATH.equals(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        try {
            if (SecurityContextHolder.getContext().getAuthentication() == null) {
                apiKeys.authenticate(request.getHeader(HEADER)).ifPresent(key -> {
                    var principal = new ApiKeyPrincipal(key.getOrganizationId(), key.getId());
                    SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                            principal, null, List.of(new SimpleGrantedAuthority("ROLE_SDK"))));
                    TenantContext.set(key.getOrganizationId());
                });
            }
            chain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }
}
