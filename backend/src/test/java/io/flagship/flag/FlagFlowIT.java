package io.flagship.flag;

import static io.flagship.TestApi.bearer;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;

import io.flagship.AbstractIntegrationTest;
import io.flagship.TestApi;
import io.flagship.TestApi.Registered;

/** Flags, per-environment state, filters, audit trail, RBAC and tenant isolation on real PostgreSQL. */
class FlagFlowIT extends AbstractIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Test
    void createFlagStartsOffInEveryDefaultEnvironment() throws Exception {
        Registered owner = TestApi.registerOwner(mvc);

        mvc.perform(post("/api/v1/flags").header(AUTHORIZATION, bearer(owner.token()))
                        .contentType(APPLICATION_JSON)
                        .content("{\"key\":\"new-checkout\",\"name\":\"New checkout\",\"description\":\"Redesigned checkout\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.key").value("new-checkout"))
                .andExpect(jsonPath("$.environments.length()").value(3))
                .andExpect(jsonPath("$.environments[0].environmentKey").value("development"))
                .andExpect(jsonPath("$.environments[1].environmentKey").value("staging"))
                .andExpect(jsonPath("$.environments[2].environmentKey").value("production"))
                .andExpect(jsonPath("$.environments[2].enabled").value(false))
                .andExpect(jsonPath("$.environments[2].rolloutPercentage").value(0))
                .andExpect(jsonPath("$.environments[2].version").value(0));
    }

    @Test
    void duplicateKeyIsConflictAndInvalidKeyIsRejected() throws Exception {
        String token = TestApi.registerOwner(mvc).token();
        createFlag(token, "dup-key");

        mvc.perform(post("/api/v1/flags").header(AUTHORIZATION, bearer(token))
                        .contentType(APPLICATION_JSON).content("{\"key\":\"dup-key\",\"name\":\"Again\"}"))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/v1/flags").header(AUTHORIZATION, bearer(token))
                        .contentType(APPLICATION_JSON).content("{\"key\":\"Bad Key\",\"name\":\"Invalid\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.key").exists());
    }

    @Test
    void environmentChangeIsAuditedWithOldAndNewValues() throws Exception {
        Registered owner = TestApi.registerOwner(mvc);
        String flagId = createFlag(owner.token(), "audited-flag");

        updateEnvironment(owner.token(), flagId, "production", true, 25, 0)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.rolloutPercentage").value(25))
                .andExpect(jsonPath("$.version").value(1));

        mvc.perform(get("/api/v1/audit-logs").param("entityId", flagId)
                        .header(AUTHORIZATION, bearer(owner.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].action").value("FLAG_ENVIRONMENT_UPDATED"))
                .andExpect(jsonPath("$.content[0].actorEmail").value(owner.email()))
                .andExpect(jsonPath("$.content[0].oldValue.enabled").value(false))
                .andExpect(jsonPath("$.content[0].newValue.enabled").value(true))
                .andExpect(jsonPath("$.content[0].newValue.rolloutPercentage").value(25))
                .andExpect(jsonPath("$.content[1].action").value("FLAG_CREATED"));
    }

    @Test
    void staleVersionIsConflict() throws Exception {
        String token = TestApi.registerOwner(mvc).token();
        String flagId = createFlag(token, "stale-flag");

        updateEnvironment(token, flagId, "staging", true, 10, 0).andExpect(status().isOk());
        updateEnvironment(token, flagId, "staging", true, 50, 0).andExpect(status().isConflict());
        updateEnvironment(token, flagId, "staging", true, 50, 1).andExpect(status().isOk());
    }

    @Test
    void rolloutOutsideZeroToHundredIsRejected() throws Exception {
        String token = TestApi.registerOwner(mvc).token();
        String flagId = createFlag(token, "range-flag");

        updateEnvironment(token, flagId, "production", true, 150, 0).andExpect(status().isBadRequest());
        updateEnvironment(token, flagId, "production", true, -1, 0).andExpect(status().isBadRequest());
        updateEnvironment(token, flagId, "unknown-env", true, 10, 0).andExpect(status().isNotFound());
    }

    @Test
    void filtersFindFlagsByEnvironmentStateAndSearch() throws Exception {
        String token = TestApi.registerOwner(mvc).token();
        String enabledId = createFlag(token, "alpha-search");
        createFlag(token, "beta-search");
        updateEnvironment(token, enabledId, "staging", true, 100, 0).andExpect(status().isOk());

        mvc.perform(get("/api/v1/flags").param("environment", "staging").param("enabled", "true")
                        .header(AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].key").value("alpha-search"));
        mvc.perform(get("/api/v1/flags").param("environment", "staging").param("enabled", "false")
                        .header(AUTHORIZATION, bearer(token)))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].key").value("beta-search"));
        mvc.perform(get("/api/v1/flags").param("search", "BETA").header(AUTHORIZATION, bearer(token)))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].key").value("beta-search"));
        mvc.perform(get("/api/v1/flags").param("enabled", "true").header(AUTHORIZATION, bearer(token)))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(get("/api/v1/flags").param("size", "1").header(AUTHORIZATION, bearer(token)))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.content.length()").value(1));
    }

    @Test
    void otherOrganizationCannotAccessFlag() throws Exception {
        String tokenA = TestApi.registerOwner(mvc).token();
        String tokenB = TestApi.registerOwner(mvc).token();
        String flagId = createFlag(tokenA, "private-flag");

        mvc.perform(get("/api/v1/flags/" + flagId).header(AUTHORIZATION, bearer(tokenB)))
                .andExpect(status().isNotFound());
        updateEnvironment(tokenB, flagId, "production", true, 100, 0).andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/flags/" + flagId).header(AUTHORIZATION, bearer(tokenB)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/flags").header(AUTHORIZATION, bearer(tokenB)))
                .andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/v1/audit-logs").param("entityId", flagId).header(AUTHORIZATION, bearer(tokenB)))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void roleRulesForFlags() throws Exception {
        String ownerToken = TestApi.registerOwner(mvc).token();
        String viewerToken = TestApi.memberToken(mvc, ownerToken, "VIEWER");
        String editorToken = TestApi.memberToken(mvc, ownerToken, "EDITOR");

        mvc.perform(post("/api/v1/flags").header(AUTHORIZATION, bearer(viewerToken))
                        .contentType(APPLICATION_JSON).content("{\"key\":\"by-viewer\",\"name\":\"X\"}"))
                .andExpect(status().isForbidden());
        String flagId = createFlag(editorToken, "by-editor");
        mvc.perform(get("/api/v1/flags").header(AUTHORIZATION, bearer(viewerToken)))
                .andExpect(status().isOk());
        mvc.perform(delete("/api/v1/flags/" + flagId).header(AUTHORIZATION, bearer(editorToken)))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/environments").header(AUTHORIZATION, bearer(viewerToken))
                        .contentType(APPLICATION_JSON).content("{\"key\":\"qa\",\"name\":\"QA\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/audit-logs").header(AUTHORIZATION, bearer(viewerToken)))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/v1/flags/" + flagId).header(AUTHORIZATION, bearer(ownerToken)))
                .andExpect(status().isNoContent());
    }

    @Test
    void archivedFlagsAreHiddenAndReadOnly() throws Exception {
        String token = TestApi.registerOwner(mvc).token();
        String flagId = createFlag(token, "to-archive");

        mvc.perform(delete("/api/v1/flags/" + flagId).header(AUTHORIZATION, bearer(token)))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/v1/flags").header(AUTHORIZATION, bearer(token)))
                .andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/v1/flags").param("archived", "true").header(AUTHORIZATION, bearer(token)))
                .andExpect(jsonPath("$.totalElements").value(1));
        updateEnvironment(token, flagId, "production", true, 10, 0).andExpect(status().isUnprocessableEntity());
        mvc.perform(put("/api/v1/flags/" + flagId).header(AUTHORIZATION, bearer(token))
                        .contentType(APPLICATION_JSON).content("{\"name\":\"Renamed\"}"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void newEnvironmentAppearsOnExistingFlags() throws Exception {
        String token = TestApi.registerOwner(mvc).token();
        String flagId = createFlag(token, "env-growth");

        mvc.perform(get("/api/v1/environments").header(AUTHORIZATION, bearer(token)))
                .andExpect(jsonPath("$.length()").value(3));
        mvc.perform(post("/api/v1/environments").header(AUTHORIZATION, bearer(token))
                        .contentType(APPLICATION_JSON).content("{\"key\":\"qa\",\"name\":\"QA\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.key").value("qa"));
        mvc.perform(post("/api/v1/environments").header(AUTHORIZATION, bearer(token))
                        .contentType(APPLICATION_JSON).content("{\"key\":\"qa\",\"name\":\"QA again\"}"))
                .andExpect(status().isConflict());

        mvc.perform(get("/api/v1/flags/" + flagId).header(AUTHORIZATION, bearer(token)))
                .andExpect(jsonPath("$.environments.length()").value(4))
                .andExpect(jsonPath("$.environments[3].environmentKey").value("qa"));
        updateEnvironment(token, flagId, "qa", true, 5, 0).andExpect(status().isOk());
    }

    // ---------- helpers ----------

    private String createFlag(String token, String key) throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/flags").header(AUTHORIZATION, bearer(token))
                        .contentType(APPLICATION_JSON)
                        .content("{\"key\":\"%s\",\"name\":\"Flag %s\",\"description\":\"test\"}".formatted(key, key)))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    private ResultActions updateEnvironment(String token, String flagId, String environment,
                                            boolean enabled, int percentage, long version) throws Exception {
        return mvc.perform(put("/api/v1/flags/" + flagId + "/environments/" + environment)
                .header(AUTHORIZATION, bearer(token))
                .contentType(APPLICATION_JSON)
                .content("{\"enabled\":%s,\"rolloutPercentage\":%d,\"version\":%d}".formatted(enabled, percentage, version)));
    }
}
