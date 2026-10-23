/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: TrongNV
 * Created: 12/10/2026
 * Description: REST API quản lý hạng phòng, quy tắc giá và combo.
 */

package com.gcmb.backend.administration.pricing.controller;

import com.gcmb.backend.administration.audit.service.MutationAudit;
import com.gcmb.backend.administration.pricing.dto.PriceCatalogItem;
import com.gcmb.backend.administration.pricing.dto.PriceCommand;
import com.gcmb.backend.administration.pricing.dto.PriceRuleCommand;
import com.gcmb.backend.administration.pricing.dto.PriceRuleView;
import com.gcmb.backend.administration.pricing.dto.RoomTypeCommand;
import com.gcmb.backend.administration.pricing.dto.RoomTypeView;
import com.gcmb.backend.administration.pricing.service.RoomCatalogService;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RoomCatalogController {
    private final RoomCatalogService service;
    private final MutationAudit audit;
    public RoomCatalogController(RoomCatalogService service, MutationAudit audit) {
        this.audit = audit;
        this.service = service;
    }

    @GetMapping("/api/v1/head-office/room-types")
    public List<RoomTypeView> roomTypes() {
        return service.roomTypes();
    }

    @PostMapping("/api/v1/head-office/room-types")
    public RoomTypeView createRoomType(@RequestBody RoomTypeCommand command) {
        return audit.record("CREATE_ROOM_TYPE", "ROOM_TYPE", null,
            () -> service.createRoomType(command), RoomTypeView::id);
    }

    @PutMapping("/api/v1/head-office/room-types/{id}")
    public RoomTypeView updateRoomType(@PathVariable UUID id, @RequestBody RoomTypeCommand command) {
        return audit.record("UPDATE_ROOM_TYPE","ROOM_TYPE",id,()->service.updateRoomType(id, command),RoomTypeView::id);
    }

    // Ghi thay đổi trạng thái và nhật ký trong cùng giao dịch.
    @PostMapping("/api/v1/head-office/room-types/{id}/deactivate")
    public RoomTypeView deactivateRoomType(@PathVariable UUID id) {
        return audit.record("DEACTIVATE_ROOM_TYPE", "ROOM_TYPE", id,
            () -> service.deactivateRoomType(id), RoomTypeView::id);
    }

    @DeleteMapping("/api/v1/head-office/room-types/{id}")
    public void deleteRoomType(@PathVariable UUID id) {
        audit.record("DELETE_ROOM_TYPE", "ROOM_TYPE", id, () -> {
            service.deleteRoomType(id);
            return null;
        }, null);
    }

    @GetMapping("/api/v1/head-office/price-rules")
    public List<PriceRuleView> priceRules(@RequestParam(required=false) UUID roomTypeId,
                                          @RequestParam(defaultValue="false") boolean activeOnly) {
        return service.priceRules(roomTypeId,activeOnly);
    }

    @PostMapping("/api/v1/head-office/price-rules")
    public PriceRuleView createPriceRule(@RequestBody PriceRuleCommand command) { return audit.record("CREATE_PRICE_RULE","PRICE_RULE",null,()->service.createPriceRule(command),PriceRuleView::id); }

    @PutMapping("/api/v1/head-office/price-rules/{id}")
    public PriceRuleView updatePriceRule(@PathVariable UUID id,@RequestBody PriceRuleCommand command) {
        return audit.record("UPDATE_PRICE_RULE","PRICE_RULE",id,()->service.updatePriceRule(id,command),PriceRuleView::id);
    }

    @DeleteMapping("/api/v1/head-office/price-rules/{id}")
    public void deletePriceRule(@PathVariable UUID id) { audit.record("DELETE_PRICE_RULE","PRICE_RULE",id,()->{service.deletePriceRule(id);return null;},null); }

    @GetMapping("/api/v1/head-office/room-prices")
    public List<PriceCatalogItem> prices(@RequestParam(required=false) String kind,
                                         @RequestParam(required=false) UUID roomTypeId,
                                         @RequestParam(defaultValue="false") boolean activeOnly) {
        return service.prices(kind == null ? null : kind.toUpperCase(), roomTypeId, activeOnly);
    }

    @PostMapping("/api/v1/head-office/room-prices/hourly/{roomTypeId}")
    public PriceCatalogItem createHourly(@PathVariable UUID roomTypeId, @RequestBody PriceCommand command) {
        return audit.record("CREATE_PRICE_RULE","PRICE_RULE",null,()->service.createPrice("HOURLY",roomTypeId,command),PriceCatalogItem::id);
    }

    @PostMapping("/api/v1/head-office/room-prices/combos/{roomTypeId}")
    public PriceCatalogItem createCombo(@PathVariable UUID roomTypeId, @RequestBody PriceCommand command) {
        return audit.record("CREATE_COMBO","COMBO",null,()->service.createPrice("COMBO",roomTypeId,command),PriceCatalogItem::id);
    }

    @PutMapping("/api/v1/head-office/room-prices/{kind}/{id}")
    public PriceCatalogItem updatePrice(@PathVariable String kind,@PathVariable UUID id,@RequestBody PriceCommand command) {
        return audit.record("UPDATE_" + ("HOURLY".equalsIgnoreCase(kind) ? "PRICE_RULE" : "COMBO"),"HOURLY".equalsIgnoreCase(kind) ? "PRICE_RULE" : "COMBO",id,()->service.updatePrice(kind.toUpperCase(),id,command),PriceCatalogItem::id);
    }

    @DeleteMapping("/api/v1/head-office/room-prices/{kind}/{id}")
    public void deletePrice(@PathVariable String kind,@PathVariable UUID id) {
        audit.record("DELETE_COMBO","COMBO",id,()->{service.deletePrice(kind.toUpperCase(),id);return null;},null);
    }
}
