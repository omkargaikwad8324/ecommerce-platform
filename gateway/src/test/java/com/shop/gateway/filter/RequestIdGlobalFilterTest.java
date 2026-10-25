package com.shop.gateway.filter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class RequestIdGlobalFilterTest {

    private final RequestIdGlobalFilter filter = new RequestIdGlobalFilter();
    private GatewayFilterChain chain;

    @BeforeEach
    void setUp() {
        chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());
    }

    private String forwardedId() {
        ArgumentCaptor<ServerWebExchange> captor = ArgumentCaptor.forClass(ServerWebExchange.class);
        verify(chain).filter(captor.capture());
        return captor.getValue().getRequest().getHeaders().getFirst("X-Request-Id");
    }

    @Test
    void generatesIdWhenMissing_andReturnsItToTheClient() {
        MockServerWebExchange ex = MockServerWebExchange.from(MockServerHttpRequest.get("/api/products").build());

        filter.filter(ex, chain).block();

        String id = forwardedId();
        assertThat(id).isNotBlank();
        assertThat(ex.getResponse().getHeaders().getFirst("X-Request-Id")).isEqualTo(id);
    }

    @Test
    void keepsValidIncomingId() {
        MockServerWebExchange ex = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/products").header("X-Request-Id", "trace-12345678").build());

        filter.filter(ex, chain).block();

        assertThat(forwardedId()).isEqualTo("trace-12345678");
    }

    @Test
    void replacesInvalidIncomingId() {
        MockServerWebExchange ex = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/products").header("X-Request-Id", "bad id!").build());

        filter.filter(ex, chain).block();

        assertThat(forwardedId()).isNotEqualTo("bad id!").hasSize(36);
    }
}