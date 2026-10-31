package com.gcmb.backend.inventory.controller;

import com.gcmb.backend.inventory.dto.FoodOrderDtos.GuestSession;
import com.gcmb.backend.inventory.dto.FoodOrderDtos.GuestOrderView;
import com.gcmb.backend.inventory.dto.FoodOrderDtos.RoomMenu;
import com.gcmb.backend.inventory.dto.FoodOrderDtos.RoomRequest;
import com.gcmb.backend.inventory.service.FoodOrderService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/public/mr/{mrCode}")
public class PublicRoomOrderController {
    private final FoodOrderService service;

    public PublicRoomOrderController(FoodOrderService service) { this.service = service; }

    @PostMapping("/session")
    public GuestSession openSession(@PathVariable String mrCode) {
        return service.openRoomSession(mrCode);
    }

    @GetMapping("/menu")
    public RoomMenu menu(@PathVariable String mrCode,
            @RequestHeader(name = "X-GCMB-QR-SESSION", required = false) String token) {
        return service.roomMenu(mrCode, token);
    }

    @GetMapping("/orders")
    public List<GuestOrderView> orders(@PathVariable String mrCode,
            @RequestHeader(name = "X-GCMB-QR-SESSION", required = false) String token) {
        return service.roomOrders(mrCode, token);
    }

    @PostMapping("/orders")
    public GuestOrderView submit(@PathVariable String mrCode,
            @RequestHeader(name = "X-GCMB-QR-SESSION", required = false) String token,
            @RequestBody RoomRequest request) {
        return service.submitRoomRequest(mrCode, token, request);
    }
}
