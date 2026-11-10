package io.flagship.evaluation;

import static io.flagship.TestApi.bearer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;

import io.flagship.AbstractIntegrationTest;
import io.flagship.TestApi;

/** The evaluation endpoint, API keys and targeting rules working together on real PostgreSQL. */
class EvaluationFlowIT extends AbstractIntegrationTest {

    private static final String API_KEY_HEADER = "X-API-Key";

    private static final String RULES = """
            [
              {"type":"USER_LIST","values":["vip-1"],"serve":true},
              {"type":"GROUP","values":["beta"],"serve":true},
              {"type":"ATTRIBUTE","attribute":"country","operator":"EQUALS","values":["BR"],"serve":true},
              {"type":"ATTRIBUTE","attribute":"plan","operator":"IN","values":["free"],"serve":false}
            ]""";

    @Autowired
    MockMvc mvc;

    @Test
    void anApplicationWithAnApiKeyCanEvaluateAFlag() throws Exception {
        String token = TestApi.registerOwner(mvc).token();
        String apiKey = createApiKey(token).key();
        String flagId = createFlag(token, "new-checkout");
        configure(token, flagId, "production", true, 100);

        evaluate(apiKey, "new-checkout", "user-123", "production", "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.reason").value("PERCENTAGE_ROLLOUT"))
                .andExpect(jsonPath("$.variant").value("on"));
    }

    @Test
    void evaluationNeedsValidCredentials() throws Exception {
        String token = TestApi.registerOwner(mvc).token();
        CreatedKey created = createApiKey(token);
        createFlag(token, "secured-flag");

        mvc.perform(post("/api/v1/evaluate").contentType(APPLICATION_JSON).content(body("secured-flag", "u1", "production", "")))
                .andExpect(status().isUnauthorized());
        evaluate("fs_not-a-real-key", "secured-flag", "u1", "production", "")
                .andExpect(status().isUnauthorized());

        mvc.perform(delete("/api/v1/api-keys/" + created.id()).header(AUTHORIZATION, bearer(token)))
                .andExpect(status().isNoContent());
        evaluate(created.key(), "secured-flag", "u1", "production", "")
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aLoggedInUserCanAlsoEvaluateWithTheirToken() throws Exception {
        String token = TestApi.registerOwner(mvc).token();
        String flagId = createFlag(token, "token-flag");
        configure(token, flagId, "staging", true, 100);

        mvc.perform(post("/api/v1/evaluate").header(AUTHORIZATION, bearer(token))
                        .contentType(APPLICATION_JSON).content(body("token-flag", "u1", "staging", "")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true));
    }

    @Test
    void unknownThingsAnswerOffWithAReason() throws Exception {
        String token = TestApi.registerOwner(mvc).token();
        String apiKey = createApiKey(token).key();
        String flagId = createFlag(token, "known-flag");

        evaluate(apiKey, "does-not-exist", "u1", "production", "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false))
                .andExpect(jsonPath("$.reason").value("FLAG_NOT_FOUND"));
        evaluate(apiKey, "known-flag", "u1", "no-such-env", "")
                .andExpect(jsonPath("$.reason").value("ENVIRONMENT_NOT_FOUND"));
        evaluate(apiKey, "known-flag", "u1", "production", "")
                .andExpect(jsonPath("$.enabled").value(false))
                .andExpect(jsonPath("$.reason").value("FLAG_DISABLED"))
                .andExpect(jsonPath("$.variant").value("off"));

        configure(token, flagId, "production", true, 100);
        mvc.perform(delete("/api/v1/flags/" + flagId).header(AUTHORIZATION, bearer(token)))
                .andExpect(status().isNoContent());
        evaluate(apiKey, "known-flag", "u1", "production", "")
                .andExpect(jsonPath("$.reason").value("FLAG_ARCHIVED"));
    }

    @Test
    void invalidRequestsAreRejected() throws Exception {
        String token = TestApi.registerOwner(mvc).token();
        String apiKey = createApiKey(token).key();

        mvc.perform(post("/api/v1/evaluate").header(API_KEY_HEADER, apiKey)
                        .contentType(APPLICATION_JSON).content("{\"flagKey\":\"\",\"userId\":\"u1\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.flagKey").exists())
                .andExpect(jsonPath("$.errors.environment").exists());
    }

    @Test
    void zeroPercentExcludesEveryoneAndFullPercentIncludesEveryone() throws Exception {
        String token = TestApi.registerOwner(mvc).token();
        String apiKey = createApiKey(token).key();
        String flagId = createFlag(token, "extremes");

        configure(token, flagId, "production", true, 0);
        evaluate(apiKey, "extremes", "u1", "production", "")
                .andExpect(jsonPath("$.enabled").value(false))
                .andExpect(jsonPath("$.reason").value("NOT_IN_ROLLOUT"));

        configure(token, flagId, "production", true, 100);
        for (int i = 0; i < 20; i++) {
            evaluate(apiKey, "extremes", "user-" + i, "production", "")
                    .andExpect(jsonPath("$.enabled").value(true));
        }
    }

    @Test
    void rolloutIsStableAndOnlyGrows() throws Exception {
        String token = TestApi.registerOwner(mvc).token();
        String apiKey = createApiKey(token).key();
        String flagId = createFlag(token, "gradual");
        int users = 100;

        configure(token, flagId, "production", true, 25);
        Set<Integer> at25 = enabledUsers(apiKey, "gradual", users);
        assertThat(enabledUsers(apiKey, "gradual", users)).as("same answers on a second round").isEqualTo(at25);
        assertThat(at25.size()).as("about a quarter of the users").isBetween(8, 45);

        configure(token, flagId, "production", true, 50);
        Set<Integer> at50 = enabledUsers(apiKey, "gradual", users);

        assertThat(at50).as("nobody loses the feature when the rollout grows").containsAll(at25);
        assertThat(at50.size()).isGreaterThanOrEqualTo(at25.size());
    }

    @Test
    void rulesDecideBeforeThePercentage() throws Exception {
        String token = TestApi.registerOwner(mvc).token();
        String apiKey = createApiKey(token).key();
        String flagId = createFlag(token, "targeted");
        configure(token, flagId, "production", true, 0);
        putRules(token, flagId, "production", currentVersion(token, flagId, "production"), RULES)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rules.length()").value(4))
                .andExpect(jsonPath("$.rules[0].priority").value(0));

        evaluate(apiKey, "targeted", "vip-1", "production", "")
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.reason").value("RULE_MATCH"));
        evaluate(apiKey, "targeted", "u-beta", "production", ",\"groups\":[\"beta\"]")
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.reason").value("RULE_MATCH"));
        evaluate(apiKey, "targeted", "u-br", "production", ",\"attributes\":{\"country\":\"BR\"}")
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.reason").value("RULE_MATCH"));
        evaluate(apiKey, "targeted", "u-plain", "production", "")
                .andExpect(jsonPath("$.enabled").value(false))
                .andExpect(jsonPath("$.reason").value("NOT_IN_ROLLOUT"));

        configure(token, flagId, "production", true, 100);
        evaluate(apiKey, "targeted", "u-free", "production", ",\"attributes\":{\"plan\":\"free\"}")
                .andExpect(jsonPath("$.enabled").value(false))
                .andExpect(jsonPath("$.reason").value("RULE_MATCH"));
        evaluate(apiKey, "targeted", "u-plain", "production", "")
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.reason").value("PERCENTAGE_ROLLOUT"));
    }

    @Test
    void ruleUpdatesAreValidatedVersionedAndAudited() throws Exception {
        String token = TestApi.registerOwner(mvc).token();
        String flagId = createFlag(token, "audited-rules");
        long version = currentVersion(token, flagId, "production");

        putRules(token, flagId, "production", version, RULES).andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(version + 1));
        putRules(token, flagId, "production", version, RULES).andExpect(status().isConflict());
        putRules(token, flagId, "production", version + 1,
                "[{\"type\":\"ATTRIBUTE\",\"values\":[\"BR\"],\"serve\":true}]")
                .andExpect(status().isUnprocessableEntity());
        putRules(token, flagId, "production", version + 1,
                "[{\"type\":\"USER_LIST\",\"attribute\":\"country\",\"values\":[\"u1\"],\"serve\":true}]")
                .andExpect(status().isUnprocessableEntity());
        putRules(token, flagId, "production", version + 1,
                "[{\"type\":\"ATTRIBUTE\",\"attribute\":\"age\",\"operator\":\"GREATER_THAN\",\"values\":[\"old\"],\"serve\":true}]")
                .andExpect(status().isUnprocessableEntity());

        mvc.perform(get("/api/v1/flags/" + flagId + "/environments/production/rules")
                        .header(AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rules.length()").value(4));
        mvc.perform(get("/api/v1/audit-logs").param("entityId", flagId).header(AUTHORIZATION, bearer(token)))
                .andExpect(jsonPath("$.content[0].action").value("FLAG_RULES_UPDATED"))
                .andExpect(jsonPath("$.content[0].oldValue.rules.length()").value(0))
                .andExpect(jsonPath("$.content[0].newValue.rules.length()").value(4));
    }

    @Test
    void rolesForRulesAndApiKeys() throws Exception {
        String ownerToken = TestApi.registerOwner(mvc).token();
        String viewerToken = TestApi.memberToken(mvc, ownerToken, "VIEWER");
        String editorToken = TestApi.memberToken(mvc, ownerToken, "EDITOR");
        String flagId = createFlag(ownerToken, "role-flag");
        long version = currentVersion(ownerToken, flagId, "production");

        putRules(viewerToken, flagId, "production", version, "[]").andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/flags/" + flagId + "/environments/production/rules")
                        .header(AUTHORIZATION, bearer(viewerToken)))
                .andExpect(status().isOk());
        putRules(editorToken, flagId, "production", version, RULES).andExpect(status().isOk());

        mvc.perform(post("/api/v1/api-keys").header(AUTHORIZATION, bearer(editorToken))
                        .contentType(APPLICATION_JSON).content("{\"name\":\"nope\"}"))
                .andExpect(status().isForbidden());
        createApiKey(ownerToken);
        mvc.perform(get("/api/v1/api-keys").header(AUTHORIZATION, bearer(ownerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].key").doesNotExist())
                .andExpect(jsonPath("$[0].prefix").exists());
    }

    @Test
    void organizationsDoNotShareApiKeysOrFlags() throws Exception {
        String tokenA = TestApi.registerOwner(mvc).token();
        String tokenB = TestApi.registerOwner(mvc).token();
        String flagId = createFlag(tokenA, "only-in-a");
        configure(tokenA, flagId, "production", true, 100);
        String keyA = createApiKey(tokenA).key();
        String keyB = createApiKey(tokenB).key();

        evaluate(keyA, "only-in-a", "u1", "production", "").andExpect(jsonPath("$.enabled").value(true));
        evaluate(keyB, "only-in-a", "u1", "production", "")
                .andExpect(jsonPath("$.enabled").value(false))
                .andExpect(jsonPath("$.reason").value("FLAG_NOT_FOUND"));
    }

    // ---------- helpers ----------

    private record CreatedKey(String id, String key) {
    }

    private CreatedKey createApiKey(String token) throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/api-keys").header(AUTHORIZATION, bearer(token))
                        .contentType(APPLICATION_JSON).content("{\"name\":\"test key\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String json = result.getResponse().getContentAsString();
        return new CreatedKey(JsonPath.read(json, "$.id"), JsonPath.read(json, "$.key"));
    }

    private String createFlag(String token, String key) throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/flags").header(AUTHORIZATION, bearer(token))
                        .contentType(APPLICATION_JSON)
                        .content("{\"key\":\"%s\",\"name\":\"Flag %s\"}".formatted(key, key)))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    private long currentVersion(String token, String flagId, String environment) throws Exception {
        MvcResult result = mvc.perform(get("/api/v1/flags/" + flagId).header(AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andReturn();
        List<Number> versions = JsonPath.read(result.getResponse().getContentAsString(),
                "$.environments[?(@.environmentKey=='" + environment + "')].version");
        return versions.get(0).longValue();
    }

    /** Sets the flag state, reading the current version first so the call never conflicts. */
    private void configure(String token, String flagId, String environment, boolean enabled, int percentage) throws Exception {
        long version = currentVersion(token, flagId, environment);
        mvc.perform(put("/api/v1/flags/" + flagId + "/environments/" + environment)
                        .header(AUTHORIZATION, bearer(token))
                        .contentType(APPLICATION_JSON)
                        .content("{\"enabled\":%s,\"rolloutPercentage\":%d,\"version\":%d}".formatted(enabled, percentage, version)))
                .andExpect(status().isOk());
    }

    private ResultActions putRules(String token, String flagId, String environment, long version, String rulesJson) throws Exception {
        return mvc.perform(put("/api/v1/flags/" + flagId + "/environments/" + environment + "/rules")
                .header(AUTHORIZATION, bearer(token))
                .contentType(APPLICATION_JSON)
                .content("{\"version\":%d,\"rules\":%s}".formatted(version, rulesJson)));
    }

    private ResultActions evaluate(String apiKey, String flagKey, String userId, String environment, String extra) throws Exception {
        return mvc.perform(post("/api/v1/evaluate").header(API_KEY_HEADER, apiKey)
                .contentType(APPLICATION_JSON)
                .content(body(flagKey, userId, environment, extra)));
    }

    private static String body(String flagKey, String userId, String environment, String extra) {
        return "{\"flagKey\":\"%s\",\"userId\":\"%s\",\"environment\":\"%s\"%s}".formatted(flagKey, userId, environment, extra);
    }

    private Set<Integer> enabledUsers(String apiKey, String flagKey, int users) throws Exception {
        Set<Integer> enabled = new HashSet<>();
        for (int i = 0; i < users; i++) {
            MvcResult result = evaluate(apiKey, flagKey, "user-" + i, "production", "")
                    .andExpect(status().isOk())
                    .andReturn();
            Boolean on = JsonPath.read(result.getResponse().getContentAsString(), "$.enabled");
            if (on) {
                enabled.add(i);
            }
        }
        return enabled;
    }
}
