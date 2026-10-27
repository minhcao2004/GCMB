/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: HuyDV
 * Created: 14/10/2026
 * Description: Ghi nhật ký thao tác thay đổi dữ liệu, người thực hiện và dữ liệu trước/sau trong cùng giao dịch nghiệp vụ.
 */
package com.gcmb.backend.audit.service;

import com.gcmb.backend.auth.dto.Identity;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class MutationAudit {
    private static final Map<String, String> TABLES = Map.of(
        "ROOM", "room", "ROOM_TYPE", "room_type", "PRICE_RULE", "price_rule",
        "COMBO", "combo", "BOOKING", "booking", "BRANCH", "branch", "MENU_ITEM", "menu_item",
        "FB_ORDER", "fb_order", "INVENTORY_ITEM", "inventory_item");
    private final JdbcTemplate db;
    private final TransactionTemplate transaction;

    public MutationAudit(JdbcTemplate db, PlatformTransactionManager manager) {
        this.db = db;
        this.transaction = new TransactionTemplate(manager);
    }

    // resultId xác định ID mới khi tạo đối tượng hoặc tạo phiên bản giá mới.
    public <T> T record(String action, String type, UUID id, Supplier<T> operation, Function<T, UUID> resultId) {
        if (!TABLES.containsKey(type)) throw new IllegalArgumentException("Unsupported audit entity");
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof Identity actor)) {
            throw new IllegalStateException("Business audit requires an authenticated actor");
        }
        return transaction.execute(status -> {
            String before = snapshot(type, id);
            T result = operation.get();
            UUID afterId = resultId == null ? id : resultId.apply(result);
            String after = snapshot(type, afterId);
            if (!java.util.Objects.equals(before, after)) {
                db.update("""
                    INSERT INTO gcmb.audit_event(actor_id,branch_id,action,entity_type,entity_id,before_data,after_data)
                    VALUES (?,COALESCE(CAST(CAST(? AS jsonb)->>'branch_id' AS uuid),
                        CAST(CAST(? AS jsonb)->>'branch_id' AS uuid)),?,?,?,CAST(? AS jsonb),
                        COALESCE(CAST(? AS jsonb),'{}'::jsonb) || jsonb_build_object('actorRole',CAST(? AS text)))
                    """, actor.id(), after, before, action, type, id == null ? afterId : id, before, after, actor.role());
            }
            return result;
        });
    }

    private String snapshot(String type, UUID id) {
        if (id == null) return null;
        // Table identifiers only come from TABLES; snapshot columns are an explicit allowlist.
        String table = TABLES.get(type);
        db.queryForList("SELECT id FROM gcmb." + table + " WHERE id=? FOR UPDATE", UUID.class, id);
        String extra = "BRANCH".equals(type) ? " || jsonb_build_object('branch_id',t.id,'managerNames',"
            + "(SELECT string_agg(COALESCE(e.full_name,u.username), ', ' ORDER BY u.username)"
            + " FROM gcmb.branch_manager_assignment a JOIN gcmb.user_account u ON u.id=a.user_id"
            + " LEFT JOIN gcmb.employee e ON e.user_id=u.id WHERE a.branch_id=t.id))" : "";
        if ("BOOKING".equals(type)) extra = " || jsonb_build_object('event',"
            + "(SELECT jsonb_build_object('type',event_type,'reason',reason,'details',details)"
            + " FROM gcmb.booking_event WHERE booking_id=t.id ORDER BY created_at DESC,id DESC LIMIT 1))";
        if ("ROOM_TYPE".equals(type)) extra = " || jsonb_build_object('combos',"
            + "(SELECT jsonb_agg(jsonb_build_object('id',id,'code',code,'name',name,'price',price)) FROM gcmb.combo WHERE room_type_id=t.id AND branch_id IS NULL),"
            + "'priceRules',(SELECT jsonb_agg(jsonb_build_object('id',id,'name',name,'hourly_price',hourly_price)) FROM gcmb.price_rule WHERE room_type_id=t.id AND branch_id IS NULL))";
        if ("INVENTORY_ITEM".equals(type)) extra = " || jsonb_build_object('stockBalance',"
            + "(SELECT jsonb_build_object('quantity',quantity,'minimum_quantity',minimum_quantity,'version',version)"
            + " FROM gcmb.stock_balance WHERE inventory_item_id=t.id))";
        List<String> snapshots = db.queryForList("""
            SELECT (COALESCE((SELECT jsonb_object_agg(key,value) FROM jsonb_each(to_jsonb(t))
                WHERE key IN ('id','code','name','branch_id','room_type_id','mode','capacity','description',
                    'address','phone','notes','updated_at','closed_on','floor_number','operating_status','status_reason','expected_ready_at','active','version',
                    'valid_from','valid_to','minute_from','minute_to','weekdays','day_kind','specific_date',
                    'hourly_price','overtime_hourly_price','billing_step_minutes','priority','price',
                    'duration_minutes','deposit_required','day_group','min_guests','included_description',
                    'status','contact_name','contact_phone','guest_count','planned_start','planned_end',
                    'actual_checkin','actual_checkout','note','channel','pricing_mode','category','kind',
                    'inventory_item_id','sale_price','description','image_original_name','image_mime_type',
                    'sku','unit_id','tracking_mode',
                    'deleted_at','deleted_by','booking_id','allocation_id','assigned_employee_id',
                    'accepted_by','accepted_at','served_at')),'{}'::jsonb)
            """ + extra + ")::text FROM gcmb." + table + " t WHERE t.id=?", String.class, id);
        return snapshots.isEmpty() ? null : snapshots.get(0);
    }
}
