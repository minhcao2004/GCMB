package com.gcmb.backend.manual.branchmanagement;

import com.gcmb.backend.manual.support.ManualConsole;

/** Menu-driven Java entry point; selecting an ID uses its prepared data and always rolls back DB changes. */
public final class BranchManagementManualRunner {
    private BranchManagementManualRunner() {}
    public static void main(String[] args) throws Exception { ManualConsole.run("branchmanagement", args); }
}
