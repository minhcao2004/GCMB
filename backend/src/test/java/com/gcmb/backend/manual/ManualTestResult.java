package com.gcmb.backend.manual;

/** Observations from one manually selected test case. No pass/fail decision is stored here. */
public record ManualTestResult(
        String actualResult,
        String exception,
        String sideEffects) {
}
