/**
 * Copyright (c) 2026 SEP490-G12.
 * Author: HuyDV
 * Created: 14/10/2026
 * Description: Cấu hình SePay phía server; khóa webhook không được trả ra frontend.
 */
package com.gcmb.backend.booking.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class SePaySettings {
    public final String bank;
    public final String account;
    public final String holder;
    private final String webhookKey;

    public SePaySettings(@Value("${SEPAY_BANK:}") String bank,
            @Value("${SEPAY_ACCOUNT_NUMBER:}") String account,
            @Value("${SEPAY_ACCOUNT_HOLDER:}") String holder,
            @Value("${SEPAY_WEBHOOK_KEY:}") String webhookKey) {
        this.bank = bank.trim();
        this.account = account.trim();
        this.holder = holder.trim();
        this.webhookKey = webhookKey;
    }

    public boolean ready() {
        return !bank.isBlank() && !account.isBlank() && !holder.isBlank() && !webhookKey.isBlank();
    }

    public boolean authorized(String header) {
        return ready() && header != null && MessageDigest.isEqual(
            ("Apikey " + webhookKey).getBytes(StandardCharsets.UTF_8), header.getBytes(StandardCharsets.UTF_8));
    }
}
