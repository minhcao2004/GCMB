/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: CaoNM
 * Created: 13/10/2026
 * Description: Stores the observed output, exception and side effects of a manual test.
 */

package com.gcmb.backend.manual;

/** Observations from one manually selected test case. No pass/fail decision is stored here. */
public record ManualTestResult(
        String actualResult,
        String exception,
        String sideEffects) {
}
