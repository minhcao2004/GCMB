package com.gcmb.backend.inventory.controller;

import com.gcmb.backend.audit.service.MutationAudit;
import com.gcmb.backend.auth.dto.Identity;
import com.gcmb.backend.inventory.dto.InventoryDtos.EvidenceContent;
import com.gcmb.backend.inventory.dto.InventoryDtos.InventoryItemCommand;
import com.gcmb.backend.inventory.dto.InventoryDtos.InventoryItemView;
import com.gcmb.backend.inventory.dto.InventoryDtos.InventoryMetadata;
import com.gcmb.backend.inventory.dto.InventoryDtos.StockAdjustmentCommand;
import com.gcmb.backend.inventory.dto.InventoryDtos.StockMovementView;
import com.gcmb.backend.inventory.dto.InventoryDtos.StockReceiptCommand;
import com.gcmb.backend.inventory.dto.InventoryDtos.StockReceiptResult;
import com.gcmb.backend.inventory.service.InventoryService;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/branch-management/inventory")
public class InventoryController {
    private final InventoryService service;
    private final MutationAudit audit;

    public InventoryController(InventoryService service, MutationAudit audit) {
        this.service = service;
        this.audit = audit;
    }

    @GetMapping("/items")
    public List<InventoryItemView> items(@AuthenticationPrincipal Identity actor) {
        return service.list(actor);
    }

    @GetMapping("/metadata")
    public InventoryMetadata metadata(@AuthenticationPrincipal Identity actor) {
        return service.metadata(actor);
    }

    @GetMapping("/items/{id}")
    public InventoryItemView item(@AuthenticationPrincipal Identity actor, @PathVariable UUID id) {
        return service.item(actor, id);
    }

    @PostMapping("/items")
    public InventoryItemView create(@AuthenticationPrincipal Identity actor, @RequestBody InventoryItemCommand body) {
        return audit.record("CREATE_INVENTORY_ITEM", "INVENTORY_ITEM", null,
            () -> service.create(actor, body), InventoryItemView::id);
    }

    @PutMapping("/items/{id}")
    public InventoryItemView update(@AuthenticationPrincipal Identity actor, @PathVariable UUID id,
            @RequestBody InventoryItemCommand body) {
        return audit.record("UPDATE_INVENTORY_ITEM", "INVENTORY_ITEM", id,
            () -> service.update(actor, id, body), InventoryItemView::id);
    }

    @DeleteMapping("/items/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Identity actor, @PathVariable UUID id) {
        audit.record("DELETE_INVENTORY_ITEM", "INVENTORY_ITEM", id, () -> {
            service.delete(actor, id);
            return null;
        }, null);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/items/{itemId}/movements")
    public List<StockMovementView> movements(@AuthenticationPrincipal Identity actor,
            @PathVariable UUID itemId, @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to, @RequestParam(required = false) String kind,
            @RequestParam(required = false) String q) {
        return service.history(actor, itemId, from, to, kind, q);
    }

    @PostMapping(value = "/receipts", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public StockReceiptResult receive(@AuthenticationPrincipal Identity actor,
            @RequestPart("command") StockReceiptCommand command,
            @RequestPart(value = "evidence", required = false) MultipartFile evidence) {
        return service.receive(actor, command, evidence);
    }

    @PostMapping(value = "/adjustments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public StockMovementView adjust(@AuthenticationPrincipal Identity actor,
            @RequestPart("command") StockAdjustmentCommand command,
            @RequestPart(value = "evidence", required = false) MultipartFile evidence) {
        return service.adjust(actor, command, evidence);
    }

    @GetMapping("/items/{itemId}/movements/{movementId}/evidence")
    public ResponseEntity<byte[]> evidence(@AuthenticationPrincipal Identity actor,
            @PathVariable UUID itemId, @PathVariable UUID movementId) {
        EvidenceContent content = service.evidence(actor, itemId, movementId);
        return ResponseEntity.ok().cacheControl(CacheControl.noCache())
            .contentType(MediaType.parseMediaType(content.mimeType()))
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                .filename(content.originalName(), StandardCharsets.UTF_8).build().toString())
            .header("X-Content-Type-Options", "nosniff")
            .body(content.bytes());
    }
}
