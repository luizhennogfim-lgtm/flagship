package io.flagship;

import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;

/** Small helpers shared by integration tests: every call creates fresh, isolated data. */
public final class TestApi {

    public static final String PASSWORD = "Str0ngPass!";

    public record Registered(String token, String email, String organizationId) {
    }

    private TestApi() {
    }

    /** Registers a brand-new user with a brand-new organization (the user is its OWNER). */
    public static Registered registerOwner(MockMvc mvc) throws Exception {
        String email = "owner-" + UUID.randomUUID() + "@example.com";
        String body = """
                {"fullName":"Owner","email":"%s","password":"%s","organizationName":"Org %s"}
                """.formatted(email, PASSWORD, UUID.randomUUID().toString().substring(0, 8));
        MvcResult result = mvc.perform(post("/api/v1/auth/register").contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn();
        String json = result.getResponse().getContentAsString();
        return new Registered(JsonPath.read(json, "$.accessToken"), email, JsonPath.read(json, "$.organizationId"));
    }

    /** Adds a member with the given role to the owner's organization and returns that member's token. */
    public static String memberToken(MockMvc mvc, String ownerToken, String role) throws Exception {
        String email = role.toLowerCase() + "-" + UUID.randomUUID() + "@example.com";
        String body = """
                {"fullName":"Member","email":"%s","password":"%s","role":"%s"}
                """.formatted(email, PASSWORD, role);
        mvc.perform(post("/api/v1/members").header(AUTHORIZATION, bearer(ownerToken))
                        .contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
        MvcResult login = mvc.perform(post("/api/v1/auth/login").contentType(APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();
        return JsonPath.read(login.getResponse().getContentAsString(), "$.accessToken");
    }

    public static String bearer(String token) {
        return "Bearer " + token;
    }

    /** Creates an API key and returns the full key (only available at creation). */
    public static String createApiKey(MockMvc mvc, String token) throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/api-keys").header(AUTHORIZATION, bearer(token))
                        .contentType(APPLICATION_JSON).content("{\"name\":\"test key\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.key");
    }

    public static String createFlag(MockMvc mvc, String token, String key) throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/flags").header(AUTHORIZATION, bearer(token))
                        .contentType(APPLICATION_JSON)
                        .content("{\"key\":\"%s\",\"name\":\"Flag %s\"}".formatted(key, key)))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    public static long currentVersion(MockMvc mvc, String token, String flagId, String environment) throws Exception {
        MvcResult result = mvc.perform(get("/api/v1/flags/" + flagId).header(AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andReturn();
        List<Number> versions = JsonPath.read(result.getResponse().getContentAsString(),
                "$.environments[?(@.environmentKey=='" + environment + "')].version");
        return versions.get(0).longValue();
    }

    /** Sets the flag state, reading the current version first so the call never conflicts. */
    public static void configure(MockMvc mvc, String token, String flagId, String environment,
                                 boolean enabled, int percentage) throws Exception {
        long version = currentVersion(mvc, token, flagId, environment);
        mvc.perform(put("/api/v1/flags/" + flagId + "/environments/" + environment)
                        .header(AUTHORIZATION, bearer(token))
                        .contentType(APPLICATION_JSON)
                        .content("{\"enabled\":%s,\"rolloutPercentage\":%d,\"version\":%d}".formatted(enabled, percentage, version)))
                .andExpect(status().isOk());
    }

    public static ResultActions evaluate(MockMvc mvc, String apiKey, String flagKey, String userId,
                                         String environment) throws Exception {
        return mvc.perform(post("/api/v1/evaluate").header("X-API-Key", apiKey)
                .contentType(APPLICATION_JSON)
                .content("{\"flagKey\":\"%s\",\"userId\":\"%s\",\"environment\":\"%s\"}".formatted(flagKey, userId, environment)));
    }
}
