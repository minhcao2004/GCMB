package com.gcmb.backend.inventory.controller;

import com.gcmb.backend.audit.service.MutationAudit;
import com.gcmb.backend.auth.dto.Identity;
import com.gcmb.backend.inventory.dto.FoodOrderDtos.ActiveBooking;
import com.gcmb.backend.inventory.dto.FoodOrderDtos.ConfirmRequest;
import com.gcmb.backend.inventory.dto.FoodOrderDtos.OrderView;
import com.gcmb.backend.inventory.dto.FoodOrderDtos.ServiceStatusCommand;
import com.gcmb.backend.inventory.dto.FoodOrderDtos.ServingEmployee;
import com.gcmb.backend.inventory.dto.FoodOrderDtos.StaffOrderRequest;
import com.gcmb.backend.inventory.dto.MenuItemDtos.PublicMenuItemView;
import com.gcmb.backend.inventory.service.FoodOrderService;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reception/orders")
public class ReceptionOrderController {
    private final FoodOrderService service;
    private final MutationAudit audit;

    public ReceptionOrderController(FoodOrderService service, MutationAudit audit) {
        this.service = service;
        this.audit = audit;
    }

    @GetMapping
    public List<OrderView> list(@AuthenticationPrincipal Identity actor,
            @RequestParam(required = false) String status) {
        return service.list(actor, status);
    }

    @GetMapping("/active-bookings")
    public List<ActiveBooking> activeBookings(@AuthenticationPrincipal Identity actor) {
        return service.activeBookings(actor);
    }

    @GetMapping("/serving-employees")
    public List<ServingEmployee> servingEmployees(@AuthenticationPrincipal Identity actor) {
        return service.servingEmployees(actor);
    }

    @GetMapping("/menu")
    public List<PublicMenuItemView> menu(@AuthenticationPrincipal Identity actor) {
        return service.receptionMenu(actor);
    }

    @PostMapping
    public OrderView addItems(@AuthenticationPrincipal Identity actor, @RequestBody StaffOrderRequest request) {
        return audit.record("ADD_FB_ORDER_ITEMS", "FB_ORDER", null,
            () -> service.addItems(actor, request), OrderView::id);
    }

    @PostMapping("/{orderId}/confirm")
    public OrderView confirm(@AuthenticationPrincipal Identity actor, @PathVariable UUID orderId,
            @RequestBody ConfirmRequest request) {
        return audit.record("CONFIRM_FB_ORDER", "FB_ORDER", orderId,
            () -> service.confirmRequest(actor, orderId, request), null);
    }

    @PostMapping("/{orderId}/reject")
    public OrderView reject(@AuthenticationPrincipal Identity actor, @PathVariable UUID orderId) {
        return audit.record("REJECT_FB_ORDER", "FB_ORDER", orderId,
            () -> service.rejectRequest(actor, orderId), null);
    }

    @PutMapping("/{orderId}/service-status")
    public OrderView serviceStatus(@AuthenticationPrincipal Identity actor, @PathVariable UUID orderId,
            @RequestBody ServiceStatusCommand command) {
        return audit.record("UPDATE_FB_ORDER_SERVICE_STATUS", "FB_ORDER", orderId,
            () -> service.updateServiceStatus(actor, orderId, command), null);
    }
}
