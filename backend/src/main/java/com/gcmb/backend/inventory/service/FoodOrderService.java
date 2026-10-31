package com.gcmb.backend.inventory.service;

import com.gcmb.backend.auth.dto.Identity;
import com.gcmb.backend.auth.security.AuthCrypto;
import com.gcmb.backend.inventory.dto.FoodOrderDtos.*;
import com.gcmb.backend.inventory.dto.MenuItemDtos.PublicMenuItemView;
import com.gcmb.backend.inventory.repository.FoodOrderRepository;
import com.gcmb.backend.inventory.repository.FoodOrderRepository.ActiveRoom;
import com.gcmb.backend.inventory.repository.FoodOrderRepository.ComboAllowance;
import com.gcmb.backend.inventory.repository.FoodOrderRepository.ExistingOrder;
import com.gcmb.backend.inventory.repository.FoodOrderRepository.OrderLock;
import com.gcmb.backend.inventory.repository.FoodOrderRepository.PendingLine;
import com.gcmb.backend.inventory.repository.FoodOrderRepository.RoomQrSession;
import com.gcmb.backend.inventory.repository.FoodOrderRepository.SellableItem;
import com.gcmb.backend.inventory.repository.MenuItemRepository;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class FoodOrderService {
    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final Pattern MR_CODE = Pattern.compile("MR-[A-F0-9]{32}");
    private final FoodOrderRepository orders;
    private final MenuItemRepository menuItems;
    private final AuthCrypto crypto;

    public FoodOrderService(FoodOrderRepository orders, MenuItemRepository menuItems, AuthCrypto crypto) {
        this.orders = orders;
        this.menuItems = menuItems;
        this.crypto = crypto;
    }

    @Transactional
    public GuestSession openRoomSession(String mrCode) {
        ActiveRoom active = activeRoom(mrCode);
        String token = crypto.token();
        orders.createSession(active.bookingId(), active.allocationId(), AuthCrypto.hash(token));
        RoomContext context = new RoomContext(active.roomCode(), active.roomName(),
            active.branchName(), Instant.now().plus(Duration.ofHours(12)));
        return new GuestSession(token, context, menuItems.publicList(active.branchId(), null, null));
    }

    @Transactional(readOnly = true)
    public RoomMenu roomMenu(String mrCode, String token) {
        RoomQrSession session = roomSession(mrCode, token);
        return new RoomMenu(context(session), menuItems.publicList(session.branchId(), null, null));
    }

    @Transactional(readOnly = true)
    public List<GuestOrderView> roomOrders(String mrCode, String token) {
        RoomQrSession session = roomSession(mrCode, token);
        return orders.qrOrders(session.id()).stream().map(FoodOrderService::guestView).toList();
    }

    @Transactional
    // BR-27, BR-117: yêu cầu QR gắn với booking đang sử dụng và số lượng món là số nguyên dương.
    public GuestOrderView submitRoomRequest(String mrCode, String token, RoomRequest request) {
        RoomQrSession session = roomSession(mrCode, token);
        if (request == null) throw bad("Vui lòng chọn món cần gửi.");
        LinkedHashMap<UUID, Integer> quantities = quantities(request.items(), false);
        Map<UUID, SellableItem> sellable = requireSellableItems(session.branchId(), quantities.keySet());
        requireAvailableItems(sellable);

        String key = idempotencyKey(request.idempotencyKey());
        UUID orderId = orders.createOrder(session.branchId(), session.bookingId(), session.allocationId(),
            "ROOM_QR", null, session.id(), "PENDING", null, null, key);
        if (orderId == null) {
            ExistingOrder existing = orders.existingByKey(key);
            if (existing == null || !session.branchId().equals(existing.branchId())
                    || !session.bookingId().equals(existing.bookingId())
                    || !session.id().equals(existing.qrSessionId()) || !"ROOM_QR".equals(existing.channel())) {
                throw conflict("Mã gửi món đã được dùng cho yêu cầu khác. Vui lòng thử lại.");
            }
            OrderView prior = orders.qrOrder(existing.id(), session.id());
            if (prior == null) throw conflict("Không tìm thấy yêu cầu món đã gửi.");
            return guestView(prior);
        }

        quantities.forEach((menuItemId, quantity) -> {
            SellableItem item = sellable.get(menuItemId);
            orders.insertLine(orderId, session.branchId(), session.bookingId(), menuItemId, null,
                item.name(), BigDecimal.valueOf(quantity), item.salePrice());
        });
        return requiredQrOrder(orderId, session.id());
    }

    @Transactional(readOnly = true)
    public List<OrderView> list(Identity actor, String status) {
        UUID branchId = branch(actor);
        String normalized = status == null || status.isBlank() || "ALL".equalsIgnoreCase(status) ? null : status.trim().toUpperCase();
        if (normalized != null && !List.of("PENDING", "ACCEPTED", "SERVED", "REJECTED", "CANCELLED").contains(normalized)) {
            throw bad("Trạng thái order không hợp lệ.");
        }
        return orders.orders(branchId, normalized);
    }

    @Transactional(readOnly = true)
    public List<ActiveBooking> activeBookings(Identity actor) {
        return orders.activeBookings(branch(actor));
    }

    @Transactional(readOnly = true)
    public List<ServingEmployee> servingEmployees(Identity actor) {
        return orders.onShiftEmployees(branch(actor));
    }

    @Transactional(readOnly = true)
    public List<PublicMenuItemView> receptionMenu(Identity actor) {
        return menuItems.publicList(branch(actor), null, null);
    }

    @Transactional
    // BR-27, BR-28, BR-29, BR-30, BR-117, BR-123, BR-172: tính phí đơn booking và phân công lễ tân ca hiện tại.
    public OrderView addItems(Identity actor, StaffOrderRequest request) {
        UUID branchId = branch(actor);
        if (request == null || request.bookingId() == null) throw bad("Vui lòng chọn booking đang sử dụng.");
        if (request.assignedEmployeeId() == null) throw bad("Vui lòng chọn lễ tân đang làm ca để phụ trách đơn.");
        LinkedHashMap<UUID, Integer> quantities = quantities(request.items(), false);
        String key = idempotencyKey(request.idempotencyKey());
        ExistingOrder existing = orders.existingByKey(key);
        if (existing != null) return existingStaffOrder(existing, branchId, request.bookingId());

        ActiveRoom booking = orders.lockActiveBooking(request.bookingId(), branchId);
        if (booking == null) throw conflict("Booking không còn lượt sử dụng đang hoạt động tại cơ sở này.");
        requireOnShift(request.assignedEmployeeId(), branchId);
        Map<UUID, SellableItem> sellable = requireSellableItems(branchId, quantities.keySet());
        requireAvailableItems(sellable);
        UUID orderId = orders.createOrder(branchId, booking.bookingId(), booking.allocationId(), "STAFF",
            actor.id(), null, "ACCEPTED", actor.id(), request.assignedEmployeeId(), key);
        if (orderId == null) {
            ExistingOrder raced = orders.existingByKey(key);
            if (raced == null) throw conflict("Đơn vừa được gửi ở phiên khác; vui lòng tải lại.");
            return existingStaffOrder(raced, branchId, request.bookingId());
        }

        quantities.forEach((menuItemId, quantity) -> {
            SellableItem item = sellable.get(menuItemId);
            orders.insertLine(orderId, branchId, booking.bookingId(), menuItemId, null,
                item.name(), BigDecimal.valueOf(quantity), item.salePrice());
        });
        allocateComboInclusions(booking.bookingId(), branchId, orderId);
        deductAutomaticStock(orderId, branchId, actor.id(), "Bán món do lễ tân thêm vào booking");
        return requiredOrder(orderId, branchId);
    }

    @Transactional
    // BR-27, BR-28, BR-29, BR-30, BR-117, BR-123, BR-172: chỉ lượng được xác nhận mới tính tiền và gắn combo.
    public OrderView confirmRequest(Identity actor, UUID orderId, ConfirmRequest request) {
        UUID branchId = branch(actor);
        OrderLock order = lockOrder(orderId, branchId);
        if (order == null) throw notFound("Không tìm thấy yêu cầu món tại cơ sở này.");
        if (!"ROOM_QR".equals(order.channel())) throw conflict("Chỉ xác nhận yêu cầu món gửi từ mã MR.");
        if ("ACCEPTED".equals(order.status())) return requiredOrder(orderId, branchId);
        if (!"PENDING".equals(order.status())) throw conflict("Yêu cầu món đã được xử lý; vui lòng tải lại danh sách.");

        ActiveRoom active = orders.lockActiveBooking(order.bookingId(), branchId);
        if (active == null || !active.allocationId().equals(order.allocationId())) {
            throw conflict("Booking hoặc phòng đã thay đổi; không thể xác nhận yêu cầu cũ.");
        }
        if (request == null || request.assignedEmployeeId() == null) throw bad("Vui lòng chọn lễ tân phụ trách đơn.");
        requireOnShift(request.assignedEmployeeId(), branchId);

        List<PendingLine> pending = orders.pendingLines(orderId);
        if (pending.isEmpty()) throw conflict("Yêu cầu không còn món để xác nhận.");
        LinkedHashMap<UUID, Integer> accepted = quantities(request.items(), true);
        Map<UUID, PendingLine> byMenuItem = pending.stream().collect(Collectors.toMap(
            PendingLine::menuItemId, Function.identity(), (first, ignored) -> first, LinkedHashMap::new));
        for (UUID itemId : accepted.keySet()) {
            if (!byMenuItem.containsKey(itemId)) throw bad("Yêu cầu xác nhận có món không nằm trong order.");
        }
        List<UUID> acceptedIds = new ArrayList<>();
        for (PendingLine line : pending) {
            int quantity = accepted.getOrDefault(line.menuItemId(), 0);
            if (BigDecimal.valueOf(quantity).compareTo(line.quantity()) > 0) {
                throw bad("Số lượng xác nhận vượt quá số lượng khách đã yêu cầu cho " + line.name() + ".");
            }
            if (quantity == 0) {
                orders.deleteLine(line.id());
            } else {
                orders.updateLine(line.id(), null, BigDecimal.valueOf(quantity), line.unitPrice());
                acceptedIds.add(line.menuItemId());
            }
        }
        if (acceptedIds.isEmpty()) throw bad("Chọn ít nhất một món để xác nhận hoặc từ chối toàn bộ yêu cầu.");
        Map<UUID, SellableItem> acceptedItems = requireSellableItems(branchId, new LinkedHashSet<>(acceptedIds));
        requireAvailableItems(acceptedItems);
        if (orders.accept(orderId, branchId, actor.id(), request.assignedEmployeeId()) != 1) {
            throw conflict("Yêu cầu món vừa được xử lý ở phiên khác; vui lòng tải lại.");
        }
        allocateComboInclusions(order.bookingId(), branchId, orderId);
        deductAutomaticStock(orderId, branchId, actor.id(), "Bán món theo yêu cầu QR đã xác nhận");
        return requiredOrder(orderId, branchId);
    }

    @Transactional
    public OrderView rejectRequest(Identity actor, UUID orderId) {
        UUID branchId = branch(actor);
        OrderLock order = lockOrder(orderId, branchId);
        if (order == null) throw notFound("Không tìm thấy yêu cầu món tại cơ sở này.");
        if (!"ROOM_QR".equals(order.channel())) throw conflict("Chỉ từ chối yêu cầu món gửi từ mã MR.");
        if ("REJECTED".equals(order.status())) return requiredOrder(orderId, branchId);
        if (!"PENDING".equals(order.status())) throw conflict("Order đã được xử lý; vui lòng tải lại danh sách.");
        if (orders.reject(orderId, branchId) != 1) throw conflict("Yêu cầu vừa thay đổi; vui lòng tải lại danh sách.");
        return requiredOrder(orderId, branchId);
    }

    @Transactional
    // BR-123: chỉ lễ tân được phân công cho đơn mới cập nhật được tiến độ phục vụ.
    public OrderView updateServiceStatus(Identity actor, UUID orderId, ServiceStatusCommand command) {
        UUID branchId = branch(actor);
        OrderLock order = lockOrder(orderId, branchId);
        if (order == null) throw notFound("Không tìm thấy order tại cơ sở này.");
        if (order.assignedUserId() == null || !order.assignedUserId().equals(actor.id())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "Chỉ lễ tân được phân công phụ trách đơn này mới có thể cập nhật tiến độ phục vụ.");
        }
        if (command == null || !"SERVED".equals(command.status())) throw bad("Chọn trạng thái phục vụ hợp lệ.");
        if ("SERVED".equals(order.status())) return requiredOrder(orderId, branchId);
        if (!"ACCEPTED".equals(order.status())) throw conflict("Chỉ cập nhật tiến độ cho order đã được xác nhận.");
        if (orders.serve(orderId, branchId) != 1) throw conflict("Order vừa thay đổi; vui lòng tải lại danh sách.");
        return requiredOrder(orderId, branchId);
    }

    // BR-30, BR-172: phần món đã nằm trong combo không bị tính lại như món gọi thêm.
    private void allocateComboInclusions(UUID bookingId, UUID branchId, UUID orderId) {
        for (PendingLine line : orders.pendingLines(orderId)) {
            ComboAllowance allowance = orders.comboAllowance(bookingId, line.menuItemId(), orderId);
            if (allowance == null) continue;
            BigDecimal freeRemaining = allowance.includedQuantity().subtract(allowance.usedQuantity()).max(ZERO);
            BigDecimal freeQuantity = line.quantity().min(freeRemaining);
            if (freeQuantity.signum() <= 0) continue;
            BigDecimal paidQuantity = line.quantity().subtract(freeQuantity);
            orders.updateLine(line.id(), allowance.id(), freeQuantity, ZERO);
            if (paidQuantity.signum() > 0) {
                orders.insertLine(orderId, branchId, bookingId, line.menuItemId(), null,
                    line.name(), paidQuantity, line.unitPrice());
            }
        }
    }

    // BR-36, BR-37, BR-38; MSG18: trừ đúng một lần khi xác nhận bán và báo tên, số tồn, đơn vị khi thiếu hàng.
    private void deductAutomaticStock(UUID orderId, UUID branchId, UUID actorId, String reason) {
        List<PendingLine> lines = orders.pendingLines(orderId);
        LinkedHashSet<UUID> itemIds = lines.stream().map(PendingLine::menuItemId)
            .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<UUID, SellableItem> items = requireSellableItems(branchId, itemIds);
        Map<UUID, BigDecimal> requiredByInventory = new LinkedHashMap<>();
        Map<UUID, String> nameByInventory = new HashMap<>();
        Map<UUID, String> unitByInventory = new HashMap<>();
        for (PendingLine line : lines) {
            SellableItem item = items.get(line.menuItemId());
            if (item.inventoryItemId() == null || !"AUTO_ON_SALE".equals(item.trackingMode())) continue;
            requiredByInventory.merge(item.inventoryItemId(), line.quantity(), BigDecimal::add);
            nameByInventory.putIfAbsent(item.inventoryItemId(), item.name());
            unitByInventory.putIfAbsent(item.inventoryItemId(), item.unitName());
        }

        List<UUID> inventoryIds = requiredByInventory.keySet().stream()
            .sorted(Comparator.comparing(UUID::toString)).toList();
        Map<UUID, BigDecimal> remaining = new LinkedHashMap<>();
        for (UUID inventoryId : inventoryIds) {
            BigDecimal available = orders.lockStock(inventoryId);
            BigDecimal required = requiredByInventory.get(inventoryId);
            if (available.compareTo(required) < 0) {
                throw conflict(stockShortageMessage(nameByInventory.get(inventoryId), available,
                    unitByInventory.get(inventoryId)));
            }
            remaining.put(inventoryId, available);
        }

        for (PendingLine line : lines) {
            SellableItem item = items.get(line.menuItemId());
            UUID inventoryId = item.inventoryItemId();
            if (inventoryId == null || !"AUTO_ON_SALE".equals(item.trackingMode())) continue;
            BigDecimal before = remaining.get(inventoryId);
            orders.insertSale(inventoryId, line.id(), before, line.quantity(), actorId, reason);
            remaining.put(inventoryId, before.subtract(line.quantity()));
        }
    }

    private Map<UUID, SellableItem> requireSellableItems(UUID branchId, java.util.Collection<UUID> ids) {
        List<UUID> uniqueIds = ids.stream().distinct().sorted(Comparator.comparing(UUID::toString)).toList();
        List<SellableItem> found = orders.sellableItems(branchId, uniqueIds);
        Map<UUID, SellableItem> result = found.stream().collect(Collectors.toMap(SellableItem::id,
            Function.identity(), (first, ignored) -> first, LinkedHashMap::new));
        if (result.size() != uniqueIds.size()) {
            throw conflict("Một hoặc nhiều món đã ngừng bán hoặc không thuộc cơ sở của booking.");
        }
        return result;
    }

    // BR-38; MSG18: số lượng tồn bằng không cũng dùng mẫu báo thiếu hàng có đơn vị tính.
    private void requireAvailableItems(Map<UUID, SellableItem> items) {
        for (SellableItem item : items.values()) {
            if ("PACKAGED".equals(item.kind())
                    && (item.stockQuantity() == null || item.stockQuantity().signum() <= 0)) {
                BigDecimal available = item.stockQuantity() == null ? ZERO : item.stockQuantity();
                throw conflict(stockShortageMessage(item.name(), available, item.unitName()));
            }
        }
    }

    private static String stockShortageMessage(String itemName, BigDecimal available, String unitName) {
        return itemName + " chỉ còn " + available.stripTrailingZeros().toPlainString() + " " + unitName
            + ". Vui lòng giảm số lượng hoặc chọn món khác.";
    }

    private static LinkedHashMap<UUID, Integer> quantities(List<ItemRequest> items, boolean allowZero) {
        if (items == null || items.isEmpty()) throw bad("Chọn ít nhất một món.");
        LinkedHashMap<UUID, Integer> result = new LinkedHashMap<>();
        for (ItemRequest item : items) {
            if (item == null || item.menuItemId() == null || item.quantity() == null
                    || item.quantity() < (allowZero ? 0 : 1)) {
                throw bad("Số lượng phải là số nguyên lớn hơn 0.");
            }
            if (result.putIfAbsent(item.menuItemId(), item.quantity()) != null) {
                throw bad("Mỗi món chỉ được xuất hiện một lần trong cùng yêu cầu.");
            }
        }
        return result;
    }

    private void requireOnShift(UUID employeeId, UUID branchId) {
        if (!orders.eligibleOnShift(employeeId, branchId)) {
            throw bad("Vui lòng chọn Lễ tân đang làm ca hiện tại tại cơ sở của booking để phụ trách đơn.");
        }
    }

    private RoomQrSession roomSession(String mrCode, String token) {
        mrCode = validateMrCode(mrCode);
        if (token == null || token.isBlank()) throw unauthorizedRoomSession();
        RoomQrSession session = orders.validSession(mrCode, AuthCrypto.hash(token.trim()));
        if (session == null) throw unauthorizedRoomSession();
        return session;
    }

    private ActiveRoom activeRoom(String mrCode) {
        String normalizedCode = validateMrCode(mrCode);
        ActiveRoom active;
        try {
            active = orders.activeRoomByMrCode(normalizedCode);
        } catch (IllegalStateException ambiguousRoom) {
            throw conflict("Mã MR không xác định được một phòng duy nhất.");
        }
        if (active == null) throw conflict("Phòng này hiện chưa có lượt sử dụng đang hoạt động. Vui lòng gọi lễ tân.");
        return active;
    }

    private static String validateMrCode(String mrCode) {
        if (mrCode == null || !MR_CODE.matcher(mrCode.trim().toUpperCase(java.util.Locale.ROOT)).matches()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Mã MR không hợp lệ.");
        }
        return mrCode.trim().toUpperCase(java.util.Locale.ROOT);
    }

    private UUID branch(Identity actor) {
        if (actor == null || !"RECEPTIONIST".equals(actor.role()) || actor.branchId() == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Tài khoản chưa được phân công tại cơ sở đang làm việc.");
        }
        return actor.branchId();
    }

    private OrderLock lockOrder(UUID orderId, UUID branchId) {
        OrderLock order = orders.lockOrder(orderId, branchId);
        return order;
    }

    private OrderView existingStaffOrder(ExistingOrder existing, UUID branchId, UUID bookingId) {
        if (!branchId.equals(existing.branchId()) || !bookingId.equals(existing.bookingId()) || !"STAFF".equals(existing.channel())) {
            throw conflict("Mã gửi món đã được dùng cho một booking khác.");
        }
        return requiredOrder(existing.id(), branchId);
    }

    private OrderView requiredOrder(UUID orderId, UUID branchId) {
        OrderView view = orders.order(orderId, branchId);
        if (view == null) throw notFound("Không tìm thấy order sau khi cập nhật.");
        return view;
    }

    private GuestOrderView requiredQrOrder(UUID orderId, UUID sessionId) {
        OrderView view = orders.qrOrder(orderId, sessionId);
        if (view == null) throw notFound("Không tìm thấy yêu cầu món đã gửi.");
        return guestView(view);
    }

    private static GuestOrderView guestView(OrderView order) {
        return new GuestOrderView(order.code(), order.roomCode(), order.status(), order.createdAt(), order.total(),
            order.items().stream().map(item -> new GuestOrderLine(item.name(), item.quantity(), item.unitPrice(),
                item.amount(), item.includedInCombo())).toList());
    }

    private RoomContext context(RoomQrSession session) {
        return new RoomContext(session.roomCode(), session.roomName(), session.branchName(), session.expiresAt());
    }

    private static String idempotencyKey(String key) {
        String value = key == null || key.isBlank() ? UUID.randomUUID().toString() : key.trim();
        if (value.length() > 100) throw bad("Mã gửi món không hợp lệ.");
        return value;
    }

    private static ResponseStatusException unauthorizedRoomSession() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED,
            "Phiên gọi món đã hết hạn hoặc phòng đã đổi lượt. Hãy quét lại mã MR trong phòng.");
    }

    private static ResponseStatusException bad(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private static ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }

    private static ResponseStatusException notFound(String message) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
    }
}
