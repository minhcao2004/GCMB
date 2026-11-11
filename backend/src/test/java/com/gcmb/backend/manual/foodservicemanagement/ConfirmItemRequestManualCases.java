/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: CaoNM
 * Created: 11/11/2026
 * Description: Defines UC-53 Confirm Item Request manual unit test cases.
 */
package com.gcmb.backend.manual.foodservicemanagement;

import static com.gcmb.backend.manual.foodservicemanagement.CreateMenuItemManualCases.note;
import static com.gcmb.backend.manual.foodservicemanagement.FoodServiceManagementManualRunner.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gcmb.backend.inventory.dto.FoodOrderDtos.ConfirmRequest;
import com.gcmb.backend.inventory.dto.FoodOrderDtos.ItemRequest;
import com.gcmb.backend.inventory.repository.FoodOrderRepository.ComboAllowance;
import com.gcmb.backend.inventory.repository.FoodOrderRepository.OrderLock;
import com.gcmb.backend.inventory.repository.FoodOrderRepository.PendingLine;
import java.math.BigDecimal;
import java.util.List;

final class ConfirmItemRequestManualCases {
    private static final String UC = "UC-53 Confirm Item Request";
    private static final String PRE = "Lễ tân letan.hoabang đăng nhập; order ROOM_QR/PENDING thuộc booking BK-DEMO đang IN_HOUSE; nhân viên phụ trách đang trong ca.";

    private ConfirmItemRequestManualCases() {}

    static List<FoodServiceManagementManualRunner.ManualTest> cases() {
        return List.of(
            tc("FSM-059", PRE, "Coca Cola yêu cầu=3, xác nhận=3.", "Order chuyển ACCEPTED, gán nhân viên, tính đúng 3 chai và trừ kho.", "Normal", "BR-27, BR-28, BR-123", "", () -> confirm(3, 3)),
            tc("FSM-060", PRE, "Coca Cola yêu cầu=3, xác nhận=2.", "Chỉ giữ và tính tiền 2 chai.", "Normal", "BR-28", "", () -> confirm(3, 2)),
            tc("FSM-061", PRE, "Coca Cola yêu cầu=2, xác nhận=3.", "Hiển thị “Số lượng xác nhận vượt quá số lượng khách đã yêu cầu cho Coca Cola.”; không accept order.", "Boundary", "BR-117", "MSG45", ConfirmItemRequestManualCases::overRequested),
            tc("FSM-062", PRE, "Tất cả món xác nhận quantity=0.", "Hiển thị “Chọn ít nhất một món để xác nhận hoặc từ chối toàn bộ yêu cầu.”; không accept order.", "Boundary", "BR-117", "MSG02", ConfirmItemRequestManualCases::allZero),
            tc("FSM-063", PRE.replace("ROOM_QR/PENDING", "STAFF/PENDING"), "Xác nhận order STAFF.", "Hiển thị “Chỉ xác nhận yêu cầu món gửi từ mã MR.”; không thay đổi order.", "Abnormal", "BR-27", "", ConfirmItemRequestManualCases::wrongChannel),
            tc("FSM-064", PRE + " Nhân viên được chọn đã hết ca.", "Xác nhận order.", "Hiển thị “Vui lòng chọn Lễ tân đang làm ca hiện tại tại cơ sở của booking để phụ trách đơn.”; không accept.", "Abnormal", "BR-123", "MSG75", ConfirmItemRequestManualCases::offShift),
            tc("FSM-065", PRE + " Tồn Coca Cola=2 chai.", "Xác nhận 3 chai.", "Hiển thị “Coca Cola chỉ còn 2 chai. Vui lòng giảm số lượng hoặc chọn món khác.”; không trừ kho.", "Abnormal", "BR-38", "MSG18", ConfirmItemRequestManualCases::stockShortage),
            tc("FSM-066", PRE + " Combo còn 2 Coca Cola miễn phí.", "Xác nhận 3 chai.", "Hai chai thuộc combo, một chai tính phí.", "Boundary", "BR-30, BR-172", "", ConfirmItemRequestManualCases::combo),
            tc("FSM-067", PRE + " Tồn kho=10 chai.", "Xác nhận 3 chai rồi gửi lại thao tác.", "Tồn kho chỉ giảm một lần từ 10 xuống 7; không tạo SALE trùng.", "Abnormal", "BR-164", "MSG74", ConfirmItemRequestManualCases::idempotent),
            tc("FSM-068", PRE + " Một phiên khác đã xử lý order đồng thời.", "Xác nhận order.", "Hiển thị xung đột; không tạo charge hoặc stock movement trùng.", "Abnormal", "BR-164", "MSG74", ConfirmItemRequestManualCases::race)
        );
    }

    private static FoodServiceManagementManualRunner.ManualTest tc(String id, String pre, String input,
            String expected, String type, String br, String msg, FoodServiceManagementManualRunner.CheckedAction action) {
        return test(id, UC, "FoodOrderService.confirmRequest", pre, input, expected, type,
                note("UC-53 - Confirm_Item_Request.docx", br, msg, "FoodOrderService.confirmRequest", "White-box"), action);
    }

    private static OrderLock lock(String channel, String status) {
        return new OrderLock(ORDER_ID, BRANCH_ID, BOOKING_ID, ALLOCATION_ID, channel, SESSION_ID, status, null, null);
    }

    private static ConfirmRequest request(int quantity) {
        return new ConfirmRequest(EMPLOYEE_ID, List.of(new ItemRequest(ITEM_ID, quantity)));
    }

    private static FoodServiceManagementManualRunner.TestResult confirm(int requested, int accepted) {
        var f = foodFixture();
        when(f.orders().lockOrder(ORDER_ID, BRANCH_ID)).thenReturn(lock("ROOM_QR", "PENDING"));
        when(f.orders().pendingLines(ORDER_ID)).thenReturn(List.of(new PendingLine(LINE_ID, ITEM_ID, "Coca Cola", BigDecimal.valueOf(requested), new BigDecimal("20000"))));
        var result = f.service().confirmRequest(receptionist(), ORDER_ID, request(accepted));
        verify(f.orders()).accept(ORDER_ID, BRANCH_ID, ACTOR_ID, EMPLOYEE_ID);
        return check(result != null, "accepted quantity=" + accepted);
    }

    private static FoodServiceManagementManualRunner.TestResult overRequested() {
        var f = foodFixture();
        when(f.orders().lockOrder(ORDER_ID, BRANCH_ID)).thenReturn(lock("ROOM_QR", "PENDING"));
        when(f.orders().pendingLines(ORDER_ID)).thenReturn(List.of(new PendingLine(LINE_ID, ITEM_ID, "Coca Cola", BigDecimal.valueOf(2), new BigDecimal("20000"))));
        return expectResponse(() -> f.service().confirmRequest(receptionist(), ORDER_ID, request(3)), 400,
                "Số lượng xác nhận vượt quá số lượng khách đã yêu cầu cho Coca Cola.");
    }

    private static FoodServiceManagementManualRunner.TestResult allZero() {
        var f = foodFixture();
        when(f.orders().lockOrder(ORDER_ID, BRANCH_ID)).thenReturn(lock("ROOM_QR", "PENDING"));
        return expectResponse(() -> f.service().confirmRequest(receptionist(), ORDER_ID, request(0)), 400,
                "Chọn ít nhất một món để xác nhận hoặc từ chối toàn bộ yêu cầu.");
    }

    private static FoodServiceManagementManualRunner.TestResult wrongChannel() {
        var f = foodFixture();
        when(f.orders().lockOrder(ORDER_ID, BRANCH_ID)).thenReturn(lock("STAFF", "PENDING"));
        return expectResponse(() -> f.service().confirmRequest(receptionist(), ORDER_ID, request(1)), 409,
                "Chỉ xác nhận yêu cầu món gửi từ mã MR.");
    }

    private static FoodServiceManagementManualRunner.TestResult offShift() {
        var f = foodFixture();
        when(f.orders().lockOrder(ORDER_ID, BRANCH_ID)).thenReturn(lock("ROOM_QR", "PENDING"));
        when(f.orders().eligibleOnShift(EMPLOYEE_ID, BRANCH_ID)).thenReturn(false);
        return expectResponse(() -> f.service().confirmRequest(receptionist(), ORDER_ID, request(1)), 400,
                "Vui lòng chọn Lễ tân đang làm ca hiện tại tại cơ sở của booking để phụ trách đơn.");
    }

    private static FoodServiceManagementManualRunner.TestResult stockShortage() {
        var f = foodFixture();
        when(f.orders().lockOrder(ORDER_ID, BRANCH_ID)).thenReturn(lock("ROOM_QR", "PENDING"));
        when(f.orders().lockStock(INVENTORY_ID)).thenReturn(new BigDecimal("2"));
        var result = expectResponse(() -> f.service().confirmRequest(receptionist(), ORDER_ID, request(3)), 409,
                "Coca Cola chỉ còn 2 chai. Vui lòng giảm số lượng hoặc chọn món khác.");
        verify(f.orders(), never()).insertSale(any(), any(), any(), any(), any(), any());
        return result;
    }

    private static FoodServiceManagementManualRunner.TestResult combo() {
        var f = foodFixture();
        when(f.orders().lockOrder(ORDER_ID, BRANCH_ID)).thenReturn(lock("ROOM_QR", "PENDING"));
        when(f.orders().comboAllowance(BOOKING_ID, ITEM_ID, ORDER_ID))
                .thenReturn(new ComboAllowance(java.util.UUID.randomUUID(), new BigDecimal("2"), BigDecimal.ZERO));
        f.service().confirmRequest(receptionist(), ORDER_ID, request(3));
        verify(f.orders()).updateLine(eq(LINE_ID), any(), eq(new BigDecimal("2")), eq(BigDecimal.ZERO));
        return check(true, "included=2, paid=1");
    }

    private static FoodServiceManagementManualRunner.TestResult idempotent() {
        var f = foodFixture();
        when(f.orders().lockOrder(ORDER_ID, BRANCH_ID)).thenReturn(lock("ROOM_QR", "PENDING"), lock("ROOM_QR", "ACCEPTED"));
        f.service().confirmRequest(receptionist(), ORDER_ID, request(3));
        f.service().confirmRequest(receptionist(), ORDER_ID, request(3));
        verify(f.orders()).insertSale(INVENTORY_ID, LINE_ID, new BigDecimal("10"), new BigDecimal("3"), ACTOR_ID,
                "Bán món theo yêu cầu QR đã xác nhận");
        return check(true, "stock movement created once");
    }

    private static FoodServiceManagementManualRunner.TestResult race() {
        var f = foodFixture();
        when(f.orders().lockOrder(ORDER_ID, BRANCH_ID)).thenReturn(lock("ROOM_QR", "PENDING"));
        when(f.orders().accept(ORDER_ID, BRANCH_ID, ACTOR_ID, EMPLOYEE_ID)).thenReturn(0);
        return expectResponse(() -> f.service().confirmRequest(receptionist(), ORDER_ID, request(3)), 409,
                "Yêu cầu món vừa được xử lý ở phiên khác; vui lòng tải lại.");
    }
}
