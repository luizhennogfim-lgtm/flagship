package io.flagship.health;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import io.flagship.AbstractIntegrationTest;

/** Health endpoints are public (no token) so load balancers and Docker can call them. */
class HealthIT extends AbstractIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Test
    void theApplicationReportsItselfHealthy() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void livenessAndReadinessAreUp() throws Exception {
        mvc.perform(get("/actuator/health/liveness")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
        mvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void otherEndpointsStillNeedAToken() throws Exception {
        mvc.perform(get("/api/v1/flags")).andExpect(status().isUnauthorized());
    }
}
