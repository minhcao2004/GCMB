/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: CaoNM
 * Created: 11/11/2026
 * Description: Defines UC-51 Submit Item Request manual unit test cases.
 */
package com.gcmb.backend.manual.foodservicemanagement;

import static com.gcmb.backend.manual.foodservicemanagement.CreateMenuItemManualCases.note;
import static com.gcmb.backend.manual.foodservicemanagement.FoodServiceManagementManualRunner.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gcmb.backend.inventory.dto.FoodOrderDtos.ItemRequest;
import com.gcmb.backend.inventory.dto.FoodOrderDtos.RoomRequest;
import com.gcmb.backend.inventory.repository.FoodOrderRepository.ExistingOrder;
import java.math.BigDecimal;
import java.util.List;

final class SubmitItemRequestManualCases {
    private static final String UC = "UC-51 Submit Item Request";
    private static final String PRE = "Khách đã quét MR hợp lệ; phiên phòng còn hạn; booking BK-DEMO đang IN_HOUSE; Coca Cola đang bán.";

    private SubmitItemRequestManualCases() {}

    static List<FoodServiceManagementManualRunner.ManualTest> cases() {
        return List.of(
            tc("FSM-049", PRE, "Coca Cola quantity=2; idempotencyKey=room-1.", "Tạo yêu cầu ROOM_QR/PENDING; chưa tính tiền hoặc trừ kho.", "Normal", "BR-27, BR-117", "", SubmitItemRequestManualCases::valid),
            tc("FSM-050", PRE, "Coca Cola quantity=0.", "Hiển thị “Số lượng phải là số nguyên lớn hơn 0.”; không tạo yêu cầu.", "Boundary", "BR-117", "MSG45", SubmitItemRequestManualCases::zero),
            tc("FSM-051", PRE + " Tồn kho Coca Cola=0 chai.", "Coca Cola quantity=1.", "Hiển thị “Coca Cola chỉ còn 0 chai. Vui lòng giảm số lượng hoặc chọn món khác.”; không tạo yêu cầu.", "Boundary", "BR-38", "MSG18", SubmitItemRequestManualCases::outOfStock),
            tc("FSM-052", "Khách dùng mã MR không hợp lệ hoặc phiên phòng đã hết hạn.", "Gửi yêu cầu món.", "Hiển thị “Phiên gọi món đã hết hạn hoặc phòng đã đổi lượt. Hãy quét lại mã MR trong phòng.”; không tạo yêu cầu.", "Abnormal", "BR-27", "", SubmitItemRequestManualCases::expired),
            tc("FSM-053", PRE, "Coca Cola xuất hiện hai lần.", "Hiển thị “Mỗi món chỉ được xuất hiện một lần trong cùng yêu cầu.”; không tạo yêu cầu.", "Abnormal", "BR-117", "", SubmitItemRequestManualCases::duplicate),
            tc("FSM-054", PRE + " idempotencyKey đã tạo yêu cầu này.", "Gửi lại cùng key.", "Trả yêu cầu hiện có; không tạo order hoặc line trùng.", "Abnormal", "BR-164", "MSG74", SubmitItemRequestManualCases::idempotent)
        );
    }

    private static FoodServiceManagementManualRunner.ManualTest tc(String id, String pre, String input,
            String expected, String type, String br, String msg, FoodServiceManagementManualRunner.CheckedAction action) {
        return test(id, UC, "FoodOrderService.submitRoomRequest", pre, input, expected, type,
                note("UC-51 - Submit_Item_Request.docx", br, msg, "FoodOrderService.submitRoomRequest", "White-box"), action);
    }

    private static RoomRequest request(int quantity, String key) {
        return new RoomRequest(List.of(new ItemRequest(ITEM_ID, quantity)), key);
    }

    private static FoodServiceManagementManualRunner.TestResult valid() {
        var f = foodFixture();
        var result = f.service().submitRoomRequest(MR_CODE, TOKEN, request(2, "room-1"));
        verify(f.orders(), never()).insertSale(any(), any(), any(), any(), any(), any());
        return check("PENDING".equals(result.status()), "status=" + result.status() + "; no stock movement");
    }

    private static FoodServiceManagementManualRunner.TestResult zero() {
        var f = foodFixture();
        return expectResponse(() -> f.service().submitRoomRequest(MR_CODE, TOKEN, request(0, "room-zero")), 400,
                "Số lượng phải là số nguyên lớn hơn 0.");
    }

    private static FoodServiceManagementManualRunner.TestResult outOfStock() {
        var f = foodFixture();
        when(f.orders().sellableItems(eq(BRANCH_ID), any())).thenReturn(List.of(sellable(BigDecimal.ZERO)));
        return expectResponse(() -> f.service().submitRoomRequest(MR_CODE, TOKEN, request(1, "room-stock")), 409,
                "Coca Cola chỉ còn 0 chai. Vui lòng giảm số lượng hoặc chọn món khác.");
    }

    private static FoodServiceManagementManualRunner.TestResult expired() {
        var f = foodFixture();
        when(f.orders().validSession(eq(MR_CODE), any())).thenReturn(null);
        return expectResponse(() -> f.service().submitRoomRequest(MR_CODE, TOKEN, request(1, "room-expired")), 401,
                "Phiên gọi món đã hết hạn hoặc phòng đã đổi lượt. Hãy quét lại mã MR trong phòng.");
    }

    private static FoodServiceManagementManualRunner.TestResult duplicate() {
        var f = foodFixture();
        var request = new RoomRequest(List.of(new ItemRequest(ITEM_ID, 1), new ItemRequest(ITEM_ID, 1)), "room-dup");
        return expectResponse(() -> f.service().submitRoomRequest(MR_CODE, TOKEN, request), 400,
                "Mỗi món chỉ được xuất hiện một lần trong cùng yêu cầu.");
    }

    private static FoodServiceManagementManualRunner.TestResult idempotent() {
        var f = foodFixture();
        when(f.orders().createOrder(any(), any(), any(), any(), any(), any(), any(), any(), any(), eq("same-room-key"))).thenReturn(null);
        when(f.orders().existingByKey("same-room-key"))
                .thenReturn(new ExistingOrder(ORDER_ID, BRANCH_ID, BOOKING_ID, "ROOM_QR", SESSION_ID));
        var result = f.service().submitRoomRequest(MR_CODE, TOKEN, request(1, "same-room-key"));
        verify(f.orders(), never()).insertLine(any(), any(), any(), any(), any(), any(), any(), any());
        return check(result != null, "returned existing request=" + result.code());
    }
}
