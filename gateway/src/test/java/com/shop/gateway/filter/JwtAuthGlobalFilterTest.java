package com.shop.gateway.filter;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class JwtAuthGlobalFilterTest {

    private static final String SECRET = "test-secret-test-secret-test-secret-123";

    private JwtAuthGlobalFilter filter;
    private GatewayFilterChain chain;

    @BeforeEach
    void setUp() {
        filter = new JwtAuthGlobalFilter(SECRET);
        chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());
    }

    private String token(String secret, String role, Instant expiry) {
        SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        return Jwts.builder().subject("a@b.com").claim("uid", 7).claim("role", role)
                .expiration(Date.from(expiry)).signWith(key).compact();
    }

    private HttpHeaders forwardedHeaders() {
        ArgumentCaptor<ServerWebExchange> captor = ArgumentCaptor.forClass(ServerWebExchange.class);
        verify(chain).filter(captor.capture());
        return captor.getValue().getRequest().getHeaders();
    }

    @Test
    void publicPath_passesThroughWithoutToken() {
        MockServerWebExchange ex = MockServerWebExchange.from(MockServerHttpRequest.post("/api/auth/login").build());

        filter.filter(ex, chain).block();

        verify(chain).filter(any());
    }

    @Test
    void missingToken_returns401() {
        MockServerWebExchange ex = MockServerWebExchange.from(MockServerHttpRequest.get("/api/users/me").build());

        filter.filter(ex, chain).block();

        assertThat(ex.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(chain, never()).filter(any());
    }

    @Test
    void validToken_forwardsTrustedIdentityHeaders() {
        String jwt = token(SECRET, "ADMIN", Instant.now().plusSeconds(60));
        MockServerWebExchange ex = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/products/1").header("Authorization", "Bearer " + jwt).build());

        filter.filter(ex, chain).block();

        HttpHeaders h = forwardedHeaders();
        assertThat(h.getFirst("X-User-Id")).isEqualTo("7");
        assertThat(h.getFirst("X-User-Email")).isEqualTo("a@b.com");
        assertThat(h.getFirst("X-User-Role")).isEqualTo("ADMIN");
    }

    @Test
    void forgedHeaders_areReplacedByTokenClaims() {
        String jwt = token(SECRET, "USER", Instant.now().plusSeconds(60));
        MockServerWebExchange ex = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/products/1")
                        .header("Authorization", "Bearer " + jwt)
                        .header("X-User-Role", "ADMIN")
                        .header("X-User-Id", "999")
                        .build());

        filter.filter(ex, chain).block();

        HttpHeaders h = forwardedHeaders();
        assertThat(h.get("X-User-Role")).containsExactly("USER");
        assertThat(h.get("X-User-Id")).containsExactly("7");
    }

    @Test
    void forgedHeaders_onPublicPath_areStripped() {
        MockServerWebExchange ex = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/auth/login").header("X-User-Role", "ADMIN").build());

        filter.filter(ex, chain).block();

        assertThat(forwardedHeaders().containsKey("X-User-Role")).isFalse();
    }

    @Test
    void expiredToken_returns401() {
        String jwt = token(SECRET, "USER", Instant.now().minusSeconds(60));
        MockServerWebExchange ex = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/users/me").header("Authorization", "Bearer " + jwt).build());

        filter.filter(ex, chain).block();

        assertThat(ex.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(chain, never()).filter(any());
    }

    @Test
    void tokenSignedWithAnotherKey_returns401() {
        String jwt = token("another-secret-another-secret-another-1", "ADMIN", Instant.now().plusSeconds(60));
        MockServerWebExchange ex = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/admin/ping").header("Authorization", "Bearer " + jwt).build());

        filter.filter(ex, chain).block();

        assertThat(ex.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(chain, never()).filter(any());
    }
}