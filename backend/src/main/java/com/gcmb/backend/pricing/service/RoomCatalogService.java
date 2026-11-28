/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: TrongNV
 * Created: 12/10/2026
 * Updated: HuyDV - 14/10/2026 (UC-14 đến UC-18).
 * Description: Nghiệp vụ quản lý hạng phòng, giá theo thời gian và combo.
 */

package com.gcmb.backend.pricing.service;

import com.gcmb.backend.pricing.dto.PriceCatalogItem;
import com.gcmb.backend.pricing.dto.PriceCommand;
import com.gcmb.backend.pricing.dto.PriceRuleCommand;
import com.gcmb.backend.pricing.dto.PriceRuleView;
import com.gcmb.backend.pricing.dto.RoomTypeCommand;
import com.gcmb.backend.pricing.dto.RoomTypeView;
import com.gcmb.backend.pricing.repository.RoomCatalogRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class RoomCatalogService {
    private static final Pattern ROOM_CODE = Pattern.compile("[A-Z0-9_-]{2,60}");
    private static final Pattern COMBO_CODE = Pattern.compile("[A-Z0-9_-]{2,60}");
    private static final Pattern PRICE_RULE_CODE = Pattern.compile("[A-Z0-9_-]{2,60}");
    private static final String DELETE_BLOCKED_MESSAGE =
        "Không thể thực hiện cách xóa này vì sẽ làm mất hoặc thay đổi dữ liệu liên quan. Lịch sử nghiệp vụ và các bản ghi liên quan phải được giữ nguyên.";
    private final RoomCatalogRepository roomCatalogRepository;

    public RoomCatalogService(RoomCatalogRepository roomCatalogRepository) {
        this.roomCatalogRepository = roomCatalogRepository;
    }

    public List<RoomTypeView> roomTypes() {
        return roomCatalogRepository.findRoomTypes();
    }

    @Transactional
    // BR-174: kiểm tra mã hạng phòng duy nhất trên toàn hệ thống trước khi tạo.
    public RoomTypeView createRoomType(RoomTypeCommand command) {
        validateRoomType(command);
        String code = command.code().trim().toUpperCase(java.util.Locale.ROOT);
        if (roomCatalogRepository.roomTypeCodeExists(code)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Mã hạng phòng “" + code + "” đã tồn tại trong hệ thống. Vui lòng nhập mã khác.");
        }
        UUID id = UUID.randomUUID();
        try {
            roomCatalogRepository.insertRoomType(id, code, command.name().trim(), command.serviceType(),
                command.capacity(), command.description() == null ? "" : command.description().trim());
        } catch (DataIntegrityViolationException duplicateCode) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Mã hạng phòng “" + code + "” đã tồn tại trong hệ thống. Vui lòng nhập mã khác.");
        }
        return roomType(id);
    }

    @Transactional
    // BR-174: không cho đổi mã hạng phòng sang một mã đã được dùng.
    public RoomTypeView updateRoomType(UUID id, RoomTypeCommand command) {
        validateRoomType(command);
        // Mã định danh giữ nguyên khi sửa; không thay đổi giá đã xác nhận của booking.
        RoomTypeView current = roomType(id);
        if (!current.code().equals(command.code().trim().toUpperCase(java.util.Locale.ROOT))) {
            throw bad("Không được thay đổi mã hạng phòng khi chỉnh sửa.");
        }
        int updated = roomCatalogRepository.updateRoomType(id, command.name().trim(), command.serviceType(),
            command.capacity(), command.description() == null ? "" : command.description().trim());
        if (updated == 0) {
            throw missing("Không tìm thấy hạng phòng.");
        }
        return roomType(id);
    }

    // Chỉ ngừng chọn cho phòng mới, giữ nguyên các tham chiếu lịch sử.
    @Transactional
    public RoomTypeView deactivateRoomType(UUID id) {
        if (roomCatalogRepository.deactivateRoomType(id) == 0) {
            throw missing("Không tìm thấy hạng phòng.");
        }
        return roomType(id);
    }

    @Transactional
    // BR-118: không xóa hạng phòng đang được lịch sử booking, giá hoặc combo tham chiếu.
    public void deleteRoomType(UUID id) {
        // Khóa bản ghi trước khi kiểm tra; FK RESTRICT bảo vệ cả trường hợp ghi đồng thời.
        if (!roomCatalogRepository.lockRoomType(id)) {
            throw missing("Không tìm thấy hạng phòng.");
        }
        if (roomCatalogRepository.roomTypeInUse(id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, DELETE_BLOCKED_MESSAGE);
        }
        try {
            roomCatalogRepository.deleteRoomType(id);
        } catch (DataIntegrityViolationException referenced) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, DELETE_BLOCKED_MESSAGE);
        }
    }

    public List<PriceRuleView> priceRules(UUID roomTypeId, boolean activeOnly) {
        return roomCatalogRepository.findPriceRules(roomTypeId, activeOnly);
    }

    @Transactional
    public PriceRuleView createPriceRule(PriceRuleCommand command) {
        validatePriceRule(command);
        roomCatalogRepository.lockPriceRuleChanges();
        roomType(command.roomTypeId());
        String code = command.code().trim().toUpperCase();
        // BR-175: mã quy tắc giá đang hoạt động không được trùng.
        if (roomCatalogRepository.activePriceRuleCodeExists(code)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Mã quy tắc giá “" + code + "” đã tồn tại trong hệ thống. Vui lòng nhập mã khác.");
        }
        return savePriceRule(code, null, command);
    }

    @Transactional
    public PriceRuleView updatePriceRule(UUID id, PriceRuleCommand command) {
        validatePriceRule(command);
        roomCatalogRepository.lockPriceRuleChanges();
        PriceRuleView previous = priceRule(id);
        if (!previous.code().equalsIgnoreCase(command.code().trim())) throw bad("Không thể đổi mã quy tắc khi đang sửa.");
        roomType(command.roomTypeId());
        return savePriceRule(previous.code(), id, command);
    }

    @Transactional
    public void deletePriceRule(UUID id) {
        try {
            if (roomCatalogRepository.deleteSharedPriceRule(id) == 0) throw missing("Không tìm thấy quy tắc giá.");
        } catch (DataIntegrityViolationException referenced) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Không thể xóa quy tắc giá vì đã được dùng trong hóa đơn.");
        }
    }

    private PriceRuleView savePriceRule(String code, UUID replacedId, PriceRuleCommand command) {
        boolean wholeDay = command.minuteFrom().equals(command.minuteTo());
        int minuteFrom = wholeDay ? 0 : command.minuteFrom();
        int minuteTo = wholeDay ? 1440 : (command.minuteTo() == 0 ? 1440 : command.minuteTo());
        if (minuteTo <= minuteFrom) throw bad("Khung giờ áp dụng không hợp lệ.");
        requireNoPriceOverlap(command.roomTypeId(), replacedId, command.validFrom(), command.validTo(),
            minuteFrom, minuteTo, command.weekdays());
        if (replacedId != null) roomCatalogRepository.deactivatePriceRule(replacedId);
        UUID id = UUID.randomUUID();
        roomCatalogRepository.insertPriceRule(id, code, roomCatalogRepository.nextPriceRuleVersion(code),
            command.roomTypeId(), command.name().trim(),
            command.validFrom(), command.validTo(), minuteFrom, minuteTo, command.weekdays(), command.hourlyPrice());
        return priceRule(id);
    }

    // BR-228: chặn cấu hình giá cùng hạng phòng giao nhau ở ngày, thứ và khung giờ.
    private void requireNoPriceOverlap(UUID roomTypeId, UUID replacedId, LocalDate validFrom, LocalDate validTo,
            int minuteFrom, int minuteTo, List<Integer> weekdays) {
        String conflict = roomCatalogRepository.overlappingPriceRuleName(
            roomTypeId, replacedId, validFrom, validTo, minuteFrom, minuteTo, weekdays);
        if (conflict != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cấu hình giá bị trùng với " + conflict
                + " về hạng phòng, ngày áp dụng và khung giờ. Vui lòng điều chỉnh thời gian áp dụng.");
        }
    }

    private PriceRuleView priceRule(UUID id) {
        List<PriceRuleView> found = roomCatalogRepository.findPriceRule(id);
        if (found.isEmpty()) throw missing("Không tìm thấy quy tắc giá.");
        return found.get(0);
    }

    private static void validatePriceRule(PriceRuleCommand command) {
        if (command == null) throw bad("Vui lòng bổ sung thông tin quy tắc giá.");
        if (command.code() == null || command.code().isBlank()) throw bad("Vui lòng bổ sung Mã quy tắc giá.");
        if (!PRICE_RULE_CODE.matcher(command.code().trim().toUpperCase(java.util.Locale.ROOT)).matches()) throw bad("Mã quy tắc giá phải có từ 2 đến 60 ký tự và chỉ được dùng chữ cái không dấu, chữ số, dấu gạch dưới (_) hoặc dấu gạch nối (-).");
        if (command.name() == null || command.name().isBlank()) throw bad("Vui lòng bổ sung Tên quy tắc giá.");
        if (command.name().trim().length() > 200) throw bad("Tên quy tắc giá chỉ được chứa tối đa 200 ký tự.");
        if (command.roomTypeId() == null) throw bad("Vui lòng bổ sung Hạng áp dụng.");
        // BR-147: quy tắc theo giờ phải có ít nhất một thứ áp dụng.
        if (command.weekdays() == null || command.weekdays().isEmpty()) throw bad("Vui lòng chọn ít nhất một ngày trong tuần áp dụng mức giá này.");
        if (command.weekdays().stream().anyMatch(day -> day == null || day < 1 || day > 7)) throw bad("Ngày áp dụng không hợp lệ.");
        if (command.validFrom() == null) throw bad("Vui lòng bổ sung Ngày bắt đầu.");
        // BR-146: ngày kết thúc hiệu lực, nếu có, không trước ngày bắt đầu.
        if (command.validTo() != null && command.validTo().isBefore(command.validFrom())) throw bad("Ngày kết thúc hiệu lực phải bằng hoặc sau ngày bắt đầu hiệu lực.");
        if (command.minuteFrom() == null) throw bad("Vui lòng bổ sung Bắt đầu.");
        if (command.minuteTo() == null) throw bad("Vui lòng bổ sung Kết thúc.");
        if (command.minuteFrom() < 0 || command.minuteFrom() > 1439 || command.minuteTo() < 0 || command.minuteTo() > 1440) throw bad("Khung giờ áp dụng không hợp lệ.");
        if (command.hourlyPrice() == null) throw bad("Vui lòng bổ sung Đơn giá.");
        if (command.hourlyPrice().signum() < 0) throw bad("Đơn giá phải lớn hơn hoặc bằng 0.");
        if (!isWholeVnd(command.hourlyPrice())) throw bad("Đơn giá phải là số tiền nguyên đồng, không có phần thập phân.");
    }

    public List<PriceCatalogItem> prices(String kind, UUID roomTypeId, boolean activeOnly) {
        return roomCatalogRepository.findPrices(kind, roomTypeId, activeOnly);
    }

    @Transactional
    public PriceCatalogItem createPrice(String kind, UUID roomTypeId, PriceCommand command) {
        validatePrice(kind, command);
        if ("COMBO".equals(kind) && (roomTypeId == null || command.roomTypeId() == null)) throw bad("Vui lòng bổ sung Hạng áp dụng.");
        if ("COMBO".equals(kind) && !roomTypeId.equals(command.roomTypeId())) throw bad("Hạng phòng và combo không khớp.");
        RoomTypeView room = roomType(roomTypeId);
        if ("HOURLY".equals(kind)) {
            roomCatalogRepository.lockPriceRuleChanges();
            String name = room.name() + " · Giờ lẻ";
            if (roomCatalogRepository.countActiveHourlyPrices(roomTypeId, name) > 0) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Hạng phòng đã có giá giờ lẻ đang áp dụng.");
            }
            requireNoPriceOverlap(roomTypeId, null, null, null, 0, 1440, List.of(1, 2, 3, 4, 5, 6, 7));
            UUID id = UUID.randomUUID();
            roomCatalogRepository.insertHourlyPrice(id, roomTypeId, name, command.price());
            return price(id, "HOURLY");
        }
        // BR-176: mã combo chỉ được dùng một lần trong các combo đang hoạt động.
        if (roomCatalogRepository.countActiveCombos(command.code()) > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Mã combo “" + command.code().trim().toUpperCase(java.util.Locale.ROOT) + "” đã tồn tại trong danh sách combo đang hoạt động. Vui lòng nhập mã khác.");
        }
        Long version = roomCatalogRepository.nextComboVersion(command.code());
        UUID id = UUID.randomUUID();
        boolean wholeDay = command.minuteFrom().equals(command.minuteTo());
        int minuteFrom = wholeDay ? 0 : command.minuteFrom();
        int minuteTo = wholeDay ? 1440 : (command.minuteTo() == 0 ? 1440 : command.minuteTo());
        roomCatalogRepository.insertCombo(id, command.code().trim().toUpperCase(), version, roomTypeId, room.serviceType(),
            command.name().trim(), command.durationMinutes(), command.price(), minuteFrom, minuteTo,
            command.includedFnbQuantity() == null ? 0 : command.includedFnbQuantity(),
            command.includedFoodQuantity() == null ? 0 : command.includedFoodQuantity(),
            command.description() == null ? "" : command.description().trim());
        return price(id, "COMBO");
    }

    @Transactional
    public PriceCatalogItem updatePrice(String kind, UUID id, PriceCommand command) {
        validatePrice(kind, command);
        if ("HOURLY".equals(kind)) {
            roomCatalogRepository.lockPriceRuleChanges();
            PriceCatalogItem previous = price(id, "HOURLY");
            roomCatalogRepository.deactivatePriceRule(id);
            return createPrice("HOURLY", previous.roomTypeId(), command);
        }
        PriceCatalogItem previous = price(id, "COMBO");
        roomCatalogRepository.retireCombo(id);
        UUID targetRoomTypeId = command.roomTypeId() == null ? previous.roomTypeId() : command.roomTypeId();
        PriceCommand next = new PriceCommand(targetRoomTypeId, previous.code(), command.name(), command.description(),
            command.durationMinutes(), command.minuteFrom(), command.minuteTo(), command.price(),
            command.includedFnbQuantity(), command.includedFoodQuantity());
        return createPrice("COMBO", targetRoomTypeId, next);
    }

    @Transactional
    public void deletePrice(String kind, UUID id) {
        if (!"COMBO".equals(kind)) throw bad("Chỉ hỗ trợ xóa combo tại chức năng này.");
        price(id, kind);
        try {
            if (roomCatalogRepository.deleteCombo(id) == 0) throw missing("Không tìm thấy combo.");
        } catch (DataIntegrityViolationException referenced) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, DELETE_BLOCKED_MESSAGE);
        }
    }

    private PriceCatalogItem price(UUID id, String kind) {
        List<PriceCatalogItem> found = roomCatalogRepository.findPrice(id, kind);
        if (found.isEmpty()) throw missing("Không tìm thấy mức giá.");
        return found.get(0);
    }

    private RoomTypeView roomType(UUID id) {
        List<RoomTypeView> found = roomCatalogRepository.findRoomType(id);
        if (found.isEmpty()) throw missing("Không tìm thấy hạng phòng.");
        return found.get(0);
    }

    // BR-135, BR-138, BR-179: kiểm tra định dạng mã, sức chứa nguyên dương và độ dài tên.
    private static void validateRoomType(RoomTypeCommand command) {
        if (command == null) throw bad("Vui lòng bổ sung Mã hạng phòng.");
        if (command.code() == null || command.code().isBlank()) throw bad("Vui lòng bổ sung Mã hạng phòng.");
        if (!ROOM_CODE.matcher(command.code().trim().toUpperCase(java.util.Locale.ROOT)).matches())
            throw bad("Mã hạng phòng phải có từ 2 đến 60 ký tự và chỉ được dùng chữ cái không dấu, chữ số, dấu gạch dưới (_) hoặc dấu gạch nối (-).");
        if (command.name() == null || command.name().isBlank()) throw bad("Vui lòng bổ sung Tên hạng phòng.");
        if (command.name().trim().length() > 200) throw bad("Tên hạng phòng chỉ được chứa tối đa 200 ký tự.");
        if (command.capacity() == null || command.capacity() < 1)
            throw bad("Sức chứa phải là số nguyên lớn hơn 0.");
        if (command.serviceType() == null || !List.of("CINEMA", "MUSIC_BOX").contains(command.serviceType()))
            throw bad("Loại dịch vụ không hợp lệ.");
    }

    // BR-135, BR-140, BR-143, BR-179: kiểm tra giá không âm, mã combo, tên và thời lượng dương.
    private static void validatePrice(String kind,PriceCommand command) {
        if(!List.of("HOURLY","COMBO").contains(kind)||command==null) throw bad("Vui lòng bổ sung thông tin mức giá.");
        if("COMBO".equals(kind)) {
            if(command.code()==null||command.code().isBlank()) throw bad("Vui lòng bổ sung Mã combo.");
            if(!COMBO_CODE.matcher(command.code().trim().toUpperCase()).matches())
                throw bad("Mã combo phải có từ 2 đến 60 ký tự và chỉ được dùng chữ cái không dấu, chữ số, dấu gạch dưới (_) hoặc dấu gạch nối (-).");
            if(command.name()==null||command.name().isBlank()) throw bad("Vui lòng bổ sung Tên combo.");
            if(command.name().trim().length()>200) throw bad("Tên combo chỉ được chứa tối đa 200 ký tự.");
            if(command.roomTypeId()==null) throw bad("Vui lòng bổ sung Hạng áp dụng.");
            if (command.durationMinutes() == null) throw bad("Vui lòng bổ sung Thời lượng (phút).");
            if (command.durationMinutes() < 1) throw bad("Thời lượng phải lớn hơn 0.");
            if(command.minuteFrom()==null) throw bad("Vui lòng bổ sung Bắt đầu.");
            if(command.minuteTo()==null) throw bad("Vui lòng bổ sung Kết thúc.");
            if(command.minuteFrom()<0||command.minuteFrom()>1439||command.minuteTo()<0||command.minuteTo()>1440)
                throw bad("Khung giờ áp dụng combo không hợp lệ.");
            if(command.includedFnbQuantity()!=null && (command.includedFnbQuantity()<0 || command.includedFnbQuantity()>20))
                throw bad("Số đồ uống tặng kèm phải từ 0 đến 20.");
            if(command.includedFoodQuantity()!=null && (command.includedFoodQuantity()<0 || command.includedFoodQuantity()>20))
                throw bad("Số đồ ăn tặng kèm phải từ 0 đến 20.");
        }
        if(command.price()==null) throw bad("COMBO".equals(kind)?"Vui lòng bổ sung Giá combo.":"Vui lòng bổ sung Đơn giá.");
        if(command.price().signum()<0) throw bad("COMBO".equals(kind)?"Giá combo phải lớn hơn hoặc bằng 0.":"Đơn giá phải lớn hơn hoặc bằng 0.");
        if(!isWholeVnd(command.price())) throw bad("COMBO".equals(kind)?"Giá combo phải là số tiền nguyên đồng, không có phần thập phân.":"Đơn giá phải là số tiền nguyên đồng, không có phần thập phân.");
    }
    private static boolean isWholeVnd(BigDecimal amount) { return amount.stripTrailingZeros().scale() <= 0; }
    private static ResponseStatusException bad(String message){return new ResponseStatusException(HttpStatus.BAD_REQUEST,message);}
    private static ResponseStatusException missing(String message){return new ResponseStatusException(HttpStatus.NOT_FOUND,message);}
}
