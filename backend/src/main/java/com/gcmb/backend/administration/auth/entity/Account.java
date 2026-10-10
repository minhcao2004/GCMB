package com.gcmb.backend.administration.auth.entity;

import java.util.UUID;

public record Account(UUID id, String username, String email, String hash, String status, String role) {}
