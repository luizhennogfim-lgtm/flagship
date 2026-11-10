package io.flagship.observability;

import static io.flagship.TestApi.configure;
import static io.flagship.TestApi.createApiKey;
import static io.flagship.TestApi.createFlag;
import static io.flagship.TestApi.evaluate;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import io.flagship.AbstractIntegrationTest;
import io.flagship.TestApi;
import io.flagship.TestApi.Registered;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

/** The numbers Prometheus and Grafana show must move when the system is used. */
class MetricsIT extends AbstractIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    MeterRegistry meters;

    @Test
    void evaluationsAreCountedByReasonAndOutcome() throws Exception {
        Registered owner = TestApi.registerOwner(mvc);
        String apiKey = createApiKey(mvc, owner.token());
        String flagId = createFlag(mvc, owner.token(), "metered-flag");
        configure(mvc, owner.token(), flagId, "production", true, 100);

        double rolloutBefore = evaluations("PERCENTAGE_ROLLOUT", "true");
        double notFoundBefore = evaluations("FLAG_NOT_FOUND", "false");
        long timerBefore = meters.get("flagship.evaluation.duration").timer().count();

        evaluate(mvc, apiKey, "metered-flag", "u1", "production").andExpect(status().isOk());
        evaluate(mvc, apiKey, "metered-flag", "u2", "production").andExpect(status().isOk());
        evaluate(mvc, apiKey, "no-such-flag", "u1", "production").andExpect(status().isOk());

        assertThat(evaluations("PERCENTAGE_ROLLOUT", "true")).isEqualTo(rolloutBefore + 2);
        assertThat(evaluations("FLAG_NOT_FOUND", "false")).isEqualTo(notFoundBefore + 1);
        assertThat(meters.get("flagship.evaluation.duration").timer().count()).isEqualTo(timerBefore + 3);
    }

    @Test
    void theCacheCountsMissesThenHits() throws Exception {
        Registered owner = TestApi.registerOwner(mvc);
        String apiKey = createApiKey(mvc, owner.token());
        createFlag(mvc, owner.token(), "cache-metered");
        double missesBefore = cacheRequests("miss");
        double hitsBefore = cacheRequests("hit");

        evaluate(mvc, apiKey, "cache-metered", "u1", "production").andExpect(status().isOk());
        evaluate(mvc, apiKey, "cache-metered", "u1", "production").andExpect(status().isOk());

        assertThat(cacheRequests("miss")).isEqualTo(missesBefore + 1);
        assertThat(cacheRequests("hit")).isEqualTo(hitsBefore + 1);
    }

    private double evaluations(String reason, String enabled) {
        Counter counter = meters.find("flagship.evaluations").tag("reason", reason).tag("enabled", enabled).counter();
        return counter == null ? 0 : counter.count();
    }

    private double cacheRequests(String result) {
        Counter counter = meters.find("flagship.cache.requests").tag("result", result).counter();
        return counter == null ? 0 : counter.count();
    }
}
