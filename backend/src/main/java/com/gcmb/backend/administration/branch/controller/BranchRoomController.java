package com.gcmb.backend.administration.branch.controller;

import com.gcmb.backend.administration.auth.dto.response.Identity;
import com.gcmb.backend.administration.branch.dto.RoomCommand;
import com.gcmb.backend.administration.branch.dto.RoomStatusCommand;
import com.gcmb.backend.administration.branch.dto.RoomView;
import com.gcmb.backend.administration.branch.service.BranchRoomService;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/branch-management/rooms")
public class BranchRoomController {
    private final BranchRoomService service;
    public BranchRoomController(BranchRoomService service){this.service=service;}
    @GetMapping public List<RoomView> list(@AuthenticationPrincipal Identity who){return service.list(who);}
    @GetMapping("/types") public List<RoomView> types(@AuthenticationPrincipal Identity who){return service.types(who);}
    @PostMapping public RoomView create(@AuthenticationPrincipal Identity who,@RequestBody RoomCommand body){return service.create(who,body);}
    @PutMapping("/{id}") public RoomView update(@AuthenticationPrincipal Identity who,@PathVariable UUID id,@RequestBody RoomCommand body){return service.update(who,id,body);}
    @DeleteMapping("/{id}") public void delete(@AuthenticationPrincipal Identity who,@PathVariable UUID id){service.delete(who,id);}
    @PutMapping("/{id}/status") public RoomView status(@AuthenticationPrincipal Identity who,@PathVariable UUID id,@RequestBody RoomStatusCommand body){return service.status(who,id,body);}
}
