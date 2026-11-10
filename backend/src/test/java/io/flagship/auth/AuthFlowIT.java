package io.flagship.auth;

import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.jayway.jsonpath.JsonPath;

import io.flagship.AbstractIntegrationTest;

/** End-to-end checks of authentication, RBAC and tenant isolation against real PostgreSQL and Redis. */
class AuthFlowIT extends AbstractIntegrationTest {

    private static final String PASSWORD = "Str0ngPass!";

    @Autowired
    MockMvc mvc;

    @Test
    void registerThenLoginReturnsTokens() throws Exception {
        String email = uniqueEmail();
        register("Acme", email);

        mvc.perform(post("/api/v1/auth/login").contentType(APPLICATION_JSON)
                        .content(loginJson(email, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.role").value("OWNER"));
    }

    @Test
    void wrongPasswordIsUnauthorized() throws Exception {
        String email = uniqueEmail();
        register("Acme", email);

        mvc.perform(post("/api/v1/auth/login").contentType(APPLICATION_JSON)
                        .content(loginJson(email, "wrong-password")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void duplicateEmailIsConflict() throws Exception {
        String email = uniqueEmail();
        register("Acme", email);

        mvc.perform(post("/api/v1/auth/register").contentType(APPLICATION_JSON)
                        .content(registerJson("Other", email)))
                .andExpect(status().isConflict());
    }

    @Test
    void invalidRegistrationReturnsFieldErrors() throws Exception {
        mvc.perform(post("/api/v1/auth/register").contentType(APPLICATION_JSON)
                        .content("{\"fullName\":\"\",\"email\":\"not-an-email\",\"password\":\"123\",\"organizationName\":\"X\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.email").exists())
                .andExpect(jsonPath("$.errors.password").exists());
    }

    @Test
    void protectedEndpointRequiresToken() throws Exception {
        mvc.perform(get("/api/v1/teams")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/teams").header(AUTHORIZATION, "Bearer not-a-real-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void organizationsCannotSeeEachOthersTeams() throws Exception {
        String tokenA = register("Org A", uniqueEmail());
        String tokenB = register("Org B", uniqueEmail());

        mvc.perform(post("/api/v1/teams").header(AUTHORIZATION, "Bearer " + tokenA)
                        .contentType(APPLICATION_JSON).content("{\"name\":\"Backend\"}"))
                .andExpect(status().isCreated());

        mvc.perform(get("/api/v1/teams").header(AUTHORIZATION, "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/api/v1/teams").header(AUTHORIZATION, "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void viewerCannotCreateTeams() throws Exception {
        String ownerToken = register("Acme", uniqueEmail());
        String viewerEmail = uniqueEmail();
        addMember(ownerToken, viewerEmail, "VIEWER", status().isCreated());
        String viewerToken = login(viewerEmail);

        mvc.perform(post("/api/v1/teams").header(AUTHORIZATION, "Bearer " + viewerToken)
                        .contentType(APPLICATION_JSON).content("{\"name\":\"Nope\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/teams").header(AUTHORIZATION, "Bearer " + viewerToken))
                .andExpect(status().isOk());
    }

    @Test
    void adminCannotGrantOwnerRole() throws Exception {
        String ownerToken = register("Acme", uniqueEmail());
        String adminEmail = uniqueEmail();
        addMember(ownerToken, adminEmail, "ADMIN", status().isCreated());
        String adminToken = login(adminEmail);

        addMember(adminToken, uniqueEmail(), "OWNER", status().isForbidden());
        addMember(adminToken, uniqueEmail(), "EDITOR", status().isCreated());
    }

    // ---------- helpers ----------

    private static String uniqueEmail() {
        return "user-" + UUID.randomUUID() + "@example.com";
    }

    private static String registerJson(String organization, String email) {
        return """
                {"fullName":"Test User","email":"%s","password":"%s","organizationName":"%s"}
                """.formatted(email, PASSWORD, organization);
    }

    private static String loginJson(String email, String password) {
        return """
                {"email":"%s","password":"%s"}
                """.formatted(email, password);
    }

    private String register(String organization, String email) throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/auth/register").contentType(APPLICATION_JSON)
                        .content(registerJson(organization, email)))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken");
    }

    private String login(String email) throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/auth/login").contentType(APPLICATION_JSON)
                        .content(loginJson(email, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken");
    }

    private void addMember(String token, String email, String role,
                           org.springframework.test.web.servlet.ResultMatcher expected) throws Exception {
        String body = """
                {"fullName":"Member","email":"%s","password":"%s","role":"%s"}
                """.formatted(email, PASSWORD, role);
        mvc.perform(post("/api/v1/members").header(AUTHORIZATION, "Bearer " + token)
                        .contentType(APPLICATION_JSON).content(body))
                .andExpect(expected);
    }
}
