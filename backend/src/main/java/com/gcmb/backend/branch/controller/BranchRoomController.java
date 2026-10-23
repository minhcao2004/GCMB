/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: TrongNV
 * Created: 13/10/2026
 * Description: API xem, tạo, sửa, xóa phòng và cập nhật trạng thái theo cơ sở.
 */

package com.gcmb.backend.branch.controller;

import com.gcmb.backend.audit.service.MutationAudit;
import com.gcmb.backend.auth.dto.Identity;
import com.gcmb.backend.branch.dto.RoomCommand;
import com.gcmb.backend.branch.dto.RoomStatusCommand;
import com.gcmb.backend.branch.dto.RoomView;
import com.gcmb.backend.branch.service.BranchRoomService;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/branch-management/rooms")
public class BranchRoomController {
    private final BranchRoomService service;
    private final MutationAudit audit;
    public BranchRoomController(BranchRoomService service, MutationAudit audit) { this.audit = audit;this.service=service;}
    @GetMapping public List<RoomView> list(@AuthenticationPrincipal Identity who){return service.list(who);}
    @GetMapping("/types") public List<RoomView> types(@AuthenticationPrincipal Identity who){return service.types(who);}
    @PostMapping public RoomView create(@AuthenticationPrincipal Identity who,@RequestBody RoomCommand body){return audit.record("CREATE_ROOM","ROOM",null,()->service.create(who,body),RoomView::id);}
    @PutMapping("/{id}") public RoomView update(@AuthenticationPrincipal Identity who,@PathVariable UUID id,@RequestBody RoomCommand body){return audit.record("UPDATE_ROOM","ROOM",id,()->service.update(who,id,body),RoomView::id);}
    @DeleteMapping("/{id}") public void delete(@AuthenticationPrincipal Identity who,@PathVariable UUID id){audit.record("DELETE_ROOM","ROOM",id,()->{service.delete(who,id);return null;},null);}
    @PutMapping("/{id}/status") public RoomView status(@AuthenticationPrincipal Identity who,@PathVariable UUID id,@RequestBody RoomStatusCommand body){return audit.record("UPDATE_ROOM_STATUS","ROOM",id,()->service.status(who,id,body),RoomView::id);}
}
