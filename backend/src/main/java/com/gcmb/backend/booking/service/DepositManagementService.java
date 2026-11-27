package com.gcmb.backend.booking.service;

import com.gcmb.backend.auth.dto.Identity;
import com.gcmb.backend.booking.dto.DepositDtos;
import com.gcmb.backend.booking.exception.BookingFailure;
import com.gcmb.backend.booking.repository.BookingRepository;
import com.gcmb.backend.booking.repository.BookingRepository.BookingRow;
import com.gcmb.backend.booking.repository.BookingRepository.BranchScope;
import com.gcmb.backend.booking.repository.DepositRepository;
import com.gcmb.backend.finance.service.FinancialPeriodService;
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
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

@Service
// Lễ tân gửi yêu cầu theo cơ sở; kế toán xử lý yêu cầu hoàn trên toàn hệ thống.
public class DepositManagementService {
    // Cọc đã trừ bill vẫn có thể tra cứu và xem lịch sử APPLY.
    private static final Set<String> STATUSES = Set.of("HELD", "PENDING_REFUND", "REFUNDED", "FORFEITED", "APPLIED");
    private static final Set<String> METHODS = Set.of("BANK_TRANSFER", "MANUAL_TRANSFER", "CASH", "CARD", "QR", "MULTIPLE");
    private static final Set<String> OPEN_REFUND_STATUSES = Set.of("REQUESTED", "APPROVED", "PROCESSING");

    private final BookingRepository bookingRepository;
    private final DepositRepository depositRepository;
    private final RefundEvidenceStorageService refundEvidenceStorageService;
    private final FinancialPeriodService financialPeriodService;

    public DepositManagementService(BookingRepository bookingRepository, DepositRepository depositRepository,
                                    RefundEvidenceStorageService refundEvidenceStorageService, FinancialPeriodService financialPeriodService) {
        this.bookingRepository = bookingRepository;
        this.depositRepository = depositRepository;
        this.refundEvidenceStorageService = refundEvidenceStorageService;
        this.financialPeriodService = financialPeriodService;
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

        long total = depositRepository.count(branch.id(), dateFrom, dateTo, branch.timezone(), status, method, search);
        int totalPages = total == 0 ? 0 : (int) Math.ceil((double) total / size);
        return new DepositDtos.DepositPage(branch.name(), depositRepository.summary(branch.id(), dateFrom, dateTo, branch.timezone()),
            depositRepository.list(branch.id(), dateFrom, dateTo, branch.timezone(), status, method, search, page, size),
            page, size, total, totalPages);
    }

    @Transactional(readOnly = true)
    public List<DepositDtos.RefundableBooking> eligibleRefundBookings(Identity identity) {
        return depositRepository.eligibleRefundBookings(branch(identity).id());
    }

    @Transactional(readOnly = true)
    public DepositDtos.DepositDetail detail(Identity identity, UUID bookingId) {
        BranchScope branch = branch(identity);
        DepositDtos.DepositItem item = depositRepository.item(branch.id(), bookingId);
        if (item == null) {
            if (bookingRepository.bookingExists(bookingId))
                throw new BookingFailure(403, "bookingId", "Bạn không có quyền xem tiền cọc đối với booking.");
            throw notFound();
        }
        return new DepositDtos.DepositDetail(item, depositRepository.history(branch.id(), bookingId),
            depositRepository.refundEvidenceForBooking(branch.id(), bookingId));
    }

    @Transactional
    public DepositDtos.RefundRequestResult createRefundRequest(Identity identity, DepositDtos.CreateRefundRequest request) {
        financialPeriodService.requireOpen(java.time.LocalDate.now(java.time.ZoneId.of("Asia/Ho_Chi_Minh")), "tạo yêu cầu hoàn cọc");
        if (request == null || request.bookingId() == null)
            throw new BookingFailure(400, "bookingId", "Vui lòng bổ sung booking cần yêu cầu hoàn cọc.");
        BranchScope branch = branch(identity);
        if (bookingRepository.lockBookingId(request.bookingId(), branch.id()) == null) {
            if (bookingRepository.bookingExists(request.bookingId()))
                throw new BookingFailure(403, "bookingId", "Bạn không có quyền gửi yêu cầu hoàn cọc đối với booking.");
            throw notFound();
        }
        BookingRow booking = bookingRepository.booking(request.bookingId(), branch.id());
        if (booking == null) throw notFound();
        if (!"CANCELLED".equals(booking.status()))
            throw new BookingFailure(409, "bookingId", "Chỉ booking đã hủy mới được gửi yêu cầu hoàn cọc");

        UUID depositId = bookingRepository.lockDeposit(request.bookingId());
        BigDecimal balance = bookingRepository.depositBalance(request.bookingId());
        if (depositId == null || balance.signum() <= 0)
            throw new BookingFailure(409, "bookingId", "Booking không còn số dư cọc đủ điều kiện hoàn");
        var deposit = depositRepository.item(branch.id(), request.bookingId());
        if (deposit != null && deposit.pendingRefundAmount().signum() > 0)
            throw new BookingFailure(409, "bookingId", "Thao tác này đã được ghi nhận. Không tạo thêm bản ghi; vui lòng kiểm tra kết quả hiện có.");
        if (deposit == null || !deposit.canRequestRefund())
            throw new BookingFailure(409, "bookingId", "Booking chưa đủ điều kiện hoàn cọc.");

        String beneficiary = required(request.beneficiaryName(), "beneficiaryName", "Tên chủ tài khoản", 200);
        String bank = required(request.bankName(), "bankName", "Ngân hàng", 200);
        String account = required(request.bankAccount(), "bankAccount", "Số tài khoản", 60);
        String reason = required(request.reason(), "reason", "Lý do hoàn cọc", 1000);
        UUID refundRequestId = bookingRepository.createDepositRefundRequest(branch.id(), request.bookingId(), depositId, balance,
            reason, beneficiary, bank, account, identity.id(), null);
        // Sự kiện cùng giao dịch giúp audit và lịch sử booking theo dõi yêu cầu hoàn.
        bookingRepository.event(request.bookingId(), identity.id(), "BOOKING_DEPOSIT_REFUND_REQUESTED", reason,
            java.util.Map.of("refundRequestId", refundRequestId.toString(), "depositAmount", balance));
        return new DepositDtos.RefundRequestResult(refundRequestId, request.bookingId(), balance, "REQUESTED");
    }

    @Transactional(readOnly = true)
    public DepositDtos.RefundRequestQueue refundRequests(Identity identity) {
        requireAccountant(identity);
        return new DepositDtos.RefundRequestQueue("Tất cả cơ sở", depositRepository.refundRequestsForAccounting());
    }

    @Transactional
    public DepositDtos.RefundCompletionResult completeRefund(Identity identity, UUID requestId,
                                                               String externalReference, MultipartFile image,
                                                               DepositDtos.RefundRecipient recipient) {
        // BR-17: Chỉ kế toán được ghi nhận hoàn cọc đã hoàn tất.
        requireAccountant(identity);
        financialPeriodService.requireOpen(java.time.LocalDate.now(java.time.ZoneId.of("Asia/Ho_Chi_Minh")), "ghi nhận hoàn cọc");
        if (requestId == null) throw new BookingFailure(400, "requestId", "Yêu cầu hoàn cọc không hợp lệ");
        String normalizedReference = externalReference == null ? "" : externalReference.strip();
        String reference = normalizedReference.isEmpty() ? null
            : required(normalizedReference, "externalReference", "Mã giao dịch hoàn", 200);
        boolean hasImage = image != null && !image.isEmpty();
        // BR-157 / MSG67: cần ít nhất một minh chứng trước khi ghi nhận khoản hoàn.
        if (reference == null && !hasImage)
            throw new BookingFailure(400, "externalReference",
                "Vui lòng tải lên minh chứng chuyển tiền hoặc nhập mã giao dịch trước khi xác nhận đã hoàn cọc.");
        RefundRequestRow initial = depositRepository.refundRequestForAccounting(requestId, false);
        if (initial == null) throw refundRequestNotFound();
        if (!OPEN_REFUND_STATUSES.contains(initial.status())) throw refundAlreadyProcessed();

        // Tất toán yêu cầu hiện có không phụ thuộc trạng thái hoạt động của cơ sở.
        if (bookingRepository.lockBookingIdForRefund(initial.bookingId(), initial.branchId()) == null) throw notFound();
        BookingRow booking = bookingRepository.booking(initial.bookingId(), initial.branchId());
        if (booking == null) throw notFound();
        boolean checkoutRefund = initial.checkoutBillId() != null;
        if (checkoutRefund && (!"COMPLETED".equals(booking.status())
                || !depositRepository.checkoutBillIsSettled(initial.checkoutBillId(), initial.bookingId())))
            throw new BookingFailure(409, "requestId", "Chỉ hoàn cọc dư khi booking và hóa đơn đã hoàn tất.");
        if (!checkoutRefund && !"CANCELLED".equals(booking.status()))
            throw new BookingFailure(409, "requestId", "Chỉ booking đã hủy mới được hoàn tiền cọc");
        UUID depositId = bookingRepository.lockDeposit(initial.bookingId());
        if (depositId == null || !depositId.equals(initial.depositId()))
            throw new BookingFailure(409, "requestId", "Tài khoản cọc của booking đã thay đổi; vui lòng tải lại yêu cầu");

        RefundRequestRow locked = depositRepository.refundRequestForAccounting(requestId, true);
        if (locked == null) throw refundRequestNotFound();
        if (!OPEN_REFUND_STATUSES.contains(locked.status())) throw refundAlreadyProcessed();
        BigDecimal balance = bookingRepository.depositBalance(locked.bookingId());
        if (balance.signum() <= 0 || locked.amount().compareTo(balance) > 0)
            throw new BookingFailure(409, "requestId", "Số tiền yêu cầu vượt quá số dư cọc còn được hoàn");

        DepositDtos.RefundRecipient verifiedRecipient = null;
        if (checkoutRefund) {
            if (recipient == null) throw new BookingFailure(400, "beneficiaryName", "Vui lòng bổ sung thông tin nhận hoàn cọc.");
            verifiedRecipient = new DepositDtos.RefundRecipient(
                required(recipient.beneficiaryName(), "beneficiaryName", "Tên chủ tài khoản", 200),
                required(recipient.bankName(), "bankName", "Ngân hàng", 200),
                required(recipient.bankAccount(), "bankAccount", "Số tài khoản", 60));
        }

        RefundEvidenceStorageService.StoredEvidence evidence = hasImage ? refundEvidenceStorageService.store(image, "tien-coc") : null;
        if (evidence != null && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCompletion(int status) {
                    if (status != STATUS_COMMITTED) refundEvidenceStorageService.deleteQuietly(evidence.objectKey());
                }
            });
        }
        try {
            if (evidence != null) {
                depositRepository.insertAttachment(evidence.id(), evidence.objectKey(), evidence.originalName(),
                    evidence.mimeType(), evidence.byteSize(), evidence.sha256(), identity.id());
            }
            if (!depositRepository.completeRefundRequest(locked.branchId(), locked, identity.id(), reference,
                    evidence == null ? null : evidence.id(), verifiedRecipient))
                throw refundAlreadyProcessed();
        } catch (RuntimeException exception) {
            if (evidence != null && !TransactionSynchronizationManager.isSynchronizationActive()) {
                refundEvidenceStorageService.deleteQuietly(evidence.objectKey());
            }
            throw exception;
        }
        return new DepositDtos.RefundCompletionResult(locked.id(), locked.bookingId(), locked.amount(), "PAID");
    }

    @Transactional(readOnly = true)
    public DepositDtos.RefundEvidenceContent refundEvidence(Identity identity, UUID requestId) {
        BranchScope branch = branch(identity);
        RefundEvidenceRow evidence = depositRepository.refundEvidence(branch.id(), requestId);
        if (evidence == null && depositRepository.refundRequestForAccounting(requestId, false) != null)
            throw new BookingFailure(403, "requestId", "Bạn không có quyền xem chứng từ hoàn cọc đối với yêu cầu hoàn cọc.");
        return evidenceContent(evidence);
    }

    @Transactional(readOnly = true)
    public DepositDtos.RefundEvidenceContent accountingRefundEvidence(Identity identity, UUID requestId) {
        requireAccountant(identity);
        return evidenceContent(depositRepository.refundEvidenceForAccounting(requestId));
    }

    private DepositDtos.RefundEvidenceContent evidenceContent(RefundEvidenceRow evidence) {
        if (evidence == null) throw new BookingFailure(404, "requestId", "Không tìm thấy chứng từ hoàn cọc");
        try {
            return new DepositDtos.RefundEvidenceContent(evidence.originalName(), evidence.mimeType(),
                refundEvidenceStorageService.read(evidence.objectKey()));
        } catch (IOException exception) {
            throw new BookingFailure(404, "requestId", "Không thể đọc ảnh chứng minh hoàn cọc");
        }
    }

    private static void requireAccountant(Identity identity) {
        if (identity == null || !"ACCOUNTANT".equals(identity.role()))
            throw new BookingFailure(403, "form", "Bạn không có quyền xử lý yêu cầu hoàn cọc đối với yêu cầu hoàn cọc.");
    }

    private BranchScope branch(Identity identity) {
        BranchScope scope = bookingRepository.branchScope(identity.id());
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
