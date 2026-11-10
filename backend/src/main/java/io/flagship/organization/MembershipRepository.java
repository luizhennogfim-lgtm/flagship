package io.flagship.organization;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MembershipRepository extends JpaRepository<Membership, UUID> {

    List<Membership> findByUserId(UUID userId);

    @Query(value = """
            select new io.flagship.organization.MemberView(u.id, u.email, u.fullName, m.role)
            from Membership m join User u on u.id = m.userId
            where m.organizationId = :organizationId
            order by u.fullName
            """,
            countQuery = "select count(m) from Membership m where m.organizationId = :organizationId")
    Page<MemberView> findMembers(@Param("organizationId") UUID organizationId, Pageable pageable);
}
