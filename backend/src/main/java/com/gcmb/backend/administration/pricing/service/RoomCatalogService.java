package com.gcmb.backend.administration.pricing.service;

import com.gcmb.backend.administration.pricing.dto.PriceCatalogItem;
import com.gcmb.backend.administration.pricing.dto.PriceCommand;
import com.gcmb.backend.administration.pricing.dto.PriceRuleCommand;
import com.gcmb.backend.administration.pricing.dto.PriceRuleView;
import com.gcmb.backend.administration.pricing.dto.RoomTypeCommand;
import com.gcmb.backend.administration.pricing.dto.RoomTypeView;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class RoomCatalogService {
    private static final Pattern ROOM_CODE = Pattern.compile("[A-Z0-9_-]{2,60}");
    private static final Pattern COMBO_CODE = Pattern.compile("[A-Z0-9_-]{2,60}");
    private static final Pattern PRICE_RULE_CODE = Pattern.compile("[A-Z0-9_-]{2,60}");
    private final JdbcTemplate db;

    public RoomCatalogService(JdbcTemplate db) { this.db = db; }

    public List<RoomTypeView> roomTypes() {
        return db.query("SELECT id,code,name,mode,capacity,description,active FROM gcmb.room_type ORDER BY code",
            (rs,n) -> new RoomTypeView(rs.getObject("id",UUID.class),rs.getString("code"),rs.getString("name"),
                rs.getString("mode"),rs.getInt("capacity"),rs.getString("description"),rs.getBoolean("active")));
    }

    @Transactional
    public RoomTypeView createRoomType(RoomTypeCommand c) {
        validateRoomType(c);
        String code=c.code().trim().toUpperCase();
        Long duplicate=db.queryForObject("SELECT COUNT(*) FROM gcmb.room_type WHERE upper(code)=?",Long.class,code);
        if(duplicate!=null&&duplicate>0) throw new ResponseStatusException(HttpStatus.CONFLICT,"Mã hạng phòng đã tồn tại. Vui lòng nhập mã khác.");
        UUID id=UUID.randomUUID();
        try {
            db.update("INSERT INTO gcmb.room_type(id,code,name,mode,capacity,description,active) VALUES (?,?,?,?,?,?,true)",
                id,code,c.name().trim(),c.serviceType(),c.capacity(),c.description()==null?"":c.description().trim());
        } catch(DataIntegrityViolationException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Mã hạng phòng đã tồn tại hoặc dữ liệu không hợp lệ.");
        }
        return roomType(id);
    }

    @Transactional
    public RoomTypeView updateRoomType(UUID id, RoomTypeCommand c) {
        validateRoomType(c);
        int updated=db.update("UPDATE gcmb.room_type SET name=?,mode=?,capacity=?,description=? WHERE id=?",
            c.name().trim(),c.serviceType(),c.capacity(),c.description()==null?"":c.description().trim(),id);
        if(updated==0) throw missing("Không tìm thấy hạng phòng.");
        return roomType(id);
    }

    @Transactional
    public void deleteRoomType(UUID id) {
        try {
            Long exists=db.queryForObject("SELECT COUNT(*) FROM gcmb.room_type WHERE id=?",Long.class,id);
            if(exists==null||exists==0) throw missing("Không tìm thấy hạng phòng.");
            db.update("DELETE FROM gcmb.combo_item WHERE combo_id IN (SELECT id FROM gcmb.combo WHERE room_type_id=? AND branch_id IS NULL)",id);
            db.update("DELETE FROM gcmb.combo WHERE room_type_id=? AND branch_id IS NULL",id);
            db.update("DELETE FROM gcmb.price_rule WHERE room_type_id=? AND branch_id IS NULL",id);
            int deleted=db.update("DELETE FROM gcmb.room_type WHERE id=?",id);
            if(deleted==0) throw missing("Không tìm thấy hạng phòng.");
        } catch(DataIntegrityViolationException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Không thể xóa hạng phòng vì phòng thực tế hoặc hóa đơn lịch sử đang sử dụng dữ liệu này. Các dữ liệu liên quan vẫn được giữ nguyên.");
        }
    }

    public List<PriceRuleView> priceRules(UUID roomTypeId, boolean activeOnly) {
        String roomFilter=roomTypeId==null?"":" AND p.room_type_id=?";
        String activeFilter=activeOnly?" AND p.active=true":"";
        String sql="""
          SELECT p.id,p.code,p.version,rt.id AS room_type_id,rt.name AS room_type_name,p.name,p.weekdays,
                 p.valid_from,p.valid_to,p.minute_from,p.minute_to,p.hourly_price,p.active
          FROM gcmb.price_rule p JOIN gcmb.room_type rt ON rt.id=p.room_type_id
          WHERE p.branch_id IS NULL
          """+roomFilter+activeFilter+" ORDER BY rt.name,p.code,p.version DESC";
        return roomTypeId==null?db.query(sql,(rs,n)->mapPriceRule(rs.getObject("id",UUID.class),rs.getString("code"),rs.getInt("version"),rs.getObject("room_type_id",UUID.class),rs.getString("room_type_name"),rs.getString("name"),rs.getArray("weekdays"),rs.getObject("valid_from",LocalDate.class),rs.getObject("valid_to",LocalDate.class),rs.getInt("minute_from"),rs.getInt("minute_to"),rs.getBigDecimal("hourly_price"),rs.getBoolean("active"))):db.query(sql,(rs,n)->mapPriceRule(rs.getObject("id",UUID.class),rs.getString("code"),rs.getInt("version"),rs.getObject("room_type_id",UUID.class),rs.getString("room_type_name"),rs.getString("name"),rs.getArray("weekdays"),rs.getObject("valid_from",LocalDate.class),rs.getObject("valid_to",LocalDate.class),rs.getInt("minute_from"),rs.getInt("minute_to"),rs.getBigDecimal("hourly_price"),rs.getBoolean("active")),roomTypeId);
    }

    @Transactional
    public PriceRuleView createPriceRule(PriceRuleCommand c) {
        validatePriceRule(c);
        roomType(c.roomTypeId());
        String code=c.code().trim().toUpperCase();
        Long exists=db.queryForObject("SELECT COUNT(*) FROM gcmb.price_rule WHERE code=? AND active=true",Long.class,code);
        if(exists!=null&&exists>0) throw new ResponseStatusException(HttpStatus.CONFLICT,"Mã quy tắc giá đang được sử dụng.");
        Long version=db.queryForObject("SELECT COALESCE(MAX(version),0)+1 FROM gcmb.price_rule WHERE code=?",Long.class,code);
        UUID id=UUID.randomUUID();
        insertPriceRule(id,code,version==null?1:version.intValue(),c);
        return priceRule(id);
    }

    @Transactional
    public PriceRuleView updatePriceRule(UUID id, PriceRuleCommand c) {
        validatePriceRule(c);
        PriceRuleView old=priceRule(id);
        if(!old.code().equalsIgnoreCase(c.code().trim())) throw bad("Không thể đổi mã quy tắc khi đang sửa.");
        db.update("UPDATE gcmb.price_rule SET active=false WHERE id=?",id);
        Long version=db.queryForObject("SELECT COALESCE(MAX(version),0)+1 FROM gcmb.price_rule WHERE code=?",Long.class,old.code());
        UUID nextId=UUID.randomUUID();
        insertPriceRule(nextId,old.code(),version==null?1:version.intValue(),c);
        return priceRule(nextId);
    }

    @Transactional
    public void deletePriceRule(UUID id) {
        try {
            int deleted=db.update("DELETE FROM gcmb.price_rule WHERE id=? AND branch_id IS NULL",id);
            if(deleted==0) throw missing("Không tìm thấy quy tắc giá.");
        } catch(DataIntegrityViolationException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Không thể xóa quy tắc giá vì đã được dùng trong hóa đơn.");
        }
    }

    private void insertPriceRule(UUID id,String code,int version,PriceRuleCommand c) {
        // JdbcTemplate exposes the connection through a callback so PostgreSQL receives a smallint[] value.
        db.execute((org.springframework.jdbc.core.ConnectionCallback<Void>) connection -> {
            Short[] dayValues=c.weekdays().stream().map(Integer::shortValue).toArray(Short[]::new);
            java.sql.Array weekdays=connection.createArrayOf("smallint",dayValues);
            try(var statement=connection.prepareStatement("""
              INSERT INTO gcmb.price_rule(id,code,version,room_type_id,branch_id,name,valid_from,valid_to,day_kind,
                specific_date,minute_from,minute_to,weekdays,hourly_price,overtime_hourly_price,billing_step_minutes,priority,active)
              VALUES(?,?,?,?,NULL,?,?,?,'ALL',NULL,?,?,?, ?, ?,60,100,true)
              """)) {
                statement.setObject(1,id);statement.setString(2,code);statement.setInt(3,version);statement.setObject(4,c.roomTypeId());
                statement.setString(5,c.name().trim());statement.setObject(6,c.validFrom());statement.setObject(7,c.validTo());
                statement.setInt(8,c.minuteFrom());statement.setInt(9,c.minuteTo());statement.setArray(10,weekdays);
                statement.setBigDecimal(11,c.hourlyPrice());statement.setBigDecimal(12,c.hourlyPrice());statement.executeUpdate();
            } finally { weekdays.free(); }
            return null;
        });
    }

    private PriceRuleView priceRule(UUID id) {
        List<PriceRuleView> found=db.query("""
          SELECT p.id,p.code,p.version,rt.id AS room_type_id,rt.name AS room_type_name,p.name,p.weekdays,
                 p.valid_from,p.valid_to,p.minute_from,p.minute_to,p.hourly_price,p.active
          FROM gcmb.price_rule p JOIN gcmb.room_type rt ON rt.id=p.room_type_id WHERE p.id=?
          """,(rs,n)->mapPriceRule(rs.getObject("id",UUID.class),rs.getString("code"),rs.getInt("version"),rs.getObject("room_type_id",UUID.class),rs.getString("room_type_name"),rs.getString("name"),rs.getArray("weekdays"),rs.getObject("valid_from",LocalDate.class),rs.getObject("valid_to",LocalDate.class),rs.getInt("minute_from"),rs.getInt("minute_to"),rs.getBigDecimal("hourly_price"),rs.getBoolean("active")),id);
        if(found.isEmpty()) throw missing("Không tìm thấy quy tắc giá.");
        return found.get(0);
    }

    private static PriceRuleView mapPriceRule(UUID id,String code,int version,UUID roomId,String roomName,String name,java.sql.Array weekdays,LocalDate from,LocalDate to,int minuteFrom,int minuteTo,BigDecimal price,boolean active) throws SQLException {
        Object raw=weekdays.getArray();List<Integer> days=new ArrayList<>();
        if(raw instanceof Object[] values) for(Object value:values) days.add(((Number)value).intValue());
        return new PriceRuleView(id,code,version,roomId,roomName,name,days,from,to,minuteFrom,minuteTo,price,active);
    }

    private static void validatePriceRule(PriceRuleCommand c) {
        if(c==null||c.code()==null||!PRICE_RULE_CODE.matcher(c.code().trim().toUpperCase()).matches()||c.name()==null||c.name().isBlank()||c.roomTypeId()==null||c.weekdays()==null||c.weekdays().isEmpty()||c.weekdays().stream().anyMatch(day->day==null||day<1||day>7)||c.validFrom()==null||(c.validTo()!=null&&!c.validTo().isAfter(c.validFrom()))||c.minuteFrom()==null||c.minuteFrom()<0||c.minuteFrom()>1439||c.minuteTo()==null||c.minuteTo()<1||c.minuteTo()>1440||c.minuteFrom().equals(c.minuteTo())||c.hourlyPrice()==null||c.hourlyPrice().signum()<0) throw bad("Thông tin quy tắc giá chưa hợp lệ.");
    }

    public List<PriceCatalogItem> prices(String kind, UUID roomTypeId, boolean activeOnly) {
        String filter=activeOnly?" AND active=true":"";
        String roomFilter=roomTypeId==null?"":" AND room_type_id=?";
        String sql="""
            SELECT * FROM (
              SELECT p.id,'HOURLY' AS kind,'HOURLY_'||rt.code AS code,rt.id AS room_type_id,rt.name AS room_type_name,
                p.name,'' AS description,NULL::integer AS duration_minutes,p.minute_from,p.minute_to,p.hourly_price AS price,p.active
              FROM gcmb.price_rule p JOIN gcmb.room_type rt ON rt.id=p.room_type_id
              WHERE p.branch_id IS NULL AND p.day_kind='ALL' AND p.minute_from=0 AND p.minute_to=1440
              UNION ALL
              SELECT c.id,'COMBO' AS kind,c.code,rt.id,rt.name,c.name,c.included_description,c.duration_minutes,
                c.minute_from,c.minute_to,c.price,c.active
              FROM gcmb.combo c JOIN gcmb.room_type rt ON rt.id=c.room_type_id
              WHERE c.branch_id IS NULL
            ) catalogue WHERE (CAST(? AS varchar) IS NULL OR kind=?)"""+filter+roomFilter+" ORDER BY room_type_name,kind,code";
        if(roomTypeId==null) return db.query(sql,(rs,n)->priceItem(rs.getObject("id",UUID.class),rs.getString("kind"),rs.getString("code"),
            rs.getObject("room_type_id",UUID.class),rs.getString("room_type_name"),rs.getString("name"),rs.getString("description"),
            (Integer)rs.getObject("duration_minutes"),(Integer)rs.getObject("minute_from"),(Integer)rs.getObject("minute_to"),rs.getBigDecimal("price"),rs.getBoolean("active")),kind,kind);
        return db.query(sql,(rs,n)->priceItem(rs.getObject("id",UUID.class),rs.getString("kind"),rs.getString("code"),
            rs.getObject("room_type_id",UUID.class),rs.getString("room_type_name"),rs.getString("name"),rs.getString("description"),
            (Integer)rs.getObject("duration_minutes"),(Integer)rs.getObject("minute_from"),(Integer)rs.getObject("minute_to"),rs.getBigDecimal("price"),rs.getBoolean("active")),kind,kind,roomTypeId);
    }

    @Transactional
    public PriceCatalogItem createPrice(String kind, UUID roomTypeId, PriceCommand c) {
        validatePrice(kind,c);
        RoomTypeView room=roomType(roomTypeId);
        if("HOURLY".equals(kind)) {
            String name=room.name()+" · Giờ lẻ";
            Integer active=db.queryForObject("SELECT COUNT(*) FROM gcmb.price_rule WHERE room_type_id=? AND name=? AND branch_id IS NULL AND active=true",Integer.class,roomTypeId,name);
            if(active!=null&&active>0) throw new ResponseStatusException(HttpStatus.CONFLICT,"Hạng phòng đã có giá giờ lẻ đang áp dụng.");
            UUID id=UUID.randomUUID();
            db.update("""
              INSERT INTO gcmb.price_rule(id,room_type_id,branch_id,name,valid_from,day_kind,minute_from,minute_to,
                hourly_price,overtime_hourly_price,billing_step_minutes,priority,active)
              VALUES(?,?,NULL,?,CURRENT_DATE,'ALL',0,1440,?,?,60,100,true)
              """,id,roomTypeId,name,c.price(),c.price());
            return price(id,"HOURLY");
        }
        Integer active=db.queryForObject("SELECT COUNT(*) FROM gcmb.combo WHERE code=? AND active=true",Integer.class,c.code());
        if(active!=null&&active>0) throw new ResponseStatusException(HttpStatus.CONFLICT,"Mã combo đã tồn tại và đang áp dụng.");
        Long version=db.queryForObject("SELECT COALESCE(MAX(version),0)+1 FROM gcmb.combo WHERE code=?",Long.class,c.code());
        UUID id=UUID.randomUUID();
        db.update("""
          INSERT INTO gcmb.combo(id,code,version,room_type_id,mode,name,duration_minutes,price,deposit_required,
            overtime_hourly_price,valid_from,active,branch_id,day_group,minute_from,minute_to,min_guests,
            included_description,priority)
          VALUES(?,?,?,?,?,?,?,?,0,0,CURRENT_TIMESTAMP,true,NULL,'ALL',?,?,1,?,100)
          """,id,c.code().trim().toUpperCase(),version,roomTypeId,room.serviceType(),c.name().trim(),c.durationMinutes(),c.price(),c.minuteFrom(),c.minuteTo(),c.description()==null?"":c.description().trim());
        return price(id,"COMBO");
    }

    @Transactional
    public PriceCatalogItem updatePrice(String kind,UUID id,PriceCommand c) {
        validatePrice(kind,c);
        if("HOURLY".equals(kind)) {
            PriceCatalogItem old=price(id,"HOURLY");
            db.update("UPDATE gcmb.price_rule SET active=false WHERE id=?",id);
            return createPrice("HOURLY",old.roomTypeId(),c);
        }
        PriceCatalogItem old=price(id,"COMBO");
        db.update("UPDATE gcmb.combo SET active=false,valid_to=GREATEST(CURRENT_TIMESTAMP+interval '1 microsecond',valid_from+interval '1 microsecond') WHERE id=?",id);
        UUID targetRoomTypeId=c.roomTypeId()==null?old.roomTypeId():c.roomTypeId();
        PriceCommand next=new PriceCommand(targetRoomTypeId,old.code(),c.name(),c.description(),c.durationMinutes(),c.minuteFrom(),c.minuteTo(),c.price());
        return createPrice("COMBO",targetRoomTypeId,next);
    }

    @Transactional
    public void deletePrice(String kind,UUID id) {
        if(!"COMBO".equals(kind)) throw bad("Chỉ hỗ trợ xóa combo tại chức năng này.");
        price(id,kind);
        try {
            int deleted=db.update("DELETE FROM gcmb.combo WHERE id=? AND branch_id IS NULL",id);
            if(deleted==0) throw missing("Không tìm thấy combo.");
        } catch(DataIntegrityViolationException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Không thể xóa combo vì đã được dùng trong hóa đơn hoặc có thành phần gắn với combo.");
        }
    }

    private PriceCatalogItem price(UUID id,String kind) {
        List<PriceCatalogItem> found=db.query("""
          SELECT * FROM (
            SELECT p.id,'HOURLY' AS kind,'HOURLY_'||rt.code AS code,rt.id AS room_type_id,rt.name AS room_type_name,p.name,
              '' AS description,NULL::integer AS duration_minutes,p.minute_from,p.minute_to,p.hourly_price AS price,p.active
            FROM gcmb.price_rule p JOIN gcmb.room_type rt ON rt.id=p.room_type_id WHERE p.id=? AND p.branch_id IS NULL
            UNION ALL
            SELECT c.id,'COMBO',c.code,rt.id,rt.name,c.name,c.included_description,c.duration_minutes,c.minute_from,c.minute_to,c.price,c.active
            FROM gcmb.combo c JOIN gcmb.room_type rt ON rt.id=c.room_type_id WHERE c.id=? AND c.branch_id IS NULL
          ) entries WHERE kind=?
          """,(rs,n)->priceItem(rs.getObject("id",UUID.class),rs.getString("kind"),rs.getString("code"),rs.getObject("room_type_id",UUID.class),rs.getString("room_type_name"),rs.getString("name"),rs.getString("description"),(Integer)rs.getObject("duration_minutes"),(Integer)rs.getObject("minute_from"),(Integer)rs.getObject("minute_to"),rs.getBigDecimal("price"),rs.getBoolean("active")),id,id,kind);
        if(found.isEmpty()) throw missing("Không tìm thấy mức giá.");
        return found.get(0);
    }
    private RoomTypeView roomType(UUID id) {
        List<RoomTypeView> found=db.query("SELECT id,code,name,mode,capacity,description,active FROM gcmb.room_type WHERE id=?",(rs,n)->new RoomTypeView(rs.getObject("id",UUID.class),rs.getString("code"),rs.getString("name"),rs.getString("mode"),rs.getInt("capacity"),rs.getString("description"),rs.getBoolean("active")),id);
        if(found.isEmpty()) throw missing("Không tìm thấy hạng phòng.");
        return found.get(0);
    }
    private static PriceCatalogItem priceItem(UUID id,String kind,String code,UUID roomId,String roomName,String name,String description,Integer duration,Integer from,Integer to,BigDecimal price,boolean active) {
        return new PriceCatalogItem(id,kind,code,roomId,roomName,name,description,duration,from,to,price,active);
    }
    private static void validateRoomType(RoomTypeCommand c) {
        if(c==null||c.code()==null||!ROOM_CODE.matcher(c.code().trim().toUpperCase()).matches()||c.name()==null||c.name().isBlank()
            ||c.capacity()==null||c.capacity()<1||c.serviceType()==null||!List.of("CINEMA","MUSIC_BOX").contains(c.serviceType()))
            throw bad("Vui lòng nhập mã, tên, loại dịch vụ và sức chứa hợp lệ.");
    }
    private static void validatePrice(String kind,PriceCommand c) {
        if(!List.of("HOURLY","COMBO").contains(kind)||c==null||c.price()==null||c.price().signum()<0) throw bad("Mức giá không hợp lệ.");
        if("COMBO".equals(kind)&&(c.code()==null||!COMBO_CODE.matcher(c.code().trim().toUpperCase()).matches()||c.name()==null||c.name().isBlank()||c.durationMinutes()==null||c.durationMinutes()<1||c.minuteFrom()==null||c.minuteFrom()<0||c.minuteFrom()>1439||c.minuteTo()==null||c.minuteTo()<1||c.minuteTo()>1440||c.minuteFrom().equals(c.minuteTo()))) throw bad("Mã combo chỉ gồm chữ in hoa, số, dấu gạch ngang hoặc gạch dưới (2–60 ký tự); tên, thời lượng và khung giờ phải hợp lệ.");
    }
    private static ResponseStatusException bad(String message){return new ResponseStatusException(HttpStatus.BAD_REQUEST,message);}
    private static ResponseStatusException missing(String message){return new ResponseStatusException(HttpStatus.NOT_FOUND,message);}
}
