package com.gcmb.backend.inventory.controller;

import com.gcmb.backend.audit.service.MutationAudit;
import com.gcmb.backend.auth.dto.Identity;
import com.gcmb.backend.inventory.dto.MenuItemDtos.MenuItemStatusCommand;
import com.gcmb.backend.inventory.dto.MenuItemDtos.MenuItemView;
import com.gcmb.backend.inventory.service.MenuItemService;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
}
