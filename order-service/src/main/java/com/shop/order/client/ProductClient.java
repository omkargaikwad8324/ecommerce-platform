package com.shop.order.client;

import com.shop.order.exception.ApiException;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Map;

@Slf4j
@Component
public class ProductClient {

    private final RestClient client;

    public ProductClient(@Value("${app.product-service.url}") String baseUrl,
                         @Value("${app.internal-key}") String internalKey) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(2000);
        factory.setReadTimeout(3000);
        this.client = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .defaultHeader("X-Internal-Key", internalKey)
                .build();
    }

    /**
     * Atomically decrements stock and returns the product (name, price) as it is right now.
     */
    public ProductInfo reserve(Long productId, int quantity) {
        try {
            return client.post()
                    .uri("/internal/products/{id}/reserve", productId)
                    .headers(ProductClient::addRequestId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("quantity", quantity))
                    .retrieve()
                    .body(ProductInfo.class);
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode().value() == 404) {
                throw new ApiException(HttpStatus.NOT_FOUND, "Product " + productId + " not found");
            }
            if (e.getStatusCode().value() == 409) {
                throw new ApiException(HttpStatus.CONFLICT, "Insufficient stock for product " + productId);
            }
            log.error("Unexpected response from product-service: {}", e.getStatusCode());
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Product service rejected the request");
        } catch (RestClientException e) {
            log.error("Product service unreachable: {}", e.getMessage());
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Product service unavailable");
        }
    }

    /**
     * Compensation call. Never throws: a failed release is logged for manual repair.
     */
    public void release(Long productId, int quantity) {
        try {
            client.post()
                    .uri("/internal/products/{id}/release", productId)
                    .headers(ProductClient::addRequestId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("quantity", quantity))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException e) {
            log.error("STOCK LEAK: failed to release {} x product {}: {}", quantity, productId, e.getMessage());
        }
    }

    private static void addRequestId(HttpHeaders headers) {
        String rid = MDC.get("rid");
        if (rid != null) {
            headers.set("X-Request-Id", rid);
        }
    }
}