package com.gcmb.backend.administration.auth.dto.response;

import java.util.UUID;

public record Identity(UUID id, String username, String role, String destination) {}
