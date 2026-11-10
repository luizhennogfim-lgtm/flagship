package io.flagship.auth;

import java.util.List;
import java.util.Locale;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.flagship.common.exception.BusinessRuleException;
import io.flagship.common.exception.ConflictException;
import io.flagship.environment.EnvironmentService;
import io.flagship.organization.Membership;
import io.flagship.organization.MembershipRepository;
import io.flagship.organization.Organization;
import io.flagship.organization.OrganizationRepository;
import io.flagship.organization.Role;
import io.flagship.organization.SlugGenerator;
import io.flagship.security.JwtService;
import io.flagship.user.User;
import io.flagship.user.UserRepository;

@Service
public class AuthService {

    private static final String BEARER = "Bearer";

    private final UserRepository users;
    private final OrganizationRepository organizations;
    private final MembershipRepository memberships;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final EnvironmentService environmentService;

    public AuthService(UserRepository users, OrganizationRepository organizations,
                       MembershipRepository memberships, PasswordEncoder passwordEncoder, JwtService jwtService,
                       EnvironmentService environmentService) {
        this.users = users;
        this.organizations = organizations;
        this.memberships = memberships;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.environmentService = environmentService;
    }

    /** Creates a user together with a brand-new organization, where the user becomes OWNER. */
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = normalize(request.email());
        if (users.existsByEmail(email)) {
            throw new ConflictException("This email is already registered");
        }
        String slug = SlugGenerator.unique(request.organizationName(), organizations::existsBySlug);
        Organization organization = organizations.save(new Organization(request.organizationName().trim(), slug));
        environmentService.seedDefaults(organization.getId());
        User user = users.save(new User(email, passwordEncoder.encode(request.password()), request.fullName().trim()));
        memberships.save(new Membership(organization.getId(), user.getId(), Role.OWNER));
        return response(user, organization, Role.OWNER);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        User user = users.findByEmail(normalize(request.email()))
                .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid credentials");
        }
        Membership membership = selectMembership(user, request.organizationSlug());
        Organization organization = organizations.findById(membership.getOrganizationId())
                .orElseThrow(() -> new IllegalStateException("Organization of an existing membership is missing"));
        return response(user, organization, membership.getRole());
    }

    private Membership selectMembership(User user, String organizationSlug) {
        List<Membership> all = memberships.findByUserId(user.getId());
        if (organizationSlug != null && !organizationSlug.isBlank()) {
            Organization requested = organizations.findBySlug(organizationSlug.trim())
                    .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));
            return all.stream()
                    .filter(m -> m.getOrganizationId().equals(requested.getId()))
                    .findFirst()
                    .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));
        }
        if (all.isEmpty()) {
            throw new BusinessRuleException("This user does not belong to any organization");
        }
        if (all.size() > 1) {
            throw new BusinessRuleException("This user belongs to several organizations; provide organizationSlug");
        }
        return all.get(0);
    }

    private AuthResponse response(User user, Organization organization, Role role) {
        String token = jwtService.issue(user.getId(), organization.getId(), role);
        return new AuthResponse(token, BEARER, jwtService.expirationSeconds(),
                organization.getId(), organization.getSlug(), role);
    }

    private static String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
