/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: TrongNV
 * Created: 13/10/2026
 * Description: API gán tài khoản quản lý cho cơ sở.
 */

package com.gcmb.backend.administration.branch.controller;

import com.gcmb.backend.administration.auth.dto.response.Identity;
import com.gcmb.backend.administration.branch.dto.BranchManagerAssignmentCommand;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@RestController
@RequestMapping("/api/v1/head-office/branches")
public class BranchManagerAssignmentController {
    private final JdbcTemplate db;
    public BranchManagerAssignmentController(JdbcTemplate db){this.db=db;}

    @GetMapping
    public List<Map<String,Object>> branches(){
        return db.queryForList("SELECT b.id AS \"branchId\",b.code,b.name AS \"branchName\",u.id AS \"managerId\",u.username AS \"managerUsername\" FROM gcmb.branch b LEFT JOIN gcmb.branch_manager_assignment a ON a.branch_id=b.id LEFT JOIN gcmb.user_account u ON u.id=a.user_id ORDER BY b.name");
    }

    @GetMapping("/managers")
    public List<Map<String,Object>> managers(){
        return db.queryForList("SELECT u.id AS \"userId\",u.username,u.email FROM gcmb.user_account u JOIN gcmb.role r ON r.id=u.role_id WHERE u.status='ACTIVE' AND r.code='BRANCH_MANAGER' ORDER BY u.username");
    }

    @PutMapping("/{branchId}/manager")
    @Transactional
    public Map<String,Object> assign(@PathVariable UUID branchId,@RequestBody BranchManagerAssignmentCommand body,@AuthenticationPrincipal Identity who){
        if(body==null||body.userId()==null)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Chọn tài khoản quản lý cơ sở");
        Integer valid=db.queryForObject("SELECT count(*) FROM gcmb.user_account u JOIN gcmb.role r ON r.id=u.role_id WHERE u.id=? AND u.status='ACTIVE' AND r.code='BRANCH_MANAGER'",Integer.class,body.userId());
        Integer branch=db.queryForObject("SELECT count(*) FROM gcmb.branch WHERE id=?",Integer.class,branchId);
        if(valid==null||valid==0)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Tài khoản phải đang hoạt động và có role BRANCH_MANAGER");
        if(branch==null||branch==0)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Không tìm thấy cơ sở");
        db.update("DELETE FROM gcmb.branch_manager_assignment WHERE branch_id=? OR user_id=?",branchId,body.userId());
        db.update("INSERT INTO gcmb.branch_manager_assignment(branch_id,user_id,assigned_by) VALUES(?,?,?)",branchId,body.userId(),who.id());
        return Map.of("message","Đã phân quyền quản lý cơ sở thành công","branchId",branchId,"userId",body.userId());
    }
}
