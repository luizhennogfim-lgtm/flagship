package io.flagship.cache;

import static io.flagship.TestApi.bearer;
import static io.flagship.TestApi.configure;
import static io.flagship.TestApi.createApiKey;
import static io.flagship.TestApi.createFlag;
import static io.flagship.TestApi.currentVersion;
import static io.flagship.TestApi.evaluate;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.web.servlet.MockMvc;

import io.flagship.AbstractIntegrationTest;
import io.flagship.TestApi;
import io.flagship.TestApi.Registered;

/** The Redis cache against a real Redis: it must speed things up without ever showing old answers. */
class CacheIT extends AbstractIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    StringRedisTemplate redis;

    @Test
    void changesAreVisibleImmediatelyDespiteTheCache() throws Exception {
        Registered owner = TestApi.registerOwner(mvc);
        String apiKey = createApiKey(mvc, owner.token());
        String flagId = createFlag(mvc, owner.token(), "cached-flag");

        // both answers are now cached, including the "not found" one
        evaluate(mvc, apiKey, "later-flag", "u1", "production").andExpect(jsonPath("$.reason").value("FLAG_NOT_FOUND"));
        evaluate(mvc, apiKey, "cached-flag", "u1", "production").andExpect(jsonPath("$.reason").value("FLAG_DISABLED"));

        configure(mvc, owner.token(), flagId, "production", true, 100);
        evaluate(mvc, apiKey, "cached-flag", "u1", "production")
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.reason").value("PERCENTAGE_ROLLOUT"));

        long version = currentVersion(mvc, owner.token(), flagId, "production");
        mvc.perform(put("/api/v1/flags/" + flagId + "/environments/production/rules")
                        .header(AUTHORIZATION, bearer(owner.token()))
                        .contentType(APPLICATION_JSON)
                        .content("{\"version\":%d,\"rules\":[{\"type\":\"USER_LIST\",\"values\":[\"blocked\"],\"serve\":false}]}".formatted(version)))
                .andExpect(status().isOk());
        evaluate(mvc, apiKey, "cached-flag", "blocked", "production")
                .andExpect(jsonPath("$.enabled").value(false))
                .andExpect(jsonPath("$.reason").value("RULE_MATCH"));

        String laterId = createFlag(mvc, owner.token(), "later-flag");   // the cached "not found" must not survive
        configure(mvc, owner.token(), laterId, "production", true, 100);
        evaluate(mvc, apiKey, "later-flag", "u1", "production").andExpect(jsonPath("$.enabled").value(true));

        mvc.perform(delete("/api/v1/flags/" + flagId).header(AUTHORIZATION, bearer(owner.token())))
                .andExpect(status().isNoContent());
        evaluate(mvc, apiKey, "cached-flag", "u1", "production").andExpect(jsonPath("$.reason").value("FLAG_ARCHIVED"));
    }

    @Test
    void aNewEnvironmentIsVisibleAfterAnEarlierNotFoundWasCached() throws Exception {
        Registered owner = TestApi.registerOwner(mvc);
        String apiKey = createApiKey(mvc, owner.token());
        createFlag(mvc, owner.token(), "env-cache");

        evaluate(mvc, apiKey, "env-cache", "u1", "qa").andExpect(jsonPath("$.reason").value("ENVIRONMENT_NOT_FOUND"));

        mvc.perform(post("/api/v1/environments").header(AUTHORIZATION, bearer(owner.token()))
                        .contentType(APPLICATION_JSON).content("{\"key\":\"qa\",\"name\":\"QA\"}"))
                .andExpect(status().isCreated());

        evaluate(mvc, apiKey, "env-cache", "u1", "qa").andExpect(jsonPath("$.reason").value("FLAG_DISABLED"));
    }

    @Test
    void snapshotsLiveInRedisWithATimeToLive() throws Exception {
        Registered owner = TestApi.registerOwner(mvc);
        UUID organizationId = UUID.fromString(owner.organizationId());
        String apiKey = createApiKey(mvc, owner.token());
        createFlag(mvc, owner.token(), "ttl-flag");

        assertThat(redis.keys(CacheKeys.snapshotPattern(organizationId))).isEmpty();
        evaluate(mvc, apiKey, "ttl-flag", "u1", "production").andExpect(status().isOk());

        Set<String> keys = redis.keys(CacheKeys.snapshotPattern(organizationId));
        assertThat(keys).hasSize(1);
        Long secondsToLive = redis.getExpire(keys.iterator().next());
        assertThat(secondsToLive).isBetween(1L, 60L);
    }

    @Test
    void everyChangeBumpsTheOrganizationEpoch() throws Exception {
        Registered owner = TestApi.registerOwner(mvc);
        UUID organizationId = UUID.fromString(owner.organizationId());
        String flagId = createFlag(mvc, owner.token(), "epoch-flag");
        long before = epochOf(organizationId);

        configure(mvc, owner.token(), flagId, "production", true, 10);
        long afterFirst = epochOf(organizationId);
        configure(mvc, owner.token(), flagId, "production", true, 20);

        assertThat(afterFirst).isGreaterThan(before);
        assertThat(epochOf(organizationId)).isGreaterThan(afterFirst);
    }

    @Test
    void emptyingRedisNeverBreaksEvaluation() throws Exception {
        Registered owner = TestApi.registerOwner(mvc);
        String apiKey = createApiKey(mvc, owner.token());
        String flagId = createFlag(mvc, owner.token(), "flushed-flag");
        configure(mvc, owner.token(), flagId, "production", true, 100);
        evaluate(mvc, apiKey, "flushed-flag", "u1", "production").andExpect(jsonPath("$.enabled").value(true));

        redis.execute((RedisCallback<Object>) connection -> {
            connection.serverCommands().flushAll();
            return null;
        });

        evaluate(mvc, apiKey, "flushed-flag", "u1", "production").andExpect(jsonPath("$.enabled").value(true));
    }

    private long epochOf(UUID organizationId) {
        String value = redis.opsForValue().get(CacheKeys.epoch(organizationId));
        return value == null ? 0 : Long.parseLong(value);
    }
}
