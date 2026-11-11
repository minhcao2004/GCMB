/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: CaoNM
 * Created: 29/10/2026
 * Description: Runs Deposit Management manual unit tests with spreadsheet test case IDs.
 */
package com.gcmb.backend.manual.depositmanagement;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gcmb.backend.auth.dto.Identity;
import com.gcmb.backend.booking.dto.CheckoutDtos;
import com.gcmb.backend.booking.dto.DepositDtos;
import com.gcmb.backend.booking.exception.BookingFailure;
import com.gcmb.backend.booking.repository.BookingRepository;
import com.gcmb.backend.booking.repository.BookingRepository.BookingRow;
import com.gcmb.backend.booking.repository.BookingRepository.BranchScope;
import com.gcmb.backend.booking.repository.DepositRepository;
import com.gcmb.backend.booking.repository.DepositRepository.RefundEvidenceRow;
import com.gcmb.backend.booking.repository.DepositRepository.RefundRequestRow;
import com.gcmb.backend.booking.service.BookingPricingService;
import com.gcmb.backend.booking.service.BookingService;
import com.gcmb.backend.booking.service.CheckoutService;
import com.gcmb.backend.booking.service.DepositManagementService;
import com.gcmb.backend.booking.service.RefundEvidenceStorageService;
import com.gcmb.backend.booking.service.SePaySettings;
import com.gcmb.backend.inventory.service.FoodOrderService;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Scanner;
import java.util.Set;
import java.util.UUID;
import org.mockito.invocation.InvocationOnMock;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.services.s3.S3Client;
import tools.jackson.databind.ObjectMapper;

public final class DepositManagementManualRunner {
    private static final int EXPECTED_CASES = 74;
    static final UUID RECEPTIONIST_ID = UUID.fromString("47570000-0000-4000-8000-000000000001");
    static final UUID ACCOUNTANT_ID = UUID.fromString("47570000-0000-4000-8000-000000000006");
    static final UUID BRANCH_ID = UUID.fromString("47570000-0000-4000-8000-000000000101");
    static final UUID BOOKING_ID = UUID.fromString("47570000-0000-4000-8000-000000000201");
    static final UUID DEPOSIT_ID = UUID.fromString("47570000-0000-4000-8000-000000000301");
    static final UUID REQUEST_ID = UUID.fromString("47570000-0000-4000-8000-000000000401");
    static final UUID ACCOUNT_ID = UUID.fromString("47570000-0000-4000-8000-000000000501");
    static final String REFERENCE = "GZ1234567890ABCDEF1234";
    static final BranchScope BRANCH = new BranchScope(BRANCH_ID, "HN_HOA_BANG", ZoneId.of("Asia/Ho_Chi_Minh"));
    static final String PRE42 = "Lễ tân letan.hoabang đăng nhập tại HN_HOA_BANG đang hoạt động; booking thuộc cùng chi nhánh; dữ liệu Flyway V14; production code thật với mock/Recording JDBC.";
    static final String PRE44 = "Kế toán ketoan đăng nhập; có request hoàn cọc PENDING của booking CANCELLED đủ điều kiện, còn số dư cọc; dữ liệu Flyway V14; production code thật với mock/Recording JDBC.";
    static final String PRE44_REQUEST = "Lễ tân letan.hoabang đăng nhập tại HN_HOA_BANG đang hoạt động; booking thuộc cùng cơ sở; dữ liệu Flyway V14; production code thật với mock/Recording JDBC.";
    static final String PRE44_RECEPTION = "Lễ tân letan.hoabang đăng nhập tại HN_HOA_BANG đang hoạt động; dữ liệu Flyway V14; production code thật với mock/Recording JDBC.";

    private DepositManagementManualRunner() {
    }

    public static void main(String[] args) {
        List<ManualTest> cases = allTests();
        if (args.length == 0) {
            menu(cases);
            return;
        }
        switch (args[0]) {
            case "--all" -> run(cases);
            case "--list" -> list(cases);
            case "--case" -> run(args.length < 2 ? List.of()
                    : cases.stream().filter(test -> test.id().equalsIgnoreCase(args[1])).toList());
            default -> System.out.println("Options: --all | --list | --case <ID>");
        }
    }

    private static List<ManualTest> allTests() {
        List<ManualTest> cases = new ArrayList<>();
        cases.addAll(CollectBookingDepositManualCases.cases());
        cases.addAll(RefundBookingDepositManualCases.cases());
        cases.sort(Comparator.comparing(ManualTest::id));
        validate(cases);
        return List.copyOf(cases);
    }

    private static void menu(List<ManualTest> cases) {
        try (Scanner scanner = new Scanner(System.in)) {
            while (true) {
                System.out.println("\nDEPOSIT MANAGEMENT - MANUAL UNIT TEST");
                System.out.println("1. List test cases");
                System.out.println("2. Run all test cases");
                System.out.println("3. Run one test case");
                System.out.println("0. Exit");
                System.out.print("Select: ");
                String option = scanner.nextLine().trim();
                if ("0".equals(option)) return;
                if ("1".equals(option)) list(cases);
                if ("2".equals(option)) run(cases);
                if ("3".equals(option)) {
                    System.out.print("Test Case ID: ");
                    String id = scanner.nextLine().trim();
                    run(cases.stream().filter(test -> test.id().equalsIgnoreCase(id)).toList());
                }
            }
        }
    }

    private static void list(List<ManualTest> cases) {
        cases.forEach(test -> System.out.printf("%-8s | %-34s | %s%n",
                test.id(), test.useCase(), test.sourceMethod()));
        System.out.println("Total: " + cases.size());
    }

    private static void run(List<ManualTest> cases) {
        if (cases.isEmpty()) {
            System.out.println("No matching test case.");
            return;
        }
        int passed = 0;
        for (ManualTest test : cases) {
            TestResult result = test.execute();
            if (result.passed()) passed++;
            System.out.println("\n[" + test.id() + "] " + test.useCase());
            System.out.println("Source      : " + test.sourceMethod());
            System.out.println("Precondition: " + test.preconditions());
            System.out.println("Input       : " + test.input());
            System.out.println("Expected    : " + test.expected());
            System.out.println("Actual      : " + result.actual());
            System.out.println("VERDICT     : " + (result.passed() ? "PASSED" : "FAILED"));
        }
        System.out.printf("%nSUMMARY: total=%d, passed=%d, failed=%d%n",
                cases.size(), passed, cases.size() - passed);
    }

    private static void validate(List<ManualTest> cases) {
        if (cases.size() != EXPECTED_CASES) {
            throw new IllegalStateException("Expected " + EXPECTED_CASES + " cases but found " + cases.size());
        }
        Set<String> ids = new HashSet<>();
        for (ManualTest test : cases) {
            if (!test.id().matches("DPM-\\d{3}") || !ids.add(test.id())) {
                throw new IllegalStateException("Invalid or duplicate Test Case ID: " + test.id());
            }
        }
    }

    static ManualTest test(String id, String useCase, String sourceMethod, String preconditions,
            String input, String expected, String type, String note) {
        return new ManualTest(id, useCase, sourceMethod, preconditions, input, expected, type, note,
                () -> executeCase(id));
    }

    static TestResult executeCase(String id) {
        int number = Integer.parseInt(id.substring(4));
        if (number <= 28) return runCollection(number);
        return runRefund(number);
    }

    private static TestResult runCollection(int number) {
        if (number == 1 || number == 2) {
            BigDecimal input = number == 1 ? new BigDecimal("100000") : new BigDecimal("101");
            BigDecimal expected = number == 1 ? new BigDecimal("30000") : new BigDecimal("31");
            BigDecimal actual = BookingPricingService.depositFor(input);
            return check(actual.compareTo(expected) == 0, "deposit=" + actual);
        }
        CheckoutFixture fixture = checkoutFixture();
        if (number <= 14) return runDepositPlan(number, fixture);
        return runDepositWebhook(number, fixture);
    }

    private static TestResult runDepositPlan(int number, CheckoutFixture f) {
        switch (number) {
            case 3 -> {
                try {
                    Map<String, Object> actual = f.service().confirmDeposit(
                            receptionist(), BOOKING_ID, "FT-20261110-001", new BigDecimal("30000"));
                    return check(!f.db().depositReceived && f.db().financialTransactions == 0,
                            "status=" + actual.get("status") + ", deposit received=" + f.db().depositReceived
                                    + ", financial transactions=" + f.db().financialTransactions);
                } catch (Throwable failure) {
                    return unexpected(failure);
                }
            }
            case 4 -> {
                f.db().depositReceived = true;
                try {
                    Map<String, Object> actual = f.service().confirmDeposit(
                            receptionist(), BOOKING_ID, "FT-20261110-001", new BigDecimal("30000"));
                    verify(f.repository(), never()).receiveDeposit(any(), any(), any(), any(), anyString(), anyString());
                    return check("SETTLED".equals(actual.get("status")) && f.db().financialTransactions == 0,
                            "status=" + actual.get("status") + ", new financial transactions=" + f.db().financialTransactions);
                } catch (Throwable failure) {
                    return unexpected(failure);
                }
            }
            case 5 -> {
                when(f.repository().booking(BOOKING_ID, BRANCH_ID)).thenReturn(booking("DRAFT"));
                return expectFailure(() -> f.service().confirmDeposit(
                        receptionist(), BOOKING_ID, "FT-20261110-001", new BigDecimal("30000")), 409,
                        "Chỉ xác nhận cọc cho booking chưa nhận phòng.");
            }
            case 6 -> {
                return expectFailure(() -> f.service().confirmDeposit(
                        receptionist(), BOOKING_ID, null, new BigDecimal("30000")), 400,
                        "Vui lòng bổ sung nội dung chuyển khoản.");
            }
            case 7 -> {
                return expectFailure(() -> f.service().confirmDeposit(
                        receptionist(), BOOKING_ID, "   ", new BigDecimal("30000")), 400,
                        "Vui lòng bổ sung nội dung chuyển khoản.");
            }
            case 8 -> {
                when(f.repository().activeChargeTotal(BOOKING_ID)).thenReturn(new BigDecimal("101"));
                try {
                    Map<String, Object> actual = f.service().confirmDeposit(
                            receptionist(), BOOKING_ID, "FT-20261110-002", new BigDecimal("31"));
                    return check(!f.db().depositReceived,
                            "status=" + actual.get("status") + ", deposit received=" + f.db().depositReceived);
                } catch (Throwable failure) {
                    return unexpected(failure);
                }
            }
            case 9 -> {
                return expectFailure(() -> f.service().confirmDeposit(
                        receptionist(), BOOKING_ID, "FT-20261110-003", null), 409,
                        "Chưa thể xác nhận kết quả thu tiền cọc. Vui lòng kiểm tra lại trạng thái của booking trước khi thực hiện lại.");
            }
            case 10 -> {
                return expectFailure(() -> f.service().confirmDeposit(
                        receptionist(), BOOKING_ID, "FT-20261110-004", new BigDecimal("29999")), 409,
                        "Chưa thể xác nhận kết quả thu tiền cọc. Vui lòng kiểm tra lại trạng thái của booking trước khi thực hiện lại.");
            }
            case 11 -> {
                when(f.repository().activeChargeTotal(BOOKING_ID)).thenReturn(BigDecimal.ZERO);
                return expectFailure(() -> f.service().confirmDeposit(
                        receptionist(), BOOKING_ID, "FT-20261110-005", BigDecimal.ZERO), 400,
                        "Số tiền cọc phải lớn hơn 0 đ.");
            }
            case 12 -> {
                doThrow(new BookingFailure(403, "form",
                        "Cơ sở đang tạm ngừng hoạt động. Bạn có thể xem dữ liệu nhưng không thể thực hiện thay đổi."))
                        .when(f.repository()).requireActiveBranch(BRANCH_ID);
                return expectFailure(() -> f.service().confirmDeposit(
                        receptionist(), BOOKING_ID, "FT-20261110-006", new BigDecimal("30000")), 403,
                        "Cơ sở đang tạm ngừng hoạt động. Bạn có thể xem dữ liệu nhưng không thể thực hiện thay đổi.");
            }
            case 13 -> {
                Identity wrong = new Identity(ACCOUNTANT_ID, "ketoan", "ACCOUNTANT", "/accounting", null, null);
                return expectFailure(() -> f.service().confirmDeposit(
                        wrong, BOOKING_ID, "FT-20261110-007", new BigDecimal("30000")), 403,
                        "Bạn không có quyền thu tiền cọc đối với booking.");
            }
            case 14 -> {
                f.db().bookingLocked = false;
                when(f.repository().bookingExists(BOOKING_ID)).thenReturn(true);
                return expectFailure(() -> f.service().confirmDeposit(
                        receptionist(), BOOKING_ID, "FT-20261110-008", new BigDecimal("30000")), 403,
                        "Bạn không có quyền thu tiền cọc đối với booking.");
            }
            default -> throw new IllegalArgumentException("Unknown plan case " + number);
        }
    }

    private static TestResult runDepositWebhook(int number, CheckoutFixture f) {
        f.db().requestExists = true;
        f.db().referenceFound = true;
        CheckoutDtos.Webhook basePayload = payload(991L, "VCB", "0123456789", "in", new BigDecimal("30000"), Instant.now());
        switch (number) {
            case 15 -> {
                return callWebhook(f, basePayload,
                        state -> state.payments == 1 && state.depositEntries == 1
                                && state.sql.stream().anyMatch(sql -> sql.contains("INSERT INTO gcmb.audit_event")),
                        "matched SePay deposit confirmation");
            }
            case 16 -> {
                return expectFailure(() -> f.service().webhook("Apikey wrong", basePayload), 401, null);
            }
            case 17 -> {
                return expectFailure(() -> f.service().webhook("Apikey test-key",
                        payload(null, "VCB", "0123456789", "in", new BigDecimal("30000"), Instant.now())), 400, null);
            }
            case 18 -> {
                return expectFailure(() -> f.service().webhook("Apikey test-key",
                        payload(991L, "VCB", "0123456789", "in", BigDecimal.ZERO, Instant.now())), 400,
                        "Số tiền giao dịch phải lớn hơn 0 đ.");
            }
            case 19 -> {
                return expectFailure(() -> f.service().webhook("Apikey test-key",
                        payload(991L, "OTHER", "000", "in", new BigDecimal("30000"), Instant.now())), 400, null);
            }
            case 20 -> {
                return expectFailure(() -> f.service().webhook("Apikey test-key",
                        payload(991L, "VCB", "0123456789", "sideways", new BigDecimal("30000"), Instant.now())), 400, null);
            }
            case 21 -> {
                f.db().referenceFound = false;
                return callWebhook(f, basePayload, state -> state.bankTransactions == 1 && state.payments == 0,
                        "UNMATCHED bank transaction stored; payment/deposit writes=0");
            }
            case 22 -> {
                CheckoutDtos.Webhook mismatch = payload(991L, "VCB", "0123456789", "in", new BigDecimal("29999"), Instant.now());
                return callWebhook(f, mismatch, state -> state.exceptionMatches == 1 && state.payments == 0,
                        "amount mismatch -> EXCEPTION; payment writes=0");
            }
            case 23 -> {
                CheckoutDtos.Webhook tooEarly = payload(991L, "VCB", "0123456789", "in", new BigDecimal("30000"), Instant.now().minusSeconds(600));
                return callWebhook(f, tooEarly, state -> state.exceptionMatches == 1 && state.payments == 0,
                        "transaction before request -> EXCEPTION");
            }
            case 24 -> {
                f.db().requestExpired = true;
                return callWebhook(f, basePayload, state -> state.exceptionMatches == 1 && state.payments == 0,
                        "transaction after expiry -> EXCEPTION");
            }
            case 25 -> {
                when(f.repository().booking(BOOKING_ID, BRANCH_ID)).thenReturn(booking("CANCELLED"));
                return callWebhook(f, basePayload, state -> state.exceptionMatches == 1 && state.payments == 0,
                        "booking no longer CONFIRMED; payment writes=0");
            }
            case 26 -> {
                when(f.repository().depositReceivedAmount(BOOKING_ID)).thenReturn(new BigDecimal("30000"));
                return callWebhook(f, basePayload, state -> state.exceptionMatches == 1 && state.payments == 0,
                        "existing deposit prevents duplicate receipt");
            }
            case 27 -> {
                when(f.repository().activeChargeTotal(BOOKING_ID)).thenReturn(new BigDecimal("100001"));
                return callWebhook(f, basePayload, state -> state.exceptionMatches == 1 && state.payments == 0,
                        "stale request after booking quote changed");
            }
            case 28 -> {
                f.db().duplicateTransaction = true;
                return callWebhook(f, basePayload, state -> state.payments == 0 && state.depositEntries == 0,
                        "duplicate external ID ignored; duplicate business writes=0");
            }
            default -> throw new IllegalArgumentException("Unknown webhook case " + number);
        }
    }

    private static TestResult runRefund(int number) {
        if (number >= 51 && number <= 61) return runEvidenceStorage(number);
        if (number == 72) return runFundPostcondition();
        RefundFixture f = refundFixture();
        return switch (number) {
            case 29 -> refundQueue(f);
            case 30 -> createRefund(f, validRequest(), true, null);
            case 31 -> expectFailure(() -> f.service().createRefundRequest(receptionist(), null), 400,
                    "Vui lòng bổ sung booking cần yêu cầu hoàn cọc.");
            case 32 -> {
                when(f.bookings().lockBookingId(BOOKING_ID, BRANCH_ID)).thenReturn(null);
                when(f.bookings().bookingExists(BOOKING_ID)).thenReturn(true);
                yield expectFailure(() -> f.service().createRefundRequest(receptionist(), validRequest()), 403,
                        "Bạn không có quyền gửi yêu cầu hoàn cọc đối với booking.");
            }
            case 33 -> {
                when(f.bookings().booking(BOOKING_ID, BRANCH_ID)).thenReturn(booking("CONFIRMED"));
                yield expectFailureStatus(() -> f.service().createRefundRequest(receptionist(), validRequest()), 409);
            }
            case 34 -> {
                when(f.bookings().lockDeposit(BOOKING_ID)).thenReturn(null);
                yield expectFailureStatus(() -> f.service().createRefundRequest(receptionist(), validRequest()), 409);
            }
            case 35 -> {
                when(f.bookings().depositBalance(BOOKING_ID)).thenReturn(BigDecimal.ZERO);
                yield expectFailureStatus(() -> f.service().createRefundRequest(receptionist(), validRequest()), 409);
            }
            case 36 -> {
                when(f.deposits().canRequestRefund(BRANCH_ID, BOOKING_ID)).thenReturn(false);
                yield expectFailure(() -> f.service().createRefundRequest(receptionist(), validRequest()), 409,
                        "Thao tác này đã được ghi nhận. Không tạo thêm bản ghi; vui lòng kiểm tra kết quả hiện có.");
            }
            case 37 -> createRefund(f, request(" ", "VCB", "012345", "Hoàn cọc"), false,
                    "Vui lòng bổ sung tên chủ tài khoản.");
            case 38 -> createRefund(f, request("Nguyễn A", " ", "012345", "Hoàn cọc"), false,
                    "Vui lòng bổ sung ngân hàng.");
            case 39 -> createRefund(f, request("Nguyễn A", "VCB", " ", "Hoàn cọc"), false,
                    "Vui lòng bổ sung số tài khoản.");
            case 40 -> createRefund(f, request("Nguyễn A", "VCB", "012345", " "), false,
                    "Vui lòng bổ sung lý do hoàn cọc.");
            case 41 -> createRefund(f, request("A".repeat(200), "VCB", "012345", "Hoàn cọc"), true, null);
            case 42 -> createRefund(f, request("A".repeat(201), "VCB", "012345", "Hoàn cọc"), false,
                    "Tên chủ tài khoản chỉ được chứa tối đa 200 ký tự.");
            case 43 -> createRefund(f, request("Nguyễn A", "A".repeat(200), "012345", "Hoàn cọc"), true, null);
            case 44 -> createRefund(f, request("Nguyễn A", "A".repeat(201), "012345", "Hoàn cọc"), false,
                    "Ngân hàng chỉ được chứa tối đa 200 ký tự.");
            case 45 -> completeRefund(f, "TX-REF", jpeg("proof.jpg"), true, null);
            case 46 -> completeRefund(f, "TX-REF", null, true, null);
            case 47 -> completeRefund(f, " ", jpeg("proof.jpg"), true, null);
            case 48 -> completeRefund(f, " ", null, false,
                    "Vui lòng tải lên minh chứng chuyển tiền hoặc nhập mã giao dịch trước khi xác nhận đã hoàn cọc.");
            case 49 -> completeRefund(f, "R".repeat(200), jpeg("proof.jpg"), true, null);
            case 50 -> completeRefund(f, "R".repeat(201), jpeg("proof.jpg"), false,
                    "Mã giao dịch hoàn chỉ được chứa tối đa 200 ký tự.");
            case 62 -> expectFailureStatus(() -> f.service().completeRefund(accountant(), null, "TX", jpeg("p.jpg")), 400);
            case 63 -> {
                when(f.deposits().refundRequestForAccounting(REQUEST_ID, false)).thenReturn(null);
                yield expectFailure(() -> f.service().completeRefund(accountant(), REQUEST_ID, "TX", jpeg("p.jpg")), 404,
                        "Không tìm thấy yêu cầu hoàn cọc");
            }
            case 64 -> {
                when(f.deposits().refundRequestForAccounting(REQUEST_ID, false)).thenReturn(refundRow("PAID", new BigDecimal("100000")));
                yield expectFailure(() -> f.service().completeRefund(accountant(), REQUEST_ID, "TX", jpeg("p.jpg")), 409,
                        "Thao tác này đã được ghi nhận. Không tạo thêm bản ghi; vui lòng kiểm tra kết quả hiện có.");
            }
            case 65 -> {
                when(f.bookings().booking(BOOKING_ID, BRANCH_ID)).thenReturn(booking("CONFIRMED"));
                yield expectFailureStatus(() -> f.service().completeRefund(accountant(), REQUEST_ID, "TX", jpeg("p.jpg")), 409);
            }
            case 66 -> {
                when(f.bookings().lockDeposit(BOOKING_ID)).thenReturn(UUID.randomUUID());
                yield expectFailureStatus(() -> f.service().completeRefund(accountant(), REQUEST_ID, "TX", jpeg("p.jpg")), 409);
            }
            case 67 -> {
                when(f.bookings().depositBalance(BOOKING_ID)).thenReturn(new BigDecimal("99999"));
                yield expectFailureStatus(() -> f.service().completeRefund(accountant(), REQUEST_ID, "TX", jpeg("p.jpg")), 409);
            }
            case 68 -> {
                when(f.deposits().refundRequestForAccounting(REQUEST_ID, true)).thenReturn(refundRow("PAID", new BigDecimal("100000")));
                yield expectFailure(() -> f.service().completeRefund(accountant(), REQUEST_ID, "TX", jpeg("p.jpg")), 409,
                        "Thao tác này đã được ghi nhận. Không tạo thêm bản ghi; vui lòng kiểm tra kết quả hiện có.");
            }
            case 69 -> readEvidence(f, true);
            case 70 -> readEvidence(f, false);
            case 71 -> readCrossBranchEvidence(f);
            case 73 -> readAccountingEvidence(f);
            case 74 -> expectFailure(() -> f.service().accountingRefundEvidence(receptionist(), REQUEST_ID), 403,
                    "Bạn không có quyền xử lý yêu cầu hoàn cọc đối với yêu cầu hoàn cọc.");
            default -> throw new IllegalArgumentException("Unknown refund case " + number);
        };
    }

    private static TestResult refundQueue(RefundFixture f) {
        DepositDtos.RefundRequestItem item = new DepositDtos.RefundRequestItem(REQUEST_ID, BOOKING_ID,
                "BK-HB-001", "Hòa Bằng", "Nguyễn A", "0900000001", new BigDecimal("100000"), "Hoàn cọc",
                "Nguyễn A", "VCB", "012345", "REQUESTED", Instant.now(), null, null, false);
        when(f.deposits().refundRequestsForAccounting()).thenReturn(List.of(item));
        try {
            DepositDtos.RefundRequestQueue actual = f.service().refundRequests(accountant());
            verify(f.deposits()).refundRequestsForAccounting();
            return check(actual.items().size() == 1 && "Tất cả cơ sở".equals(actual.branchName())
                            && "Hòa Bằng".equals(actual.items().get(0).branchName()),
                    "scope=" + actual.branchName() + ", item branch=" + actual.items().get(0).branchName()
                            + ", items=" + actual.items().size());
        } catch (Throwable failure) {
            return unexpected(failure);
        }
    }

    private static TestResult createRefund(RefundFixture f, DepositDtos.CreateRefundRequest input,
            boolean expectedSuccess, String expectedMessage) {
        try {
            DepositDtos.RefundRequestResult actual = f.service().createRefundRequest(receptionist(), input);
            boolean passed = expectedSuccess && "REQUESTED".equals(actual.status())
                    && actual.amount().compareTo(new BigDecimal("100000")) == 0;
            return check(passed, "status=" + actual.status() + ", amount=" + actual.amount());
        } catch (BookingFailure failure) {
            boolean passed = !expectedSuccess && (expectedMessage == null || expectedMessage.equals(failure.getMessage()));
            return check(passed, "status=" + failure.status() + ", message=" + failure.getMessage());
        } catch (Throwable failure) {
            return unexpected(failure);
        }
    }

    private static TestResult completeRefund(RefundFixture f, String reference, MultipartFile file,
            boolean expectedSuccess, String expectedMessage) {
        try {
            DepositDtos.RefundCompletionResult actual = f.service().completeRefund(accountant(), REQUEST_ID, reference, file);
            boolean passed = expectedSuccess && "PAID".equals(actual.status());
            return check(passed, "status=" + actual.status() + ", amount=" + actual.amount());
        } catch (BookingFailure failure) {
            boolean passed = !expectedSuccess && (expectedMessage == null || expectedMessage.equals(failure.getMessage()));
            return check(passed, "status=" + failure.status() + ", message=" + failure.getMessage());
        } catch (Throwable failure) {
            return unexpected(failure);
        }
    }

    private static TestResult runEvidenceStorage(int number) {
        try {
            Path dir = Files.createTempDirectory("gcmb-deposit-ut-");
            RefundEvidenceStorageService storage = new RefundEvidenceStorageService(
                    dir.toString(), "unit-test", "us-east-1", "", true);
            var clientField = RefundEvidenceStorageService.class.getDeclaredField("client");
            clientField.setAccessible(true);
            clientField.set(storage, mock(S3Client.class));
            MultipartFile file;
            boolean expectedSuccess;
            String expectedMessage = null;
            switch (number) {
                case 51 -> { file = png("proof.png"); expectedSuccess = true; }
                case 52 -> { file = webp("proof.webp"); expectedSuccess = true; }
                case 53 -> { file = bytes("proof.heic", "image/heic", heifBytes("heic")); expectedSuccess = true; }
                case 54 -> { file = bytes("proof.heif", "image/heif", heifBytes("mif1")); expectedSuccess = true; }
                case 55 -> { file = bytes("proof.pdf", "application/pdf", "%PDF-1.7\n%%EOF".getBytes(StandardCharsets.US_ASCII)); expectedSuccess = true; }
                case 56 -> { file = bytes("proof.txt", "text/plain", "text".getBytes(StandardCharsets.UTF_8)); expectedSuccess = false; expectedMessage = "Chỉ hỗ trợ tệp JPEG, PNG, WebP, HEIC, HEIF hoặc PDF. Vui lòng chọn tệp khác."; }
                case 57 -> { file = bytes("proof.png", "image/png", new byte[0]); expectedSuccess = false; expectedMessage = "Tệp phải có dung lượng lớn hơn 0 và không vượt quá 20 MB."; }
                case 58 -> { byte[] data = new byte[20_000_000]; System.arraycopy(pngBytes(), 0, data, 0, pngBytes().length); file = bytes("proof.png", "image/png", data); expectedSuccess = true; }
                case 59 -> { byte[] data = new byte[20_000_001]; System.arraycopy(pngBytes(), 0, data, 0, pngBytes().length); file = bytes("proof.png", "image/png", data); expectedSuccess = false; expectedMessage = "Tệp phải có dung lượng lớn hơn 0 và không vượt quá 20 MB."; }
                case 60 -> { file = jpeg("proof.png"); expectedSuccess = false; expectedMessage = "Không thể đọc tệp hoặc định dạng tệp không khớp với nội dung. Vui lòng xuất lại tệp đúng định dạng và tải lên lại."; }
                case 61 -> { file = bytes("proof.jpg", "image/jpeg", "broken".getBytes(StandardCharsets.UTF_8)); expectedSuccess = false; expectedMessage = "Không thể đọc tệp hoặc định dạng tệp không khớp với nội dung. Vui lòng xuất lại tệp đúng định dạng và tải lên lại."; }
                default -> throw new IllegalArgumentException();
            }
            try {
                RefundEvidenceStorageService.StoredEvidence stored = storage.store(file);
                storage.deleteQuietly(stored.objectKey());
                return check(expectedSuccess && stored.byteSize() == file.getBytes().length,
                        "stored mime=" + stored.mimeType() + ", bytes=" + stored.byteSize()
                                + ", object key=" + stored.objectKey());
            } catch (BookingFailure failure) {
                return check(!expectedSuccess && Objects.equals(expectedMessage, failure.getMessage()),
                        "status=" + failure.status() + ", message=" + failure.getMessage());
            }
        } catch (Throwable failure) {
            return unexpected(failure);
        }
    }

    private static TestResult readEvidence(RefundFixture f, boolean exists) {
        try {
            if (exists) {
                when(f.deposits().refundEvidence(BRANCH_ID, REQUEST_ID))
                        .thenReturn(new RefundEvidenceRow("s3/proof.png", "proof.png", "image/png"));
                when(f.storage().read("s3/proof.png")).thenReturn(pngBytes());
                DepositDtos.RefundEvidenceContent actual = f.service().refundEvidence(receptionist(), REQUEST_ID);
                return check("proof.png".equals(actual.originalName()) && actual.bytes().length == pngBytes().length,
                        "name=" + actual.originalName() + ", mime=" + actual.mimeType() + ", bytes=" + actual.bytes().length);
            }
            when(f.deposits().refundEvidence(BRANCH_ID, REQUEST_ID)).thenReturn(null);
            when(f.deposits().refundRequestForAccounting(REQUEST_ID, false)).thenReturn(null);
            return expectFailureStatus(() -> f.service().refundEvidence(receptionist(), REQUEST_ID), 404);
        } catch (Throwable failure) {
            return unexpected(failure);
        }
    }

    private static TestResult readCrossBranchEvidence(RefundFixture f) {
        when(f.deposits().refundEvidence(BRANCH_ID, REQUEST_ID)).thenReturn(null);
        return expectFailure(() -> f.service().refundEvidence(receptionist(), REQUEST_ID), 403,
                "Bạn không có quyền xem chứng từ hoàn cọc đối với yêu cầu hoàn cọc.");
    }

    private static TestResult readAccountingEvidence(RefundFixture f) {
        try {
            when(f.deposits().refundEvidenceForAccounting(REQUEST_ID))
                    .thenReturn(new RefundEvidenceRow("s3/proof.png", "proof.png", "image/png"));
            when(f.storage().read("s3/proof.png")).thenReturn(pngBytes());
            DepositDtos.RefundEvidenceContent actual = f.service().accountingRefundEvidence(accountant(), REQUEST_ID);
            return check("proof.png".equals(actual.originalName()) && actual.bytes().length == pngBytes().length,
                    "name=" + actual.originalName() + ", mime=" + actual.mimeType()
                            + ", bytes=" + actual.bytes().length);
        } catch (Throwable failure) {
            return unexpected(failure);
        }
    }

    private static TestResult runFundPostcondition() {
        List<String> sql = new ArrayList<>();
        UUID periodId = UUID.fromString("47570000-0000-4000-8000-000000000601");
        JdbcTemplate db = mock(JdbcTemplate.class, invocation -> {
            String method = invocation.getMethod().getName();
            Object[] args = invocation.getArguments();
            String statement = args.length > 0 && args[0] instanceof String value ? value : "";
            if ("update".equals(method)) {
                sql.add(statement);
                return 1;
            }
            if ("query".equals(method) && statement.contains("financial_period")) return List.of(periodId);
            if ("query".equals(method)) return List.of(ACCOUNT_ID);
            return null;
        });
        DepositRepository repository = new DepositRepository(db);
        boolean completed = repository.completeRefundRequest(BRANCH_ID,
                refundRow("REQUESTED", new BigDecimal("100000")), ACCOUNTANT_ID, "TX-REF", UUID.randomUUID());
        boolean fundEntry = sql.stream().anyMatch(statement -> statement.contains("financial_transaction")
                && statement.contains("REFUND"));
        return check(completed && fundEntry,
                "request/deposit REFUND recorded=" + completed + ", financial REFUND transaction=" + fundEntry);
    }

    private static CheckoutFixture checkoutFixture() {
        return checkoutFixture(new SePaySettings("VCB", "0123456789", "GENZ CINEMA", "test-key"));
    }

    private static CheckoutFixture checkoutFixture(SePaySettings sepay) {
        BookingRepository repository = mock(BookingRepository.class);
        BookingService bookings = mock(BookingService.class);
        CheckoutJdbcState state = new CheckoutJdbcState();
        JdbcTemplate db = mock(JdbcTemplate.class, state::answer);
        when(repository.branchScope(RECEPTIONIST_ID)).thenReturn(BRANCH);
        when(repository.branchScope(ACCOUNTANT_ID)).thenReturn(BRANCH);
        when(repository.booking(BOOKING_ID, BRANCH_ID)).thenReturn(booking("CONFIRMED"));
        when(repository.depositReceivedAmount(BOOKING_ID))
                .thenAnswer(invocation -> state.depositReceived ? new BigDecimal("30000") : BigDecimal.ZERO);
        when(repository.activeChargeTotal(BOOKING_ID)).thenReturn(new BigDecimal("100000"));
        doAnswer(invocation -> {
            state.depositReceived = true;
            return null;
        }).when(repository).receiveDeposit(eq(BRANCH_ID), eq(BOOKING_ID), eq(RECEPTIONIST_ID),
                any(BigDecimal.class), eq("MANUAL_TRANSFER"), anyString());
        CheckoutService service = new CheckoutService(
                db, repository, bookings, sepay, new ObjectMapper(), mock(FoodOrderService.class));
        return new CheckoutFixture(repository, service, state);
    }

    private static RefundFixture refundFixture() {
        try {
            BookingRepository bookings = mock(BookingRepository.class);
            DepositRepository deposits = mock(DepositRepository.class);
            RefundEvidenceStorageService storage = mock(RefundEvidenceStorageService.class);
            when(storage.store(any(MultipartFile.class))).thenAnswer(invocation -> {
                MultipartFile file = invocation.getArgument(0);
                return new RefundEvidenceStorageService.StoredEvidence(UUID.randomUUID(), "s3/proof.jpg",
                        file.getOriginalFilename(), file.getContentType(), file.getSize(), "test-sha256");
            });
            DepositManagementService service = new DepositManagementService(bookings, deposits, storage);
            when(bookings.branchScope(RECEPTIONIST_ID)).thenReturn(BRANCH);
            when(bookings.branchScope(ACCOUNTANT_ID)).thenReturn(BRANCH);
            when(bookings.lockBookingId(BOOKING_ID, BRANCH_ID)).thenReturn(BOOKING_ID);
            when(bookings.booking(BOOKING_ID, BRANCH_ID)).thenReturn(booking("CANCELLED"));
            when(bookings.lockDeposit(BOOKING_ID)).thenReturn(DEPOSIT_ID);
            when(bookings.depositBalance(BOOKING_ID)).thenReturn(new BigDecimal("100000"));
            when(bookings.createDepositRefundRequest(eq(BRANCH_ID), eq(BOOKING_ID), eq(DEPOSIT_ID),
                    eq(new BigDecimal("100000")), anyString(), anyString(), anyString(), anyString(), eq(RECEPTIONIST_ID)))
                    .thenReturn(REQUEST_ID);
            when(deposits.canRequestRefund(BRANCH_ID, BOOKING_ID)).thenReturn(true);
            when(deposits.refundRequestForAccounting(REQUEST_ID, false))
                    .thenReturn(refundRow("REQUESTED", new BigDecimal("100000")));
            when(deposits.refundRequestForAccounting(REQUEST_ID, true))
                    .thenReturn(refundRow("REQUESTED", new BigDecimal("100000")));
            when(deposits.completeRefundRequest(eq(BRANCH_ID), any(), eq(ACCOUNTANT_ID), any(), any())).thenReturn(true);
            return new RefundFixture(bookings, deposits, storage, service);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static TestResult callWebhook(CheckoutFixture f, CheckoutDtos.Webhook payload,
            StateCheck condition, String actual) {
        try {
            f.service().webhook("Apikey test-key", payload);
            return check(condition.test(f.db()), actual
                    + "; bank transactions=" + f.db().bankTransactions
                    + "; exception matches=" + f.db().exceptionMatches
                    + "; payment writes=" + f.db().payments
                    + "; deposit entry writes=" + f.db().depositEntries);
        } catch (Throwable failure) {
            return unexpected(failure);
        }
    }

    private static BookingRow booking(String status) {
        Instant now = Instant.now();
        return new BookingRow(BOOKING_ID, BRANCH_ID, "BK-HB-001", "Nguyễn A", "0900000001", 2,
                "PHONE", now.plusSeconds(3600), now.plusSeconds(10800), null, null, status,
                "CANCELLED".equals(status) ? now.minusSeconds(60) : null, null, 40,
                RECEPTIONIST_ID, null, now.minusSeconds(3600));
    }

    private static DepositDtos.CreateRefundRequest validRequest() {
        return request("Nguyễn A", "VCB", "012345", "Hoàn cọc");
    }

    private static DepositDtos.CreateRefundRequest request(String beneficiary, String bank, String account, String reason) {
        return new DepositDtos.CreateRefundRequest(BOOKING_ID, beneficiary, bank, account, reason);
    }

    private static RefundRequestRow refundRow(String status, BigDecimal amount) {
        return new RefundRequestRow(REQUEST_ID, BRANCH_ID, BOOKING_ID, DEPOSIT_ID, amount, "Hoàn cọc", status);
    }

    private static Identity receptionist() {
        return new Identity(RECEPTIONIST_ID, "letan.hoabang", "RECEPTIONIST", "/reception", BRANCH_ID, BRANCH.name());
    }

    private static Identity accountant() {
        return new Identity(ACCOUNTANT_ID, "ketoan", "ACCOUNTANT", "/accounting", null, null);
    }

    private static CheckoutDtos.Webhook payload(Long id, String gateway, String account, String type,
            BigDecimal amount, Instant occurred) {
        String time = LocalDateTime.ofInstant(occurred, ZoneId.of("Asia/Ho_Chi_Minh"))
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        return new CheckoutDtos.Webhook(id, gateway, time, account, REFERENCE, REFERENCE, type, amount, "REF-" + id);
    }

    private static MockMultipartFile jpeg(String name) {
        return bytes(name, "image/jpeg", new byte[] {(byte) 0xff, (byte) 0xd8, (byte) 0xff, 0x00});
    }

    private static MockMultipartFile png(String name) {
        return bytes(name, "image/png", pngBytes());
    }

    private static byte[] pngBytes() {
        return new byte[] {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};
    }

    private static byte[] heifBytes(String brand) {
        byte[] bytes = new byte[] {0, 0, 0, 16, 'f', 't', 'y', 'p', 0, 0, 0, 0, 0, 0, 0, 0};
        byte[] brandBytes = brand.getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(brandBytes, 0, bytes, 8, 4);
        return bytes;
    }

    private static MockMultipartFile webp(String name) {
        return bytes(name, "image/webp", "RIFF0000WEBP".getBytes(StandardCharsets.US_ASCII));
    }

    private static MockMultipartFile bytes(String name, String contentType, byte[] content) {
        return new MockMultipartFile("image", name, contentType, content);
    }

    private static TestResult expectFailure(ThrowingRunnable action, int status, String exactMessage) {
        try {
            action.run();
            return check(false, "No BookingFailure was thrown");
        } catch (BookingFailure failure) {
            boolean messageMatches = exactMessage == null || exactMessage.equals(failure.getMessage());
            return check(failure.status() == status && messageMatches,
                    "status=" + failure.status() + ", message=" + failure.getMessage());
        } catch (Throwable failure) {
            return unexpected(failure);
        }
    }

    private static TestResult expectFailureStatus(ThrowingRunnable action, int status) {
        return expectFailure(action, status, null);
    }

    static TestResult check(boolean passed, String actual) {
        return new TestResult(passed, actual);
    }

    static TestResult unexpected(Throwable failure) {
        return new TestResult(false, failure.getClass().getSimpleName() + ": " + failure.getMessage());
    }

    record ManualTest(String id, String useCase, String sourceMethod, String preconditions,
                      String input, String expected, String type, String note, CheckedAction action) {
        ManualTest {
            Objects.requireNonNull(action, "action");
        }

        TestResult execute() {
            try {
                TestResult result = action.run();
                return result == null ? check(false, "Test action returned no result") : result;
            } catch (Throwable failure) {
                return unexpected(failure);
            }
        }
    }

    record TestResult(boolean passed, String actual) {}
    record CheckoutFixture(BookingRepository repository, CheckoutService service, CheckoutJdbcState db) {}
    record RefundFixture(BookingRepository bookings, DepositRepository deposits,
                         RefundEvidenceStorageService storage, DepositManagementService service) {}

    @FunctionalInterface interface CheckedAction { TestResult run() throws Exception; }
    @FunctionalInterface interface ThrowingRunnable { void run() throws Exception; }
    @FunctionalInterface interface StateCheck { boolean test(CheckoutJdbcState value); }

    static final class CheckoutJdbcState {
        final List<String> sql = new ArrayList<>();
        boolean requestExists;
        boolean requestExpired;
        boolean referenceFound;
        boolean duplicateTransaction;
        boolean bookingLocked = true;
        boolean depositReceived;
        int insertedRequests;
        int cancelledRequests;
        int bankTransactions;
        int payments;
        int depositEntries;
        int exceptionMatches;
        int financialTransactions;
        BigDecimal requestAmount = new BigDecimal("30000");

        Object answer(InvocationOnMock invocation) {
            String method = invocation.getMethod().getName();
            Object[] args = invocation.getArguments();
            String statement = args.length > 0 && args[0] instanceof String value ? value : "";
            if (!statement.isBlank()) sql.add(statement);
            if ("queryForObject".equals(method)) return ACCOUNT_ID;
            if ("queryForMap".equals(method)) {
                if (statement.contains("INSERT INTO gcmb.payment_request")) {
                    requestExists = true;
                    requestExpired = false;
                    requestAmount = new BigDecimal("30000");
                    insertedRequests++;
                    return requestRow();
                }
                if (statement.contains("financial_account")) {
                    return Map.of("bank_name", "VCB", "bank_account_number", "0123456789", "account_holder", "GENZ CINEMA");
                }
                if (statement.contains("financial_period")) {
                    return Map.of("id", UUID.fromString("47570000-0000-4000-8000-000000000601"), "status", "OPEN");
                }
                return requestRow();
            }
            if ("update".equals(method)) return update(statement);
            if (!"queryForList".equals(method)) return null;
            if (statement.contains("FROM gcmb.financial_account WHERE kind='BANK'")) {
                return List.of(ACCOUNT_ID);
            }
            if (statement.contains("SELECT id FROM gcmb.booking") && statement.contains("FOR UPDATE")) {
                return bookingLocked ? List.of(BOOKING_ID) : List.of();
            }
            if (statement.contains("WHERE booking_id=? AND purpose='DEPOSIT'")) {
                return requestExists ? List.of(requestRow()) : List.of();
            }
            if (statement.contains("WHERE reference_code=?")) {
                return referenceFound ? List.of(requestRow()) : List.of();
            }
            return List.of();
        }

        private int update(String statement) {
            if (statement.contains("UPDATE gcmb.payment_request SET status='CANCELLED'")) cancelledRequests++;
            if (statement.contains("INSERT INTO gcmb.bank_transaction")) {
                if (duplicateTransaction) return 0;
                bankTransactions++;
            }
            if (statement.contains("INSERT INTO gcmb.payment(")) payments++;
            if (statement.contains("INSERT INTO gcmb.deposit_entry")) depositEntries++;
            if (statement.contains("INSERT INTO gcmb.financial_transaction")) financialTransactions++;
            if (statement.contains("match_status='EXCEPTION'")) exceptionMatches++;
            return 1;
        }

        private Map<String, Object> requestRow() {
            Instant now = Instant.now();
            Map<String, Object> row = new HashMap<>();
            row.put("id", REQUEST_ID);
            row.put("branch_id", BRANCH_ID);
            row.put("booking_id", BOOKING_ID);
            row.put("bill_id", null);
            row.put("purpose", "DEPOSIT");
            row.put("account_id", ACCOUNT_ID);
            row.put("reference_code", REFERENCE);
            row.put("expected_amount", requestAmount);
            row.put("status", "PENDING");
            row.put("created_at", Timestamp.from(now.minusSeconds(60)));
            row.put("expires_at", Timestamp.from(requestExpired ? now.minusSeconds(1) : now.plusSeconds(1740)));
            return row;
        }
    }
}
