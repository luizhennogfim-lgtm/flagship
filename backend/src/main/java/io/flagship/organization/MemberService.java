package io.flagship.organization;

import java.util.Locale;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.flagship.common.api.PageResponse;
import io.flagship.common.api.Paging;
import io.flagship.common.exception.ConflictException;
import io.flagship.common.tenant.TenantContext;
import io.flagship.security.AuthenticatedUser;
import io.flagship.security.CurrentUser;
import io.flagship.user.User;
import io.flagship.user.UserRepository;

@Service
public class MemberService {

    private final UserRepository users;
    private final MembershipRepository memberships;
    private final PasswordEncoder passwordEncoder;

    public MemberService(UserRepository users, MembershipRepository memberships, PasswordEncoder passwordEncoder) {
        this.users = users;
        this.memberships = memberships;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public PageResponse<MemberView> list(int page, int size) {
        return PageResponse.from(memberships.findMembers(TenantContext.require(), Paging.of(page, size)));
    }

    @Transactional
    public MemberView add(CreateMemberRequest request) {
        AuthenticatedUser caller = CurrentUser.get();
        if (request.role() == Role.OWNER && caller.role() != Role.OWNER) {
            throw new AccessDeniedException("Only an owner can grant the OWNER role");
        }
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (users.existsByEmail(email)) {
            throw new ConflictException("This email is already registered");
        }
        User user = users.save(new User(email, passwordEncoder.encode(request.password()), request.fullName().trim()));
        memberships.save(new Membership(caller.organizationId(), user.getId(), request.role()));
        return new MemberView(user.getId(), user.getEmail(), user.getFullName(), request.role());
    }
}
