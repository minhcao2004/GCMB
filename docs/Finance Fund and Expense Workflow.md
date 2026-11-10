# Quỹ kế toán, đề nghị thanh toán và hoàn ứng

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

- At **3,000,000 VND or less**, the request enters the Accountant queue.
- Above **3,000,000 VND**, it enters the Head Office Manager approval queue first; after approval, the Accountant pays it.
- Rejection records the actor, time, and reason. The review screen asks the Accountant to confirm or cancel before approval/rejection is submitted. Missing evidence does not block the review; approval does not reduce the fund.
- Accountant records the actual payment date, optional bank reference, and transfer proof. The system checks the shared balance under a transaction lock, rejects insufficient funds, records the payment in the branch/category history, and deducts the amount once.
- UC120 history is read-only. Head Office Manager and Accountant can see all branches; a Branch Manager sees their assigned branch only.

## Evidence storage and access

Finance receipts and supporting documents are stored on the backend host in `GCMB_ATTACHMENT_STORAGE_DIR` (default: `./data/attachments`) and are not uploaded to Google Drive. Equipment asset images and incident evidence use the same local attachment directory. Configure this path on a persistent local volume for deployments where backend containers or instances can be replaced. Binary files stay out of Git; metadata and a SHA-256 hash are stored in PostgreSQL. Authenticated attachment endpoints stream files inline after role and branch-scope checks, so images can be previewed by authorized roles without public links. Existing records that reference files previously stored on Drive are not migrated automatically; upload those files again to create local copies.

The Accountant navigation separates **Thu & cấp quỹ**, **Chi & hoàn ứng**, and **Danh mục chi phí**. The fund page shows the shared balance and three tabs: incoming transfers (including transfer date and request-created timestamp), outgoing requests, and expense category maintenance. Categories can be created, edited, and deactivated; deactivation preserves references in historical requests. The expense page remains separate with two views: requests waiting for accountant action and closed history (paid or rejected), each filterable by branch or chain-wide. Approval and rejection use an in-app confirmation dialog. Branch managers can create requests and upload supporting evidence, but cannot approve or record payment.

The Head Office Manager workspace combines pending fund requests and transfer history in one **Cấp quỹ** tab. A **Tạo yêu cầu chuyển tiền** action opens a centered dialog for the amount, transfer date, bank reference, and optional receipt; submitting sends the request to the Accountant for confirmation. The same tab shows all fund requests, status, created date, transfer date, and local receipt previews in a vertically scrollable list. Date-only values are formatted independently from timestamps to prevent browser locale parsing errors. Chain-wide payment/reimbursement requests remain a separate tab and can be filtered by branch before high-value requests are reviewed.

## API overview

- `/api/v1/head-office/finance`: chain view, create incoming fund transfer, upload its receipt to local storage separately, approve/reject high-value request.
- `/api/v1/accounting/finance`: accountant balance, fund confirmation, category maintenance, request review and payment.
- `/api/v1/branch-management/finance`: branch view and payment/reimbursement request creation.
- `/api/v1/finance/attachments/{id}`: authenticated inline local evidence access, retaining support for previously stored Drive keys.

V40 adds finance transfers, categories, requests, attachments and request history. It does not modify deposit/refund records or branch balances.
