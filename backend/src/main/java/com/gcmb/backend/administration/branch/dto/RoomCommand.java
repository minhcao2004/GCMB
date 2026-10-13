package com.gcmb.backend.administration.branch.dto;

import java.util.UUID;

public record RoomCommand(String code, String name, Integer floorNumber, UUID roomTypeId,
                          Integer capacity, String description) {}
