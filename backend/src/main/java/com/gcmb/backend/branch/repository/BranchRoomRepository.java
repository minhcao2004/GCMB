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
    private final JdbcTemplate db;
    public BranchRoomRepository(JdbcTemplate db) { this.db = db; }
    private RoomView map(ResultSet r, int n) throws SQLException {
        Timestamp ready = r.getTimestamp("expected_ready_at");
        return new RoomView(r.getObject("id", UUID.class), r.getString("code"), r.getString("name"),
            r.getInt("floor_number"), r.getObject("room_type_id", UUID.class), r.getString("type_code"),
            r.getString("type_name"), r.getString("mode"), r.getInt("capacity"), r.getString("description"),
            r.getString("operating_status"), r.getString("status_reason"), ready == null ? null : ready.toInstant().atOffset(java.time.ZoneOffset.UTC), r.getString("booking_status"));
    }
    private static final String SELECT = "SELECT r.*, rt.code AS type_code, rt.name AS type_name, rt.mode, CASE WHEN EXISTS (SELECT 1 FROM gcmb.room_allocation a JOIN gcmb.booking b ON b.id=a.booking_id AND b.branch_id=a.branch_id WHERE a.room_id=r.id AND a.branch_id=r.branch_id AND (a.state='OCCUPIED' OR b.status='IN_HOUSE') AND a.actual_end IS NULL) THEN 'IN_USE' WHEN EXISTS (SELECT 1 FROM gcmb.room_allocation a JOIN gcmb.booking b ON b.id=a.booking_id AND b.branch_id=a.branch_id WHERE a.room_id=r.id AND a.branch_id=r.branch_id AND a.state='RESERVED' AND b.status='CONFIRMED' AND a.starts_at>=CURRENT_TIMESTAMP AND a.starts_at<CURRENT_TIMESTAMP+INTERVAL '1 day') THEN 'BOOKED' ELSE 'READY' END AS booking_status FROM gcmb.room r JOIN gcmb.room_type rt ON rt.id=r.room_type_id ";
    public List<RoomView> list(UUID branchId) {
        return db.query(SELECT + "WHERE r.branch_id=? ORDER BY r.floor_number,r.code", this::map, branchId);
    }
    public RoomView get(UUID branchId, UUID id) {
        List<RoomView> rows=db.query(SELECT+"WHERE r.branch_id=? AND r.id=?",this::map,branchId,id);
        return rows.isEmpty()?null:rows.get(0);
    }
    public List<RoomView> types() {
        return db.query("SELECT id, code, name, mode FROM gcmb.room_type WHERE active=true ORDER BY name",
            (r,n)->new RoomView(null,r.getString("code"),r.getString("name"),null,r.getObject("id",UUID.class),r.getString("code"),r.getString("name"),r.getString("mode"),null,null,null,null,null,null));
    }
    public RoomView create(UUID branch, RoomCommand c) {
        UUID id=db.queryForObject("INSERT INTO gcmb.room(branch_id,room_type_id,code,name,capacity,operating_status,floor_number,description,status_reason) SELECT ?,id,?,?,?,'AVAILABLE',?,?,'' FROM gcmb.room_type WHERE id=? AND active=true RETURNING id",UUID.class,branch,c.code().trim(),c.name().trim(),c.capacity(),c.floorNumber(),c.description()==null?"":c.description().trim(),c.roomTypeId());
        return get(branch,id);
    }
    public RoomView update(UUID branch, UUID id, RoomCommand c) {
        int changed=db.update("UPDATE gcmb.room r SET room_type_id=rt.id,code=?,name=?,capacity=?,floor_number=?,description=? FROM gcmb.room_type rt WHERE r.id=? AND r.branch_id=? AND rt.id=? AND rt.active=true",c.code().trim(),c.name().trim(),c.capacity(),c.floorNumber(),c.description()==null?"":c.description().trim(),id,branch,c.roomTypeId());
        return changed==0?null:get(branch,id);
    }
    public boolean delete(UUID branch, UUID id) { return db.update("DELETE FROM gcmb.room WHERE id=? AND branch_id=? AND NOT EXISTS(SELECT 1 FROM gcmb.room_allocation a WHERE a.room_id=gcmb.room.id) AND NOT EXISTS(SELECT 1 FROM gcmb.asset x WHERE x.room_id=gcmb.room.id) AND NOT EXISTS(SELECT 1 FROM gcmb.asset_transfer at WHERE at.to_room_id=gcmb.room.id)",id,branch)>0; }
    public RoomView changeStatus(UUID branch, UUID id, UUID actor, RoomStatusCommand c) {
        RoomView before=get(branch,id); if(before==null)return null;
        Timestamp ready=c.expectedReadyAt()==null?null:Timestamp.from(c.expectedReadyAt().toInstant());
        int changed=db.update("UPDATE gcmb.room SET operating_status=?,status_reason=?,expected_ready_at=? WHERE id=? AND branch_id=?",c.status(),c.reason()==null?"":c.reason().trim(),ready,id,branch);
        if(changed==0)return null;
        db.update("INSERT INTO gcmb.room_operating_status_history(room_id,branch_id,previous_status,new_status,reason,expected_ready_at,changed_by) VALUES(?,?,?,?,?,?,?)",id,branch,before.operatingStatus(),c.status(),c.reason()==null?"":c.reason().trim(),ready,actor);
        return get(branch,id);
    }
}
