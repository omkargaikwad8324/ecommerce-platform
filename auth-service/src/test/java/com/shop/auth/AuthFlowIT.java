package com.shop.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;
import java.util.TimeZone;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class AuthFlowIT {

    static {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;

    private ResultActions postJson(String url, Map<String, String> body) throws Exception {
        return mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsString(body)));
    }

    private ResultActions register(String email, String password) throws Exception {
        return postJson("/api/auth/register", Map.of("fullName", "Test User", "email", email, "password", password));
    }

    private ResultActions login(String email, String password) throws Exception {
        return postJson("/api/auth/login", Map.of("email", email, "password", password));
    }

    private ResultActions refresh(String token) throws Exception {
        return postJson("/api/auth/refresh", Map.of("refreshToken", token));
    }

    private JsonNode loginOk(String email, String password) throws Exception {
        String json = login(email, password).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return om.readTree(json);
    }

    private String newUser() throws Exception {
        String email = "u" + UUID.randomUUID() + "@test.com";
        register(email, "Pass@1234").andExpect(status().isCreated());
        return email;
    }

    @Test
    void register_duplicateEmail_returns409_andNeverExposesHash() throws Exception {
        String email = "u" + UUID.randomUUID() + "@test.com";

        register(email, "Pass@1234")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
        register(email, "Pass@1234").andExpect(status().isConflict());
    }

    @Test
    void register_shortPassword_returns400() throws Exception {
        register("u" + UUID.randomUUID() + "@test.com", "123").andExpect(status().isBadRequest());
    }

    @Test
    void login_unknownEmailAndWrongPassword_giveTheSameResponse() throws Exception {
        String email = newUser();

        login("nobody-" + UUID.randomUUID() + "@test.com", "Pass@1234")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid credentials"));
        login(email, "WrongPass1")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid credentials"));
    }

    @Test
    void protectedEndpoint_requiresValidToken() throws Exception {
        String email = newUser();
        String access = loginOk(email, "Pass@1234").get("accessToken").asText();

        mvc.perform(get("/api/users/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/users/me").header("Authorization", "Bearer abc.def.ghi"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/users/me").header("Authorization", "Bearer " + access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email));
    }

    @Test
    void adminEndpoint_userForbidden_adminAllowed() throws Exception {
        String userToken = loginOk(newUser(), "Pass@1234").get("accessToken").asText();
        String adminToken = loginOk("admin@shop.com", "Admin@123").get("accessToken").asText();

        mvc.perform(get("/api/admin/ping").header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/ping").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    @Test
    void refresh_rotatesToken_andReuseRevokesEverySession() throws Exception {
        String email = newUser();
        String r1 = loginOk(email, "Pass@1234").get("refreshToken").asText();

        String body = refresh(r1).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String r2 = om.readTree(body).get("refreshToken").asText();
        assertThat(r2).isNotEqualTo(r1);

        refresh(r1).andExpect(status().isUnauthorized());   // reuse detected
        refresh(r2).andExpect(status().isUnauthorized());   // revoked by the reuse detection
    }

    @Test
    void lockout_afterFiveFailedAttempts_returns423() throws Exception {
        String email = newUser();

        for (int i = 0; i < 5; i++) {
            login(email, "WrongPass1").andExpect(status().isUnauthorized());
        }
        login(email, "Pass@1234").andExpect(status().is(423));
    }

    @Test
    void refresh_withGarbageToken_returns401() throws Exception {
        refresh("not-a-real-token").andExpect(status().isUnauthorized());
    }

    @Test
    void everyResponseCarriesTheRequestId_evenWhenUnauthorized() throws Exception {
        mvc.perform(get("/api/users/me").header("X-Request-Id", "trace-12345678"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("X-Request-Id", "trace-12345678"));
    }
}