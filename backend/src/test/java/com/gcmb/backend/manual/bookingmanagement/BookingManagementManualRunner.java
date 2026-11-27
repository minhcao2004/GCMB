package com.gcmb.backend.manual.bookingmanagement;

import com.gcmb.backend.manual.support.ManualConsole;

/** Menu-driven Java entry point; selecting an ID uses its prepared data and always rolls back DB changes. */
public final class BookingManagementManualRunner {
    private BookingManagementManualRunner() {}
    public static void main(String[] args) throws Exception { ManualConsole.run("bookingmanagement", args); }
}
