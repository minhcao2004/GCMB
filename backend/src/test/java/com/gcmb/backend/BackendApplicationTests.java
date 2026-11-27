package com.gcmb.backend;

/** Plain Java entry point for tester-operated UT; no test framework or automatic verdict. */
public final class BackendApplicationTests {
    private BackendApplicationTests() {}
    public static void main(String[] args) throws Exception {
        com.gcmb.backend.manual.all.AllManualRunner.main(args);
    }
}
