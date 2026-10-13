package com.gcmb.backend.administration.branch.dto;

import java.time.OffsetDateTime;

public record RoomStatusCommand(String status, String reason, OffsetDateTime expectedReadyAt) {}
