package com.gcmb.backend.booking.service;

import com.gcmb.backend.auth.dto.Identity;
import com.gcmb.backend.booking.dto.DepositDtos;
import com.gcmb.backend.booking.exception.BookingFailure;
import com.gcmb.backend.booking.repository.BookingRepository;
import com.gcmb.backend.booking.repository.BookingRepository.BookingRow;
import com.gcmb.backend.booking.repository.BookingRepository.BranchScope;
import com.gcmb.backend.booking.repository.DepositRepository;
import com.gcmb.backend.booking.repository.DepositRepository.RefundEvidenceRow;
import com.gcmb.backend.booking.repository.DepositRepository.RefundRequestRow;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
// Lễ tân gửi yêu cầu theo cơ sở; kế toán xử lý yêu cầu hoàn trên toàn hệ thống.
public class DepositManagementService {
    // Cọc đã trừ bill vẫn có thể tra cứu và xem lịch sử APPLY.
    private static final Set<String> STATUSES = Set.of("HELD", "PENDING_REFUND", "REFUNDED", "FORFEITED", "APPLIED");
    private static final Set<String> METHODS = Set.of("BANK_TRANSFER", "MANUAL_TRANSFER", "CASH", "CARD", "QR", "MULTIPLE");
    private static final Set<String> OPEN_REFUND_STATUSES = Set.of("REQUESTED", "APPROVED", "PROCESSING");

    private final BookingRepository bookings;
    private final DepositRepository deposits;
    private final RefundEvidenceStorageService evidenceStorage;

    public DepositManagementService(BookingRepository bookings, DepositRepository deposits,
                                    RefundEvidenceStorageService evidenceStorage) {
        this.bookings = bookings;
        this.deposits = deposits;
        this.evidenceStorage = evidenceStorage;
    }

    @Transactional(readOnly = true)
    public DepositDtos.DepositPage list(Identity identity, LocalDate dateFrom, LocalDate dateTo,
                                         String requestedStatus, String requestedMethod, String requestedSearch,
                                         int page, int size) {
        BranchScope branch = branch(identity);
        if (dateFrom != null && dateTo != null && dateTo.isBefore(dateFrom))
            throw new BookingFailure(400, "dateTo", "Ngày kết thúc phải bằng hoặc sau ngày bắt đầu");
        if (page < 0) throw new BookingFailure(400, "page", "Số trang không hợp lệ");
        if (size < 1 || size > 100) throw new BookingFailure(400, "size", "Số dòng mỗi trang phải từ 1 đến 100");
        String status = normalizeFilter(requestedStatus, STATUSES, "status", "Trạng thái tiền cọc không hợp lệ");
        String method = normalizeFilter(requestedMethod, METHODS, "method", "Phương thức thu cọc không hợp lệ");
        String search = requestedSearch == null ? null : requestedSearch.trim();
        if (search != null && search.length() > 100)
            throw new BookingFailure(400, "search", "Từ khóa tìm kiếm không được vượt quá 100 ký tự");

        long total = deposits.count(branch.id(), dateFrom, dateTo, branch.timezone(), status, method, search);
        int totalPages = total == 0 ? 0 : (int) Math.ceil((double) total / size);
        return new DepositDtos.DepositPage(branch.name(), deposits.summary(branch.id(), dateFrom, dateTo, branch.timezone()),
            deposits.list(branch.id(), dateFrom, dateTo, branch.timezone(), status, method, search, page, size),
            page, size, total, totalPages);
    }

    @Transactional(readOnly = true)
    public List<DepositDtos.RefundableBooking> eligibleRefundBookings(Identity identity) {
        return deposits.eligibleRefundBookings(branch(identity).id());
    }

    @Transactional(readOnly = true)
    public DepositDtos.DepositDetail detail(Identity identity, UUID bookingId) {
        BranchScope branch = branch(identity);
        DepositDtos.DepositItem item = deposits.item(branch.id(), bookingId);
        if (item == null) {
            if (bookings.bookingExists(bookingId))
                throw new BookingFailure(403, "bookingId", "Bạn không có quyền xem tiền cọc đối với booking.");
            throw notFound();
        }
        return new DepositDtos.DepositDetail(item, deposits.history(branch.id(), bookingId),
            deposits.refundEvidenceForBooking(branch.id(), bookingId));
    }

    @Transactional
    public DepositDtos.RefundRequestResult createRefundRequest(Identity identity, DepositDtos.CreateRefundRequest request) {
        if (request == null || request.bookingId() == null)
            throw new BookingFailure(400, "bookingId", "Vui lòng bổ sung booking cần yêu cầu hoàn cọc.");
        BranchScope branch = branch(identity);
        if (bookings.lockBookingId(request.bookingId(), branch.id()) == null) {
            if (bookings.bookingExists(request.bookingId()))
                throw new BookingFailure(403, "bookingId", "Bạn không có quyền gửi yêu cầu hoàn cọc đối với booking.");
            throw notFound();
        }
        BookingRow booking = bookings.booking(request.bookingId(), branch.id());
        if (booking == null) throw notFound();
        if (!"CANCELLED".equals(booking.status()))
            throw new BookingFailure(409, "bookingId", "Chỉ booking đã hủy mới được gửi yêu cầu hoàn cọc");

        UUID depositId = bookings.lockDeposit(request.bookingId());
        BigDecimal balance = bookings.depositBalance(request.bookingId());
        if (depositId == null || balance.signum() <= 0)
            throw new BookingFailure(409, "bookingId", "Booking không còn số dư cọc đủ điều kiện hoàn");
        if (!deposits.canRequestRefund(branch.id(), request.bookingId()))
            throw new BookingFailure(409, "bookingId", "Booking chưa đủ điều kiện hoặc đã có yêu cầu hoàn cọc đang xử lý");

        String beneficiary = required(request.beneficiaryName(), "beneficiaryName", "Tên chủ tài khoản", 200);
        String bank = required(request.bankName(), "bankName", "Ngân hàng", 200);
        String account = required(request.bankAccount(), "bankAccount", "Số tài khoản", 60);
        String reason = required(request.reason(), "reason", "Lý do hoàn cọc", 1000);
        UUID refundRequestId = bookings.createDepositRefundRequest(branch.id(), request.bookingId(), depositId, balance,
            reason, beneficiary, bank, account, identity.id());
        // Sự kiện cùng giao dịch giúp audit và lịch sử booking theo dõi yêu cầu hoàn.
        bookings.event(request.bookingId(), identity.id(), "BOOKING_DEPOSIT_REFUND_REQUESTED", reason,
            java.util.Map.of("refundRequestId", refundRequestId.toString(), "depositAmount", balance));
        return new DepositDtos.RefundRequestResult(refundRequestId, request.bookingId(), balance, "REQUESTED");
    }

    @Transactional(readOnly = true)
    public DepositDtos.RefundRequestQueue refundRequests(Identity identity) {
        requireAccountant(identity);
        return new DepositDtos.RefundRequestQueue("Tất cả cơ sở", deposits.refundRequestsForAccounting());
    }

    @Transactional
    public DepositDtos.RefundCompletionResult completeRefund(Identity identity, UUID requestId,
                                                               String externalReference, MultipartFile image) {
        requireAccountant(identity);
        if (requestId == null) throw new BookingFailure(400, "requestId", "Yêu cầu hoàn cọc không hợp lệ");
        // BR-157: kế toán được xác nhận hoàn bằng mã giao dịch hoặc tệp minh chứng.
        String normalizedReference = externalReference == null ? "" : externalReference.trim();
        String reference = normalizedReference.isEmpty() ? null
            : required(normalizedReference, "externalReference", "Mã giao dịch hoàn", 200);
        boolean hasImage = image != null && !image.isEmpty();
        if (reference == null && !hasImage)
            throw new BookingFailure(400, "form", "Vui lòng tải lên minh chứng chuyển tiền hoặc nhập mã giao dịch trước khi xác nhận đã hoàn cọc.");
        RefundRequestRow initial = deposits.refundRequestForAccounting(requestId, false);
        if (initial == null) throw refundRequestNotFound();
        if (!OPEN_REFUND_STATUSES.contains(initial.status())) throw refundAlreadyProcessed();

        if (bookings.lockBookingId(initial.bookingId(), initial.branchId()) == null) throw notFound();
        BookingRow booking = bookings.booking(initial.bookingId(), initial.branchId());
        if (booking == null) throw notFound();
        if (!"CANCELLED".equals(booking.status()))
            throw new BookingFailure(409, "requestId", "Chỉ booking đã hủy mới được hoàn tiền cọc");
        UUID depositId = bookings.lockDeposit(initial.bookingId());
        if (depositId == null || !depositId.equals(initial.depositId()))
            throw new BookingFailure(409, "requestId", "Tài khoản cọc của booking đã thay đổi; vui lòng tải lại yêu cầu");

        RefundRequestRow locked = deposits.refundRequestForAccounting(requestId, true);
        if (locked == null) throw refundRequestNotFound();
        if (!OPEN_REFUND_STATUSES.contains(locked.status())) throw refundAlreadyProcessed();
        BigDecimal balance = bookings.depositBalance(locked.bookingId());
        if (balance.signum() <= 0 || locked.amount().compareTo(balance) > 0)
            throw new BookingFailure(409, "requestId", "Số tiền yêu cầu vượt quá số dư cọc còn được hoàn");

        RefundEvidenceStorageService.StoredEvidence evidence = hasImage ? evidenceStorage.store(image) : null;
        try {
            if (evidence != null) {
                deposits.insertAttachment(evidence.id(), evidence.objectKey(), evidence.originalName(),
                    evidence.mimeType(), evidence.byteSize(), evidence.sha256(), identity.id());
            }
            if (!deposits.completeRefundRequest(locked.branchId(), locked, identity.id(), reference,
                    evidence == null ? null : evidence.id()))
                throw refundAlreadyProcessed();
        } catch (RuntimeException exception) {
            if (evidence != null) evidenceStorage.deleteQuietly(evidence.objectKey());
            throw exception;
        }
        return new DepositDtos.RefundCompletionResult(locked.id(), locked.bookingId(), locked.amount(), "PAID");
    }

    @Transactional(readOnly = true)
    public DepositDtos.RefundEvidenceContent refundEvidence(Identity identity, UUID requestId) {
        BranchScope branch = branch(identity);
        RefundEvidenceRow evidence = deposits.refundEvidence(branch.id(), requestId);
        if (evidence == null && deposits.refundRequestForAccounting(requestId, false) != null)
            throw new BookingFailure(403, "requestId", "Bạn không có quyền xem chứng từ hoàn cọc đối với yêu cầu hoàn cọc.");
        return evidenceContent(evidence);
    }

    @Transactional(readOnly = true)
    public DepositDtos.RefundEvidenceContent accountingRefundEvidence(Identity identity, UUID requestId) {
        requireAccountant(identity);
        return evidenceContent(deposits.refundEvidenceForAccounting(requestId));
    }

    private DepositDtos.RefundEvidenceContent evidenceContent(RefundEvidenceRow evidence) {
        if (evidence == null) throw new BookingFailure(404, "requestId", "Không tìm thấy chứng từ hoàn cọc");
        try {
            return new DepositDtos.RefundEvidenceContent(evidence.originalName(), evidence.mimeType(),
                evidenceStorage.read(evidence.objectKey()));
        } catch (IOException exception) {
            throw new BookingFailure(404, "requestId", "Không thể đọc ảnh chứng minh hoàn cọc");
        }
    }

    private static void requireAccountant(Identity identity) {
        if (identity == null || !"ACCOUNTANT".equals(identity.role()))
            throw new BookingFailure(403, "form", "Bạn không có quyền xử lý yêu cầu hoàn cọc đối với yêu cầu hoàn cọc.");
    }

    private BranchScope branch(Identity identity) {
        BranchScope scope = bookings.branchScope(identity.id());
        if (scope == null) throw new BookingFailure(403, "form", "Bạn không có quyền truy cập đối với tiền cọc.");
        return scope;
    }

    private static String normalizeFilter(String value, Set<String> allowed, String field, String message) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!allowed.contains(normalized)) throw new BookingFailure(400, field, message);
        return normalized;
    }

    private static String required(String value, String field, String label, int maxLength) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) throw new BookingFailure(400, field, "Vui lòng bổ sung " + label.toLowerCase(Locale.ROOT) + ".");
        if (normalized.length() > maxLength) throw new BookingFailure(400, field, label + " chỉ được chứa tối đa " + maxLength + " ký tự.");
        return normalized;
    }

    private static BookingFailure notFound() {
        return new BookingFailure(404, "bookingId", "Không tìm thấy booking hoặc tiền cọc tại chi nhánh đang làm việc");
    }

    private static BookingFailure refundRequestNotFound() {
        return new BookingFailure(404, "requestId", "Không tìm thấy yêu cầu hoàn cọc");
    }

    private static BookingFailure refundAlreadyProcessed() {
        return new BookingFailure(409, "requestId", "Thao tác này đã được ghi nhận. Không tạo thêm bản ghi; vui lòng kiểm tra kết quả hiện có.");
    }
}
