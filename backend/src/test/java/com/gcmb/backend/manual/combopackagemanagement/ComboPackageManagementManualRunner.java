package com.gcmb.backend.manual.combopackagemanagement;

import com.gcmb.backend.manual.support.ManualConsole;

/** Menu-driven Java entry point; selecting an ID uses its prepared data and always rolls back DB changes. */
public final class ComboPackageManagementManualRunner {
    private ComboPackageManagementManualRunner() {}
    public static void main(String[] args) throws Exception { ManualConsole.run("combopackagemanagement", args); }
}
