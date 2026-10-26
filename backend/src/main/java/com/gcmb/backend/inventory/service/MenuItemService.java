package com.gcmb.backend.inventory.service;

import com.gcmb.backend.auth.dto.Identity;
import com.gcmb.backend.inventory.dto.MenuItemDtos.MenuItemStatusCommand;
import com.gcmb.backend.inventory.dto.MenuItemDtos.MenuItemView;
import com.gcmb.backend.inventory.repository.MenuItemRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class MenuItemService {
    private final MenuItemRepository repository;

    public MenuItemService(MenuItemRepository repository) { this.repository = repository; }

    @Transactional(readOnly = true)
    public List<MenuItemView> list(Identity actor) {
        return repository.list(branch(actor));
    }

    @Transactional(readOnly = true)
    public List<String> categories(Identity actor) {
        return repository.categories(branch(actor));
    }

    @Transactional
    public MenuItemView changeStatus(Identity actor, UUID id, MenuItemStatusCommand command) {
        if (command == null || command.active() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Vui lòng chọn trạng thái bán hợp lệ.");
        }
        MenuItemView updated = repository.changeStatus(branch(actor), id, command.active());
        if (updated == null) throw notFound();
        return updated;
    }

    @Transactional
    public void delete(Identity actor, UUID id) {
        UUID branchId = branch(actor);
        if (!repository.softDelete(branchId, id, actor.id())) throw notFound();
    }

    private UUID branch(Identity actor) {
        if (actor == null || !"BRANCH_MANAGER".equals(actor.role()) || actor.branchId() == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Tài khoản chưa được phân quyền quản lý cơ sở.");
        }
        return actor.branchId();
    }

    private ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy món trong cơ sở được phân quyền.");
    }
}
