/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: CaoNM
 * Created: 20/10/2026
 * Description: Describes one selectable manual test and its executable action.
 */

package com.gcmb.backend.manual;

import java.util.Objects;

public record ManualTestCase(String key,String id,String sourceClass,String sourceMethod,
    String preconditions,String input,String expectedResult,Executor executor) {
    public ManualTestCase {
        Objects.requireNonNull(key);
        Objects.requireNonNull(id);
        Objects.requireNonNull(sourceClass);
        Objects.requireNonNull(sourceMethod);
        Objects.requireNonNull(preconditions);
        Objects.requireNonNull(input);
        Objects.requireNonNull(expectedResult);
        Objects.requireNonNull(executor);
    }

    public String displayId() {
        return id;
    }

    public static String identifier(String documentId,String sourceId) {
        String document=documentId==null||documentId.isBlank()?"UNMAPPED":documentId;
        return sourceId==null||sourceId.isBlank()||sourceId.equals(document)?document:document+" / "+sourceId;
    }
    @FunctionalInterface public interface Executor {
        ManualTestResult execute() throws Exception;
    }
}
