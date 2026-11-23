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
    private final EquipmentIncidentRepository equipmentIncidentRepository;
    private final EquipmentAssetFileStorageService equipmentAssetFileStorageService;
    // UC-70–UC-76: Dùng chung dịch vụ phạm vi dữ liệu cho các luồng bảo trì và điều chuyển.
    public EquipmentIncidentService(EquipmentIncidentRepository equipmentIncidentRepository, EquipmentAssetFileStorageService equipmentAssetFileStorageService) { this.equipmentIncidentRepository = equipmentIncidentRepository; this.equipmentAssetFileStorageService=equipmentAssetFileStorageService; }

    // BR-41/BR-177: Nhân viên chỉ thao tác trên cơ sở được gán; HQ chỉ xem/báo cáo khi chỉ định cơ sở.
    private UUID branch(Identity actor, UUID requested) {
        return branch(actor, requested, "xem", "sự cố thiết bị");
    }

    private UUID branch(Identity actor, UUID requested, String action, String entity) {
        if (actor == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Vui lòng đăng nhập lại");
        if (Set.of("BRANCH_MANAGER","RECEPTIONIST").contains(actor.role())) {
            if(actor.branchId()==null || (requested!=null && !actor.branchId().equals(requested))) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Bạn không có quyền " + action + " đối với " + entity + ".");
            return actor.branchId();
        }
        if("HEAD_OFFICE_MANAGER".equals(actor.role())) return requested;
        throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Bạn không có quyền " + action + " đối với " + entity + ".");
    }

    // UC-72: Trả tiến độ sự cố theo branch, tìm theo mã/tên thiết bị hoặc mô tả.
    public List<IncidentView> incidents(Identity actor, UUID branch, String search, String status) { return equipmentIncidentRepository.incidents(branch(actor,branch),search==null?"":search.trim(),status==null?"":status); }
    // UC-70: Danh sách thiết bị báo cáo trong phạm vi được cấp.
    public List<AssetOption> assets(Identity actor, UUID requested) { return equipmentIncidentRepository.assets(branch(actor,requested)); }
    // UC-70/BR-111: Tạo báo cáo đủ mô tả, trong đúng chi nhánh và gắn với tài sản đang hoạt động.
    @Transactional public UUID create(Identity actor, UUID requested, IncidentCommand c, MultipartFile evidence) {
        UUID branch=branch(actor,requested,"báo cáo","sự cố thiết bị");
        if(c==null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Vui lòng bổ sung thông tin báo cáo sự cố.");
        if(c.assetId()==null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Vui lòng bổ sung thiết bị.");
        if(c.description()==null || c.description().isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Vui lòng bổ sung mô tả sự cố.");
        if(c.severity()==null || !Set.of("LOW","MEDIUM","HIGH","CRITICAL").contains(c.severity())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Vui lòng bổ sung mức độ nghiêm trọng hợp lệ.");
        if(c.priority()==null || !Set.of("LOW","NORMAL","HIGH","URGENT").contains(c.priority())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Vui lòng bổ sung mức độ ưu tiên hợp lệ.");
        if(c.expectedCompletionOn()==null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Vui lòng bổ sung ngày dự kiến hoàn thành.");
        if(c.handlingPlan()==null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Vui lòng bổ sung phương án xử lý.");
        if(!Set.of("REPAIR","REPLACE").contains(c.handlingPlan())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Vui lòng bổ sung phương án xử lý hợp lệ.");
        if(c.serviceProvider()==null || c.serviceProvider().isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Vui lòng bổ sung đơn vị sửa chữa/thay thế.");
        if(c.serviceProvider().trim().length()>200) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Đơn vị sửa chữa/thay thế chỉ được chứa tối đa 200 ký tự.");
        if(c.estimatedCost()!=null && c.estimatedCost().signum()<0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Chi phí dự kiến phải lớn hơn hoặc bằng 0.");
        if("REPLACE".equals(c.handlingPlan()) && (c.replacementDetails()==null || c.replacementDetails().isBlank())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Vui lòng bổ sung mô tả thiết bị thay thế.");
        if(!Set.of("RECEPTIONIST","BRANCH_MANAGER").contains(actor.role())) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Bạn không có quyền báo cáo đối với sự cố thiết bị.");
        try { UUID id=equipmentIncidentRepository.createIncident(branch,actor.id(),actor.role(),c); if(id==null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Thiết bị không thuộc cơ sở hoặc đã ngừng sử dụng"); if("BRANCH_MANAGER".equals(actor.role())) equipmentIncidentRepository.updateAssetForPlan(branch,id,c.handlingPlan()); if(evidence!=null&&!evidence.isEmpty()) { var file=equipmentAssetFileStorageService.store(evidence, "su-co-thiet-bi"); try { equipmentIncidentRepository.saveEvidence(file,actor.id()); if(!equipmentIncidentRepository.setIncidentEvidence(branch,id,file.id())) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Không tìm thấy báo cáo sự cố"); } catch(RuntimeException e) { equipmentAssetFileStorageService.deleteQuietly(file.objectKey()); throw e; } } return id; }
        catch(org.springframework.dao.EmptyResultDataAccessException e){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Thiết bị không thuộc cơ sở hoặc đã ngừng sử dụng");}
    }
    public List<ProgressView> progress(Identity actor, UUID requested, UUID id) { return equipmentIncidentRepository.progress(branch(actor,requested),id); }
    public com.gcmb.backend.inventory.dto.EquipmentAssetDtos.ImageContent repairAttachment(Identity actor, UUID id, String kind) {
        if (!Set.of("invoice","transfer-proof").contains(kind)) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Không tìm thấy chứng từ");
        var metadata=equipmentIncidentRepository.repairAttachment(branch(actor,null),id,"invoice".equals(kind)?"invoice":"transfer");
        if(metadata==null) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Chưa có chứng từ này");
        try { return new com.gcmb.backend.inventory.dto.EquipmentAssetDtos.ImageContent(metadata.originalName(),metadata.mimeType(),equipmentAssetFileStorageService.read(metadata.objectKey())); }
        catch(java.io.IOException e) { throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Không tìm thấy file chứng từ"); }
    }
    @Transactional public void review(Identity actor, UUID requested, UUID id, ReviewCommand c) {
        UUID b=branch(actor,requested,"duyệt","sự cố thiết bị"); manager(actor,"duyệt");
        if(c==null || c.note()==null || c.note().isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Ghi chú duyệt hoặc từ chối là bắt buộc");
        if(!equipmentIncidentRepository.review(b,actor.id(),id,c)) throw new ResponseStatusException(HttpStatus.CONFLICT,"Sự cố không còn chờ duyệt hoặc không thuộc cơ sở này");
        if(c.approved()) equipmentIncidentRepository.updateAssetForPlan(b,id,"REPAIR".equals(equipmentIncidentRepository.incidentPlan(b,id))?"REPAIR":"REPLACE");
    }
    @Transactional public void suggest(Identity actor, UUID requested, UUID id, String solution) {
        UUID b=branch(actor,requested);
        if(!"RECEPTIONIST".equals(actor.role()) || solution==null || solution.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Nhân viên cần nhập phương án đề xuất");
        if(!equipmentIncidentRepository.suggest(b,actor.id(),id,solution)) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Không tìm thấy sự cố đang mở");
    }
    @Transactional public void progress(Identity actor, UUID requested, UUID id, ProgressCommand c) {
        UUID b=branch(actor,requested,"cập nhật tiến độ","sự cố thiết bị"); manager(actor,"cập nhật tiến độ");
        if(c==null || c.note()==null || c.note().isBlank() || c.status()==null || !Set.of("ASSESSED","IN_PROGRESS").contains(c.status())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Nhập tiến trình và trạng thái hợp lệ; nghiệm thu chỉ thực hiện qua kết quả sửa chữa");
        if(!equipmentIncidentRepository.progress(b,actor.id(),id,c)) throw new ResponseStatusException(HttpStatus.CONFLICT,"Không thể cập nhật sự cố đã đóng hoặc không tồn tại");
    }
    @Transactional public void close(Identity actor, UUID requested, UUID id, String note) {
        UUID b=branch(actor,requested,"đóng","sự cố thiết bị"); manager(actor,"đóng");
        if(note==null || note.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Nhập ghi chú đóng sự cố");
        if(!equipmentIncidentRepository.close(b,actor.id(),id,note)) throw new ResponseStatusException(HttpStatus.CONFLICT,"Thiết bị chưa được xác nhận sửa chữa đạt yêu cầu. Chưa thể đưa thiết bị trở lại sử dụng bình thường hoặc đóng sự cố.");
    }
    // UC-73/BR-42/BR-44: Chỉ quản lý cơ sở được ghi kế hoạch sửa chữa hoặc thay thế.
    @Transactional public void assess(Identity actor, UUID requested, UUID id, AssessmentCommand c) {
        UUID b=branch(actor,requested,"đánh giá","sự cố thiết bị"); manager(actor,"đánh giá");
        if(c==null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Vui lòng bổ sung thông tin đánh giá sự cố.");
        if(c.plan()==null || !Set.of("REPAIR","REPLACE","NO_ACTION").contains(c.plan())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Vui lòng bổ sung phương án đánh giá hợp lệ.");
        if(c.note()==null || c.note().isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Vui lòng bổ sung ghi chú đánh giá.");
        if("REPLACE".equals(c.plan()) && c.replacementAssetId()==null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Vui lòng bổ sung thiết bị thay thế đã đăng ký.");
        if(equipmentIncidentRepository.assess(b,actor.id(),id,c)==0) throw new ResponseStatusException(HttpStatus.CONFLICT,"Sự cố không còn ở trạng thái chờ đánh giá hoặc thiết bị thay thế không hợp lệ");
        equipmentIncidentRepository.updateAssetForPlan(b,id,c.plan());
        equipmentIncidentRepository.markRoomMaintenance(b,actor.id(),id,c.note().trim());
    }
    // UC-71/BR-45/BR-46/BR-205: Chỉ xác nhận sửa chữa khi đủ kết quả, chi phí, ngày hoàn tất và tình trạng.
    @Transactional public void repair(Identity actor, UUID requested, UUID id, RepairCommand c, MultipartFile invoice, MultipartFile transferProof) {
        UUID b=branch(actor,requested,"ghi nhận kết quả sửa chữa","sự cố thiết bị"); manager(actor,"ghi nhận kết quả sửa chữa");
        if(c==null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Vui lòng bổ sung thông tin kết quả sửa chữa.");
        if(c.vendorName()==null || c.vendorName().isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Vui lòng bổ sung đơn vị sửa chữa.");
        if(c.outcome()==null || c.outcome().isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Vui lòng bổ sung kết quả sửa chữa.");
        if(c.actualCost()==null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Vui lòng bổ sung chi phí sửa chữa thực tế.");
        if(c.actualCost().signum()<0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Chi phí sửa chữa thực tế phải lớn hơn hoặc bằng 0.");
        if(c.completedOn()==null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Vui lòng bổ sung ngày hoàn tất sửa chữa.");
        if(c.assetCondition()==null || !Set.of("IN_USE","BROKEN","REPAIRING").contains(c.assetCondition())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Vui lòng bổ sung tình trạng thiết bị sau sửa chữa hợp lệ.");
        if(invoice==null || invoice.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Vui lòng bổ sung hóa đơn sửa chữa.");
        if(transferProof==null || transferProof.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Vui lòng bổ sung chứng từ chuyển khoản.");
        if(equipmentIncidentRepository.repair(b,id,actor.id(),c)==0) throw new ResponseStatusException(HttpStatus.CONFLICT,"Thiết bị chưa được xác nhận sửa chữa đạt yêu cầu. Chưa thể đưa thiết bị trở lại sử dụng bình thường hoặc đóng sự cố.");
        EquipmentAssetFileStorageService.StoredFile invoiceFile=null, transferFile=null;
        try {
            invoiceFile=equipmentAssetFileStorageService.store(invoice, "su-co-thiet-bi");
            transferFile=equipmentAssetFileStorageService.store(transferProof, "su-co-thiet-bi");
            equipmentIncidentRepository.saveEvidence(invoiceFile,actor.id());
            equipmentIncidentRepository.saveEvidence(transferFile,actor.id());
            if(!equipmentIncidentRepository.setRepairEvidence(b,id,invoiceFile.id(),transferFile.id())) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Không tìm thấy kết quả sửa chữa");
        } catch(RuntimeException e) {
            if(invoiceFile!=null) equipmentAssetFileStorageService.deleteQuietly(invoiceFile.objectKey());
            if(transferFile!=null) equipmentAssetFileStorageService.deleteQuietly(transferFile.objectKey());
            throw e;
        }
        equipmentIncidentRepository.updateAssetCondition(b,id,c.assetCondition());
        equipmentIncidentRepository.logProgress(actor.id(),id,"VERIFIED",c.outcome().trim(),c.completedOn());
    }
    // BR-42: Trạng thái sửa chữa/đánh giá chỉ do Branch Manager điều chỉnh.
    private void manager(Identity actor, String action) { if(actor==null || !"BRANCH_MANAGER".equals(actor.role())) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Bạn không có quyền " + action + " đối với sự cố thiết bị."); }
    // UC-74/BR-48: Chỉ quản lý cấp cao có thể mở yêu cầu điều chuyển.
    @Transactional public UUID transfer(Identity actor, TransferCommand c) {
        if(actor==null || !"HEAD_OFFICE_MANAGER".equals(actor.role())) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Bạn không có quyền tạo yêu cầu điều chuyển đối với thiết bị.");
        if(c==null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Vui lòng bổ sung thông tin điều chuyển thiết bị.");
        if(c.assetId()==null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Vui lòng bổ sung thiết bị.");
        if(c.toBranchId()==null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Vui lòng bổ sung cơ sở nhận.");
        if(c.reason()==null || c.reason().isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Vui lòng bổ sung lý do điều chuyển.");
        UUID asset=equipmentIncidentRepository.assetBranch(c.assetId());
        if(asset==null || asset.equals(c.toBranchId())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Cơ sở nhận phải khác cơ sở hiện tại của thiết bị");
        try { UUID id=equipmentIncidentRepository.createTransfer(c.assetId(),asset,c.toBranchId(),actor.id(),c.reason()); if(id==null) throw new ResponseStatusException(HttpStatus.CONFLICT,"Thiết bị không thể điều chuyển"); return id; }
        catch(org.springframework.dao.DataAccessException e){throw new ResponseStatusException(HttpStatus.CONFLICT,"Thiết bị không thể điều chuyển hoặc đang có yêu cầu điều chuyển khác");}
    }
    // UC-75: Handover chỉ được xác nhận bởi quản lý cơ sở nguồn.
    public void handover(Identity actor, UUID id) {
        UUID b=transferScope(actor,id,true,"xác nhận bàn giao");
        if(equipmentIncidentRepository.handover(id,b,actor.id())==0) throw new ResponseStatusException(HttpStatus.CONFLICT,"Chưa thể xác nhận kết quả bàn giao. Vui lòng kiểm tra lại trạng thái của phiếu điều chuyển trước khi thực hiện lại.");
    }
    // UC-76/BR-49/BR-50: Nhận chỉ được xác nhận bởi cơ sở đích sau bàn giao, với vị trí thuộc cơ sở.
    public void receive(Identity actor, UUID id, TransferReceiptCommand c) {
        UUID b=transferScope(actor,id,false,"xác nhận tiếp nhận");
        if(c==null||c.toRoomId()==null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Vui lòng bổ sung phòng nhận thiết bị.");
        if(equipmentIncidentRepository.receive(id,b,actor.id(),c.toRoomId())==0) {
            // BR-49 / MSG82: cơ sở nguồn phải bàn giao trước khi cơ sở đích xác nhận nhận.
            if(equipmentIncidentRepository.awaitingHandover(id,b)) throw new ResponseStatusException(HttpStatus.CONFLICT,"Chưa thể xác nhận nhận thiết bị vì cơ sở gửi chưa xác nhận bàn giao.");
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Chưa thể xác nhận nhận: kiểm tra bàn giao và phòng đích");
        }
    }
    private UUID transferScope(Identity actor, UUID id, boolean source, String action) {
        String message="Bạn không có quyền " + action + " đối với phiếu điều chuyển.";
        if(actor==null || !"BRANCH_MANAGER".equals(actor.role()) || actor.branchId()==null)
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,message);
        UUID owner=equipmentIncidentRepository.transferBranch(id,source);
        if(owner!=null && !owner.equals(actor.branchId())) throw new ResponseStatusException(HttpStatus.FORBIDDEN,message);
        return actor.branchId();
    }
    // UC-74–UC-76: HQ chọn cơ sở xem; quản lý cơ sở xem lịch sử và tác vụ liên quan.
    public List<TransferView> transfers(Identity actor, UUID requested) { return equipmentIncidentRepository.transfers(branch(actor,requested,"xem","phiếu điều chuyển")); }
}
