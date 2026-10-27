package com.gcmb.backend.inventory.controller;

import com.gcmb.backend.audit.service.MutationAudit;
import com.gcmb.backend.auth.dto.Identity;
import com.gcmb.backend.inventory.dto.MenuItemDtos.MenuItemStatusCommand;
import com.gcmb.backend.inventory.dto.MenuItemDtos.MenuItemCommand;
import com.gcmb.backend.inventory.dto.MenuItemDtos.MenuItemView;
import com.gcmb.backend.inventory.dto.MenuItemDtos.InventoryOption;
import com.gcmb.backend.inventory.service.MenuItemService;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/branch-management/menu-items")
public class MenuItemController {
    private final MenuItemService service;
    private final MutationAudit audit;

    public MenuItemController(MenuItemService service, MutationAudit audit) {
        this.service = service;
        this.audit = audit;
    }

    @GetMapping
    public List<MenuItemView> list(@AuthenticationPrincipal Identity actor) {
        return service.list(actor);
    }

    @GetMapping("/categories")
    public List<String> categories(@AuthenticationPrincipal Identity actor) {
        return service.categories(actor);
    }

    @GetMapping("/inventory-options")
    public List<InventoryOption> inventoryOptions(@AuthenticationPrincipal Identity actor) {
        return service.inventoryOptions(actor);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public MenuItemView create(@AuthenticationPrincipal Identity actor,
            @RequestPart("command") MenuItemCommand body,
            @RequestPart(value = "image", required = false) MultipartFile image) {
        return audit.record("CREATE_MENU_ITEM", "MENU_ITEM", null,
            () -> service.create(actor, body, image), MenuItemView::id);
    }

    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public MenuItemView update(@AuthenticationPrincipal Identity actor, @PathVariable UUID id,
            @RequestPart("command") MenuItemCommand body,
            @RequestPart(value = "image", required = false) MultipartFile image) {
        return audit.record("UPDATE_MENU_ITEM", "MENU_ITEM", id,
            () -> service.update(actor, id, body, image), MenuItemView::id);
    }

    @GetMapping("/{id}/image")
    public ResponseEntity<byte[]> image(@AuthenticationPrincipal Identity actor, @PathVariable UUID id) {
        MenuItemService.ImageContent content = service.image(actor, id);
        return imageResponse(content);
    }

    @PutMapping("/{id}/status")
    public MenuItemView changeStatus(@AuthenticationPrincipal Identity actor, @PathVariable UUID id,
            @RequestBody MenuItemStatusCommand body) {
        return audit.record("CHANGE_MENU_ITEM_STATUS", "MENU_ITEM", id,
            () -> service.changeStatus(actor, id, body), MenuItemView::id);
    }

    @DeleteMapping("/{id}")
    public void delete(@AuthenticationPrincipal Identity actor, @PathVariable UUID id) {
        audit.record("DELETE_MENU_ITEM", "MENU_ITEM", id, () -> {
            service.delete(actor, id);
            return null;
        }, null);
    }

    static ResponseEntity<byte[]> imageResponse(MenuItemService.ImageContent content) {
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(content.mimeType()))
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                .filename(content.originalName(), StandardCharsets.UTF_8).build().toString())
            .body(content.bytes());
    }
}
