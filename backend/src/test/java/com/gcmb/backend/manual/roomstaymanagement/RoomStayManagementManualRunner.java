package com.gcmb.backend.manual.roomstaymanagement;

import com.gcmb.backend.manual.support.ManualConsole;

/** Menu-driven Java entry point; selecting an ID uses its prepared data and always rolls back DB changes. */
public final class RoomStayManagementManualRunner {
    private RoomStayManagementManualRunner() {}
    public static void main(String[] args) throws Exception { ManualConsole.run("roomstaymanagement", args); }
}
