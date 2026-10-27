/**
 * Copyright (c) 2026 SEP490-G12.
 * Author: HuyDV
 * Created: 27/10/2026
 * Description: Lễ tân gửi yêu cầu; kế toán cập nhật tiến độ hóa đơn theo UC-127/BR-125 đã thống nhất lại.
 */
package com.gcmb.backend.booking.service;

import com.gcmb.backend.auth.dto.Identity;
import com.gcmb.backend.auth.exception.AuthFailure;
import com.gcmb.backend.auth.service.AuthValidation;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
@Transactional
public class TaxInvoiceService {
    private final JdbcTemplate db;
    private final ObjectMapper json;
    public TaxInvoiceService(JdbcTemplate db,ObjectMapper json){this.db=db;this.json=json;}
    public record Request(String buyerName,String taxCode,String buyerAddress,String contactEmail,String note) {}
    public record Progress(String status,String deliveryResult,Long version) {}
    private static final Set<String> STATUSES=Set.of("REQUESTED","PREPARING","ISSUED","DELIVERED","DELIVERY_FAILED");
    private static final Set<String> DELIVERED_RESULTS=Set.of("DELIVERED","EMAIL","DIRECT","MESSAGE");
    private static final Set<String> FAILED_RESULTS=Set.of("INVALID_CONTACT","UNREACHABLE","SEND_FAILED");
    private static final String SELECT="""
        SELECT t.booking_id AS "bookingId",b.code AS "bookingCode",b.contact_name AS customer,
          b.branch_id AS "branchId",br.name AS "branchName",t.buyer_name AS "buyerName",
          t.tax_code AS "taxCode",t.buyer_address AS "buyerAddress",t.contact_email AS "contactEmail",
          t.status,t.note,t.delivery_result AS "deliveryResult",t.version,
          t.created_at AS "createdAt",t.updated_at AS "updatedAt"
        FROM gcmb.tax_invoice_request t JOIN gcmb.booking b ON b.id=t.booking_id
        JOIN gcmb.branch br ON br.id=b.branch_id
        """;

    private void authorize(Identity actor,boolean reception){
        if(actor==null || !(reception?"RECEPTIONIST".equals(actor.role()):Set.of("RECEPTIONIST","ACCOUNTANT").contains(actor.role())))throw denied();
        if(!Boolean.TRUE.equals(db.queryForObject("SELECT EXISTS(SELECT 1 FROM gcmb.user_account u JOIN gcmb.role r ON r.id=u.role_id WHERE u.id=? AND u.status='ACTIVE' AND r.code=? AND (r.code='ACCOUNTANT' OR u.branch_id=?))",Boolean.class,actor.id(),actor.role(),actor.branchId())))throw denied();
    }
    private Map<String,Object> booking(Identity actor,UUID id,boolean writing){
        authorize(actor,false);
        var rows=db.queryForList("SELECT b.id,b.code,b.branch_id,br.name,br.status FROM gcmb.booking b JOIN gcmb.branch br ON br.id=b.branch_id WHERE b.id=?"+(writing?" FOR UPDATE OF b FOR SHARE OF br":""),id);
        if(rows.isEmpty())throw new AuthFailure(404,"form","Không tìm thấy booking.");
        var b=rows.get(0);
        if("RECEPTIONIST".equals(actor.role())&&!Objects.equals(actor.branchId(),b.get("branch_id")))throw denied();
        // BR-177 / MSG43: cơ sở tạm ngừng vẫn xem được yêu cầu và lịch sử.
        if(writing&&!"ACTIVE".equals(b.get("status")))throw new AuthFailure(403,"form","Cơ sở "+b.get("name")+" đang tạm ngừng hoạt động. Bạn có thể xem dữ liệu nhưng không thể thực hiện thay đổi.");
        return b;
    }
    @Transactional(readOnly=true)
    public List<Map<String,Object>> list(Identity actor){
        authorize(actor,false);
        return db.queryForList(SELECT+" WHERE (?='ACCOUNTANT' OR b.branch_id=?) ORDER BY t.updated_at DESC,t.booking_id",actor.role(),actor.branchId());
    }
    @Transactional(readOnly=true)
    public Map<String,Object> detail(Identity actor,UUID id){
        var b=booking(actor,id,false);
        var rows=db.queryForList(SELECT+" WHERE t.booking_id=?",id);
        var result=new LinkedHashMap<String,Object>();
        result.put("bookingId",id);result.put("bookingCode",b.get("code"));
        result.put("request",rows.isEmpty()?null:rows.get(0));
        result.put("history",db.queryForList("SELECT action,after_data::text AS data,created_at AS \"createdAt\" FROM gcmb.audit_event WHERE entity_type='TAX_INVOICE_REQUEST' AND entity_id=? ORDER BY created_at,id",id));
        return result;
    }
    public Map<String,Object> create(Identity actor,UUID id,Request input){
        authorize(actor,true); // Lễ tân tiếp nhận yêu cầu của khách và gửi kế toán.
        var b=booking(actor,id,true);
        if(input==null)throw new AuthFailure(400,"form","Vui lòng bổ sung thông tin yêu cầu hóa đơn.");
        // BR-111/113; MSG02/03/05. Request snapshot is retained for external handling.
        String buyer=AuthValidation.required(input.buyerName(),"buyerName","tên người mua / đơn vị",200,true);
        String email=optional(input.contactEmail(),"contactEmail","Email",254);
        if(!email.isEmpty())email=AuthValidation.email(email);
        if(!db.queryForList("SELECT booking_id FROM gcmb.tax_invoice_request WHERE booking_id=?",id).isEmpty())
            throw new AuthFailure(409,"form","Booking đã có yêu cầu hóa đơn. Vui lòng xem yêu cầu hiện có.");
        db.update("INSERT INTO gcmb.tax_invoice_request(booking_id,buyer_name,tax_code,buyer_address,contact_email,note,created_by,updated_by) VALUES (?,?,?,?,?,?,?,?)",
            id,buyer,optional(input.taxCode(),"taxCode","Mã số thuế",30),optional(input.buyerAddress(),"buyerAddress","Địa chỉ",2000),email,optional(input.note(),"note","Ghi chú",2000),actor.id(),actor.id());
        audit(actor,id,b,"CREATE_TAX_INVOICE_REQUEST");
        return detail(actor,id); // Yêu cầu mới đồng thời xuất hiện trong hộp thông báo của kế toán.
    }
    public Map<String,Object> progress(Identity actor,UUID id,Progress input){
        // UC-127 / BR-125: theo phân công mới của người dùng, chỉ kế toán được cập nhật tiến độ.
        authorize(actor,false);
        if(!"ACCOUNTANT".equals(actor.role()))throw denied(); // MSG24, áp dụng cả khi gọi trực tiếp service.
        var b=booking(actor,id,true);
        var rows=db.queryForList("SELECT version,status,delivery_result FROM gcmb.tax_invoice_request WHERE booking_id=? FOR UPDATE",id);
        if(rows.isEmpty())throw new AuthFailure(404,"form","Booking chưa có yêu cầu hóa đơn thuế.");
        if(input==null||input.version()==null||input.version().longValue()!=((Number)rows.get(0).get("version")).longValue())
            throw new AuthFailure(409,"form","Yêu cầu đã thay đổi. Vui lòng tải lại và kiểm tra trước khi xác nhận.");
        if(input.status()==null||!STATUSES.contains(input.status()))throw new AuthFailure(400,"status","Vui lòng bổ sung trạng thái xử lý hợp lệ.");
        String delivery=optional(input.deliveryResult(),"deliveryResult","Kết quả giao hóa đơn",2000);
        if(Set.of("DELIVERED","DELIVERY_FAILED").contains(input.status()))delivery=AuthValidation.required(delivery,"deliveryResult","kết quả giao hóa đơn",2000,true);
        // UC-127 theo yêu cầu đã chốt: dùng lựa chọn kết quả cố định, không nhập ghi chú xử lý.
        boolean allowed="DELIVERED".equals(input.status())?DELIVERED_RESULTS.contains(delivery)
            :"DELIVERY_FAILED".equals(input.status())?FAILED_RESULTS.contains(delivery):delivery.isEmpty();
        // Giữ nguyên văn kết quả cũ khi trạng thái/kết quả không đổi, không biến dữ liệu lịch sử thành lỗi.
        boolean unchanged=Objects.equals(input.status(),rows.get(0).get("status"))&&Objects.equals(delivery,rows.get(0).get("delivery_result"));
        if(!allowed&&!unchanged)throw new AuthFailure(400,"deliveryResult","Vui lòng chọn kết quả giao hóa đơn phù hợp với trạng thái xử lý.");
        db.update("UPDATE gcmb.tax_invoice_request SET status=?,delivery_result=?,version=version+1,updated_by=?,updated_at=CURRENT_TIMESTAMP WHERE booking_id=?",
            input.status(),delivery,actor.id(),id);
        audit(actor,id,b,"UPDATE_TAX_INVOICE_REQUEST");
        return detail(actor,id);
    }
    @Transactional(readOnly=true)
    public Map<String,Object> unread(Identity actor){
        authorize(actor,false);if(!"ACCOUNTANT".equals(actor.role()))throw denied();
        return Map.of("count",db.queryForObject("SELECT count(*) FROM gcmb.tax_invoice_request t WHERE NOT EXISTS(SELECT 1 FROM gcmb.tax_invoice_read r WHERE r.booking_id=t.booking_id AND r.user_id=?)",Long.class,actor.id()));
    }
    public void read(Identity actor,UUID id){
        authorize(actor,false);if(!"ACCOUNTANT".equals(actor.role()))throw denied();
        if(db.queryForList("SELECT booking_id FROM gcmb.tax_invoice_request WHERE booking_id=?",id).isEmpty())throw new AuthFailure(404,"form","Không tìm thấy yêu cầu hóa đơn.");
        db.update("INSERT INTO gcmb.tax_invoice_read(booking_id,user_id) VALUES (?,?) ON CONFLICT DO NOTHING",id,actor.id());
    }
    private void audit(Identity actor,UUID id,Map<String,Object> b,String action){
        var after=db.queryForMap(SELECT+" WHERE t.booking_id=?",id);
        db.update("INSERT INTO gcmb.audit_event(actor_id,branch_id,action,entity_type,entity_id,after_data) VALUES (?,?,?,'TAX_INVOICE_REQUEST',?,CAST(? AS jsonb))",actor.id(),b.get("branch_id"),action,id,json.writeValueAsString(after));
    }
    private static String optional(String value,String field,String label,int max){return value==null||value.isBlank()?"":AuthValidation.required(value,field,label,max,true);}
    private static AuthFailure denied(){return new AuthFailure(403,"form","Bạn không có quyền thực hiện thao tác đối với yêu cầu hóa đơn thuế.");} // MSG24
}
