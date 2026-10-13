package com.gcmb.backend.administration.branch.dto;

import java.util.UUID;

public record RoomView(UUID id, String code, String name, Integer floorNumber, UUID roomTypeId,
                       String roomTypeCode, String roomTypeName, String mode, Integer capacity,
                       String description, String operatingStatus, String statusReason,
                       java.time.OffsetDateTime expectedReadyAt, String bookingStatus) {}
