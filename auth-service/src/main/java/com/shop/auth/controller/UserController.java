package com.shop.auth.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class UserController {

    @GetMapping("/api/users/me")
    public Map<String, Object> me(Authentication auth) {
        return Map.of("email", auth.getName(), "authorities", auth.getAuthorities().toString());
    }

    @GetMapping("/api/admin/ping")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, String> adminPing() {
        return Map.of("message", "Hello admin");
    }
}