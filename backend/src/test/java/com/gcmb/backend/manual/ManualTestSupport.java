/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: CaoNM
 * Created: 13/10/2026
 * Description: Captures test outcomes and renders exceptions and mock invocations.
 */

package com.gcmb.backend.manual;

import com.gcmb.backend.administration.auth.exception.AuthFailure;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import org.mockito.Mockito;
import org.mockito.invocation.Invocation;

/** Helpers for capturing observations without assertions or pass/fail calculation. */
public final class ManualTestSupport {
    private ManualTestSupport() {
    }

    public static ManualTestResult observe(ThrowingSupplier<?> action, Object... testDoubles) {
        Object actual = null;
        Throwable thrown = null;
        try {
            actual = action.get();
        } catch (Throwable failure) {
            thrown = unwrap(failure);
        }
        return new ManualTestResult(
                thrown == null ? renderActual(actual) : "No return value because an exception was thrown",
                renderException(thrown),
                renderInvocations(testDoubles));
    }

    public static ManualTestResult observeVoid(ThrowingRunnable action, Object... testDoubles) {
        return observe(() -> {
            action.run();
            return "Method completed without throwing";
        }, testDoubles);
    }

    public static String capture(ThrowingRunnable action) {
        try {
            action.run();
            return "completed without throwing";
        } catch (Throwable failure) {
            return renderException(unwrap(failure));
        }
    }

    public static String renderInvocations(Object... testDoubles) {
        List<Invocation> invocations = new ArrayList<>();
        for (Object testDouble : testDoubles) {
            if (testDouble != null && Mockito.mockingDetails(testDouble).isMock()) {
                invocations.addAll(Mockito.mockingDetails(testDouble).getInvocations());
            }
        }
        invocations.sort(Comparator.comparingInt(Invocation::getSequenceNumber));
        if (invocations.isEmpty()) {
            return "No dependency interaction observed";
        }
        return invocations.stream()
                .map(invocation -> Mockito.mockingDetails(invocation.getMock())
                        .getMockCreationSettings().getTypeToMock().getSimpleName()
                        + "." + invocation.getMethod().getName()
                        + "(" + Arrays.stream(invocation.getArguments())
                        .map(ManualTestSupport::safeValue)
                        .collect(Collectors.joining(", ")) + ")")
                .collect(Collectors.joining(System.lineSeparator()));
    }

    public static String renderException(Throwable failure) {
        if (failure == null) {
            return "None";
        }
        if (failure instanceof AuthFailure authFailure) {
            return authFailure.getClass().getName()
                    + " {status=" + authFailure.status()
                    + ", message=" + authFailure.getMessage() + "}";
        }
        return failure.getClass().getName() + ": " + safeValue(failure.getMessage());
    }

    private static Throwable unwrap(Throwable failure) {
        if (failure instanceof InvocationTargetException invocation && invocation.getCause() != null) {
            return invocation.getCause();
        }
        return failure;
    }

    private static String renderActual(Object actual) {
        return actual == null ? "null" : safeValue(actual);
    }

    private static String safeValue(Object value) {
        if (value == null) {
            return "null";
        }
        String text = String.valueOf(value);
        return text.replace("\r", "\\r").replace("\n", "\\n").replace("\t", "\\t");
    }

    @FunctionalInterface
    public interface ThrowingSupplier<T> {
        T get() throws Exception;
    }

    @FunctionalInterface
    public interface ThrowingRunnable {
        void run() throws Exception;
    }
}
