/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: CaoNM
 * Created: 13/10/2026
 * Description: Defines the metadata and executable action for a manual test case.
 */

package com.gcmb.backend.manual;

import java.util.Objects;

/** Metadata and isolated executable for one selectable manual test data set. */
public record ManualTestCase(
        String key,
        String documentTestCaseId,
        String sourceTestCaseId,
        String sourceClass,
        String sourceMethod,
        String testType,
        String traceability,
        String preconditions,
        String input,
        String expectedResult,
        Executor executor) {

    public ManualTestCase {
        Objects.requireNonNull(key);
        Objects.requireNonNull(sourceClass);
        Objects.requireNonNull(sourceMethod);
        Objects.requireNonNull(testType);
        Objects.requireNonNull(preconditions);
        Objects.requireNonNull(input);
        Objects.requireNonNull(expectedResult);
        Objects.requireNonNull(executor);
    }

    public String displayId() {
        String document = documentTestCaseId == null || documentTestCaseId.isBlank()
                ? "UNMAPPED"
                : documentTestCaseId;
        if (sourceTestCaseId == null || sourceTestCaseId.isBlank()
                || sourceTestCaseId.equals(document)) {
            return document;
        }
        return document + " / " + sourceTestCaseId;
    }

    @FunctionalInterface
    public interface Executor {
        ManualTestResult execute() throws Exception;
    }
}
