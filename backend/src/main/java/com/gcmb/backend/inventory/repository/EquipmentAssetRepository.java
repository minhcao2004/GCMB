/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: TrongNV
 * Created: 28/10
 * Description: Truy vấn tài sản, danh mục, phòng và lịch sử sự cố trong phạm vi cơ sở.
 */
package com.gcmb.backend.inventory.repository;

import com.gcmb.backend.inventory.dto.EquipmentAssetDtos.CategoryOption;
import com.gcmb.backend.inventory.dto.EquipmentAssetDtos.Command;
import com.gcmb.backend.inventory.dto.EquipmentAssetDtos.ImageContent;
import com.gcmb.backend.inventory.dto.EquipmentAssetDtos.Options;
import com.gcmb.backend.inventory.dto.EquipmentAssetDtos.RoomOption;
import com.gcmb.backend.inventory.dto.EquipmentAssetDtos.StoredImage;
import com.gcmb.backend.inventory.dto.EquipmentAssetDtos.View;
import com.gcmb.backend.inventory.service.EquipmentAssetFileStorageService.StoredFile;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class EquipmentAssetRepository {
    private final JdbcTemplate db;
    // BR-202: Repository dùng truy vấn theo branch để giữ tài sản đúng cơ sở được phân quyền.
    public EquipmentAssetRepository(JdbcTemplate db) { this.db = db; }

    // UC-66: Ghép dữ liệu danh mục, phòng và sự cố gần nhất vào một hồ sơ xem.
    private View map(ResultSet r, int row) throws SQLException {
        return new View(r.getObject("id", UUID.class), r.getObject("branch_id", UUID.class), r.getString("code"), r.getString("name"),
            r.getObject("category_id", UUID.class), r.getString("category_code"), r.getString("category_name"),
            r.getObject("room_id", UUID.class), r.getString("room_code"), r.getString("room_name"), r.getString("location_note"), r.getString("serial_number"),
            r.getObject("purchase_date", java.time.LocalDate.class), r.getBigDecimal("purchase_value"),
            r.getObject("warranty_expiry", java.time.LocalDate.class), r.getString("status"), r.getBoolean("active"),
            r.getObject("image_attachment_id", UUID.class) != null, r.getObject("latest_incident_id", UUID.class),
            r.getString("latest_incident"), r.getTimestamp("created_at").toInstant().atOffset(java.time.ZoneOffset.UTC));
    }

    private static final String SELECT = """
        SELECT a.*, c.code category_code, c.name category_name, r.code room_code, r.name room_name,
               incident.id latest_incident_id, incident.description latest_incident
        FROM gcmb.asset a
        JOIN gcmb.asset_category c ON c.id=a.category_id
        LEFT JOIN gcmb.room r ON r.id=a.room_id AND r.branch_id=a.branch_id
        LEFT JOIN LATERAL (SELECT i.id,i.description FROM gcmb.asset_incident i
          WHERE i.asset_id=a.id ORDER BY i.created_at DESC,i.id DESC LIMIT 1) incident ON true
        """;

    // UC-66: Tìm kiếm/lọc thiết bị chỉ trong chi nhánh hiện hành, không thay đổi dữ liệu.
    public List<View> list(UUID branchId, String search, UUID roomId, String status, Boolean active) {
        return db.query(SELECT + """
            WHERE a.branch_id=? AND (CAST(? AS uuid) IS NULL OR a.room_id=?)
              AND (?='' OR a.status=?) AND (CAST(? AS boolean) IS NULL OR a.active=?)
              AND (?='' OR a.code ILIKE '%'||?||'%' OR a.name ILIKE '%'||?||'%' OR c.name ILIKE '%'||?||'%'
                   OR COALESCE(r.code,'') ILIKE '%'||?||'%' OR COALESCE(a.location_note,'') ILIKE '%'||?||'%')
            ORDER BY a.code
            """, this::map, branchId, roomId, roomId, status, status, active, active,
            search, search, search, search, search, search);
    }

    // UC-65/67: Trả các nhóm/phòng dùng được tại cơ sở; giữ lựa chọn hiện tại để sửa hồ sơ lịch sử.
    public Options options(UUID branchId, UUID assetId) {
        List<CategoryOption> categories = db.query("""
            SELECT id,code,name,active FROM gcmb.asset_category
            WHERE branch_id=? AND (active OR id=(SELECT category_id FROM gcmb.asset WHERE id=? AND branch_id=?))
            ORDER BY active DESC,code
            """, (r,n) -> new CategoryOption(r.getObject("id", UUID.class), r.getString("code"), r.getString("name"), r.getBoolean("active")),
            branchId, assetId, branchId);
        List<RoomOption> rooms = db.query("""
            SELECT id,code,name,operating_status FROM gcmb.room
            WHERE branch_id=? AND (operating_status<>'INACTIVE' OR id=(SELECT room_id FROM gcmb.asset WHERE id=? AND branch_id=?))
            ORDER BY code
            """, (r,n) -> new RoomOption(r.getObject("id", UUID.class), r.getString("code"), r.getString("name"), r.getString("operating_status")),
            branchId, assetId, branchId);
        return new Options(categories, rooms);
    }

    // UC-66/67: Lấy hồ sơ đầy đủ để hiển thị hoặc nạp biểu mẫu sửa.
    public View get(UUID branchId, UUID id) {
        List<View> rows = db.query(SELECT + "WHERE a.branch_id=? AND a.id=?", this::map, branchId, id);
        return rows.isEmpty() ? null : rows.get(0);
    }

    // BR-202: Nhóm dùng cho tài sản phải thuộc cơ sở; giữ nguyên nhóm cũ hợp lệ khi sửa hồ sơ đã chuyển cơ sở.
    public boolean categoryAvailable(UUID branchId, UUID categoryId, UUID assetId) {
        Integer count = db.queryForObject("""
            SELECT count(*) FROM gcmb.asset_category c
            WHERE c.id=? AND ((c.branch_id=? AND c.active) OR EXISTS
              (SELECT 1 FROM gcmb.asset a WHERE a.id=? AND a.branch_id=? AND a.category_id=c.id))
            """, Integer.class, categoryId, branchId, assetId, branchId);
        return count != null && count > 0;
    }

    // BR-202: Phòng phải thuộc cùng cơ sở; cho phép giữ phòng hiện tại nếu phòng đã ngừng hoạt động.
    public boolean roomAvailable(UUID branchId, UUID roomId, UUID assetId) {
        if (roomId == null) return true;
        Integer count = db.queryForObject("""
            SELECT count(*) FROM gcmb.room r WHERE r.id=? AND r.branch_id=? AND
            (r.operating_status<>'INACTIVE' OR EXISTS (SELECT 1 FROM gcmb.asset a WHERE a.id=? AND a.branch_id=? AND a.room_id=r.id))
            """, Integer.class, roomId, branchId, assetId, branchId);
        return count != null && count > 0;
    }

    // UC-65: Tạo tài sản trong cơ sở đang quản lý, khởi tạo hoạt động và không tự gán tài sản sang cơ sở khác.
    public View create(UUID branchId, Command c) {
        UUID id = db.queryForObject("""
            INSERT INTO gcmb.asset(code,category_id,branch_id,room_id,location_note,name,serial_number,
              purchase_date,purchase_value,warranty_expiry,status,active)
            VALUES(?,?,?,?,?,?,?,?,?,?,?,true) RETURNING id
            """, UUID.class, c.code().trim(), c.categoryId(), branchId, c.roomId(), clean(c.locationNote()), c.name().trim(),
            clean(c.serialNumber()), c.purchaseDate(), c.purchaseValue(), c.warrantyExpiry(), c.status());
        return get(branchId, id);
    }

    // UC-67/BR-110: Cập nhật thông tin không đổi branch_id; điều chuyển vẫn phải qua quy trình xác nhận riêng.
    public View update(UUID branchId, UUID id, Command c) {
        int changed = db.update("""
            UPDATE gcmb.asset SET code=?,category_id=?,room_id=?,location_note=?,name=?,serial_number=?,
              purchase_date=?,purchase_value=?,warranty_expiry=?,status=?
            WHERE id=? AND branch_id=?
            """, c.code().trim(), c.categoryId(), c.roomId(), clean(c.locationNote()), c.name().trim(), clean(c.serialNumber()),
            c.purchaseDate(), c.purchaseValue(), c.warrantyExpiry(), c.status(), id, branchId);
        return changed == 0 ? null : get(branchId, id);
    }

    // UC-68: Chỉ đổi cờ sử dụng; giữ nguyên trạng thái thiết bị và lịch sử sửa chữa/sự cố.
    public View setActive(UUID branchId, UUID id, boolean active) {
        int changed = db.update("UPDATE gcmb.asset SET active=? WHERE id=? AND branch_id=?", active, id, branchId);
        return changed == 0 ? null : get(branchId, id);
    }

    // UC-69/BR-118: Chỉ xóa asset chưa có lịch sử sự cố, sửa chữa, điều chuyển hoặc được dùng làm tài sản thay thế.
    public boolean deleteUnused(UUID branchId, UUID id) {
        return db.update("""
            DELETE FROM gcmb.asset a WHERE a.id=? AND a.branch_id=? AND a.status<>'IN_TRANSIT' AND a.expense_id IS NULL
              AND NOT EXISTS (SELECT 1 FROM gcmb.asset_incident i WHERE i.asset_id=a.id OR i.replacement_asset_id=a.id)
              AND NOT EXISTS (SELECT 1 FROM gcmb.asset_transfer t WHERE t.asset_id=a.id)
            """, id, branchId) > 0;
    }

    // BR-115: Ghi metadata file vào bảng attachment, không lưu tên file do người dùng cung cấp làm đường dẫn.
    public StoredImage insertImage(StoredFile file, UUID uploadedBy) {
        db.update("""
            INSERT INTO gcmb.attachment(id,object_key,original_name,mime_type,byte_size,sha256,uploaded_by)
            VALUES(?,?,?,?,?,?,?)
            """, file.id(), file.objectKey(), file.originalName(), file.mimeType(), file.byteSize(), file.sha256(), uploadedBy);
        return new StoredImage(file.id(), file.objectKey(), file.originalName(), file.mimeType(), file.byteSize(), file.sha256());
    }

    // UC-65/67: Liên kết chứng từ đã lưu với tài sản nằm trong đúng phạm vi cơ sở.
    public boolean setImage(UUID branchId, UUID id, UUID attachmentId) {
        return db.update("UPDATE gcmb.asset SET image_attachment_id=? WHERE id=? AND branch_id=?", attachmentId, id, branchId) > 0;
    }

    // UC-66: Chỉ trả ảnh của tài sản người dùng được quyền xem.
    public StoredImage image(UUID branchId, UUID id) {
        List<StoredImage> rows = db.query("""
            SELECT x.id,x.object_key,x.original_name,x.mime_type,x.byte_size,x.sha256
            FROM gcmb.asset a JOIN gcmb.attachment x ON x.id=a.image_attachment_id
            WHERE a.id=? AND a.branch_id=?
            """, (r,n) -> new StoredImage(r.getObject("id", UUID.class), r.getString("object_key"), r.getString("original_name"),
            r.getString("mime_type"), r.getLong("byte_size"), r.getString("sha256")), id, branchId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    // BR-111: Lưu chuỗi tùy chọn dưới dạng null thay vì chuỗi chỉ có khoảng trắng.
    private String clean(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}
