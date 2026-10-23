package com.shop.order.messaging;

import com.shop.order.dto.OrderEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderEventPublisher {

    public static final String EXCHANGE = "shop.orders";

    private final RabbitTemplate rabbit;

    /** Best effort: a broker outage is logged and never fails the order. */
    public void publish(String routingKey, OrderEvent event) {
        try {
            rabbit.convertAndSend(EXCHANGE, routingKey, event);
        } catch (AmqpException e) {
            log.error("EVENT LOST {} for order {}: {}", routingKey, event.orderId(), e.getMessage());
        }
    }
}