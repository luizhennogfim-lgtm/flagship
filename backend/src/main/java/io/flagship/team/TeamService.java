package io.flagship.team;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.flagship.common.api.PageResponse;
import io.flagship.common.api.Paging;
import io.flagship.common.exception.ConflictException;
import io.flagship.common.tenant.TenantContext;

/** Every method works only on the organization bound to the current request (tenant isolation). */
@Service
public class TeamService {

    private final TeamRepository teams;

    public TeamService(TeamRepository teams) {
        this.teams = teams;
    }

    @Transactional(readOnly = true)
    public PageResponse<TeamResponse> list(int page, int size) {
        UUID organizationId = TenantContext.require();
        return PageResponse.from(teams.findByOrganizationIdOrderByNameAsc(organizationId, Paging.of(page, size))
                .map(TeamResponse::from));
    }

    @Transactional
    public TeamResponse create(CreateTeamRequest request) {
        UUID organizationId = TenantContext.require();
        String name = request.name().trim();
        if (teams.existsByOrganizationIdAndName(organizationId, name)) {
            throw new ConflictException("A team named '" + name + "' already exists");
        }
        return TeamResponse.from(teams.save(new Team(organizationId, name)));
    }
}
