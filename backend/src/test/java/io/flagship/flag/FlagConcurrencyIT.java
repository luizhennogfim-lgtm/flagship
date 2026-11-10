package io.flagship.flag;

import static io.flagship.TestApi.bearer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.jayway.jsonpath.JsonPath;

import io.flagship.AbstractIntegrationTest;
import io.flagship.TestApi;

/** Several people save the same flag version at the same time: exactly one may win. */
class FlagConcurrencyIT extends AbstractIntegrationTest {

    private static final int WRITERS = 8;

    @Autowired
    MockMvc mvc;

    @Test
    void onlyOneOfManyConcurrentUpdatesWins() throws Exception {
        String token = TestApi.registerOwner(mvc).token();
        MvcResult created = mvc.perform(post("/api/v1/flags").header(AUTHORIZATION, bearer(token))
                        .contentType(APPLICATION_JSON).content("{\"key\":\"race-flag\",\"name\":\"Race\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String flagId = JsonPath.read(created.getResponse().getContentAsString(), "$.id");

        ExecutorService pool = Executors.newFixedThreadPool(WRITERS);
        CountDownLatch ready = new CountDownLatch(WRITERS);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();
        for (int i = 0; i < WRITERS; i++) {
            int percentage = 10 + i;
            results.add(pool.submit(() -> {
                ready.countDown();
                go.await();
                return mvc.perform(put("/api/v1/flags/" + flagId + "/environments/production")
                                .header(AUTHORIZATION, bearer(token))
                                .contentType(APPLICATION_JSON)
                                .content("{\"enabled\":true,\"rolloutPercentage\":%d,\"version\":0}".formatted(percentage)))
                        .andReturn().getResponse().getStatus();
            }));
        }
        ready.await();
        go.countDown();

        int succeeded = 0;
        int conflicted = 0;
        for (Future<Integer> result : results) {
            int httpStatus = result.get(30, TimeUnit.SECONDS);
            if (httpStatus == 200) {
                succeeded++;
            } else if (httpStatus == 409) {
                conflicted++;
            }
        }
        pool.shutdown();

        assertThat(succeeded).isEqualTo(1);
        assertThat(conflicted).isEqualTo(WRITERS - 1);
        mvc.perform(get("/api/v1/flags/" + flagId).header(AUTHORIZATION, bearer(token)))
                .andExpect(jsonPath("$.environments[?(@.environmentKey=='production')].version")
                        .value(Matchers.contains(1)));
    }
}
