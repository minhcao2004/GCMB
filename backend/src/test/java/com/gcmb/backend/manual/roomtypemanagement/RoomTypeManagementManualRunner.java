package com.gcmb.backend.manual.roomtypemanagement;

import com.gcmb.backend.manual.support.ManualConsole;

/** Menu-driven Java entry point; selecting an ID uses its prepared data and always rolls back DB changes. */
public final class RoomTypeManagementManualRunner {
    private RoomTypeManagementManualRunner() {}
    public static void main(String[] args) throws Exception { ManualConsole.run("roomtypemanagement", args); }
}
