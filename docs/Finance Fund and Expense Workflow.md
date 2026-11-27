# Quỹ kế toán, đề nghị thanh toán và hoàn ứng

## Accountant financial overview data

The accountant overview reads canonical accounting transactions first and fills branches without canonical income from imported source records for the selected month. When both Kiot invoices and booking rows exist for a branch, invoice rows take precedence to avoid counting the same revenue twice. Synthetic source rows remain labeled as simulated; imported files do not provide operating expense figures. The overview filters by month, year, date range, and branch. All cash-flow figures use transaction dates; there is no separate date-basis selector. Shift filtering is unavailable because the accounting transaction feed does not contain an operational shift field.

### Spreadsheet source imports

Booking workbooks and KiotViet invoice exports can be retained in `gcmb.source_import_batch` and `gcmb.source_import_record`. Batches are scoped to a branch and identified by source-file SHA-256; source rows retain their workbook sheet/row, business date, source key, and original values as JSONB. `backend/scripts/import_september_2026_source_records.py` imports the supplied September 2026 workbooks for Hồ Tây (branch code `HO_TAY`, displayed name GenZ Cinema Nhật Chiêu), Hoàng Công Chất, and Hoa Bằng. `backend/scripts/generate_september_2026_other_branch_data.py` creates clearly marked synthetic booking and invoice rows for the remaining 16 branches (60 of each per branch); `backend/scripts/generate_november_2026_all_branches_data.py` inserts 60 synthetic bookings and 60 synthetic invoices for each of the 19 active branches for 01–21 November 2026. These rows use source system `SYNTHETIC_DEMO`. The **Báo cáo & thống kê** report reads these import tables and labels synthetic rows in the detail table. For branches without imported invoices in the selected range, it also reads settled operational bills and their payments; this fallback does not duplicate branches that already have imported invoice rows. Its expense totals and expense analysis come from the paid operating-expense report. Source imports do not create canonical `booking`, `bill`, `payment`, or financial-ledger rows, so they do not appear in operational booking/payment screens. The accountant overview uses imported source income as a fallback only for branches without canonical income; source costs are unavailable there. Reconcile source totals before any future promotion to operational records to avoid duplicate revenue. The import schema is in `backend/scripts/source_import_schema.sql`; it was applied directly because the connected database has a later Flyway version than this checkout.

This document describes the operational finance workflows delivered for UC102–UC120. It supersedes the earlier branch-fund allocation interpretation for UC103.

## Roles and scope

- **Head Office Manager:** records a transfer to the accountant's chain-wide operating fund; approves expenses strictly greater than 3,000,000 VND; views requests and supporting documents from every branch.
- **Accountant:** confirms receipt of incoming transfers, owns the shared expense categories, reviews eligible requests, records completed payments, and views chain-wide expense history.
- **Branch Manager:** creates payment requests and requests reimbursement for business expenses they paid personally; can see only the currently assigned branch's requests.
- **Receptionist:** has no permission to create reimbursement or payment requests in this workflow.

## Fund received from Head Office (UC102–UC104)

1. Head Office Manager enters the amount and transfer date and may record the bank transaction reference. Saving the transfer request does not depend on the optional receipt upload.
2. The request is stored with a generated business code in `FUND-YYMMDD-XXXXXXXX` format and immediately appears in the accountant queue. The transfer receipt can be uploaded with the request or added later while it is awaiting confirmation.
3. The receipt is stored on the backend's local attachment directory and can be previewed in the authenticated application. The Accountant checks the receipt when present, verifies the incoming bank transaction, and confirms receipt. The available accountant balance increases only after this confirmation. A missing receipt does not prevent manual bank verification and confirmation.
4. This is one chain-wide fund. There is no branch allocation step. The displayed balance equals confirmed incoming transfers less completed expense payments.

Database UUIDs remain the technical primary keys. `FUND`, `EXP`, and `CAT` codes are human-readable identifiers; the random suffix avoids collisions within a date.

## Expense categories (UC115–UC119)

Accountant creates and updates shared categories such as food purchases, beverage purchases, and repairs. Removing a category deactivates it instead of deleting its database row, so historical expenses keep their original category. Deactivated categories cannot be selected for new requests.

## Payment requests and reimbursements (UC107–UC114)

The Branch Manager submits the branch, category, expense date (or planned date), amount, description, beneficiary, bank account and bank name. Request metadata is saved first; invoice/expense evidence and (for payment requests) the quote or bank-transfer document are uploaded separately to the backend's local attachment directory. An upload failure therefore does not discard the request: the manager can retry missing uploads from the request history. A reimbursement request is for an expense already paid personally by the Branch Manager and requires its supporting invoice.

Trong **Chi phí & đề nghị** của Quản lý cơ sở, cả **Đề nghị thanh toán** và **Hoàn ứng tiền đã chi** trình bày thông tin hạng mục ngay trong ô chọn. Yêu cầu hóa đơn hiển thị tại ô đính kèm **Hóa đơn/ảnh chứng minh**.

- At **3,000,000 VND or less**, the request enters the Accountant queue.
- Above **3,000,000 VND**, it enters the Head Office Manager approval queue first; after approval, the Accountant pays it.
- Rejection records the actor, time, and reason. Review and approval can occur before payment. The Accountant's combined approval and payment action requires a payment proof file before either step is recorded.
- The Accountant provides payment proof and may enter a bank reference. The system checks the shared balance under a transaction lock, rejects insufficient funds, records the payment in the branch/category history, and deducts the amount once. Missing payment proof returns MSG95.
- UC120 history is read-only. Head Office Manager and Accountant can see all branches; a Branch Manager sees their assigned branch only.

## Ứng lương và thanh toán cuối kỳ

- Sau khi Quản lý cơ sở gửi bảng lương, kế toán vẫn có thể ghi nhận ứng lương nếu nhân viên chưa nhận lương cuối kỳ của tháng đó. Khoản ứng phải nằm trong số lương còn có thể ứng và số còn phải trả.
- Khoản ứng đã chi được ghi riêng, trừ quỹ một lần và khấu trừ vào số lương còn phải trả. Lương gốc giữ nguyên. Ví dụ lương 440.000đ đã ứng 100.000đ thì còn phải trả 340.000đ.
- Khi lương cuối kỳ đã được ghi nhận thanh toán thành công, hệ thống chặn ứng thêm cho nhân viên đó trong cùng kỳ.

## Evidence storage and access

Finance receipts and supporting documents are stored on the backend host in `GCMB_ATTACHMENT_STORAGE_DIR` (default: `./data/attachments`) and are not uploaded to Google Drive. Equipment asset images and incident evidence use the same local attachment directory. Configure this path on a persistent local volume for deployments where backend containers or instances can be replaced. Binary files stay out of Git; metadata and a SHA-256 hash are stored in PostgreSQL. Authenticated attachment endpoints stream files inline after role and branch-scope checks. The frontend fetches evidence with the session cookie and shows it in an in-app preview dialog, avoiding direct browser navigation to the protected API URL. Existing records that reference files previously stored on Drive are not migrated automatically; upload those files again to create local copies.

The Accountant sidebar groups destinations under **Tổng quan**, **Thu chi & thanh toán**, **Đối soát & kiểm soát**, and **Nhân sự & chi phí**. Each group expands to its existing route; the top-level destination set remains Tổng quan tài chính, Báo cáo & thống kê, Thu & cấp quỹ, Chi & hoàn ứng, Hoàn tiền cọc, Hóa đơn thuế, Đối soát chốt quỹ, Lương nhân viên, and Báo cáo chi phí. The financial overview and report share a compact ledger-focused design with KPI cards, trend charts, tables, and labeled filters. The overview reads live accounting APIs and has no sample-data mode. It filters by month, year, date range, and branch; date-basis selection and the payroll summary card are omitted. Income and expense trends, pending work, payment-method proportions, branch revenue, and recent transactions remain on the overview. Payment-method amounts and percentages follow the selected date range and branch. The detailed cash-closing table, SePay exception queue, expense/reimbursement table, and period status panel are not shown on the overview; SePay exception review remains available in **Báo cáo & thống kê → Giao dịch**. The **Giao dịch gần đây trong kỳ** table supports the selected range and branch, an optional transaction date, page-size selection, and pagination. **Báo cáo & thống kê** places period, year, date-range, and branch controls directly below the page title. These filters apply to the five report views, summary figures, transaction ledger, and operating-expense analysis; the ledger supports search and 10/25/50-row pagination. Expense analysis shows cost structure, cost by branch, and a grouped comparison of branch income, expense, and net operating difference. Selecting a cost category filters its trend; its detail action opens the expense report with the selected dates, category, and branch scope. The payroll category opens the salary register for the selected month and, when one branch is selected, that branch. The period-close view summarizes passed and pending checks before the irreversible close action and presents the financial-period history separately. The report page omits the duplicate monthly results table and comparison panel. The fund page shows the shared balance and three tabs: incoming transfers (including transfer date and request-created timestamp), outgoing requests, and expense category maintenance. Categories can be created, edited, and deactivated; deactivation preserves references in historical requests. The expense page remains separate with two views: requests waiting for accountant action and closed history (paid or rejected), each filterable by branch or chain-wide. Incoming, outgoing, request, reimbursement, and history tables scroll vertically within a bounded area with sticky column headers; each request remains a single horizontal row. Approval and rejection use an in-app confirmation dialog. Branch managers can create requests and upload supporting evidence, but cannot approve or record payment.

The Head Office Manager workspace combines pending fund requests and transfer history in one **Cấp quỹ** tab. A **Tạo yêu cầu chuyển tiền** action opens a centered dialog for the amount, transfer date, bank reference, and optional receipt; submitting sends the request to the Accountant for confirmation. The same tab shows each transfer as one horizontal row with fixed sender, sent date, amount, bank reference/receipt, and status columns; the list scrolls vertically with its header fixed. Date-only values are formatted independently from timestamps to prevent browser locale parsing errors. Chain-wide payment/reimbursement requests remain a separate tab and can be filtered by branch before high-value requests are reviewed.

Trên **Tổng quan** của Quản lý cấp cao, chuông vàng nổi trên màn hình và nhấp nháy khi có cảnh báo chưa đọc. Có thể kéo chuông hoặc dùng phím mũi tên khi nút được chọn. Bấm chuông để xem mức độ, phạm vi và nội dung cảnh báo. Mỗi trang cảnh báo được đánh dấu đã đọc khi mở. Đóng bảng bằng nút **Đóng**, bấm ra ngoài hoặc nhấn **Escape**. Chuông tự ẩn khi đã đọc hết và hiện lại khi báo cáo tải được cảnh báo mới hoặc nội dung cảnh báo thay đổi. Trạng thái đã đọc được lưu theo tài khoản trên trình duyệt. Chế độ giảm chuyển động hiển thị chuông vàng tĩnh cùng số cảnh báo chưa đọc. Danh sách **Việc cần làm** mở đề nghị tại **Phê duyệt chi → Đề nghị thanh toán và hoàn ứng**.

The overview has day, month, and year selectors for its totals, branch revenue chart, and revenue/profit trend. The branch chart can show the full chain or one selected branch. The trend compares the selected period with the immediately preceding period. Alerts include negative profit and revenue/profit declines of at least 20% versus that comparison period. Selecting an alert opens its related report, branch detail, or equipment incident. The payment mix separates cash, bank transfers, and forfeited deposits by actual payment amount. Branch ranking and occupancy lists scroll vertically.

### So sánh chỉ số của Quản lý cấp cao

- **Tổng quan** hiển thị bốn thẻ Thu nhập vận hành, Tỷ lệ lấp đầy, Chi phí vận hành và Lợi nhuận vận hành. Ba chỉ số tài chính có mũi tên và tỷ lệ thay đổi theo kỳ đang chọn, mặc định so với tháng trước.
- **Báo cáo kinh doanh** mở thẳng phần Báo cáo & thống kê. Bộ lọc **Xem theo** cho phép chọn Ngày, Tháng hoặc Năm. Có thể thu hẹp khoảng ngày trong tháng hoặc năm. Bốn thẻ Doanh Thu, Chi phí vận hành, Lợi Nhuận và Giao dịch đều so với kỳ liền trước trên cùng phạm vi cơ sở.
- Doanh Thu của báo cáo lấy tiền khách đã trả trên hóa đơn nguồn hoặc hóa đơn vận hành đã tất toán theo quy tắc nguồn ở trên. Lợi Nhuận lấy khoản thu này trừ chi phí vận hành đã thanh toán. Giao dịch đếm các dòng booking và hóa đơn trong báo cáo nguồn. Các kỳ dùng cùng cách tính.
- Kỳ ngày so với ngày liền trước. Tháng đầy đủ so với cả tháng trước. Khoảng ngày trong tháng hoặc năm được lùi theo lịch và giới hạn ở ngày cuối của tháng tương ứng nếu cần. Tỷ lệ được tính bằng chênh lệch chia cho trị tuyệt đối của giá trị kỳ trước. Dấu **—** biểu thị thiếu số liệu hoặc giá trị kỳ trước bằng 0. Chi phí giảm dùng màu xanh, chi phí tăng dùng màu đỏ.
- API `GET /api/v1/accounting/reports/financial-overview` hỗ trợ thêm `compare=previous-day|previous-month|previous-year` cho các kỳ theo lịch. API `GET /api/v1/accounting/reports/source-imports` cho phép Quản lý cấp cao xem tối đa 366 ngày. Tham số `summaryOnly=true` trả về `receipts` và `recordCount` để đối chiếu kỳ trước theo cùng quy tắc chọn nguồn.

## API overview

- `/api/v1/head-office/finance`: chain view, create incoming fund transfer, upload its receipt to local storage separately, approve/reject high-value request.
- `GET /api/v1/head-office/finance/requests/pending`: Head Office Manager only. Returns the requests in `WAITING_HEAD_OFFICE`, `WAITING_APPROVAL` or `WAITING_MANAGER`, with the same row shape as `requests` in `/head-office/finance` (attachments, history, allocations, stock receipts). The chain dashboard uses it instead of downloading every request.
- `/api/v1/head-office/dashboard/revenue-mix`: Head Office Manager payment-method totals for an inclusive date range, optionally scoped to a branch.
- `/api/v1/accounting/dashboard-details`: accountant payment-method totals over an inclusive `from`/`to` date range, optionally scoped to a branch; omitting dates returns the selected month.
- `/api/v1/accounting/finance`: accountant balance, fund confirmation, category maintenance, request review and payment.
- `/api/v1/branch-management/finance`: branch view and payment/reimbursement request creation.
- `/api/v1/finance/attachments/{id}`: authenticated inline local evidence access.

V40 adds finance transfers, categories, requests, attachments and request history. It does not modify deposit/refund records or branch balances.

### Bộ lọc và danh sách phê duyệt quản lý cấp cao

Hai tab Cấp quỹ và Đề nghị thanh toán/hoàn ứng có bộ lọc khoảng ngày; bảng kết quả hiển thị dạng danh sách cuộn trong khung có tiêu đề cột cố định. Báo cáo chi phí của quản lý cấp cao không có liên kết quay lại báo cáo hiệu quả tháng; bảng tiền đã chi hiển thị 10 dòng mỗi trang. Biểu mẫu chi và hoàn ứng chỉ cho chọn hạng mục chi vận hành, không liệt kê nhóm CAPEX/Đầu tư.

Trong **Báo cáo chi phí** của Quản lý cấp cao, thẻ **Tổng chi phí** cộng số tiền đã thanh toán cho vận hành và đầu tư theo bộ lọc đang chọn. Số tiền hiển thị cỡ lớn trên thẻ nền tím đậm, cùng số khoản chi. Các bộ lọc nằm trong khung ngay bên dưới thẻ. Bảng chi tiết tiếp tục hiển thị nhóm chi của từng khoản.

Hai tab phê duyệt cấp quỹ và đề nghị chi dùng chung ngôn ngữ giao diện: tiêu đề phân cấp theo vai trò, tóm tắt số liệu, bộ lọc nằm trong một vùng riêng, bảng cuộn có tiêu đề cố định và thao tác rõ ràng. Trên màn hình nhỏ, bộ lọc và chỉ số chuyển thành một cột để tránh tràn ngang.
