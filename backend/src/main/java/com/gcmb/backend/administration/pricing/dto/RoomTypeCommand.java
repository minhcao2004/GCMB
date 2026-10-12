package com.gcmb.backend.administration.pricing.dto;

public record RoomTypeCommand(String code, String name, String serviceType,
                              Integer capacity, String description) {}
