/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: HuyDV
 * Created: 14/10/2026
 * Description: Lọc các trường được phép hiển thị trong chi tiết nhật ký, loại bỏ mật khẩu, hash và token khỏi dữ liệu trả về.
 */
package com.gcmb.backend.administration.audit.service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import tools.jackson.databind.ObjectMapper;

public final class AuditSnapshots {
    private AuditSnapshots() {}
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Set<String> FIELDS = Set.of(
        "address","notes","updated_at","closed_on","username","role","branchName","status","fullName","email","phone","id","code","name",
        "branch_id","room_type_id","mode","capacity","description","floor_number","operating_status",
        "status_reason","expected_ready_at","active","version","valid_from","valid_to","minute_from",
        "minute_to","weekdays","day_kind","specific_date","hourly_price","overtime_hourly_price",
        "billing_step_minutes","priority","price","duration_minutes","deposit_required","day_group",
        "min_guests","included_description","contact_name","contact_phone","guest_count","planned_start",
        "planned_end","actual_checkin","actual_checkout","note","channel","pricing_mode","managerId",
        "combos","priceRules","event","type","reason","details","previousRoomId","previousRoomCode","roomId","roomCode",
        "changeCause","changedAt","plannedEnd","previousAmount","updatedAmount","adjustmentAmount",
        "usedSegmentAmount","nextSegmentAmount","pricingMode","comboId","hours","startsAt","endsAt",
        "addedAmount","startedAt","billedFrom","reservedUntil","hourlyPrice","stoppedAt","billedMinutes",
        "amount","pastReservationLimit","branchId","expectedAmount","depositRequired","guestCount",
        "depositReceived","depositMethod","actualCheckin","depositBalance","earlyCheckIn",
        "plannedStartBeforeCheckIn","plannedEndBeforeCheckIn","plannedStartAfterCheckIn","plannedEndAfterCheckIn",
        "previousStart","previousEnd","expectedAmountPreserved","cancelledAt","depositOutcome",
        "depositAmount","refundCutoffMinutes","releasedRoomId","refundRequestId","noShowAt");

    public static Map<String,Object> read(String value) {
        if (value == null) return Map.of();
        return clean(JSON.readValue(value, Map.class));
    }

    private static Map<String,Object> clean(Map<?,?> source) {
        Map<String,Object> result = new LinkedHashMap<>();
        source.forEach((key,value) -> {
            if (FIELDS.contains(key)) result.put(key.toString(), cleanValue(value));
        });
        return result;
    }

    private static Object cleanValue(Object value) {
        if (value instanceof Map<?,?> nested) return clean(nested);
        if (value instanceof java.util.List<?> list) return list.stream().map(AuditSnapshots::cleanValue).toList();
        return value;
    }
}
