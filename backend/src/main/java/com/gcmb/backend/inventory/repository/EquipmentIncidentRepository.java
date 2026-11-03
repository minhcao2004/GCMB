/**
 * Copyright (c) 2026 SEP490-G12.
 * Author: TrongNV
 * Created: 28/10
 * Description: Truy vấn sự cố sửa chữa và điều chuyển tài sản UC70–UC76.
 */
package com.gcmb.backend.inventory.repository;

import com.gcmb.backend.inventory.dto.EquipmentIncidentDtos.*;
import java.util.List;
import java.util.UUID;
import com.gcmb.backend.inventory.service.EquipmentAssetFileStorageService.StoredFile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class EquipmentIncidentRepository {
    private final JdbcTemplate db;
    // UC-72/UC-74: Dùng JDBC hiện có để trả dữ liệu theo phạm vi cơ sở.
    public EquipmentIncidentRepository(JdbcTemplate db) { this.db = db; }

    // UC-74: Tìm cơ sở hiện tại để tạo yêu cầu điều chuyển mà chưa cập nhật tài sản.
    public UUID assetBranch(UUID assetId) { return db.query("SELECT branch_id FROM gcmb.asset WHERE id=?",rs->rs.next()?rs.getObject(1,UUID.class):null,assetId); }

    // UC-70: Cung cấp tài sản còn hoạt động để Lễ tân hoặc Quản lý cơ sở báo cáo.
    public List<AssetOption> assets(UUID branch) {
        return db.query("SELECT a.id,a.code,a.name,r.code room_code,COALESCE(r.name,a.location_note) location FROM gcmb.asset a LEFT JOIN gcmb.room r ON r.id=a.room_id WHERE a.branch_id=? AND a.active AND a.status NOT IN ('IN_TRANSIT','RETIRED') ORDER BY a.code",
            (rs,n)->new AssetOption(rs.getObject("id",UUID.class),rs.getString("code"),rs.getString("name"),rs.getString("room_code"),rs.getString("location")),branch);
    }

    // BR-115/BR-116: Ghi metadata chứng từ đã được xác minh bởi dịch vụ lưu trữ.
    public void saveEvidence(StoredFile file, UUID actor) {
        db.update("INSERT INTO gcmb.attachment(id,object_key,original_name,mime_type,byte_size,sha256,uploaded_by) VALUES(?,?,?,?,?,?,?)",file.id(),file.objectKey(),file.originalName(),file.mimeType(),file.byteSize(),file.sha256(),actor);
    }

    // UC-70: Liên kết chứng cứ với báo cáo thuộc đúng cơ sở.
    public boolean setIncidentEvidence(UUID branch, UUID id, UUID attachment) { return db.update("UPDATE gcmb.asset_incident SET evidence_id=? WHERE id=? AND branch_id=?",attachment,id,branch)>0; }

    // UC-71: Liên kết chứng từ sửa chữa với kết quả mới nhất của sự cố.
    public boolean setRepairEvidence(UUID branch, UUID id, UUID attachment) { return db.update("UPDATE gcmb.asset_repair p SET evidence_id=? FROM gcmb.asset_incident i WHERE p.incident_id=i.id AND i.id=? AND i.branch_id=? AND p.created_at=(SELECT max(x.created_at) FROM gcmb.asset_repair x WHERE x.incident_id=i.id)",attachment,id,branch)>0; }

    // UC-72: Hiển thị sự cố cùng tài sản, cơ sở, vị trí và lần sửa mới nhất.
    public List<IncidentView> incidents(UUID branch, String search, String status) {
        return db.query("""
            SELECT i.id,i.asset_id,a.code asset_code,a.name asset_name,i.branch_id,b.name branch_name,
              a.room_id,r.code room_code,COALESCE(r.name,a.location_note,'—') location,
              i.description,i.room_unusable,i.status,i.handling_plan,i.assessment_note,
              p.vendor_name,p.actual_cost,p.finished_at::date completed_on,p.outcome,i.created_at
            FROM gcmb.asset_incident i JOIN gcmb.asset a ON a.id=i.asset_id
            JOIN gcmb.branch b ON b.id=i.branch_id LEFT JOIN gcmb.room r ON r.id=a.room_id
            LEFT JOIN LATERAL (SELECT vendor_name,actual_cost,finished_at,outcome FROM gcmb.asset_repair x
              WHERE x.incident_id=i.id ORDER BY x.created_at DESC LIMIT 1) p ON true
            WHERE i.branch_id=? AND (?='' OR i.status=?) AND
              (?='' OR lower(concat_ws(' ',a.code,a.name,i.description,i.assessment_note)) LIKE lower('%'||?||'%'))
            ORDER BY i.created_at DESC,i.id DESC
            """, (rs,n) -> new IncidentView(rs.getObject("id",UUID.class),rs.getObject("asset_id",UUID.class),rs.getString("asset_code"),rs.getString("asset_name"),rs.getObject("branch_id",UUID.class),rs.getString("branch_name"),rs.getObject("room_id",UUID.class),rs.getString("room_code"),rs.getString("location"),rs.getString("description"),rs.getBoolean("room_unusable"),rs.getString("status"),rs.getString("handling_plan"),rs.getString("assessment_note"),rs.getString("vendor_name"),rs.getBigDecimal("actual_cost"),rs.getObject("completed_on",java.time.LocalDate.class),rs.getString("outcome"),rs.getObject("created_at",java.time.OffsetDateTime.class)), branch,status,status,search,search);
    }

    // UC-70: Tạo sự cố ban đầu ở trạng thái REPORTED và lưu người báo.
    public UUID createIncident(UUID branch, UUID actor, IncidentCommand c) {
        return db.queryForObject("INSERT INTO gcmb.asset_incident(asset_id,branch_id,reported_by,description,room_unusable,status) SELECT a.id,a.branch_id,?,?,?, 'REPORTED' FROM gcmb.asset a WHERE a.id=? AND a.branch_id=? AND a.active RETURNING id",UUID.class,actor,c.description().trim(),c.roomUnusable(),c.assetId(),branch);
    }

    // UC-73: Lưu đánh giá và chọn nhánh sửa chữa hoặc thay thế.
    public int assess(UUID branch, UUID actor, UUID id, AssessmentCommand c) {
        return db.update("UPDATE gcmb.asset_incident i SET handling_plan=?,assessment_note=?,replacement_asset_id=?,assessed_by=?,assessed_at=CURRENT_TIMESTAMP,status='ASSESSED' WHERE i.id=? AND i.branch_id=? AND i.status='REPORTED' AND (?<>'REPLACE' OR EXISTS(SELECT 1 FROM gcmb.asset a WHERE a.id=? AND a.branch_id=i.branch_id AND a.active))",c.plan(),c.note().trim(),c.replacementAssetId(),actor,id,branch,c.plan(),c.replacementAssetId());
    }

    // BR-43: Khi báo cáo ảnh hưởng khả dụng phòng, chuyển phòng sang bảo trì và lưu lịch sử trạng thái.
    public void markRoomMaintenance(UUID branch, UUID actor, UUID incidentId, String reason) {
        List<RoomStatus> rooms=db.query("SELECT r.id,r.operating_status FROM gcmb.asset_incident i JOIN gcmb.asset a ON a.id=i.asset_id JOIN gcmb.room r ON r.id=a.room_id AND r.branch_id=i.branch_id WHERE i.id=? AND i.branch_id=? AND i.room_unusable AND r.operating_status<>'UNDER_MAINTENANCE'",(rs,n)->new RoomStatus(rs.getObject("id",UUID.class),rs.getString("operating_status")),incidentId,branch);
        for(RoomStatus room:rooms) {
            String reasonText="Sự cố thiết bị: "+reason;
            db.update("UPDATE gcmb.room SET operating_status='UNDER_MAINTENANCE',status_reason=?,expected_ready_at=NULL WHERE id=? AND branch_id=?",reasonText,room.id(),branch);
            db.update("INSERT INTO gcmb.room_operating_status_history(room_id,branch_id,previous_status,new_status,reason,expected_ready_at,changed_by) VALUES(?,?,?,'UNDER_MAINTENANCE',?,NULL,?)",room.id(),branch,room.status(),reasonText,actor);
        }
    }

    private record RoomStatus(UUID id,String status) {}

    // UC-71/BR-45: Chỉ cho lưu nghiệm thu từ luồng sửa chữa đã được đánh giá.
    public int repair(UUID branch, UUID id, UUID actor, RepairCommand c) {
        return db.update("WITH target AS (SELECT i.id,i.asset_id FROM gcmb.asset_incident i WHERE i.id=? AND i.branch_id=? AND i.status IN ('ASSESSED','IN_PROGRESS') AND i.handling_plan='REPAIR' FOR UPDATE), ins AS (INSERT INTO gcmb.asset_repair(incident_id,vendor_name,actual_cost,finished_at,outcome,verified_by,verified_at) SELECT id,?,?,?::date::timestamptz,?, ?,CURRENT_TIMESTAMP FROM target RETURNING incident_id) UPDATE gcmb.asset_incident i SET status=CASE WHEN ?='IN_USE' THEN 'CLOSED' ELSE 'IN_PROGRESS' END FROM ins WHERE i.id=ins.incident_id",id,branch,c.vendorName().trim(),c.actualCost(),c.completedOn(),c.outcome().trim(),actor,c.assetCondition());
    }

    // UC-71: Cập nhật tình trạng tài sản sau khi ghi nhận kết quả đã nghiệm thu.
    public int updateAssetCondition(UUID branch, UUID incidentId, String condition) {
        return db.update("UPDATE gcmb.asset a SET status=? FROM gcmb.asset_incident i WHERE i.id=? AND i.branch_id=? AND i.asset_id=a.id",condition,incidentId,branch);
    }

    // BR-44: Đồng bộ tình trạng tài sản với phương án sửa hoặc thay thế đã đánh giá.
    public void updateAssetForPlan(UUID branch, UUID id, String plan) {
        String status="REPAIR".equals(plan)?"REPAIRING":"BROKEN";
        if(!"NO_ACTION".equals(plan)) db.update("UPDATE gcmb.asset a SET status=? FROM gcmb.asset_incident i WHERE i.id=? AND i.branch_id=? AND i.asset_id=a.id",status,id,branch);
    }

    // UC-74: Tạo yêu cầu nhưng giữ nguyên cơ sở của tài sản đến khi bên nhận xác nhận.
    public UUID createTransfer(UUID asset, UUID from, UUID to, UUID actor, String reason) {
        return db.queryForObject("INSERT INTO gcmb.asset_transfer(asset_id,from_branch_id,to_branch_id,initiated_by,reason,status) SELECT a.id,a.branch_id,?,?,?,'REQUESTED' FROM gcmb.asset a WHERE a.id=? AND a.branch_id=? AND a.active AND a.status<>'IN_TRANSIT' RETURNING id",UUID.class,to,actor,reason.trim(),asset,from);
    }

    // UC-75: Chỉ nguồn chuyển xác nhận bàn giao, sau đó thiết bị ở trạng thái IN_TRANSIT.
    public int handover(UUID id, UUID branch, UUID actor) {
        return db.update("WITH x AS (UPDATE gcmb.asset_transfer SET status='IN_TRANSIT',handed_over_by=?,handed_over_at=CURRENT_TIMESTAMP WHERE id=? AND from_branch_id=? AND status='REQUESTED' RETURNING asset_id) UPDATE gcmb.asset a SET status='IN_TRANSIT' FROM x WHERE a.id=x.asset_id",actor,id,branch);
    }

    // UC-76: Chuyển cơ sở và vị trí chỉ sau khi nguồn đã bàn giao và đích xác nhận.
    public int receive(UUID id, UUID branch, UUID actor, UUID room) {
        return db.update("WITH x AS (UPDATE gcmb.asset_transfer SET status='COMPLETED',received_by=?,received_at=CURRENT_TIMESTAMP,to_room_id=? WHERE id=? AND to_branch_id=? AND status='IN_TRANSIT' AND (CAST(? AS uuid) IS NULL OR EXISTS(SELECT 1 FROM gcmb.room r WHERE r.id=? AND r.branch_id=? AND r.active)) RETURNING asset_id,to_branch_id) UPDATE gcmb.asset a SET branch_id=x.to_branch_id,room_id=?,status='IN_USE' FROM x WHERE a.id=x.asset_id",actor,room,id,branch,room,room,branch,room);
    }

    // UC-74–UC-76: Liệt kê lịch sử và trạng thái điều chuyển theo một trong hai cơ sở liên quan.
    public List<TransferView> transfers(UUID branch) {
        return db.query("SELECT t.id,t.asset_id,a.code asset_code,a.name asset_name,t.from_branch_id,f.name from_name,t.to_branch_id,d.name to_name,t.to_room_id,r.code to_room_code,t.reason,t.status,t.created_at FROM gcmb.asset_transfer t JOIN gcmb.asset a ON a.id=t.asset_id JOIN gcmb.branch f ON f.id=t.from_branch_id JOIN gcmb.branch d ON d.id=t.to_branch_id LEFT JOIN gcmb.room r ON r.id=t.to_room_id WHERE t.from_branch_id=? OR t.to_branch_id=? ORDER BY t.created_at DESC",(rs,n)->new TransferView(rs.getObject("id",UUID.class),rs.getObject("asset_id",UUID.class),rs.getString("asset_code"),rs.getString("asset_name"),rs.getObject("from_branch_id",UUID.class),rs.getString("from_name"),rs.getObject("to_branch_id",UUID.class),rs.getString("to_name"),rs.getObject("to_room_id",UUID.class),rs.getString("to_room_code"),rs.getString("reason"),rs.getString("status"),rs.getObject("created_at",java.time.OffsetDateTime.class)),branch,branch);
    }
}


