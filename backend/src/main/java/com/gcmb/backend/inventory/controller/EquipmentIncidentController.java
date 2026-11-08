/**
 * Copyright (c) 2026 SEP490-G12.
 * Author: TrongNV
 * Created: 28/10
 * Description: REST API báo cáo, xem và xử lý sự cố thiết bị, cùng lịch sử điều chuyển UC70–UC76.
 */
package com.gcmb.backend.inventory.controller;

import com.gcmb.backend.audit.service.MutationAudit;
import com.gcmb.backend.auth.dto.Identity;
import com.gcmb.backend.inventory.dto.EquipmentIncidentDtos.*;
import com.gcmb.backend.inventory.dto.EquipmentIncidentDtos;
import com.gcmb.backend.inventory.service.EquipmentIncidentService;
import java.util.List;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ContentDisposition;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class EquipmentIncidentController {
    private final EquipmentIncidentService service;
    private final MutationAudit audit;
    // UC-70–UC-76: Kết nối dịch vụ nghiệp vụ và audit thay đổi.
    public EquipmentIncidentController(EquipmentIncidentService service, MutationAudit audit) { this.service=service; this.audit=audit; }

    // UC-70: Cung cấp danh sách thiết bị được chọn để Lễ tân có thể báo sự cố.
    @GetMapping({"/api/v1/branch-management/equipment-incidents/assets","/api/v1/reception/equipment-incidents/assets"})
    public List<EquipmentIncidentDtos.AssetOption> assets(@AuthenticationPrincipal Identity actor) { return service.assets(actor,null); }

    // UC-72: Tìm kiếm và lọc tiến độ sự cố trong phạm vi cơ sở.
    @GetMapping({"/api/v1/branch-management/equipment-incidents","/api/v1/head-office/equipment-incidents","/api/v1/reception/equipment-incidents"})
    public List<IncidentView> incidents(@AuthenticationPrincipal Identity actor,@RequestParam(required=false) UUID branchId,@RequestParam(defaultValue="") String search,@RequestParam(defaultValue="") String status) { return service.incidents(actor,branchId,search,status); }
    @GetMapping({"/api/v1/branch-management/equipment-incidents/{id}/progress","/api/v1/reception/equipment-incidents/{id}/progress","/api/v1/head-office/equipment-incidents/{id}/progress"})
    public List<ProgressView> progress(@AuthenticationPrincipal Identity actor,@PathVariable UUID id) { return service.progress(actor,null,id); }
    @GetMapping({"/api/v1/branch-management/equipment-incidents/{id}/repair-documents/{kind}","/api/v1/head-office/equipment-incidents/{id}/repair-documents/{kind}","/api/v1/reception/equipment-incidents/{id}/repair-documents/{kind}"})
    public ResponseEntity<byte[]> repairDocument(@AuthenticationPrincipal Identity actor,@PathVariable UUID id,@PathVariable String kind) { var file=service.repairAttachment(actor,id,kind); return ResponseEntity.ok().contentType(MediaType.parseMediaType(file.mimeType())).header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.inline().filename(file.originalName()).build().toString()).body(file.bytes()); }
    // UC-70/BR-41: Tạo báo cáo thiết bị chỉ trong cơ sở được phân công.
    @PostMapping(value={"/api/v1/branch-management/equipment-incidents","/api/v1/reception/equipment-incidents"},consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public UUID create(@AuthenticationPrincipal Identity actor,@RequestPart("command") IncidentCommand command,@RequestPart(value="evidence",required=false) MultipartFile evidence) { return audit.record("CREATE_EQUIPMENT_INCIDENT","INCIDENT",null,()->service.create(actor,null,command,evidence),id->id); }
    @PutMapping("/api/v1/branch-management/equipment-incidents/{id}/review")
    public void review(@AuthenticationPrincipal Identity actor,@PathVariable UUID id,@RequestBody ReviewCommand command) { audit.record(command.approved()?"APPROVE_EQUIPMENT_INCIDENT":"REJECT_EQUIPMENT_INCIDENT","INCIDENT",id,()->{service.review(actor,null,id,command);return id;},x->x); }
    @PutMapping("/api/v1/reception/equipment-incidents/{id}/suggestion")
    public void suggest(@AuthenticationPrincipal Identity actor,@PathVariable UUID id,@RequestBody java.util.Map<String,String> body) { audit.record("SUGGEST_EQUIPMENT_REPAIR","INCIDENT",id,()->{service.suggest(actor,null,id,body.get("solution"));return id;},x->x); }
    @PutMapping("/api/v1/branch-management/equipment-incidents/{id}/progress")
    public void progress(@AuthenticationPrincipal Identity actor,@PathVariable UUID id,@RequestBody ProgressCommand command) { audit.record("UPDATE_EQUIPMENT_INCIDENT_PROGRESS","INCIDENT",id,()->{service.progress(actor,null,id,command);return id;},x->x); }
    @PutMapping("/api/v1/branch-management/equipment-incidents/{id}/close")
    public void close(@AuthenticationPrincipal Identity actor,@PathVariable UUID id,@RequestBody java.util.Map<String,String> body) { audit.record("CLOSE_EQUIPMENT_INCIDENT","INCIDENT",id,()->{service.close(actor,null,id,body.get("note"));return id;},x->x); }
    // UC-73/BR-42: Ghi quyết định sửa chữa hoặc thay thế.
    @PutMapping("/api/v1/branch-management/equipment-incidents/{id}/assessment")
    public void assess(@AuthenticationPrincipal Identity actor,@PathVariable UUID id,@RequestBody AssessmentCommand command) { audit.record("ASSESS_EQUIPMENT_INCIDENT","INCIDENT",id,()->{service.assess(actor,null,id,command);return id;},x->x); }
    // UC-71/BR-45: Lưu kết quả, chi phí thực tế, ngày hoàn tất và tình trạng sau nghiệm thu.
    @PostMapping(value="/api/v1/branch-management/equipment-incidents/{id}/repair-outcome",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public void repair(@AuthenticationPrincipal Identity actor,@PathVariable UUID id,@RequestPart("command") RepairCommand command,@RequestPart(value="invoice") MultipartFile invoice,@RequestPart(value="transferProof") MultipartFile transferProof) { audit.record("RECORD_EQUIPMENT_REPAIR_OUTCOME","INCIDENT",id,()->{service.repair(actor,null,id,command,invoice,transferProof);return id;},x->x); }
    // UC-74/BR-48: Chỉ quản lý cấp cao tạo yêu cầu, chưa đổi cơ sở tài sản.
    @PostMapping("/api/v1/head-office/equipment-transfers")
    public UUID transfer(@AuthenticationPrincipal Identity actor,@RequestBody TransferCommand command) { return audit.record("CREATE_EQUIPMENT_TRANSFER","TRANSFER",null,()->service.transfer(actor,command),x->x); }
    // UC-74–UC-76: Xem lịch sử chuyển trong phạm vi một cơ sở.
    @GetMapping({"/api/v1/branch-management/equipment-transfers","/api/v1/head-office/equipment-transfers"})
    public List<TransferView> transfers(@AuthenticationPrincipal Identity actor,@RequestParam(required=false) UUID branchId) { return service.transfers(actor,branchId); }
    // UC-75/BR-49: Quản lý cơ sở nguồn xác nhận giao tài sản.
    @PutMapping("/api/v1/branch-management/equipment-transfers/{id}/handover")
    public void handover(@AuthenticationPrincipal Identity actor,@PathVariable UUID id) { audit.record("CONFIRM_EQUIPMENT_HANDOVER","TRANSFER",id,()->{service.handover(actor,id);return id;},x->x); }
    // UC-76/BR-50/BR-51: Cơ sở đích xác nhận nhận và vị trí mới.
    @PutMapping("/api/v1/branch-management/equipment-transfers/{id}/receipt")
    public void receipt(@AuthenticationPrincipal Identity actor,@PathVariable UUID id,@RequestBody TransferReceiptCommand command) { audit.record("CONFIRM_EQUIPMENT_RECEIPT","TRANSFER",id,()->{service.receive(actor,id,command);return id;},x->x); }
}
