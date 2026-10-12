package com.gcmb.backend.administration.pricing.dto;

import java.util.UUID;

public record RoomTypeView(UUID id, String code, String name, String serviceType,
                           Integer capacity, String description, boolean active) {}
