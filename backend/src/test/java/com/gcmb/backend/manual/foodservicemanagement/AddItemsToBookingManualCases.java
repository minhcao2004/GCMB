/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: CaoNM
 * Created: 11/11/2026
 * Description: Defines UC-50 Add Items to Booking manual unit test cases.
 */
package com.gcmb.backend.manual.foodservicemanagement;

import static com.gcmb.backend.manual.foodservicemanagement.CreateMenuItemManualCases.note;
import static com.gcmb.backend.manual.foodservicemanagement.FoodServiceManagementManualRunner.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gcmb.backend.auth.dto.Identity;
import com.gcmb.backend.inventory.dto.FoodOrderDtos.StaffItemRequest;
import com.gcmb.backend.inventory.dto.FoodOrderDtos.StaffOrderRequest;
import com.gcmb.backend.inventory.repository.FoodOrderRepository.ComboAllowance;
import com.gcmb.backend.inventory.repository.FoodOrderRepository.ExistingOrder;
import java.math.BigDecimal;
import java.util.List;

final class AddItemsToBookingManualCases {
    private static final String UC = "UC-50 Add Items to Booking";
    private static final String PRE = "Lễ tân letan.hoabang đăng nhập; booking BK-DEMO đang IN_HOUSE tại HN_HOA_BANG; lễ tân phụ trách đang trong ca; Coca Cola đang bán.";

    private AddItemsToBookingManualCases() {}

    static List<FoodServiceManagementManualRunner.ManualTest> cases() {
        return List.of(
            tc("FSM-036", PRE, "Coca Cola: quantity=2; unitPrice=20.000đ.", "Tạo order ACCEPTED, tổng tiền 40.000đ và gắn booking.", "Normal", "BR-27, BR-28", "", () -> add(2, new BigDecimal("20000"))),
            tc("FSM-037", PRE + " Combo còn 2 Coca Cola miễn phí.", "Coca Cola: quantity=2.", "Hai chai được ghi nhận là phần combo, đơn giá 0đ.", "Normal", "BR-30, BR-172", "", () -> combo(2, 2)),
            tc("FSM-038", PRE + " Combo còn 2 Coca Cola miễn phí.", "Coca Cola: quantity=3.", "Hai chai miễn phí và một chai tính 20.000đ.", "Boundary", "BR-30, BR-172", "", () -> combo(3, 2)),
            tc("FSM-039", PRE + " Tồn kho Coca Cola=10 chai.", "Coca Cola: quantity=3.", "Tạo đúng một stock movement SALE; tồn sau=7.", "Normal", "BR-38", "", AddItemsToBookingManualCases::stockDeduction),
            tc("FSM-040", PRE + " Tồn kho Coca Cola=2 chai.", "Coca Cola: quantity=3.", "Hiển thị “Coca Cola chỉ còn 2 chai. Vui lòng giảm số lượng hoặc chọn món khác.”; không tạo SALE.", "Abnormal", "BR-38", "MSG18", AddItemsToBookingManualCases::stockShortage),
            tc("FSM-041", PRE, "Coca Cola: quantity=0.", "Hiển thị “Số lượng phải là số nguyên lớn hơn 0.”; không tạo order.", "Boundary", "BR-117", "MSG45", AddItemsToBookingManualCases::zeroQuantity),
            tc("FSM-042", "Lễ tân đăng nhập; booking không còn IN_HOUSE hoặc đã kết thúc.", "Thêm Coca Cola.", "Từ chối; không tạo order hoặc charge.", "Abnormal", "BR-27", "", AddItemsToBookingManualCases::inactiveBooking),
            tc("FSM-043", PRE, "Cùng Coca Cola xuất hiện hai lần trong request.", "Hiển thị “Mỗi món chỉ được xuất hiện một lần trong cùng yêu cầu.”; không tạo order.", "Abnormal", "BR-117", "", AddItemsToBookingManualCases::duplicateItem),
            tc("FSM-044", PRE + " Nhân viên được chọn không làm ca hiện tại.", "Thêm Coca Cola và gán nhân viên ngoài ca.", "Hiển thị “Vui lòng chọn Lễ tân đang làm ca hiện tại tại cơ sở của booking để phụ trách đơn.”; không tạo order.", "Abnormal", "BR-123", "MSG75", AddItemsToBookingManualCases::offShift),
            tc("FSM-045", PRE + " Món đã ngừng bán hoặc thuộc cơ sở khác.", "Thêm món không hợp lệ.", "Từ chối; không tạo order hoặc charge.", "Abnormal", "BR-38, BR-177", "", AddItemsToBookingManualCases::unavailableItem),
            tc("FSM-046", PRE + " idempotencyKey đã tạo đúng order của booking.", "Gửi lại cùng request/key.", "Trả order hiện có; không tạo order/line/SALE trùng.", "Abnormal", "BR-164", "MSG74", () -> idempotent(false)),
            tc("FSM-047", PRE + " idempotencyKey đã dùng cho booking khác.", "Gửi request với key trùng.", "Hiển thị “Mã gửi món đã được dùng cho một booking khác.”; không lộ order kia.", "Abnormal", "BR-164", "MSG74", () -> idempotent(true)),
            tc("FSM-048", "Kế toán ketoan đăng nhập; booking đang IN_HOUSE.", "Kế toán thêm món.", "Hiển thị “Bạn không có quyền thêm món đối với booking.”; không đọc/ghi order.", "Abnormal", "Không có BR riêng", "MSG24", AddItemsToBookingManualCases::wrongRole)
        );
    }

    private static FoodServiceManagementManualRunner.ManualTest tc(String id, String pre, String input,
            String expected, String type, String br, String msg, FoodServiceManagementManualRunner.CheckedAction action) {
        return test(id, UC, "FoodOrderService.addItems", pre, input, expected, type,
                note("UC-50 - Add_Items_to_Booking.docx", br, msg, "FoodOrderService.addItems", "White-box"), action);
    }

    private static StaffOrderRequest request(int quantity, BigDecimal price, String key) {
        return new StaffOrderRequest(BOOKING_ID, EMPLOYEE_ID,
                List.of(new StaffItemRequest(ITEM_ID, quantity, price)), key);
    }

    private static FoodServiceManagementManualRunner.TestResult add(int quantity, BigDecimal price) {
        var f = foodFixture();
        var result = f.service().addItems(receptionist(), request(quantity, price, "fsm-add"));
        return check(result != null && "ACCEPTED".equals(result.status()), "status=" + result.status());
    }

    private static FoodServiceManagementManualRunner.TestResult combo(int quantity, int included) {
        var f = foodFixture();
        when(f.orders().comboAllowance(BOOKING_ID, ITEM_ID, ORDER_ID))
                .thenReturn(new ComboAllowance(java.util.UUID.randomUUID(), BigDecimal.valueOf(included), BigDecimal.ZERO));
        f.service().addItems(receptionist(), request(quantity, null, "fsm-combo-" + quantity));
        verify(f.orders()).updateLine(eq(LINE_ID), any(), eq(BigDecimal.valueOf(Math.min(quantity, included))), eq(BigDecimal.ZERO));
        return check(true, "included=" + Math.min(quantity, included) + ", paid=" + Math.max(0, quantity - included));
    }

    private static FoodServiceManagementManualRunner.TestResult stockDeduction() {
        var f = foodFixture();
        f.service().addItems(receptionist(), request(3, new BigDecimal("20000"), "fsm-stock"));
        verify(f.orders()).insertSale(INVENTORY_ID, LINE_ID, new BigDecimal("10"), new BigDecimal("3"), ACTOR_ID,
                "Bán món do lễ tân thêm vào booking");
        return check(true, "stock movement: before=10, quantity=3, after=7");
    }

    private static FoodServiceManagementManualRunner.TestResult stockShortage() {
        var f = foodFixture();
        when(f.orders().lockStock(INVENTORY_ID)).thenReturn(new BigDecimal("2"));
        var result = expectResponse(() -> f.service().addItems(receptionist(), request(3, null, "fsm-short")), 409,
                "Coca Cola chỉ còn 2 chai. Vui lòng giảm số lượng hoặc chọn món khác.");
        verify(f.orders(), never()).insertSale(any(), any(), any(), any(), any(), any());
        return result;
    }

    private static FoodServiceManagementManualRunner.TestResult zeroQuantity() {
        var f = foodFixture();
        return expectResponse(() -> f.service().addItems(receptionist(), request(0, null, "fsm-zero")), 400,
                "Số lượng phải là số nguyên lớn hơn 0.");
    }

    private static FoodServiceManagementManualRunner.TestResult inactiveBooking() {
        var f = foodFixture();
        when(f.orders().lockActiveBooking(BOOKING_ID, BRANCH_ID)).thenReturn(null);
        return expectResponse(() -> f.service().addItems(receptionist(), request(1, null, "fsm-inactive")), 409,
                "Booking không còn lượt sử dụng đang hoạt động tại cơ sở này.");
    }

    private static FoodServiceManagementManualRunner.TestResult duplicateItem() {
        var f = foodFixture();
        var request = new StaffOrderRequest(BOOKING_ID, EMPLOYEE_ID,
                List.of(new StaffItemRequest(ITEM_ID, 1, null), new StaffItemRequest(ITEM_ID, 1, null)), "fsm-duplicate");
        return expectResponse(() -> f.service().addItems(receptionist(), request), 400,
                "Mỗi món chỉ được xuất hiện một lần trong cùng yêu cầu.");
    }

    private static FoodServiceManagementManualRunner.TestResult offShift() {
        var f = foodFixture();
        when(f.orders().eligibleOnShift(EMPLOYEE_ID, BRANCH_ID)).thenReturn(false);
        return expectResponse(() -> f.service().addItems(receptionist(), request(1, null, "fsm-shift")), 400,
                "Vui lòng chọn Lễ tân đang làm ca hiện tại tại cơ sở của booking để phụ trách đơn.");
    }

    private static FoodServiceManagementManualRunner.TestResult unavailableItem() {
        var f = foodFixture();
        when(f.orders().sellableItems(eq(BRANCH_ID), any())).thenReturn(List.of());
        return expectResponse(() -> f.service().addItems(receptionist(), request(1, null, "fsm-item")), 409,
                "Một hoặc nhiều món đã ngừng bán hoặc không thuộc cơ sở của booking.");
    }

    private static FoodServiceManagementManualRunner.TestResult idempotent(boolean otherBooking) {
        var f = foodFixture();
        var booking = otherBooking ? java.util.UUID.randomUUID() : BOOKING_ID;
        when(f.orders().existingByKey("same-key")).thenReturn(new ExistingOrder(ORDER_ID, BRANCH_ID, booking, "STAFF", null));
        if (otherBooking) {
            return expectResponse(() -> f.service().addItems(receptionist(), request(1, null, "same-key")), 409,
                    "Mã gửi món đã được dùng cho một booking khác.");
        }
        var row = f.service().addItems(receptionist(), request(1, null, "same-key"));
        verify(f.orders(), never()).createOrder(any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
        return check(row != null, "returned existing order=" + row.id());
    }

    private static FoodServiceManagementManualRunner.TestResult wrongRole() {
        var f = foodFixture();
        Identity accountant = new Identity(ACTOR_ID, "ketoan", "ACCOUNTANT", "/accounting", null, null);
        return expectResponse(() -> f.service().addItems(accountant, request(1, null, "fsm-role")), 403,
                "Bạn không có quyền thêm món đối với booking.");
    }
}
