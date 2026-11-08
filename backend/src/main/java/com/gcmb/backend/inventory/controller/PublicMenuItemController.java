package com.gcmb.backend.inventory.controller;

import com.gcmb.backend.inventory.dto.MenuItemDtos.PublicMenuItemView;
import com.gcmb.backend.inventory.service.MenuItemService;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/public/branches/{branchId}/menu-items")
public class PublicMenuItemController {
    private final MenuItemService service;

    public PublicMenuItemController(MenuItemService service) { this.service = service; }

    @GetMapping
    public List<PublicMenuItemView> list(@PathVariable UUID branchId,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String category) {
        return service.publicList(branchId, q, category);
    }

    @GetMapping("/{id}/image")
    public ResponseEntity<byte[]> image(@PathVariable UUID branchId, @PathVariable UUID id) {
        return MenuItemController.imageResponse(service.publicImage(branchId, id));
    }
}
