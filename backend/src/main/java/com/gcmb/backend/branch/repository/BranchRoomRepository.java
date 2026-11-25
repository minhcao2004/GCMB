/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: TrongNV
 * Created: 13/10/2026
 * Description: Truy vấn dữ liệu phòng trong phạm vi cơ sở được phân quyền.
 */

package com.gcmb.backend.branch.repository;

import com.gcmb.backend.branch.dto.RoomCommand;
import com.gcmb.backend.branch.dto.RoomStatusCommand;
import com.gcmb.backend.branch.dto.RoomView;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class BranchRoomRepository {
    private final JdbcTemplate jdbcTemplate;
    public BranchRoomRepository(JdbcTemplate jdbcTemplate) { this.jdbcTemplate = jdbcTemplate; }
    // BR-177: chỉ ghi tại cơ sở ACTIVE, giữ khóa đến cuối transaction.
    public void requireActive(UUID branch) {
        if (jdbcTemplate.queryForList("SELECT id FROM gcmb.branch WHERE id=? AND status='ACTIVE' FOR SHARE", UUID.class, branch).isEmpty())
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN,"Cơ sở đang tạm ngừng hoạt động. Bạn có thể xem dữ liệu nhưng không thể thực hiện thay đổi.");
    }
    public boolean codeExists(UUID branch, String code, UUID exceptRoomId) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject("SELECT EXISTS(SELECT 1 FROM gcmb.room WHERE branch_id=? AND upper(code)=upper(?) AND (?::uuid IS NULL OR id<>?))", Boolean.class, branch, code.trim(), exceptRoomId, exceptRoomId));
    }
    public String branchName(UUID branch) {
        return jdbcTemplate.queryForObject("SELECT name FROM gcmb.branch WHERE id=?", String.class, branch);
    }
    private RoomView map(ResultSet r, int n) throws SQLException {
        Timestamp ready = r.getTimestamp("expected_ready_at");
        return new RoomView(r.getObject("id", UUID.class), r.getString("code"), r.getString("name"),
            r.getInt("floor_number"), r.getObject("room_type_id", UUID.class), r.getString("type_code"),
            r.getString("type_name"), r.getString("mode"), r.getInt("capacity"), r.getString("description"),
            r.getString("operating_status"), r.getString("status_reason"), ready == null ? null : ready.toInstant().atOffset(java.time.ZoneOffset.UTC), r.getString("booking_status"), r.getString("mr_code"));
    }
    private static final String SELECT = "SELECT r.*, rt.code AS type_code, rt.name AS type_name, rt.mode, CASE WHEN EXISTS (SELECT 1 FROM gcmb.room_allocation a JOIN gcmb.booking b ON b.id=a.booking_id AND b.branch_id=a.branch_id WHERE a.room_id=r.id AND a.branch_id=r.branch_id AND (a.state='OCCUPIED' OR b.status='IN_HOUSE') AND a.actual_end IS NULL) THEN 'IN_USE' WHEN EXISTS (SELECT 1 FROM gcmb.room_allocation a JOIN gcmb.booking b ON b.id=a.booking_id AND b.branch_id=a.branch_id WHERE a.room_id=r.id AND a.branch_id=r.branch_id AND a.state='RESERVED' AND b.status='CONFIRMED' AND a.starts_at>=CURRENT_TIMESTAMP AND a.starts_at<CURRENT_TIMESTAMP+INTERVAL '1 day') THEN 'BOOKED' ELSE 'READY' END AS booking_status FROM gcmb.room r JOIN gcmb.room_type rt ON rt.id=r.room_type_id ";
    public List<RoomView> list(UUID branchId) {
        return jdbcTemplate.query(SELECT + "WHERE r.branch_id=? ORDER BY r.floor_number,r.code", this::map, branchId);
    }
    public RoomView get(UUID branchId, UUID id) {
        List<RoomView> rows=jdbcTemplate.query(SELECT+"WHERE r.branch_id=? AND r.id=?",this::map,branchId,id);
        return rows.isEmpty()?null:rows.get(0);
    }
    public List<RoomView> types() {
        return jdbcTemplate.query("SELECT id, code, name, mode FROM gcmb.room_type WHERE active=true ORDER BY name",
            (resultSet, rowNumber) ->new RoomView(null,resultSet.getString("code"),resultSet.getString("name"),null,resultSet.getObject("id",UUID.class),resultSet.getString("code"),resultSet.getString("name"),resultSet.getString("mode"),null,null,null,null,null,null,null));
    }
    public RoomView create(UUID branch, RoomCommand command) {
        UUID id=jdbcTemplate.queryForObject("INSERT INTO gcmb.room(branch_id,room_type_id,code,name,capacity,operating_status,floor_number,description,status_reason) SELECT ?,id,?,?,?,'AVAILABLE',?,?,'' FROM gcmb.room_type WHERE id=? AND active=true RETURNING id",UUID.class,branch,command.code().trim(),command.name().trim(),command.capacity(),command.floorNumber(),command.description()==null?"":command.description().trim(),command.roomTypeId());
        return get(branch,id);
    }
    public RoomView update(UUID branch, UUID id, RoomCommand command) {
        int changed=jdbcTemplate.update("UPDATE gcmb.room r SET room_type_id=rt.id,code=?,name=?,capacity=?,floor_number=?,description=? FROM gcmb.room_type rt WHERE r.id=? AND r.branch_id=? AND rt.id=? AND rt.active=true",command.code().trim(),command.name().trim(),command.capacity(),command.floorNumber(),command.description()==null?"":command.description().trim(),id,branch,command.roomTypeId());
        return changed==0?null:get(branch,id);
    }
    public boolean delete(UUID branch, UUID id) { return jdbcTemplate.update("DELETE FROM gcmb.room WHERE id=? AND branch_id=? AND NOT EXISTS(SELECT 1 FROM gcmb.room_allocation a WHERE a.room_id=gcmb.room.id) AND NOT EXISTS(SELECT 1 FROM gcmb.asset x WHERE x.room_id=gcmb.room.id) AND NOT EXISTS(SELECT 1 FROM gcmb.asset_transfer at WHERE at.to_room_id=gcmb.room.id)",id,branch)>0; }
    public RoomView changeStatus(UUID branch, UUID id, UUID actor, RoomStatusCommand command) {
        RoomView before=get(branch,id); if(before==null)return null;
        Timestamp ready=command.expectedReadyAt()==null?null:Timestamp.from(command.expectedReadyAt().toInstant());
        int changed=jdbcTemplate.update("UPDATE gcmb.room SET operating_status=?,status_reason=?,expected_ready_at=? WHERE id=? AND branch_id=?",command.status(),command.reason()==null?"":command.reason().trim(),ready,id,branch);
        if(changed==0)return null;
        jdbcTemplate.update("INSERT INTO gcmb.room_operating_status_history(room_id,branch_id,previous_status,new_status,reason,expected_ready_at,changed_by) VALUES(?,?,?,?,?,?,?)",id,branch,before.operatingStatus(),command.status(),command.reason()==null?"":command.reason().trim(),ready,actor);
        return get(branch,id);
    }
}
