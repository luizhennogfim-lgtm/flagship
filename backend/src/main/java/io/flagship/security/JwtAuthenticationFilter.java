package io.flagship.security;

import java.io.IOException;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import io.flagship.common.tenant.TenantContext;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Reads the Bearer token, authenticates the request and binds the tenant (organization).
 * Intentionally NOT a Spring bean: it is created inside the security filter chain only,
 * otherwise Spring Boot would also register it as a plain servlet filter.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    private static final String BEARER = "Bearer ";

    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        try {
            if (header != null && header.startsWith(BEARER)) {
                authenticate(header.substring(BEARER.length()));
            }
            chain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }

    private void authenticate(String token) {
        try {
            AuthenticatedUser user = jwtService.parse(token);
            var authentication = new UsernamePasswordAuthenticationToken(user, null,
                    List.of(new SimpleGrantedAuthority("ROLE_" + user.role().name())));
            SecurityContextHolder.getContext().setAuthentication(authentication);
            TenantContext.set(user.organizationId());
        } catch (JwtException | IllegalArgumentException ex) {
            // Invalid token: leave the request unauthenticated; the entry point answers 401.
            log.debug("Rejected invalid JWT: {}", ex.getMessage());
        }
    }
}
