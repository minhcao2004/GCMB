/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: CaoNM
 * Created: 11/11/2026
 * Description: Defines UC-126 Update Order Service Status manual unit test cases.
 */
package com.gcmb.backend.manual.foodservicemanagement;

import static com.gcmb.backend.manual.foodservicemanagement.CreateMenuItemManualCases.note;
import static com.gcmb.backend.manual.foodservicemanagement.FoodServiceManagementManualRunner.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gcmb.backend.inventory.dto.FoodOrderDtos.ServiceStatusCommand;
import com.gcmb.backend.inventory.repository.FoodOrderRepository.OrderLock;
import java.util.List;

final class UpdateOrderServiceStatusManualCases {
    private static final String UC = "UC-126 Update Order Service Status";
    private static final String PRE = "Lễ tân letan.hoabang đăng nhập; order thuộc HN_HOA_BANG và được phân công cho chính tài khoản này.";

    private UpdateOrderServiceStatusManualCases() {}

    static List<FoodServiceManagementManualRunner.ManualTest> cases() {
        return List.of(
            tc("FSM-069", PRE + " Order đang ACCEPTED.", "status=SERVED.", "Order chuyển SERVED và trả dữ liệu cập nhật.", "Normal", "BR-123", "", () -> status("ACCEPTED", ACTOR_ID, new ServiceStatusCommand("SERVED"))),
            tc("FSM-070", PRE.replace("chính tài khoản này", "lễ tân khác"), "status=SERVED.", "Hiển thị “Chỉ lễ tân được phân công phụ trách đơn này mới có thể cập nhật tiến độ phục vụ.”; không cập nhật.", "Abnormal", "BR-123", "MSG76", UpdateOrderServiceStatusManualCases::otherUser),
            tc("FSM-071", PRE + " Order đang ACCEPTED.", "status=null.", "Hiển thị “Chọn trạng thái phục vụ hợp lệ.”; không cập nhật.", "Abnormal", "BR-111", "MSG02", () -> status("ACCEPTED", ACTOR_ID, new ServiceStatusCommand(null))),
            tc("FSM-072", PRE + " Order đang PENDING.", "status=SERVED.", "Hiển thị “Chỉ cập nhật tiến độ cho order đã được xác nhận.”; không cập nhật.", "Abnormal", "BR-123", "", () -> status("PENDING", ACTOR_ID, new ServiceStatusCommand("SERVED"))),
            tc("FSM-073", PRE + " Order đã SERVED.", "status=SERVED lần nữa.", "Trả order hiện có; không ghi trạng thái lần hai.", "Abnormal", "BR-164", "MSG74", UpdateOrderServiceStatusManualCases::alreadyServed),
            tc("FSM-074", PRE + " Order đang ACCEPTED và đã có charge/stock movement.", "status=SERVED.", "Chỉ cập nhật trạng thái; charge và stock không thay đổi.", "Normal", "BR-27, BR-38, BR-123", "", UpdateOrderServiceStatusManualCases::onlyStatus)
        );
    }

    private static FoodServiceManagementManualRunner.ManualTest tc(String id, String pre, String input,
            String expected, String type, String br, String msg, FoodServiceManagementManualRunner.CheckedAction action) {
        return test(id, UC, "FoodOrderService.updateServiceStatus", pre, input, expected, type,
                note("UC-126 - Update_Order_Service_Status.docx", br, msg, "FoodOrderService.updateServiceStatus", "White-box"), action);
    }

    private static OrderLock lock(String status, java.util.UUID assignedUser) {
        return new OrderLock(ORDER_ID, BRANCH_ID, BOOKING_ID, ALLOCATION_ID, "STAFF", null,
                status, EMPLOYEE_ID, assignedUser);
    }

    private static FoodServiceManagementManualRunner.TestResult status(String current, java.util.UUID assignedUser,
            ServiceStatusCommand command) {
        var f = foodFixture();
        when(f.orders().lockOrder(ORDER_ID, BRANCH_ID)).thenReturn(lock(current, assignedUser));
        if (command == null || !"SERVED".equals(command.status())) {
            return expectResponse(() -> f.service().updateServiceStatus(receptionist(), ORDER_ID, command), 400,
                    "Chọn trạng thái phục vụ hợp lệ.");
        }
        if (!"ACCEPTED".equals(current)) {
            return expectResponse(() -> f.service().updateServiceStatus(receptionist(), ORDER_ID, command), 409,
                    "Chỉ cập nhật tiến độ cho order đã được xác nhận.");
        }
        when(f.orders().order(ORDER_ID, BRANCH_ID)).thenReturn(order("SERVED", "STAFF", EMPLOYEE_ID));
        var row = f.service().updateServiceStatus(receptionist(), ORDER_ID, command);
        return check("SERVED".equals(row.status()), "status=" + row.status());
    }

    private static FoodServiceManagementManualRunner.TestResult otherUser() {
        var f = foodFixture();
        when(f.orders().lockOrder(ORDER_ID, BRANCH_ID)).thenReturn(lock("ACCEPTED", OTHER_ACTOR_ID));
        return expectResponse(() -> f.service().updateServiceStatus(receptionist(), ORDER_ID, new ServiceStatusCommand("SERVED")), 403,
                "Chỉ lễ tân được phân công phụ trách đơn này mới có thể cập nhật tiến độ phục vụ.");
    }

    private static FoodServiceManagementManualRunner.TestResult alreadyServed() {
        var f = foodFixture();
        when(f.orders().lockOrder(ORDER_ID, BRANCH_ID)).thenReturn(lock("SERVED", ACTOR_ID));
        when(f.orders().order(ORDER_ID, BRANCH_ID)).thenReturn(order("SERVED", "STAFF", EMPLOYEE_ID));
        var row = f.service().updateServiceStatus(receptionist(), ORDER_ID, new ServiceStatusCommand("SERVED"));
        verify(f.orders(), never()).serve(any(), any());
        return check("SERVED".equals(row.status()), "status already SERVED; no update");
    }

    private static FoodServiceManagementManualRunner.TestResult onlyStatus() {
        var f = foodFixture();
        when(f.orders().lockOrder(ORDER_ID, BRANCH_ID)).thenReturn(lock("ACCEPTED", ACTOR_ID));
        when(f.orders().order(ORDER_ID, BRANCH_ID)).thenReturn(order("SERVED", "STAFF", EMPLOYEE_ID));
        f.service().updateServiceStatus(receptionist(), ORDER_ID, new ServiceStatusCommand("SERVED"));
        verify(f.orders()).serve(ORDER_ID, BRANCH_ID);
        verify(f.orders(), never()).insertSale(any(), any(), any(), any(), any(), any());
        verify(f.orders(), never()).updateLine(any(), any(), any(), any());
        return check(true, "only fb_order status update was requested");
    }
}
