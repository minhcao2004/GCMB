package com.gcmb.backend.pricing.repository;

import com.gcmb.backend.pricing.dto.PriceCatalogItem;
import com.gcmb.backend.pricing.dto.PriceRuleView;
import com.gcmb.backend.pricing.dto.RoomTypeView;
import java.math.BigDecimal;
import java.sql.Array;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
// Hạng phòng, quy tắc giá theo khung giờ và combo dùng chung toàn hệ thống (branch_id IS NULL).
public class RoomCatalogRepository {

    private static final String PRICE_RULE_COLUMNS = """
        SELECT p.id,p.code,p.version,rt.id AS room_type_id,rt.name AS room_type_name,p.name,p.weekdays,
               p.valid_from,(p.valid_to - 1) AS valid_to,p.minute_from,p.minute_to,p.hourly_price,p.active
        FROM gcmb.price_rule p JOIN gcmb.room_type rt ON rt.id=p.room_type_id
        """;

    private static final RowMapper<RoomTypeView> ROOM_TYPE_MAPPER = (resultSet, rowNumber) -> new RoomTypeView(
        resultSet.getObject("id", UUID.class), resultSet.getString("code"), resultSet.getString("name"),
        resultSet.getString("mode"), resultSet.getInt("capacity"), resultSet.getString("description"),
        resultSet.getBoolean("active"));

    private static final RowMapper<PriceRuleView> PRICE_RULE_MAPPER = (resultSet, rowNumber) -> new PriceRuleView(
        resultSet.getObject("id", UUID.class), resultSet.getString("code"), resultSet.getInt("version"),
        resultSet.getObject("room_type_id", UUID.class), resultSet.getString("room_type_name"),
        resultSet.getString("name"), weekdays(resultSet.getArray("weekdays")),
        resultSet.getObject("valid_from", LocalDate.class), resultSet.getObject("valid_to", LocalDate.class),
        resultSet.getInt("minute_from"), resultSet.getInt("minute_to"), resultSet.getBigDecimal("hourly_price"),
        resultSet.getBoolean("active"));

    private static final RowMapper<PriceCatalogItem> PRICE_ITEM_MAPPER = (resultSet, rowNumber) -> new PriceCatalogItem(
        resultSet.getObject("id", UUID.class), resultSet.getString("kind"), resultSet.getString("code"),
        resultSet.getObject("room_type_id", UUID.class), resultSet.getString("room_type_name"),
        resultSet.getString("name"), resultSet.getString("description"),
        (Integer) resultSet.getObject("duration_minutes"), (Integer) resultSet.getObject("minute_from"),
        (Integer) resultSet.getObject("minute_to"), resultSet.getBigDecimal("price"), resultSet.getBoolean("active"),
        resultSet.getObject("branch_id", UUID.class), (Integer) resultSet.getObject("included_fnb_quantity"),
        (Integer) resultSet.getObject("included_food_quantity"));

    private final JdbcTemplate jdbcTemplate;

    public RoomCatalogRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // ---- Hạng phòng ----

    public List<RoomTypeView> findRoomTypes() {
        return jdbcTemplate.query("""
            SELECT id, code, name, mode, capacity, description, active
            FROM gcmb.room_type ORDER BY code
            """, ROOM_TYPE_MAPPER);
    }

    public List<RoomTypeView> findRoomType(UUID id) {
        return jdbcTemplate.query("SELECT id,code,name,mode,capacity,description,active FROM gcmb.room_type WHERE id=?",
            ROOM_TYPE_MAPPER, id);
    }

    public boolean roomTypeCodeExists(String code) {
        Long duplicate = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM gcmb.room_type WHERE upper(code)=?", Long.class, code);
        return duplicate != null && duplicate > 0;
    }

    public void insertRoomType(UUID id, String code, String name, String serviceType, Integer capacity, String description) {
        jdbcTemplate.update("""
            INSERT INTO gcmb.room_type(id, code, name, mode, capacity, description, active)
            VALUES (?, ?, ?, ?, ?, ?, true)
            """, id, code, name, serviceType, capacity, description);
    }

    public int updateRoomType(UUID id, String name, String serviceType, Integer capacity, String description) {
        return jdbcTemplate.update("""
            UPDATE gcmb.room_type SET name=?, mode=?, capacity=?, description=? WHERE id=?
            """, name, serviceType, capacity, description, id);
    }

    public int deactivateRoomType(UUID id) {
        return jdbcTemplate.update("UPDATE gcmb.room_type SET active=false WHERE id=?", id);
    }

    public boolean lockRoomType(UUID id) {
        return !jdbcTemplate.queryForList("SELECT id FROM gcmb.room_type WHERE id=? FOR UPDATE", UUID.class, id).isEmpty();
    }

    public boolean roomTypeInUse(UUID id) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject("""
            SELECT EXISTS(SELECT 1 FROM gcmb.room WHERE room_type_id=?)
                OR EXISTS(SELECT 1 FROM gcmb.price_rule WHERE room_type_id=?)
                OR EXISTS(SELECT 1 FROM gcmb.combo WHERE room_type_id=?)
            """, Boolean.class, id, id, id));
    }

    public void deleteRoomType(UUID id) {
        jdbcTemplate.update("DELETE FROM gcmb.room_type WHERE id=?", id);
    }

    // ---- Quy tắc giá ----

    public List<PriceRuleView> findPriceRules(UUID roomTypeId, boolean activeOnly) {
        String roomFilter = roomTypeId == null ? "" : " AND p.room_type_id=?";
        String activeFilter = activeOnly ? " AND p.active=true" : "";
        String sql = PRICE_RULE_COLUMNS + " WHERE p.branch_id IS NULL" + roomFilter + activeFilter
            + " ORDER BY rt.name,p.code,p.version DESC";
        return roomTypeId == null
            ? jdbcTemplate.query(sql, PRICE_RULE_MAPPER)
            : jdbcTemplate.query(sql, PRICE_RULE_MAPPER, roomTypeId);
    }

    public List<PriceRuleView> findPriceRule(UUID id) {
        return jdbcTemplate.query(PRICE_RULE_COLUMNS + " WHERE p.id=? AND p.branch_id IS NULL", PRICE_RULE_MAPPER, id);
    }

    public void lockPriceRuleChanges() {
        jdbcTemplate.execute("LOCK TABLE gcmb.price_rule IN SHARE ROW EXCLUSIVE MODE");
    }

    public String overlappingPriceRuleName(UUID roomTypeId, UUID replacedId, LocalDate validFrom,
            LocalDate validTo, int minuteFrom, int minuteTo, List<Integer> weekdays) {
        String days = weekdays.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(","));
        List<String> names = jdbcTemplate.queryForList("""
            WITH requested AS (
              SELECT ?::uuid AS room_type_id, ?::uuid AS replaced_id,
                     COALESCE(?::date, CURRENT_DATE) AS valid_from, ?::date AS valid_until,
                     ?::integer AS minute_from, ?::integer AS minute_to,
                     string_to_array(?, ',')::smallint[] AS weekdays
            )
            SELECT p.name
            FROM gcmb.price_rule p CROSS JOIN requested r
            WHERE p.active AND p.branch_id IS NULL AND p.room_type_id = r.room_type_id
              AND p.id IS DISTINCT FROM r.replaced_id
              AND p.minute_from < r.minute_to AND r.minute_from < p.minute_to
              AND p.weekdays && r.weekdays
              AND EXISTS (
                SELECT 1
                FROM generate_series(0, 6) AS offsets(days)
                CROSS JOIN LATERAL (
                  SELECT CASE WHEN p.day_kind = 'SPECIFIC' THEN p.specific_date
                              ELSE GREATEST(p.valid_from, r.valid_from) + offsets.days END AS active_on
                ) d
                WHERE d.active_on >= GREATEST(p.valid_from, r.valid_from)
                  AND (p.valid_to IS NULL OR d.active_on < p.valid_to)
                  AND (r.valid_until IS NULL OR d.active_on < r.valid_until)
                  AND EXTRACT(ISODOW FROM d.active_on)::smallint = ANY(p.weekdays)
                  AND EXTRACT(ISODOW FROM d.active_on)::smallint = ANY(r.weekdays)
                  AND CASE p.day_kind
                        WHEN 'ALL' THEN true
                        WHEN 'WEEKDAY' THEN EXTRACT(ISODOW FROM d.active_on) <= 5
                        WHEN 'WEEKEND' THEN EXTRACT(ISODOW FROM d.active_on) >= 6
                        WHEN 'SPECIFIC' THEN true
                        ELSE false
                      END
              )
            ORDER BY p.code, p.version DESC
            LIMIT 1
            """, String.class, roomTypeId, replacedId, validFrom, exclusiveEnd(validTo), minuteFrom, minuteTo, days);
        return names.isEmpty() ? null : names.get(0);
    }

    // Ngày kết thúc trên form có hiệu lực trọn ngày. Database dùng biên phải loại trừ.
    private static LocalDate exclusiveEnd(LocalDate validTo) {
        return validTo == null ? null : validTo.plusDays(1);
    }

    public boolean activePriceRuleCodeExists(String code) {
        Long exists = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM gcmb.price_rule WHERE code=? AND active=true", Long.class, code);
        return exists != null && exists > 0;
    }

    public int nextPriceRuleVersion(String code) {
        Long version = jdbcTemplate.queryForObject(
            "SELECT COALESCE(MAX(version),0)+1 FROM gcmb.price_rule WHERE code=?", Long.class, code);
        return version == null ? 1 : version.intValue();
    }

    public void deactivatePriceRule(UUID id) {
        jdbcTemplate.update("UPDATE gcmb.price_rule SET active=false WHERE id=?", id);
    }

    public int deleteSharedPriceRule(UUID id) {
        return jdbcTemplate.update("DELETE FROM gcmb.price_rule WHERE id=? AND branch_id IS NULL", id);
    }

    public void insertPriceRule(UUID id, String code, int version, UUID roomTypeId, String name, LocalDate validFrom,
            LocalDate validTo, int minuteFrom, int minuteTo, List<Integer> weekdays, BigDecimal hourlyPrice) {
        // JdbcTemplate exposes the connection through a callback so PostgreSQL receives a smallint[] value.
        jdbcTemplate.execute((ConnectionCallback<Void>) connection -> {
            Short[] dayValues = weekdays.stream().map(Integer::shortValue).toArray(Short[]::new);
            Array weekdayArray = connection.createArrayOf("smallint", dayValues);
            try (var statement = connection.prepareStatement("""
                INSERT INTO gcmb.price_rule(id,code,version,room_type_id,branch_id,name,valid_from,valid_to,day_kind,
                  specific_date,minute_from,minute_to,weekdays,hourly_price,overtime_hourly_price,billing_step_minutes,priority,active)
                VALUES(?,?,?,?,NULL,?,?,?,'ALL',NULL,?,?,?, ?, ?,60,100,true)
                """)) {
                statement.setObject(1, id);
                statement.setString(2, code);
                statement.setInt(3, version);
                statement.setObject(4, roomTypeId);
                statement.setString(5, name);
                statement.setObject(6, validFrom);
                statement.setObject(7, exclusiveEnd(validTo));
                statement.setInt(8, minuteFrom);
                statement.setInt(9, minuteTo);
                statement.setArray(10, weekdayArray);
                statement.setBigDecimal(11, hourlyPrice);
                statement.setBigDecimal(12, hourlyPrice);
                statement.executeUpdate();
            } finally {
                weekdayArray.free();
            }
            return null;
        });
    }

    // ---- Bảng giá giờ lẻ và combo ----

    public List<PriceCatalogItem> findPrices(String kind, UUID roomTypeId, boolean activeOnly) {
        String activeFilter = activeOnly ? " AND active=true" : "";
        String roomFilter = roomTypeId == null ? "" : " AND room_type_id=?";
        String sql = """
            SELECT * FROM (
              SELECT p.id,'HOURLY' AS kind,'HOURLY_'||rt.code AS code,rt.id AS room_type_id,rt.name AS room_type_name,
                NULL::uuid AS branch_id,
                p.name,'' AS description,NULL::integer AS duration_minutes,p.minute_from,p.minute_to,p.hourly_price AS price,p.active,NULL::integer AS included_fnb_quantity,NULL::integer AS included_food_quantity
              FROM gcmb.price_rule p JOIN gcmb.room_type rt ON rt.id=p.room_type_id
              WHERE p.branch_id IS NULL AND p.day_kind='ALL' AND p.minute_from=0 AND p.minute_to=1440
              UNION ALL
              SELECT c.id,'COMBO' AS kind,c.code,rt.id,rt.name,c.branch_id,c.name,c.included_description,c.duration_minutes,
                c.minute_from,c.minute_to,c.price,c.active,c.included_fnb_quantity,c.included_food_quantity
              FROM gcmb.combo c JOIN gcmb.room_type rt ON rt.id=c.room_type_id
            ) catalogue WHERE (CAST(? AS varchar) IS NULL OR kind=?)""" + activeFilter + roomFilter
            + " ORDER BY room_type_name,kind,code";
        return roomTypeId == null
            ? jdbcTemplate.query(sql, PRICE_ITEM_MAPPER, kind, kind)
            : jdbcTemplate.query(sql, PRICE_ITEM_MAPPER, kind, kind, roomTypeId);
    }

    public List<PriceCatalogItem> findPrice(UUID id, String kind) {
        return jdbcTemplate.query("""
            SELECT * FROM (
              SELECT p.id,'HOURLY' AS kind,'HOURLY_'||rt.code AS code,rt.id AS room_type_id,rt.name AS room_type_name,p.name,
                '' AS description,NULL::integer AS duration_minutes,p.minute_from,p.minute_to,p.hourly_price AS price,p.active,NULL::uuid AS branch_id,NULL::integer AS included_fnb_quantity,NULL::integer AS included_food_quantity
              FROM gcmb.price_rule p JOIN gcmb.room_type rt ON rt.id=p.room_type_id WHERE p.id=? AND p.branch_id IS NULL
              UNION ALL
              SELECT c.id,'COMBO',c.code,rt.id,rt.name,c.name,c.included_description,c.duration_minutes,c.minute_from,c.minute_to,c.price,c.active,c.branch_id,c.included_fnb_quantity,c.included_food_quantity
              FROM gcmb.combo c JOIN gcmb.room_type rt ON rt.id=c.room_type_id WHERE c.id=?
            ) entries WHERE kind=?
            """, PRICE_ITEM_MAPPER, id, id, kind);
    }

    public int countActiveHourlyPrices(UUID roomTypeId, String name) {
        Integer active = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM gcmb.price_rule WHERE room_type_id=? AND name=? AND branch_id IS NULL AND active=true",
            Integer.class, roomTypeId, name);
        return active == null ? 0 : active;
    }

    public void insertHourlyPrice(UUID id, UUID roomTypeId, String name, BigDecimal price) {
        jdbcTemplate.update("""
            INSERT INTO gcmb.price_rule(id,room_type_id,branch_id,name,code,valid_from,day_kind,minute_from,minute_to,
              hourly_price,overtime_hourly_price,billing_step_minutes,priority,active)
            VALUES(?,?,NULL,?,?,CURRENT_DATE,'ALL',0,1440,?,?,60,100,true)
            """, id, roomTypeId, name, "HOURLY-" + id.toString().toUpperCase(java.util.Locale.ROOT), price, price);
    }

    public int countActiveCombos(String code) {
        Integer active = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM gcmb.combo WHERE code=? AND active=true", Integer.class, code);
        return active == null ? 0 : active;
    }

    public Long nextComboVersion(String code) {
        return jdbcTemplate.queryForObject("SELECT COALESCE(MAX(version),0)+1 FROM gcmb.combo WHERE code=?", Long.class, code);
    }

    public void insertCombo(UUID id, String code, Long version, UUID roomTypeId, String serviceType, String name,
            Integer durationMinutes, BigDecimal price, int minuteFrom, int minuteTo, int includedFnbQuantity,
            int includedFoodQuantity, String description) {
        jdbcTemplate.update("""
            INSERT INTO gcmb.combo(id,code,version,room_type_id,mode,name,duration_minutes,price,deposit_required,
              overtime_hourly_price,valid_from,active,branch_id,day_group,minute_from,minute_to,min_guests,included_fnb_quantity,included_food_quantity,
              included_description,priority)
            VALUES(?,?,?,?,?,?,?,?,0,0,CURRENT_TIMESTAMP,true,NULL,'ALL',?,?,1,?,?,?,100)
            """, id, code, version, roomTypeId, serviceType, name, durationMinutes, price, minuteFrom, minuteTo,
            includedFnbQuantity, includedFoodQuantity, description);
    }

    public void retireCombo(UUID id) {
        jdbcTemplate.update("UPDATE gcmb.combo SET active=false,valid_to=GREATEST(CURRENT_TIMESTAMP+interval '1 microsecond',valid_from+interval '1 microsecond') WHERE id=?", id);
    }

    public int deleteCombo(UUID id) {
        jdbcTemplate.update("DELETE FROM gcmb.combo_item ci WHERE ci.combo_id=? AND NOT EXISTS (SELECT 1 FROM gcmb.booking_combo_item bci WHERE bci.combo_item_id=ci.id)", id);
        return jdbcTemplate.update("DELETE FROM gcmb.combo WHERE id=?", id);
    }

    private static List<Integer> weekdays(Array weekdays) throws SQLException {
        Object raw = weekdays.getArray();
        List<Integer> days = new ArrayList<>();
        if (raw instanceof Object[] values) for (Object value : values) days.add(((Number) value).intValue());
        return days;
    }
}
