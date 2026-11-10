package io.flagship.ratelimit;

import static io.flagship.TestApi.configure;
import static io.flagship.TestApi.createApiKey;
import static io.flagship.TestApi.createFlag;
import static io.flagship.TestApi.evaluate;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import io.flagship.AbstractIntegrationTest;
import io.flagship.TestApi;
import io.flagship.TestApi.Registered;
import io.micrometer.core.instrument.MeterRegistry;

/** The HTTP behaviour of rate limiting, with very low limits so the tests stay fast. */
@TestPropertySource(properties = {
        "flagship.rate-limit.evaluate-limit=5",
        "flagship.rate-limit.auth-limit=8",
        "flagship.rate-limit.key-prefix=flagship:rl-filter-it"
})
class RateLimitFilterIT extends AbstractIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    MeterRegistry meters;

    @Test
    void anApiKeyIsBlockedAfterItsLimitWithoutAffectingOtherKeys() throws Exception {
        Registered owner = TestApi.registerOwner(mvc);
        String keyA = createApiKey(mvc, owner.token());
        String keyB = createApiKey(mvc, owner.token());
        String flagId = createFlag(mvc, owner.token(), "limited-flag");
        configure(mvc, owner.token(), flagId, "production", true, 100);

        for (int i = 1; i <= 5; i++) {
            evaluate(mvc, keyA, "limited-flag", "u1", "production")
                    .andExpect(status().isOk())
                    .andExpect(header().string("X-RateLimit-Limit", "5"))
                    .andExpect(header().string("X-RateLimit-Remaining", String.valueOf(5 - i)));
        }

        evaluate(mvc, keyA, "limited-flag", "u1", "production")
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.title").value("Too many requests"));
        evaluate(mvc, keyB, "limited-flag", "u1", "production").andExpect(status().isOk());
        assertThat(meters.get("flagship.ratelimit.rejected").tag("policy", "evaluate").counter().count())
                .isGreaterThanOrEqualTo(1.0);
    }

    @Test
    void loginAttemptsAreLimitedPerIp() throws Exception {
        List<Integer> statuses = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            statuses.add(mvc.perform(post("/api/v1/auth/login").contentType(APPLICATION_JSON)
                            .content("{\"email\":\"nobody@example.com\",\"password\":\"wrong-password\"}"))
                    .andReturn().getResponse().getStatus());
        }

        assertThat(statuses).contains(401).contains(429);
        int firstBlocked = statuses.indexOf(429);
        assertThat(statuses.subList(firstBlocked, statuses.size())).containsOnly(429);
    }
}
