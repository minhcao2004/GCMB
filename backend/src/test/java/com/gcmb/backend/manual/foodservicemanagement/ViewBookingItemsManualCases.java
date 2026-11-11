/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: CaoNM
 * Created: 11/11/2026
 * Description: Defines UC-52 View Booking Items manual unit test cases.
 */
package com.gcmb.backend.manual.foodservicemanagement;

import static com.gcmb.backend.manual.foodservicemanagement.CreateMenuItemManualCases.note;
import static com.gcmb.backend.manual.foodservicemanagement.FoodServiceManagementManualRunner.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

final class ViewBookingItemsManualCases {
    private ViewBookingItemsManualCases() {}

    static List<FoodServiceManagementManualRunner.ManualTest> cases() {
        String uc = "UC-52 View Booking Items";
        String pre = "Lễ tân letan.hoabang đăng nhập tại HN_HOA_BANG; order món đã tồn tại.";
        return List.of(
            test("FSM-055", uc, "FoodOrderService.list", pre, "status=ALL.", "Trả chi tiết order của cơ sở ở các trạng thái; không ghi dữ liệu.", "Normal", note("UC-52 - View_Booking_Items.docx", "BR-27, BR-177", "", "FoodOrderService.list", "Black-box"), ViewBookingItemsManualCases::all),
            test("FSM-056", uc, "FoodOrderService.list", pre, "status=UNKNOWN.", "Hiển thị “Trạng thái order không hợp lệ.”; không truy vấn danh sách.", "Abnormal", note("UC-52 - View_Booking_Items.docx", "BR-111", "MSG02", "FoodOrderService.list", "White-box"), ViewBookingItemsManualCases::invalid),
            test("FSM-057", uc, "FoodOrderService.list", pre, "status=REJECTED nhưng không có kết quả.", "Trả danh sách rỗng; không ghi dữ liệu.", "Normal", note("UC-52 - View_Booking_Items.docx", "BR-27", "", "FoodOrderService.list", "Black-box"), ViewBookingItemsManualCases::empty),
            test("FSM-058", uc, "FoodOrderService.list", pre + " Cơ sở khác có order.", "Xem status=ALL.", "Chỉ truy vấn và trả order HN_HOA_BANG.", "Abnormal", note("UC-52 - View_Booking_Items.docx", "BR-177", "", "FoodOrderService.list", "White-box"), ViewBookingItemsManualCases::branch)
        );
    }

    private static FoodServiceManagementManualRunner.TestResult all() {
        var f = foodFixture();
        when(f.orders().orders(BRANCH_ID, null)).thenReturn(List.of(order("PENDING", "ROOM_QR", null), order("ACCEPTED", "STAFF", EMPLOYEE_ID)));
        var rows = f.service().list(receptionist(), "ALL");
        return check(rows.size() == 2, "rows=" + rows.size());
    }

    private static FoodServiceManagementManualRunner.TestResult invalid() {
        var f = foodFixture();
        var result = expectResponse(() -> f.service().list(receptionist(), "UNKNOWN"), 400, "Trạng thái order không hợp lệ.");
        verify(f.orders(), never()).orders(any(), any());
        return result;
    }

    private static FoodServiceManagementManualRunner.TestResult empty() {
        var f = foodFixture();
        when(f.orders().orders(BRANCH_ID, "REJECTED")).thenReturn(List.of());
        return check(f.service().list(receptionist(), "REJECTED").isEmpty(), "rows=0");
    }

    private static FoodServiceManagementManualRunner.TestResult branch() {
        var f = foodFixture();
        f.service().list(receptionist(), "ALL");
        verify(f.orders()).orders(BRANCH_ID, null);
        return check(true, "repository queried branch=" + BRANCH_ID);
    }
}
