package com.shop.notification.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "notifications")
@Getter
@Setter
@NoArgsConstructor
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String eventId;

    private Long orderId;

    private Long userId;

    private String recipient;

    @Column(nullable = false)
    private String type;

    @Column(nullable = false)
    private String subject;

    @Column(length = 1000)
    private String body;

    @Column(nullable = false)
    private String status;

    private Instant createdAt = Instant.now();
}