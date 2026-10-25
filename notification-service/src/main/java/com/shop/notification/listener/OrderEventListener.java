package com.shop.notification.listener;

import com.shop.notification.config.RabbitConfig;
import com.shop.notification.dto.OrderEvent;
import com.shop.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderEventListener {

    private final NotificationService service;

    @RabbitListener(queues = RabbitConfig.QUEUE)
    public void onOrderEvent(OrderEvent event) {
        if (event.requestId() != null) {
            MDC.put("rid", event.requestId());
        }
        try {
            log.info("Received {} for order {}", event.type(), event.orderId());
            service.process(event);
        } finally {
            MDC.remove("rid");
        }
    }
}