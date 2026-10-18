package com.shop.auth.dto;

public record UserResponse(Long id, String fullName, String email, String role) {
}