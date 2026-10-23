package com.shop.notification.service;

import com.shop.notification.dto.OrderEvent;
import com.shop.notification.entity.Notification;
import com.shop.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository repo;
    private final JavaMailSender mailSender;

    @Value("${app.mail.from}")
    private String from;

    public void process(OrderEvent e) {
        if (repo.existsByEventId(e.eventId())) {
            log.info("Duplicate event {} ignored", e.eventId());
            return;
        }

        String subject = subject(e);
        String body = body(e);

        Notification n = new Notification();
        n.setEventId(e.eventId());
        n.setOrderId(e.orderId());
        n.setUserId(e.userId());
        n.setRecipient(e.email());
        n.setType(e.type());
        n.setSubject(subject);
        n.setBody(body);

        if (e.email() == null || e.email().isBlank()) {
            n.setStatus("SKIPPED");   // e.g. orders created before emails were stored
        } else {
            send(e.email(), subject, body);   // throws on failure -> retry -> DLQ
            n.setStatus("SENT");
        }

        try {
            repo.save(n);
        } catch (DataIntegrityViolationException dup) {
            log.info("Event {} was stored concurrently, ignoring", e.eventId());
        }
    }

    private void send(String to, String subject, String body) {
        SimpleMailMessage msg = new SimpleMailMessage();
        msg.setFrom(from);
        msg.setTo(to);
        msg.setSubject(subject);
        msg.setText(body);
        mailSender.send(msg);
    }

    private String subject(OrderEvent e) {
        return switch (e.type()) {
            case "ORDER_CREATED" -> "Order #" + e.orderId() + " placed";
            case "ORDER_CANCELLED" -> "Order #" + e.orderId() + " cancelled";
            default -> "Order #" + e.orderId() + " is now " + e.status();
        };
    }

    private String body(OrderEvent e) {
        return switch (e.type()) {
            case "ORDER_CREATED" -> "Thanks for your order. Order #" + e.orderId()
                    + " for INR " + e.totalAmount() + " has been placed.";
            case "ORDER_CANCELLED" -> "Your order #" + e.orderId() + " (INR " + e.totalAmount()
                    + ") has been cancelled and the items returned to stock.";
            default -> "Your order #" + e.orderId() + " status changed to " + e.status() + ".";
        };
    }
}