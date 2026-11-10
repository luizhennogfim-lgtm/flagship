package io.flagship.observability;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import io.flagship.AbstractIntegrationTest;

class RequestIdIT extends AbstractIntegrationTest {

    private static final String UUID_PATTERN = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";

    @Autowired
    MockMvc mvc;

    @Test
    void everyResponseCarriesARequestId() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(header().string(RequestIdFilter.HEADER, matchesPattern(UUID_PATTERN)));
    }

    @Test
    void aSafeIdSentByTheCallerIsKept() throws Exception {
        mvc.perform(get("/actuator/health").header(RequestIdFilter.HEADER, "trace-abc-123"))
                .andExpect(header().string(RequestIdFilter.HEADER, "trace-abc-123"));
    }

    @Test
    void anUnsafeIdIsReplaced() throws Exception {
        mvc.perform(get("/actuator/health").header(RequestIdFilter.HEADER, "bad value\nERROR forged"))
                .andExpect(header().string(RequestIdFilter.HEADER, not(equalTo("bad value\nERROR forged"))))
                .andExpect(header().string(RequestIdFilter.HEADER, matchesPattern(UUID_PATTERN)));
    }

    @Test
    void rejectedRequestsAreTraceableToo() throws Exception {
        mvc.perform(get("/api/v1/flags"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().exists(RequestIdFilter.HEADER));
    }
}
