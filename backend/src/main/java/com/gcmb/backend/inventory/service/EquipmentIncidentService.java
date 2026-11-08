/**
 * Copyright (c) 2026 SEP490-G12.
 * Author: TrongNV
 * Created: 28/10
 * Description: Quy tắc phân quyền và vòng đời sự cố, sửa chữa, điều chuyển UC70–UC76.
 */
package com.gcmb.backend.inventory.service;

import com.gcmb.backend.auth.dto.Identity;
import com.gcmb.backend.inventory.dto.EquipmentIncidentDtos.*;
import com.gcmb.backend.inventory.repository.EquipmentIncidentRepository;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class EquipmentIncidentService {
    private final EquipmentIncidentRepository repository;
    private final EquipmentAssetFileStorageService fileStorage;
    // UC-70–UC-76: Dùng chung dịch vụ phạm vi dữ liệu cho các luồng bảo trì và điều chuyển.
    public EquipmentIncidentService(EquipmentIncidentRepository repository, EquipmentAssetFileStorageService fileStorage) { this.repository = repository; this.fileStorage=fileStorage; }

    // BR-41/BR-177: Nhân viên chỉ thao tác trên cơ sở được gán; HQ chỉ xem/báo cáo khi chỉ định cơ sở.
    private UUID branch(Identity actor, UUID requested) {
        if (actor == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Vui lòng đăng nhập lại");
        if (Set.of("BRANCH_MANAGER","RECEPTIONIST").contains(actor.role())) {
            if(actor.branchId()==null || (requested!=null && !actor.branchId().equals(requested))) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Không có quyền truy cập cơ sở này");
            return actor.branchId();
        }
        if("HEAD_OFFICE_MANAGER".equals(actor.role())) return requested;
        throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Không có quyền truy cập sự cố thiết bị");
    }

    // UC-72: Trả tiến độ sự cố theo branch, tìm theo mã/tên thiết bị hoặc mô tả.
    public List<IncidentView> incidents(Identity actor, UUID branch, String search, String status) { return repository.incidents(branch(actor,branch),search==null?"":search.trim(),status==null?"":status); }
    // UC-70: Danh sách thiết bị báo cáo trong phạm vi được cấp.
    public List<AssetOption> assets(Identity actor, UUID requested) { return repository.assets(branch(actor,requested)); }
    // UC-70/BR-111: Tạo báo cáo đủ mô tả, trong đúng chi nhánh và gắn với tài sản đang hoạt động.
    @Transactional public UUID create(Identity actor, UUID requested, IncidentCommand c, MultipartFile evidence) {
        UUID branch=branch(actor,requested);
        if(c==null || c.assetId()==null || c.description()==null || c.description().isBlank() || c.severity()==null || !Set.of("LOW","MEDIUM","HIGH","CRITICAL").contains(c.severity()) || c.priority()==null || !Set.of("LOW","NORMAL","HIGH","URGENT").contains(c.priority()) || c.expectedCompletionOn()==null || c.handlingPlan()==null || !Set.of("REPAIR","REPLACE").contains(c.handlingPlan()) || c.serviceProvider()==null || c.serviceProvider().isBlank() || c.serviceProvider().trim().length()>200 || (c.estimatedCost()!=null && c.estimatedCost().signum()<0) || ("REPLACE".equals(c.handlingPlan()) && (c.replacementDetails()==null || c.replacementDetails().isBlank()))) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Thiết bị, severity, priority, phương án, đơn vị sửa chữa/thay thế và ngày dự kiến là bắt buộc; chi phí dự kiến không được âm, phương án thay thế cần mô tả thiết bị thay thế");
        if(!Set.of("RECEPTIONIST","BRANCH_MANAGER").contains(actor.role())) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Chỉ lễ tân hoặc quản lý cơ sở được báo cáo sự cố");
        try { UUID id=repository.createIncident(branch,actor.id(),actor.role(),c); if(id==null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Thiết bị không thuộc cơ sở hoặc đã ngừng sử dụng"); if("BRANCH_MANAGER".equals(actor.role())) repository.updateAssetForPlan(branch,id,c.handlingPlan()); if(evidence!=null&&!evidence.isEmpty()) { var file=fileStorage.store(evidence); try { repository.saveEvidence(file,actor.id()); if(!repository.setIncidentEvidence(branch,id,file.id())) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Không tìm thấy báo cáo sự cố"); } catch(RuntimeException e) { fileStorage.deleteQuietly(file.objectKey()); throw e; } } return id; }
        catch(org.springframework.dao.EmptyResultDataAccessException e){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Thiết bị không thuộc cơ sở hoặc đã ngừng sử dụng");}
    }
    public List<ProgressView> progress(Identity actor, UUID requested, UUID id) { return repository.progress(branch(actor,requested),id); }
    public com.gcmb.backend.inventory.dto.EquipmentAssetDtos.ImageContent repairAttachment(Identity actor, UUID id, String kind) {
        if (!Set.of("invoice","transfer-proof").contains(kind)) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Không tìm thấy chứng từ");
        var metadata=repository.repairAttachment(branch(actor,null),id,"invoice".equals(kind)?"invoice":"transfer");
        if(metadata==null) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Chưa có chứng từ này");
        try { return new com.gcmb.backend.inventory.dto.EquipmentAssetDtos.ImageContent(metadata.originalName(),metadata.mimeType(),fileStorage.read(metadata.objectKey())); }
        catch(java.io.IOException e) { throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Không tìm thấy file chứng từ"); }
    }
    @Transactional public void review(Identity actor, UUID requested, UUID id, ReviewCommand c) {
        UUID b=branch(actor,requested); manager(actor);
        if(c==null || c.note()==null || c.note().isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Ghi chú duyệt hoặc từ chối là bắt buộc");
        if(!repository.review(b,actor.id(),id,c)) throw new ResponseStatusException(HttpStatus.CONFLICT,"Sự cố không còn chờ duyệt hoặc không thuộc cơ sở này");
        if(c.approved()) repository.updateAssetForPlan(b,id,"REPAIR".equals(repository.incidentPlan(b,id))?"REPAIR":"REPLACE");
    }
    @Transactional public void suggest(Identity actor, UUID requested, UUID id, String solution) {
        UUID b=branch(actor,requested);
        if(!"RECEPTIONIST".equals(actor.role()) || solution==null || solution.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Nhân viên cần nhập phương án đề xuất");
        if(!repository.suggest(b,actor.id(),id,solution)) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Không tìm thấy sự cố đang mở");
    }
    @Transactional public void progress(Identity actor, UUID requested, UUID id, ProgressCommand c) {
        UUID b=branch(actor,requested); manager(actor);
        if(c==null || c.note()==null || c.note().isBlank() || c.status()==null || !Set.of("ASSESSED","IN_PROGRESS").contains(c.status())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Nhập tiến trình và trạng thái hợp lệ; nghiệm thu chỉ thực hiện qua kết quả sửa chữa");
        if(!repository.progress(b,actor.id(),id,c)) throw new ResponseStatusException(HttpStatus.CONFLICT,"Không thể cập nhật sự cố đã đóng hoặc không tồn tại");
    }
    @Transactional public void close(Identity actor, UUID requested, UUID id, String note) {
        UUID b=branch(actor,requested); manager(actor);
        if(note==null || note.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Nhập ghi chú đóng sự cố");
        if(!repository.close(b,actor.id(),id,note)) throw new ResponseStatusException(HttpStatus.CONFLICT,"Chỉ đóng sự cố đã hoàn tất và nghiệm thu");
    }
    // UC-73/BR-42/BR-44: Chỉ quản lý cơ sở được ghi kế hoạch sửa chữa hoặc thay thế.
    @Transactional public void assess(Identity actor, UUID requested, UUID id, AssessmentCommand c) {
        UUID b=branch(actor,requested); manager(actor);
        if(c==null || c.plan()==null || !Set.of("REPAIR","REPLACE","NO_ACTION").contains(c.plan()) || c.note()==null || c.note().isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Chọn phương án và nhập ghi chú đánh giá");
        if("REPLACE".equals(c.plan()) && c.replacementAssetId()==null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Chọn thiết bị thay thế đã đăng ký");
        if(repository.assess(b,actor.id(),id,c)==0) throw new ResponseStatusException(HttpStatus.CONFLICT,"Sự cố không còn ở trạng thái chờ đánh giá hoặc thiết bị thay thế không hợp lệ");
        repository.updateAssetForPlan(b,id,c.plan());
        repository.markRoomMaintenance(b,actor.id(),id,c.note().trim());
    }
    // UC-71/BR-45/BR-46/BR-205: Chỉ xác nhận sửa chữa khi đủ kết quả, chi phí, ngày hoàn tất và tình trạng.
    @Transactional public void repair(Identity actor, UUID requested, UUID id, RepairCommand c, MultipartFile invoice, MultipartFile transferProof) {
        UUID b=branch(actor,requested); manager(actor);
        if(c==null || c.vendorName()==null || c.vendorName().isBlank() || c.outcome()==null || c.outcome().isBlank() || c.actualCost()==null || c.actualCost().signum()<0 || c.completedOn()==null || c.assetCondition()==null || !Set.of("IN_USE","BROKEN","REPAIRING").contains(c.assetCondition()) || invoice==null || invoice.isEmpty() || transferProof==null || transferProof.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Nhập đủ kết quả sửa chữa, hóa đơn, chứng từ chuyển khoản, chi phí và ngày hoàn tất");
        fileStorage.requireDriveConfigured();
        if(repository.repair(b,id,actor.id(),c)==0) throw new ResponseStatusException(HttpStatus.CONFLICT,"Sự cố chưa được đánh giá sửa chữa hoặc không thuộc cơ sở này");
        EquipmentAssetFileStorageService.StoredFile invoiceFile=null, transferFile=null;
        try {
            invoiceFile=fileStorage.storeInDrive(invoice);
            transferFile=fileStorage.storeInDrive(transferProof);
            repository.saveEvidence(invoiceFile,actor.id());
            repository.saveEvidence(transferFile,actor.id());
            if(!repository.setRepairEvidence(b,id,invoiceFile.id(),transferFile.id())) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Không tìm thấy kết quả sửa chữa");
        } catch(RuntimeException e) {
            if(invoiceFile!=null) fileStorage.deleteQuietly(invoiceFile.objectKey());
            if(transferFile!=null) fileStorage.deleteQuietly(transferFile.objectKey());
            throw e;
        }
        repository.updateAssetCondition(b,id,c.assetCondition());
        repository.logProgress(actor.id(),id,"VERIFIED",c.outcome().trim(),c.completedOn());
    }
    // BR-42: Trạng thái sửa chữa/đánh giá chỉ do Branch Manager điều chỉnh.
    private void manager(Identity actor) { if(actor==null || !"BRANCH_MANAGER".equals(actor.role())) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Chỉ quản lý cơ sở được xử lý sự cố"); }
    // UC-74/BR-48: Chỉ quản lý cấp cao có thể mở yêu cầu điều chuyển.
    @Transactional public UUID transfer(Identity actor, TransferCommand c) {
        if(actor==null || !"HEAD_OFFICE_MANAGER".equals(actor.role())) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Chỉ quản lý cấp cao được tạo yêu cầu điều chuyển");
        if(c==null || c.assetId()==null || c.toBranchId()==null || c.reason()==null || c.reason().isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Thiết bị, cơ sở nhận và lý do điều chuyển là bắt buộc");
        UUID asset=repository.assetBranch(c.assetId());
        if(asset==null || asset.equals(c.toBranchId())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Cơ sở nhận phải khác cơ sở hiện tại của thiết bị");
        try { UUID id=repository.createTransfer(c.assetId(),asset,c.toBranchId(),actor.id(),c.reason()); if(id==null) throw new ResponseStatusException(HttpStatus.CONFLICT,"Thiết bị không thể điều chuyển"); return id; }
        catch(org.springframework.dao.DataAccessException e){throw new ResponseStatusException(HttpStatus.CONFLICT,"Thiết bị không thể điều chuyển hoặc đang có yêu cầu điều chuyển khác");}
    }
    // UC-75: Handover chỉ được xác nhận bởi quản lý cơ sở nguồn.
    public void handover(Identity actor, UUID id) { UUID b=branch(actor,null); manager(actor); if(repository.handover(id,b,actor.id())==0) throw new ResponseStatusException(HttpStatus.CONFLICT,"Chỉ cơ sở nguồn mới xác nhận bàn giao yêu cầu đang chờ"); }
    // UC-76/BR-49/BR-50: Nhận chỉ được xác nhận bởi cơ sở đích sau bàn giao, với vị trí thuộc cơ sở.
    public void receive(Identity actor, UUID id, TransferReceiptCommand c) { UUID b=branch(actor,null); manager(actor); if(c==null||c.toRoomId()==null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Chọn phòng nhận thiết bị"); if(repository.receive(id,b,actor.id(),c.toRoomId())==0) throw new ResponseStatusException(HttpStatus.CONFLICT,"Chưa thể xác nhận nhận: kiểm tra bàn giao và phòng đích"); }
    // UC-74–UC-76: HQ chọn cơ sở xem; quản lý cơ sở xem lịch sử và tác vụ liên quan.
    public List<TransferView> transfers(Identity actor, UUID requested) { return repository.transfers(branch(actor,requested)); }
}

