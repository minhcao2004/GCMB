# GCMB — GenZ Cinema & Music Box Management System

 is a centralized web application for managing the GenZ Cinema & Music Box chain. It connects room bookings, F&B orders, inventory, equipment, staff, payroll, and financial operations across branches.

The project aims to reduce duplicate data entry and manual reconciliation caused by operational data being spread across KiotViet, spreadsheets, and manual records.

**FPT University Capstone Project · SEP490-G12**  
**Architecture:** Modular Monolith + Feature-based Structure  
**Stack:** React (JavaScript/JSX + plain CSS), Java 17, Spring Boot, PostgreSQL

> This README describes the agreed project structure and development setup. Feature descriptions represent the planned scope, not a completion checklist. The source repository was not supplied for verification; configuration examples below must match the actual `pom.xml`, `package.json`, and API implementation.

## Table of Contents

- [About the Project](#about-the-project)
- [Features](#features)
- [Tech Stack](#tech-stack)
- [Architecture](#architecture)
- [Project Structure](#project-structure)
- [Getting Started](#getting-started)
- [Configuration](#configuration)
- [Usage](#usage)
- [Testing](#testing)
- [Contributing](#contributing)
- [Documentation](#documentation)
- [Team](#team)
- [License](#license)

## About the Project

GCMB brings branch operations and financial records into one system. Its goals are to prevent conflicting room assignments, standardize operational workflows, maintain transaction history, and provide consistent branch and chain-level reporting.

Access is controlled by both business role and branch scope.

| User | Main responsibilities |
| --- | --- |
| Head Office Manager | Manage branches, shared configuration, access rights, major approvals, and chain reports. |
| Branch Manager | Manage branch rooms, stock, equipment, shifts, attendance, expenses, and daily cash closing. |
| Receptionist | Handle bookings, check-in/out, deposits, payments, F&B orders, and personal shift information. |
| Accountant | Process refunds, funds, expenses, reimbursements, reconciliation, payroll, and financial reports. |
| Customer | View the F&B menu and submit orders using an in-room QR code. |

Customer interaction is limited to in-room F&B ordering. Room reservations received through social channels are entered by staff.

## Features

| ID | Feature | Planned capabilities |
| --- | --- | --- |
| FE-01 | System Administration and Configuration | Accounts, role and branch access, password recovery, audit logs, branches, room categories, rooms, time-based pricing, and combo packages. |
| FE-02 | Booking and Room Operations | Walk-in and advance bookings, availability checks, cancellation, check-in/out, room changes, extensions, no-shows, deposits, refunds, billing, and payments. |
| FE-03 | F&B, Inventory and Equipment Management | Menu and room-linked orders, QR ordering, stock receipts and adjustments, equipment assets, incident reports, repairs, and equipment transfers. |
| FE-04 | Workforce and Payroll Management | Staff records and branch assignments, shifts, attendance, monthly attendance closing, payroll, salary advances, Excel export, and salary payment records. |
| FE-05 | Financial Management and Reporting | Cash closing and reconciliation, funds, expenses, approvals, reimbursements, financial-period closing, dashboards, reports, and tax-accounting data export. |

The current scope excludes a customer booking portal, hardware barcode-scanner integration, statutory tax report generation, recipe/BOM-based ingredient management, content licensing management, and regulatory or safety compliance enforcement.

### Staff records (UC-77–82)

Migration numbering update: the staff, tax-invoice, shift-version and attendance-state migrations were renumbered from 21–24 to 26–29 without changing their SQL contents. Databases that already applied the old versions need their Flyway history coordinated with this rename before restarting the backend; do not rerun the same DDL or disable validation. Fresh databases use the new filenames directly. The renumbering does not add schema or business behavior.

The staff module implements create, view/search, update, deactivate/reactivate, initial home-branch assignment and transfer. The UI uses the supplied staff mockups; attachments are excluded from this implementation.

- APIs: `/api/v1/head-office/staff` and `/api/v1/branch-management/staff`; list, `/options`, `/{id}`, `/{id}/status`, `/{id}/branch`, and `/{id}/transfers`. Updates require the current record `version`; stale writes return HTTP 409.
- BR-52/177: branch managers access staff in their managed branch; suspended branches remain readable and reject changes. Initial assignment and transfers are head-office-only. A branch-manager-created record is assigned to the current branch as part of creation.
- BR-171: deactivating an employee revokes the linked login and tokens in the same transaction. Reactivating a record does not automatically restore login access. Transfers retain assignment history; linked receptionist access follows the new branch and existing sessions are revoked. Manager responsibilities remain separately managed.
- BR-111/119/199 and corresponding MSG references are recorded in the service and UI validation. Previous record values are retained in audit entries. Phone validation follows BR-119 (digits with an optional leading `+`, maximum 30 characters from the schema), not a ten-digit-only rule.
- Flyway migration `V26__staff_records.sql` adds position, contact email, notes, optimistic version and the account-lock trigger. Restart the backend to apply migrations before using the staff screens. No attachment storage or endpoints are introduced.

### Weekly shifts (UC-83–86)

The Branch Manager's **Ca làm & chấm công** menu now opens the weekly schedule. The receptionist's **Ca làm việc** menu opens `/le-tan/ca-lam`. The calendar uses seven day columns and four fixed shift rows, a sticky time column, today highlighting, staffing counts, employee names for managers, status text/color, search/filter, week navigation, and an accessible detail/confirmation dialog. Smaller screens scroll the calendar horizontally. Unpublished cells are explicit; no demonstration employees or shifts are saved.

- UC-85 / BR-53, BR-111, BR-192, BR-203: managers configure dates, fixed shifts, positive integer capacities and registration availability. At the user's request this covers both the **current** and upcoming Monday–Sunday week. The toolbar's **Mở / chỉnh ca tuần đang xem** opens the displayed week; an unpublished eligible cell has a **Mở ca** button that preselects that cell. The header shortcut still opens next week. Ended shifts, shifts with attendance and closed attendance months cannot be changed. Ongoing shifts without attendance remain editable until their end; closing registration retains existing assignments and capacity cannot fall below confirmed headcount. Each request must contain slots from one week and returns that same week's schedule. Four fixed local-time periods are 00:00–07:00, 07:00–12:00, 12:00–18:00 and 18:00–00:00 next day.
- UC-84: managers view/search/filter their assigned branch's schedules. BR-60: receptionists receive only their own assignment details; available-shift cards expose aggregate capacity, not coworker names or history.
- UC-83 / BR-54, BR-55, BR-56: receptionists self-register for open shifts at their home branch in the **current or upcoming week**, provided the shift has not started. This extends the original next-week-only requirement at the user's request, in step with current-week publication. Registration is automatically confirmed, duplicate retries are idempotent, and capacity is checked under transaction/row locks. Staff/account activity, current branch and assignment coverage for the shift are rechecked at confirmation. The calendar disables started shifts and labels them “Ca đã bắt đầu”; the server rechecks the actual start time at confirmation. No approval queue is introduced.
- User-approved UC-86 revision / BR-58: when A cannot work, the manager replaces A with an active receptionist B in the **same shift**. Candidates come from employee profiles with position `RECEPTIONIST` and a current assignment at that branch; accountants and branch managers are excluded. The server rechecks the receptionist position and assignment coverage when confirming, including direct API calls. The date, times and capacity are preserved. A's assignment is marked cancelled and retained with the reason; B receives a confirmed manager assignment. The new employee cannot already occupy the same/overlapping shift. Ended shifts, assignments with attendance, and closed attendance periods cannot be changed by this workflow. A replacement is permitted on a shift closed to new self-registration.
- BR-177 / MSG43: suspended branches remain readable and reject writes. Server authorization uses actual account/branch assignments. A global workforce/account lock coordinates transfers/status changes with shift operations; optimistic shift versions reject stale confirmations. The calendar reloads after successful changes and refreshes when focused/every 15 seconds outside dialogs. Editor data is not silently overwritten while a dialog is open.
- BR/MSG references are included near validation: MSG01, MSG02, MSG19, MSG24, MSG43 where applicable, MSG44, MSG84, MSG85. Technical stale/duplicate/overlap and replacement errors have explicit messages rather than invented BR numbers.

Migration `V28__shift_schedule_versions.sql` adds only a shift version and week-query index; existing `shift_template`, `scheduled_shift`, `shift_assignment`, `attendance_period` and audit tables are reused. Restart the backend to apply Flyway migrations. APIs: `GET /api/v1/branch-management/shifts?week=YYYY-MM-DD`, `PUT /api/v1/branch-management/shifts/availability`, `POST /api/v1/branch-management/shifts/assignments/{assignmentId}/replacement`; receptionist APIs are `GET /api/v1/reception/shifts` and `POST /api/v1/reception/shifts/{shiftId}/register`. Manager calls carry the selected branch header. Availability payload contains changed slots `{date,templateId,capacity,open,version}`; replacement uses `{employeeId,reason,version}`. Week dates start Monday and follow the branch timezone. Weekly responses include `editableSlots` (date/template keys computed using server time, attendance and period state) to disable locked editor controls; the server rechecks these conditions when saving.

**Documentation/mockup differences and team follow-up:**

- The first mockup and both requirement documents use the four fixed periods above. Other supplied mockups show three different shifts (08–14, 14–18, 18–23). Implementation follows BR-53, not those inconsistent time labels.
- Report 3 UC-86 currently describes moving an employee to another shift. The user explicitly chose **B covers A's existing shift**, not a date/time move or an exchange of two schedules. Update UC-86 description, steps 4–8, postconditions and the mockup's duplicate “Người hiện tại” labels to identify current employee / replacement employee.
- `Business Rules.docx` includes BR-57 forbidding cross-branch self-registration; Report 3 expresses the branch restriction through BR-54 and omits BR-57. The implementation enforces both meanings. The older Business Rules file also points attendance BR-59/61 to UC-88, while Report 3 uses UC-87; align those references before implementing attendance.
- The mockup does not clearly distinguish the viewed week from the week being configured. The calendar can view other weeks; the editor labels its actual target dates. The user extended manager publication from next week to the current week as well; update UC-85 to cover current-week opening/capacity/status adjustments before a shift ends, excluding shifts with attendance or finalized periods. Update UC-83 to allow receptionist self-registration in either week before the shift starts. This does not add an initial manager-assignment workflow.
- The documents do not define per-day/week maximum shifts, minimum rest time, a registration cutoff within the upcoming week, self-cancellation, or a required employee-to-employee agreement for replacement. No such limits, approval/cancellation flow or notifications were invented. Repeated names across all four daily shifts in the sample images are not a staffing policy.
- The receptionist registration screen is not provided in the screenshots. It reuses the calendar with self-registration actions and own-assignment privacy. Attendance entry and monthly closing are implemented separately below; payroll calculation remains outside the shift/attendance implementation.

### Daily attendance and monthly attendance sheets (UC-87–89)

The Branch Manager's **Ca làm & chấm công** menu has **Lịch ca tuần**, **Chấm công ngày / ca**, and **Bảng công tháng** tabs based on the supplied mockups. UC-88 supports searching by employee name/code, filtering by work date, fixed shift and attendance status, and viewing records/details. Monthly sheets show assigned shift count, actual worked hours, absence shifts, shifts with exceptions, missing/invalid records and unresolved attendance issues. Counts are shifts, not an assumed eight-hour workday. Historical employees remain visible after transfer/deactivation. Summaries always cover the whole selected branch/month, independently of the name filter.

- UC-87 / BR-59, BR-61, BR-111, BR-193, BR-194, BR-195, BR-204: only the branch's current authorized manager records/corrects a confirmed shift assignment. At the user's request the existing form now prefills a new record's entry/exit from its assigned shift, using **HH:mm-only** inputs. The manager edits these suggested times when actual work differs and reviews before confirming; no attendance is created merely by opening the form. Existing attendance retains its saved actual values. The UI combines the entered clock time with the date closest to the original actual timestamp (or planned endpoint for a new record), displays same/previous/next-day hints, and sends full dated timestamps to the unchanged API. This handles midnight checkout and an early 23:xx checkout before scheduled 00:00 without adding a spurious day. Worked minutes equal actual exit minus actual entry without deducting breaks. Absence clears actual timestamps and records zero minutes. Late arrival, early departure and time after the planned end are displayed as attendance exceptions; these labels do not calculate overtime pay.
- UC-89 / BR-62, BR-63, BR-64: closing checks every required assignment, existing record validity and open `attendance_issue` entries. Existing issues are shown in the daily record form; a manager may explicitly select resolved issues and supply a resolution while confirming corrected attendance. Issues are never automatically resolved merely by editing a record. Closing requires a separate confirmation that all employee attendance is correct. Closed records cannot be edited or reopened through these APIs.
- MSG02/07/20/24/43/86 are referenced at validation. BR-177 keeps suspended branch history readable and blocks all writes. Transactions use the shared workforce lock and attendance-period row lock; record versions and a monthly snapshot revision reject stale confirmations. Existing PostgreSQL closed-period guards remain enabled. Every record/correction and monthly closure retains an audit entry with before/after data.
- Additional operational guards: inputs have whole-minute precision in the branch timezone; future actual exit times/work dates cannot be recorded. Closing is available only after the calendar month and its final overnight shift have ended, and only for a nonempty, complete sheet. Notes/resolutions are limited to 2000 characters. Invalid legacy records (including records that deduct breaks or lack required actual times) must be corrected before closure; they are not silently counted as valid work.

Migration `V29__attendance_record_state.sql` adds explicit `WORKED`/`ABSENT` status and optimistic versions, reusing the existing attendance, attendance-period and attendance-issue tables. It fixes the attendance trigger's schema references and locks issue changes against finalized periods, including concurrent reports while closing. Restart the backend to apply it. APIs: `GET /api/v1/branch-management/attendance?date=YYYY-MM-DD`, `GET /api/v1/branch-management/attendance/month?month=YYYY-MM`, `POST /api/v1/branch-management/attendance/{assignmentId}/record` with `{status,actualIn,actualOut,note,version,resolvedIssueIds,resolution}`, and `POST /api/v1/branch-management/attendance/month/{month}/close` with `{revision,confirmedCorrect}`. Calls use the selected branch header. Month ownership follows the assigned shift's work date, including shifts ending in the next month.

Team follow-up: Report 3 assigns Record Daily Attendance to UC-87 and View Attendance Records to UC-88; the older Business Rules file still labels attendance recording UC-88. Align those references and document the operational guards above. Receptionist personal work-record viewing/reporting (UC-90) and accountant payroll processing (UC-91 onward) are not added by this task. Finalized attendance is stored for the later payroll module; this implementation does not calculate salary.

### Account creation from staff profiles

Staff contact uniqueness (user-requested extension to UC-77/79 and BR-134): create/update checks contact email against all other employee profiles and login accounts, including inactive records. Email comparison ignores case and surrounding spaces. Staff phone numbers are also unique across employee profiles after trimming; BR-119 syntax remains unchanged, and no conversion between local/international prefixes is performed. Optional blank contact fields remain allowed. A record and its own linked account may share the same email; retaining one's own contact values is allowed when no other record conflicts.

The same checks apply when changing account-profile contact information and creating accounts from older staff records. All these write paths use the existing transaction advisory lock so simultaneous application writes cannot reserve the same email/phone. Duplicate errors are returned against `contactEmail`/`phone` in staff forms and `email`/`phone` in account forms. Existing duplicates are not automatically changed or deleted; the responsible manager must correct the conflicting profile before account creation. No migration or new test source file is introduced. The team should document staff phone uniqueness and the expanded email uniqueness rule; BR-119 alone only defines phone syntax.

Contact validation was checked against PostgreSQL in an always-rolled-back transaction, including create/update conflicts, case-insensitive email, trimmed contacts, inactive records, own linked account, optional blanks, existing conflicting account data, and the shared lock. Existing `AccountProfileServiceTest` reports 25/29 passing: four cases still require the former 7–15-digit, leading-zero, no-plus phone policy, which differs from the current BR-119. The test source and existing phone-format validation were kept unchanged.

UC-04 now takes the selected employee's position, current home branch and contact email from the database, ignoring client overrides. The create screen displays those values read-only; only employee, username and password are submitted. Missing email, unsupported position or missing branch for REC/BM must be resolved in the employee profile first. HOM/ACC retain chain-wide access. Later role changes use the existing permission workflow; editing the staff position does not silently change an existing login role. This replaces manual role/branch/email selection at account creation at the user's request.

### Tax invoice request tracking (UC-127)

User-approved revision to UC-127 / BR-125: the receptionist records and sends a booking's tax invoice request from checkout at `/le-tan/hoa-don-thue`, then has read-only access to progress and delivery results. The accountant reviews/confirms progress updates at `/ke-toan/hoa-don-thue`. Requests retain buyer details, notes, delivery results and an audit history. Issuance and delivery take place outside GCMB; this feature never changes booking charges or payments. BR-177 still allows reading but blocks writes at a suspended branch.

The optional customer note is labeled “Yêu cầu bổ sung của khách” when creating a request. The accountant progress form has no processing-note field. The confirmation summary and compact action buttons occupy separate rows.

The detail screen separates saved information from progress edits and explains delivery-result requirements. Switching to the request list offers a return button to the last booking and preserves unsaved fields per booking in component memory, including a new request not yet present in the list. Drafts are cleared after saving or leaving the screen; a draft is restored only if the server version is unchanged. The tax-invoice navigation item includes a receipt icon for both receptionist and accountant menus.

The requested addition beyond Report 3 is an accountant inbox at `/ke-toan/hoa-don-thue`, with a persistent per-account read receipt and an unread notification refreshed every 30 seconds. Accountants view requests, mark notifications read and update progress and delivery results. Receptionists create requests and view updates; they cannot change handling progress. There are no external email sends, invoice issuance integrations or attachments. Implementation statuses are `REQUESTED`, `PREPARING`, `ISSUED`, `DELIVERED`, and `DELIVERY_FAILED`; delivery outcomes require a predefined result selection. Updates require a current version to prevent overwriting another user's change.

Migration `V27__tax_invoice_requests.sql` adds request/read-receipt storage. APIs use `/api/v1/reception/tax-invoices` and `/api/v1/accounting/tax-invoices`; each request is identified by its booking UUID. Restart the backend to apply migrations.

The single “Trạng thái xử lý” dropdown has two groups with detailed choices: “Đã giao thành công” (email, direct handover, Zalo/contact channel) and “Chưa giao thành công” (invalid contact, unreachable customer, sending failure). Pending/preparing/externally-issued stages are not selectable. Selecting a detailed outcome maps to the matching existing status/result fields. The saved summary and list display that same detailed outcome only after the confirmed API update succeeds. Requests without a result display “Chưa giao thành công”; the dropdown requires an explicit outcome before saving. Earlier values remain readable. The read button stays at the bottom; processing notes remain removed. No database migration is needed.

The update endpoint is `PUT /api/v1/accounting/tax-invoices/{bookingId}`. Receptionists cannot update progress through either the UI or service/API; accountants cannot create requests on behalf of receptionists. Customer notes submitted at creation remain available in the creation audit snapshot when processing notes are updated. No new database migration is needed for this role change.

Team handoff: Report 3 UC-127 currently names Receptionist as its primary actor, and BR-125 assigns progress updates to Receptionist. Update both to Accountant and revise the permission matrix: REC creates/reads requests; ACC reads/updates progress and delivery. This user-approved workflow supersedes those passages; the original Word specification has not been edited.

The staff review covers BR-52/111/119/171/177/199, email validation reused from BR-113, and MSG01–05/10–12/14/24/26/27/40/43/88. UC-81/82 history and confirmation requirements are checked directly against the use-case text. Technical conflicts and duplicate employee identifiers use explicit messages because Report 3 does not assign a staff-specific message to those cases.

Validation note (27/10/2026): the database rollback checks pass for profile-derived account creation and invoice request lifecycle, read receipts, branch authorization, stale versions and suspended-branch access. Existing `AccountServiceTest` has not been edited per instruction: 14/30 cases pass; 16 fail/error because its fixtures and assertions still target manually supplied role/branch/email and do not provide the new employee-profile lookup. The team must update those tests to the revised UC-04 contract before requiring a green full test suite.

## Tech Stack

| Area | Technology |
| --- | --- |
| Frontend | React with Create React App, JavaScript, JSX, plain CSS |
| Backend | Java 17, Spring Boot, Maven Wrapper |
| Database | PostgreSQL with version-controlled SQL migration scripts |
| Communication | REST API with JSON |
| External integrations | SePay, email service, cloud storage |
| Version control | Git and GitHub |
| Testing | JUnit and API testing tools such as Postman |
| Deployment target | Docker on VPS/Cloud |

Dependency versions are defined in `backend/pom.xml`, `frontend/package.json`, and the frontend lockfile. Use the versions committed by the team.

## Architecture

GCMB uses a Web Client–Server architecture. The React frontend calls the centralized Spring Boot REST API, and the backend accesses PostgreSQL and external services.

The backend is deployed as one application. The current backend contains seven peer feature packages: `auth`, `accounts`, `audit`, `branch`, `pricing`, `booking`, and `inventory`. Each feature keeps its own technical layers. The five feature areas above describe business scope, not a required Java package hierarchy. Frontend organization is maintained separately.

- Keep business rules, authorization, and branch-scope checks in backend services.
- Use DTOs for API requests and responses.
- Expose explicit service operations when modules need to collaborate.
- Keep business-specific code and supporting technical classes within their owning feature. The current backend does not introduce `shared/` or `infrastructure/` packages.

## Project Structure

The tree below reflects the current repository structure. `inventory` and `workforce` are active backend feature packages. Finance remains in the planned business scope.

```text
GCMB/
├── backend/
│   ├── src/main/java/com/gcmb/backend/
│   │   ├── auth/
│   │   ├── accounts/
│   │   ├── audit/
│   │   ├── branch/
│   │   ├── pricing/
│   │   ├── booking/
│   │   ├── inventory/
│   │   ├── workforce/
│   │   └── BackendApplication.java
│   ├── src/main/resources/
│   │   ├── application.yaml
│   │   ├── application-dev.yml
│   │   └── db/migration/
│   ├── src/test/
│   ├── .mvn/wrapper/
│   ├── pom.xml
│   ├── mvnw
│   └── mvnw.cmd
├── frontend/
│   ├── public/
│   ├── src/
│   │   ├── features/
│   │   │   ├── auth/
│   │   │   ├── backoffice/
│   │   │   ├── booking/
│   │   │   ├── branch/
│   │   │   ├── inventory/
│   │   │   ├── landing/
│   │   │   ├── pricing/
│   │   │   └── workforce/
│   │   ├── shared/
│   │   │   ├── components/
│   │   │   └── utils/
│   │   ├── layouts/
│   │   ├── routes/
│   │   ├── api/
│   │   ├── assets/
│   │   │   ├── images/
│   │   │   └── styles/
│   │   ├── App.js
│   │   ├── App.css
│   │   ├── index.js
│   │   └── index.css
│   ├── package.json
│   └── package-lock.json
└── README.md
```

| Module | Ownership |
| --- | --- |
| `auth` | Login/logout, sessions, password recovery/change, security configuration, recovery email, and head-office dashboard navigation restored from the URL. |
| `accounts` | User accounts, personal profiles, roles, and access management. |
| `audit` | Mutation audit recording and audit-history lookup. |
| `branch` | Branches, manager assignments, rooms, room operating status, and branch-manager dashboard navigation restored from the URL. |
| `pricing` | Room types, room prices, pricing rules, and combo packages. |
| `booking` | Reservations, room sessions, deposits, checkout bills, customer payments, and tax invoice request tracking. |
| `inventory` | F&B catalog, guest QR menu and cart persistence across reloads, room/staff order processing, service assignment, inventory item management, stock receipts, adjustments, and movement history. |
| `workforce` | Staff records, branch assignments/transfers, shift schedules, and attendance. Payroll remains planned. |
| `finance` (planned) | Funds, expenses, reimbursements, cash reconciliation, and financial reporting. |

F&B charges are linked to an active booking and included in its checkout bill. Order processing and inventory updates belong to `inventory`; bill calculation belongs to `booking`. Accountant responsibilities span `booking`, `workforce`, and `finance`.

Within a backend feature, create `controller/`, `service/`, `repository/`, `entity/`, and `dto/` as needed. Within a frontend feature, use `pages/`, `components/`, `services/`, `hooks/`, and `styles/` as needed.

Backend package reorganization (24/10/2026): the former `backoffice/` directory and `com.gcmb.backend.administration.*` declarations were replaced by the six peer packages then in scope. All existing classes were retained. `branch`, `pricing`, and `booking` kept their existing responsibilities; this change did not extract a `rooms` module or change API routes, database schema, or business behavior. Tests follow the same package hierarchy. The `inventory` backend package was added later for F&B catalog and order processing.

`auth` has six direct subpackages: `controller`, `dto`, `repository`, `service`, `security`, and `exception`. Request/response records and `LoginResult` live in `dto`; internal JDBC models `Account` and `Reset` live in `repository/model`; `AuthMapper` lives in `repository`; `AuthValidation` lives in `service`; `AuthSecurity` and `AuthSettings` live in `security`. Internal models and `LoginResult` are not automatically exposed as HTTP responses by their package location.

Frontend account management lives in `frontend/src/features/backoffice/accounts/`: `pages/` contains `AccountsPage` and `AuditLogPage`; `components/` contains account dialogs and the password form; `services/` contains API clients; `styles/` contains CSS. `CreateAccountDrawer` remains inside `pages/AccountsPage.js`. The parent dashboard remains at `frontend/src/features/auth/components/HeadOfficeDashboard.js`.

## Getting Started

### Prerequisites

- JDK 17; `JAVA_HOME` must point to the JDK.
- Node.js and npm compatible with the committed frontend dependencies.
- PostgreSQL running locally.
- Git.

Maven Wrapper is used, so a separate Maven installation is optional. Docker is optional for local development and is used for the planned container deployment.

### 1. Clone the repository

Replace `YOUR_REPOSITORY_URL` with the team's Git repository URL. The final argument sets the local folder name.

```bash
git clone YOUR_REPOSITORY_URL GCMB
cd GCMB
```

### 2. Create a development database

Run the following in pgAdmin Query Tool or `psql` using a PostgreSQL administrator account. Replace the example password before executing.

```sql
CREATE USER GCMB_dev WITH PASSWORD 'REPLACE_WITH_LOCAL_PASSWORD';
CREATE DATABASE GCMB OWNER GCMB_dev;
```

PostgreSQL is separate from SQL Server. GCMB uses the PostgreSQL database and connection settings below.

### 3. Configure and run the backend

Ensure `application-dev.yml` uses the [configuration example](#configuration) below, or adjust the environment variables to match the project's existing configuration.

**Windows PowerShell — from the repository root:**

```powershell
cd backend
$env:SPRING_PROFILES_ACTIVE = "dev"
$env:DB_URL = "jdbc:postgresql://localhost:5432/GCMB"
$env:DB_USERNAME = "GCMB_dev"
$env:DB_PASSWORD = "REPLACE_WITH_LOCAL_PASSWORD"
.\mvnw.cmd spring-boot:run
```

**macOS/Linux — from the repository root:**

```bash
cd backend
export SPRING_PROFILES_ACTIVE=dev
export DB_URL='jdbc:postgresql://localhost:5432/GCMB'
export DB_USERNAME='GCMB_dev'
export DB_PASSWORD='REPLACE_WITH_LOCAL_PASSWORD'
./mvnw spring-boot:run
```

The example configuration runs the backend at **http://localhost:8080**. Keep this terminal open.

### 4. Configure and run the frontend

Create `frontend/.env.local`:

```dotenv
REACT_APP_API_BASE_URL=http://localhost:8080/api/v1
```

`/api/v1` is the proposed API base path. Match it to the backend routes, and ensure the shared API client in `src/api/` reads `process.env.REACT_APP_API_BASE_URL`.

Open a second terminal at the repository root.

**Windows PowerShell:**

```powershell
cd frontend
npm.cmd ci
npm.cmd start
```

**macOS/Linux:**

```bash
cd frontend
npm ci
npm start
```

`npm ci` requires a committed `package-lock.json` matching `package.json`. If the project has no lockfile yet, run `npm install` once and commit the generated lockfile.

Open **http://localhost:3000**. Create React App uses `npm start`. Restart the frontend after changing `.env.local`.

### Troubleshooting

Backend entry point: `com.gcmb.backend.BackendApplication`, tại `backend/src/main/java/com/gcmb/backend/BackendApplication.java`. Maven chỉ định lớp này trong `pom.xml`; cấu hình Run của IDE cũng cần dùng đúng tên package. Nếu IDE còn chọn `com.gcmb.BackendApplication`, xóa cấu hình Run cũ và chọn lại lớp hiện tại.

| Problem | Check or action |
| --- | --- |
| PowerShell blocks `npm.ps1` | Use `npm.cmd` as shown above. |
| Backend cannot connect to PostgreSQL | Check the database service, port, database name, username, and password. |
| Backend reports a missing table | Confirm the database migrations have been configured and applied. |
| Frontend cannot call the API | Check the backend is running, the API base path is correct, and backend CORS allows `http://localhost:3000`. |
| Java version mismatch | Check `java -version`, `JAVA_HOME`, and the IDE project SDK; use JDK 17. |
| `Unresolved compilation problem` at `BackendApplication.main` while Maven says `Nothing to compile` | Stop the development servers, run `.\mvnw.cmd clean compile` from `backend/`, then run `npm.cmd start` from `frontend/`. This rebuilds generated classes after an IDE compilation or entry-point package change. Ensure the IDE uses `com.gcmb.backend.BackendApplication`. |

## Configuration

| File | Purpose |
| --- | --- |
| `application.yml` | Common backend settings. |
| `application-dev.yml` | Local development settings. |
| `application-prod.yml` | Production settings using environment-supplied secrets. |
| `frontend/.env.local` | Local frontend configuration; exclude from Git. |

Example `backend/src/main/resources/application-dev.yml`:

```yaml
server:
  port: 8080

spring:
  datasource:
    url: ${DB_URL:jdbc:postgresql://localhost:5432/GCMB}
    username: ${DB_USERNAME:GCMB_dev}
    password: ${DB_PASSWORD}
```

Set the active profile through `SPRING_PROFILES_ACTIVE`. Backend `.env` files are not loaded automatically by Spring Boot; supply variables through the terminal, IDE run configuration, or deployment environment.

Keep database passwords and SePay, email, and cloud-storage credentials outside source control. Frontend `REACT_APP_*` variables are exposed in the browser bundle and must contain only public configuration.

### Database migrations

Store schema changes under `backend/src/main/resources/db/migration/`. For Flyway, use names such as `V1__create_initial_schema.sql` and `V2__add_booking_indexes.sql`.

The directory alone does not run migrations. If the team selects Flyway, configure its compatible dependencies in `pom.xml`, including PostgreSQL support where required. With Flyway enabled, migrations can run at backend startup. Keep schema generation under migration control; use Hibernate `ddl-auto: validate` when the migrated schema is ready.

Add a new migration for changes to an already-shared schema. Preserve applied migrations and test changes against a development database.

## Usage

Cơ sở tạm ngừng có thể mở lại bằng **Mở lại cơ sở** và xác nhận; hệ thống ghi nhật ký `REACTIVATE_BRANCH`, chuyển về ACTIVE và xóa lý do trạng thái hiện tại, giữ lịch sử tạm ngừng. API: `POST /api/v1/head-office/branches/{id}/reactivate` dành cho quản lý cấp cao, chỉ nhận cơ sở SUSPENDED (gọi lại với ACTIVE không đổi dữ liệu). Màn cơ sở không còn nút **Đóng cơ sở**; bản ghi CLOSED cũ vẫn được giữ để tra cứu. Lịch sử sửa thông tin không dùng lại lý do tạm ngừng; ghi chú vận hành và lý do trạng thái là hai thông tin riêng. Không cần migration.

### Checkout, bill và SePay (14/10/2026)

Lễ tân chọn phòng đang sử dụng → **Checkout & lập bill**. Kiểm tra tiền phòng theo giá đã xác nhận, F&B đã SERVED (không tính lại món combo giá 0), phụ phí có lý do và xác nhận đã kiểm tra phòng. **Tiếp tục thanh toán** chốt bill và thời điểm ngừng tính sử dụng; sau đó phí/order/đổi phòng/gia hạn bị khóa. Khách trả sớm vẫn giữ các khoản booking đã xác nhận; không tự tính lại bằng bảng giá mới. Nếu đã quá giờ mà chưa có phiên giờ lẻ, cần xác nhận gia hạn trước; đồng hồ giờ lẻ đang chạy được dừng bằng service hiện hành khi chốt bill.

Trong thanh toán chọn **Tiền mặt**, **SePay** hoặc **Kết hợp**. Phần tiền mặt nhập khi tạo QR được lưu thành phân bổ tạm trên bill, chưa tạo payment và không hiển thị là đã thu. **Đổi phương thức** hủy QR cũ, xóa toàn bộ phân bổ tạm và đưa form về tổng bill ban đầu. Bill 170.000đ nhập tạm 50.000đ tiền mặt + QR 120.000đ: sau đặt lại vẫn cần thanh toán 170.000đ, không giữ 50.000đ. Khi SePay đã xác nhận chuyển khoản hoặc có khoản thu thực tế, không cho đặt lại. Webhook của mã đã hủy được lưu EXCEPTION, không cộng vào bill; hủy QR không hủy giao dịch tại ngân hàng. Lễ tân bấm **Hoàn tất thanh toán** mới ghi tiền mặt, tiền khách đưa và tiền thừa trong cùng giao dịch kết thúc bill. Trường readyToComplete cho biết tổng khoản thu thực tế + tiền mặt dự kiến bằng đúng net_amount; tổng paid vẫn chỉ gồm payment thật. QR có hạn 30 phút, có thể gia hạn cùng mã sau hết hạn. Hoàn tất chuyển bill SETTLED, booking COMPLETED, allocation FINISHED và phòng AVAILABLE (Trống); gửi lặp không thu hai lần. Menu **Thanh toán** tra cứu 100 bill gần nhất của cơ sở; có in bill qua trình duyệt.


Dữ liệu hoàn cọc local (15/10/2026), tài khoản test.rec.ha tại TEST_CS_B: phòng TEST_REFUND_01; BK-20261015-E6426634 cọc 100.000đ và BK-20261015-53FBBC28 cọc 150.000đ đã hủy sớm, có yêu cầu REQUESTED; BK-20261015-D79299DC cọc 80.000đ đã hủy đủ điều kiện, chưa gửi yêu cầu. Thông tin nhận hoàn dùng TEST BANK / 0000000000, hoàn toàn giả lập. Đây là dữ liệu thêm theo yêu cầu trên database local, không phải migration và chưa ghi nhận khoản hoàn thực tế.

### Ghép tiền cọc vào checkout (15/10/2026)

Đã ghép màn Tiền cọc của Thanh vào AppShell chung, giữ cả menu Tiền cọc và Thanh toán. Màn Tiền cọc chờ xác thực trước khi dựng AppShell; thanh tài khoản dùng chung xử lý trường hợp chưa có user và khóa thao tác tài khoản trong thời gian đó. AuthErrors xử lý cả ResponseStatusException và MethodArgumentTypeMismatchException. Các endpoint danh sách/chi tiết/yêu cầu hoàn cọc được giữ cùng MutationAudit của booking.

Khi lập bill, GCMB khóa booking và ví cọc, kiểm tra yêu cầu hoàn đang REQUESTED/APPROVED/PROCESSING, rồi ghi deposit_entry.kind=APPLY cho số dư cọc đã nhận. Không ghi thêm payment cho cọc, không sửa sổ cọc gốc; gọi lặp không trừ lần hai. Tổng bill vẫn giữ nguyên; depositApplied là cọc đã trừ, paid là khoản thu thêm, remaining = amount - depositApplied - paid. Tiền mặt/QR chỉ thu phần remaining. Ví dụ bill 170.000đ, cọc 50.000đ: cần thu thêm 120.000đ. Đổi phương thức chỉ hủy phân bổ tiền mặt/QR tạm, không hủy cọc đã áp dụng. Hoàn tất khi depositApplied + paid + plannedCash bằng tổng bill. Nếu cọc bằng bill thì hoàn tất không cần thu thêm.

API preview checkout trả depositBalance và hasPendingRefund; API bill trả depositApplied. Màn Tiền cọc có trạng thái APPLIED (Đã trừ vào bill) và lịch sử APPLY, không làm mất hồ sơ sau khi dùng hết cọc. Chưa triển khai hoàn cọc dư khi checkout: dữ liệu cọc âm/vượt tổng bill bị từ chối để kiểm tra; luồng yêu cầu hoàn của Thanh vẫn áp dụng cho booking đã hủy. Không có migration mới, dùng sổ cọc hiện hữu V1/V8/V9 và bill V12/V13.

Customers scan the in-room QR code to view the F&B menu and submit an order for their active room session.

Obtain development accounts through the team's agreed seed or account-provisioning process. No demo credentials are specified here.

V14 seed correction (24/10/2026): the six starter rooms per seeded branch are initial data, not a business limit. Branches can retain existing rooms and add more through room management. V14 no longer requires exactly six rooms or all existing rooms to be AVAILABLE; it retains the service-mode consistency check. Room inserts still use `ON CONFLICT (branch_id, code) DO NOTHING`, preserving existing room details and status. This correction targets databases where V14 has not succeeded. If V14 has already been applied on another environment, coordinate migration history before distributing the edited script; do not reset that database or rewrite its Flyway checksum automatically.

## Testing

Run commands from the corresponding project directory.

| Check | Windows PowerShell | macOS/Linux |
| --- | --- | --- |
| Backend tests (`backend/`) | `.\mvnw.cmd test` | `./mvnw test` |
| Backend verification (`backend/`) | `.\mvnw.cmd verify` | `./mvnw verify` |
| Frontend tests (`frontend/`) | `npm.cmd test -- --watchAll=false` | `npm test -- --watchAll=false` |
| Frontend production build (`frontend/`) | `npm.cmd run build` | `npm run build` |

These commands assume the standard Maven and Create React App scripts are present. Database-dependent tests must use a separate test database and the configuration defined by the test suite.

Prioritize tests for booking conflicts, role and branch access, bill/deposit calculations, stock movements, payroll calculations, and financial reconciliation. API/system tests and UAT should cover complete workflows and their failure cases. Do not use production customer or financial data for testing.

## Contributing

Work on a separate branch and submit a pull request to the team's integration branch. Agree on the integration branch name before adopting a `develop` workflow.

Use descriptive branch names such as `feature/booking-create` or `fix/booking-conflict`. Use concise commit messages such as `feat: add booking creation`, `fix: validate overlapping bookings`, or `docs: update local setup`.

Before opening a pull request:

- Synchronize with the integration branch and resolve conflicts.
- Run checks relevant to the change and report the results.
- Describe the resulting behavior and any required configuration or migration.
- Update documentation when setup, APIs, or behavior changes.
- Check that no secrets or local generated files are included.

Review and test changes before merging. Keep `node_modules/`, `build/`, `target/`, and local environment files out of Git; preserve examples such as `.env.example` and Maven Wrapper files.

## Documentation

The detailed project references are:

- Report 1 — Project Introduction.
- Report 2 — Project Management Plan.
- GenZ Use Case List.
- GenZ Cinema AS-IS / TO-BE workflows.

Keep detailed requirements, design, business rules, and test documentation outside this README. Add repository-relative links when approved documents are placed in `docs/`, or link to their authorized shared location.

README structure reference: [How to Write a Good README File — freeCodeCamp](https://www.freecodecamp.org/news/how-to-write-a-good-readme-file/).

Technical setup references: [Create React App environment variables](https://create-react-app.dev/docs/adding-custom-environment-variables/), [Spring Boot profiles](https://docs.spring.io/spring-boot/reference/features/profiles.html), and [Flyway documentation](https://documentation.red-gate.com/flyway).

## Team

| Name | Project role |
| --- | --- |
| Nguyen Minh Cao | Team Leader |
| Dao Van Huy | Member |
| Cu Thi Huyen Trang | Member |
| Nguyen Van Trong | Member; business stakeholder as Branch Manager |
| Nguyen Tuan Thanh | Member |

**Supervisor:** Nguyen Thi Hanh.

## License

GCMB is developed as an FPT University capstone project. A software license has not been specified in the supplied project materials. The team should agree on usage and distribution terms and add a `LICENSE` file before granting reuse rights.


Reception merge (15/10/2026): màn Phòng giữ bộ lọc ngày và lấy giờ hiện tại cộng 5 phút, không có ô chọn giờ riêng. AppShell dùng giao diện chung và ẩn ô tìm kiếm trên topbar khi màn Phòng đã có ô tìm kiếm. Giữ luồng đổi phòng/gia hạn và nối Checkout & lập bill tới các route thanh toán.

Checkout và thu gọn giao diện (24/10/2026): hoàn tất thanh toán/bill trả phòng trực tiếp về AVAILABLE (Trống), không còn bước Đang dọn hoặc nút Dọn xong. Bỏ API POST /api/v1/reception/rooms/{roomId}/finish-cleaning và không cho chọn CLEANING tại API/giao diện quản lý phòng. V16__retire_room_cleaning_status.sql chuyển phòng CLEANING cũ về AVAILABLE, xóa giờ dự kiến sẵn sàng và thêm ràng buộc không tạo trạng thái CLEANING mới; giữ nguyên booking, allocation và lịch sử trạng thái cũ. Lịch đặt tiếp theo và các kiểm tra trùng phòng vẫn áp dụng. Menu bên trái ở khung lễ tân/kế toán và bảng Phòng đang chọn bên phải có nút thu gọn/mở lại, dùng được bằng bàn phím; thu gọn không mất lựa chọn phòng hay bộ lọc. Trên màn hình nhỏ, menu thu gọn được ẩn thay vì chiếm một cột.

Checkout sớm và đặt lại phòng (24/10/2026): V17__release_finished_room_allocations.sql đồng bộ ràng buộc `no_room_overlap` với kiểm tra phòng trống/tạo booking: chỉ allocation RESERVED hoặc OCCUPIED giữ lịch phòng; FINISHED và RELEASED không chặn booking mới. Sau checkout đã hoàn tất, có thể đặt lại phòng dù giờ trả dự kiến của lượt trước chưa đến. Giữ nguyên toàn bộ mốc giờ và lịch sử booking/allocation; không thay đổi ràng buộc `no_booking_parallel_rooms`. Booking còn CONFIRMED/IN_HOUSE vẫn phải vượt qua kiểm tra trùng lịch hiện hành.

### Cọc QR 30% và nhiều quản lý cơ sở (25/10/2026)

- Khi bật thu cọc, số tiền bằng 30% giá booking do backend báo (combo hoặc tính giờ), làm tròn HALF_UP đến đồng. Đây là quy tắc mới thay cho mức cọc cấu hình riêng của combo trong luồng tạo booking. Không sửa các khoản cọc lịch sử. Tiền mặt chỉ ghi nhận khi lễ tân đã nhận tiền; chuyển khoản không còn ghi nhận thu thủ công lúc tạo booking.
- Chọn Chuyển khoản rồi bấm **Tạo booking & lấy QR cọc**: tạo booking giữ phòng, sau đó hiển thị QR có số tiền và mã tham chiếu riêng. Lưu mã booking trong bản nháp phiên để tải lại trang không tạo booking lần hai. Rời QR vẫn giữ booking; có thể mở **QR cọc 30%** trong chi tiết booking CONFIRMED chưa nhận cọc.
- API mới: `POST /api/v1/reception/bookings/{id}/deposit-payment` tạo/dùng lại yêu cầu cọc; `GET` cùng đường dẫn đọc trạng thái. Giới hạn booking thuộc cơ sở của lễ tân. Phản hồi gồm `status`, `bookingId`, `bookingCode`, `amount`, `receivedAmount`; yêu cầu chưa thu còn có `reference`, `expiresAt`, `bank`, `account`, `holder`, `qrUrl` khi còn hiệu lực. QR có hạn 30 phút; lấy lại QR sẽ hủy yêu cầu cũ đã hết hạn hoặc lệch giá. Không nhận cọc lần hai.
- Dùng cấu hình SePay hiện có: `SEPAY_BANK`, `SEPAY_ACCOUNT_NUMBER`, `SEPAY_ACCOUNT_HOLDER`, `SEPAY_WEBHOOK_KEY` trong `backend/.env`. Webhook `/api/v1/payments/sepay/webhook` xác thực và đối chiếu mã, tài khoản, số tiền, thời gian; chỉ sau đó tạo payment DEPOSIT và bút toán RECEIVE. Gửi lại cùng giao dịch không ghi trùng. Giao dịch sai số tiền, QR cũ, booking đã hủy/check-in hoặc giá đã đổi được lưu EXCEPTION để đối chiếu, không tự ghi nhận cọc. Không nhập khóa webhook trên frontend. Nhật ký mới: PLAN_DEPOSIT_PAYMENT, CONFIRM_SEPAY_DEPOSIT.
- Mỗi cơ sở có tối đa **2 quản lý**, một quản lý có thể phụ trách nhiều cơ sở. Gán thêm giữ nguyên cả các quản lý hiện tại lẫn những cơ sở người đó đang phụ trách. Áp dụng chung cho màn Cơ sở và phân quyền/tạo tài khoản. API cơ sở trả các tên/mã/tài khoản quản lý nối bằng dấu phẩy và `managerCount`, thay trường `managerId` đơn. Thu hồi/chuyển quản lý qua phân quyền tài khoản như trước.
- Migration mới `V18__allow_three_branch_managers.sql` đổi khóa chính phân công thành `(branch_id,user_id)`, giữ UNIQUE user_id và thêm trigger giới hạn 3. Không thay V1–V17 đã chạy; Flyway áp dụng V18 khi backend khởi động. Không tạo thêm file test.

### Quản lý nhiều cơ sở và chọn cơ sở trên dashboard (25/10/2026)

- Đây là cập nhật yêu cầu sau Report 2/3: quan hệ quản lý–cơ sở là nhiều–nhiều; mỗi cơ sở tối đa 2 quản lý. V19__managers_with_multiple_branches.sql bỏ UNIQUE user_id của phân công và bổ sung auth_token.selected_branch_id. Giữ nguyên V18 đã áp dụng và các phân công hiện hữu. Backend cần khởi động lại để Flyway chạy V19 trước khi dùng code mới.
- Màn Tài khoản → Vai trò & phạm vi truy cập: Quản lý cơ sở có thể tích nhiều cơ sở; danh sách gửi trong branchIds của API đổi quyền. Danh sách này thay thế toàn bộ phân công của tài khoản trong một transaction; bỏ chọn là thu hồi quyền tại cơ sở đó. Nếu phân công mới khiến một cơ sở vượt 2 quản lý, từ chối toàn bộ thay đổi. Người dùng cần đăng nhập lại sau khi đổi quyền như trước. branchId vẫn là cơ sở mặc định để tương thích dữ liệu/tài khoản cũ. AccountView bổ sung managedBranchIds và hiển thị tên tất cả cơ sở; lọc tài khoản theo cơ sở xét mọi phân công quản lý.
- Màn Cơ sở → Thêm quản lý chỉ thêm phân công, không chuyển người khỏi cơ sở khác; gọi lại phân công đã tồn tại không tạo trùng. Danh sách ứng viên không bị lặp khi người đó có nhiều cơ sở.
- Dashboard quản lý: dropdown **Cơ sở đang quản lý** cùng hàng với Thông tin tài khoản chỉ liệt kê các cơ sở ACTIVE được phân công. Đổi lựa chọn cập nhật phiên, tiêu đề cơ sở và tải lại dữ liệu, xóa bộ lọc/biểu mẫu phòng cũ. Các menu chưa triển khai vẫn là màn chờ theo phạm vi cơ sở đang chọn.
- API mới cho BRANCH_MANAGER: GET /api/v1/auth/branches trả [{id,name}], POST /api/v1/auth/branch với {branchId} trả Identity của cơ sở đã chọn. Có CSRF và kiểm tra phân công/cơ sở ACTIVE tại server; không thể chọn cơ sở ngoài quyền bằng cách sửa request. Lựa chọn được nhớ theo phiên đăng nhập. Nếu cơ sở bị thu hồi hoặc tạm ngừng, danh tính chọn lại một cơ sở còn hợp lệ; không còn cơ sở nào thì không tiếp tục sử dụng phiên quản lý.
- API phòng quản lý gửi X-GCMB-Branch-Id theo màn hình đang mở. SessionFilter xác minh lại phân công trước khi đặt phạm vi Identity, giúp các tab đang xem cơ sở khác nhau không ghi nhầm dữ liệu khi phiên đổi lựa chọn. Khách API không gửi header dùng cơ sở đang chọn của phiên. Header không tự cấp thêm quyền.
- File ảnh hưởng: branch/BranchAdministrationService, auth/{AuthRepository,AuthService,AuthController,SessionFilter,AuthSecurity}, accounts/{DTO,AccountRepository,AccountAccessService}; frontend BranchManagerDashboard, DashboardTopbar, RoleHomePage, AccountAccessDialog, BranchManagementPage, BranchRoomsPage và các API/styles liên quan. Không thêm file test.

### Giới hạn hai quản lý và bố trí dropdown (25/10/2026)

- Yêu cầu hiện hành: mỗi cơ sở tối đa 2 quản lý; một quản lý vẫn có thể quản lý nhiều cơ sở. BranchAdministrationService chặn người thứ ba, kể cả khi gán từ màn tài khoản; giao diện phân quyền và bộ đếm cơ sở hiển thị giới hạn 2.
- Dropdown cơ sở nằm cùng hàng với tên đăng nhập, Thông tin tài khoản và Đăng xuất trên desktop; màn hình nhỏ được xuống hàng để không tràn. Thay đổi CSS tại frontend/src/features/branch/pages/branch-rooms.css.
- V20__limit_two_branch_managers.sql thay hàm trigger giới hạn 3 thành 2, không sửa V18/V19 đã phát hành. Migration kiểm tra phân công hiện hữu và báo cơ sở có quá 2 người để quản lý chủ động chọn người cần thu hồi; không tự xóa phân công. Khởi động lại backend để Flyway áp dụng các migration còn thiếu.
- Theo lựa chọn trực tiếp của người dùng, đã bỏ phân công qlcs.nguyenluongbang tại HN_HOA_BANG trên database local, ghi UNASSIGN_BRANCH_MANAGER. Giữ nguyên tài khoản và phân công tại cơ sở khác. Các môi trường khác cần tự kiểm tra dữ liệu trước khi chạy V20. Không thêm file test.

Hợp nhất giao diện (25/10/2026): `BookingManagementPage` giữ khung chi tiết booking có nút đóng và chế độ hộp thoại trên màn hình nhỏ, đồng thời hỗ trợ QR cọc 30%; rời QR tải lại danh sách và chi tiết để cập nhật tiền cọc. `DashboardTopbar` nhận đồng thời các thuộc tính thu gọn menu (`onToggleSidebar`, `sidebarExpanded`) và vùng chọn cơ sở (`accountHeader`).

### Checkout booking quá giờ (25/10/2026)

- Checkout tự báo phần quá giờ chưa thu từ `plannedEnd` hiện hành (đã gồm thời lượng gia hạn) đến lúc chốt bill, không yêu cầu bật đồng hồ trước. Theo BR-169/170: quá hạn không quá 5 phút được miễn phí; vượt ngưỡng tính đủ số giây, chia theo từng khung giá và làm tròn tiền từng đoạn lên đồng. Không làm tròn thời gian lên phút.
- Nếu bật đồng hồ muộn, bổ sung đoạn từ `plannedEnd` đến `billedFrom` theo giá tại `plannedEnd`. Đoạn đồng hồ RUNNING dùng đơn giá đã lưu và tự dừng khi lập bill. Đồng hồ STOPPED giữ nguyên khoản đã ghi và thời điểm dừng; checkout chỉ bổ sung đoạn trước đồng hồ nếu thiếu, không thu lại thời gian đã chốt. Các đoạn tính theo giây; tiền từng khung giá làm tròn lên đồng. Khoản giờ lẻ chưa lập bill theo cách cũ được thay bằng phép tính đúng BR-170; giữ lại bản gốc SUPERSEDED.
- API preview checkout bổ sung `overtime: [{from,to,seconds,hourlyPrice,amount,priceRuleId}]` và `overtimeAmount`; `lines` và `amount` đã bao gồm các khoản này. Preview chỉ đọc, chưa ghi phí. Request lập bill bổ sung `overtimeConfirmed` và `overtimeAmount`. Backend kiểm tra báo giá máy chủ đã ký trong giao dịch có khóa booking; thiếu xác nhận, báo giá hết hạn hoặc dữ liệu thay đổi trả 409, không ghi phí/bill. Gửi lại booking đã có bill trả bill hiện hữu, không thu trùng.
- Màn Checkout hiển thị từng khoảng giờ, đơn giá, tiền phát sinh và ô xác nhận bắt buộc; có nút **Cập nhật tiền phòng**. Khi lập bill gặp 409, tải lại preview và bỏ chọn xác nhận để lễ tân kiểm tra lại. Cần triển khai frontend/backend cùng nhau. Không thêm migration; không tính lại hóa đơn đã lập.
- File ảnh hưởng: `CheckoutDtos.java`, `BookingService.java`, `CheckoutService.java`, `CheckoutPage.js`. Các file test tạo trong đợt sửa này đã được xóa theo yêu cầu; giữ nguyên các test có sẵn.

### Đồng bộ Iteration 1 HuyDV với Report 3 (25/10/2026)

Phạm vi: UC-01–UC-13 và UC-41. Hoãn BR-125 (nhu cầu hóa đơn thuế) và BR-22 (giảm giá/voucher) theo yêu cầu; không coi hai phần này đã hoàn thành. Không tạo file test mới, không sửa các test có sẵn.

- BR-114: dùng chung kiểm tra mật khẩu 8–64 ký tự, đủ nhóm chữ hoa/thường/số/ký tự đặc biệt, không khoảng trắng. Áp dụng tạo/đổi/reset mật khẩu, không áp đặt lên mật khẩu cũ khi đăng nhập. BR-126: mã reset hết hạn sau 10 phút; kiểm tra cả mã cũ lưu hạn infinity theo created_at, không thay hạn phiên đăng nhập.
- UC-04/05/07: dashboard quản lý cơ sở thêm Tài khoản. API `/api/v1/branch-management/accounts` hỗ trợ GET danh sách, GET `/options`, POST tạo, POST `/{id}/deactivate`, POST `/{id}/reactivate`. Server xác minh phân công cơ sở đang chọn qua header `X-GCMB-Branch-Id`; chỉ quản lý tài khoản Lễ tân tại cơ sở đó, không cấp vai trò quản lý/toàn chuỗi. UC-06 vẫn chỉ Head Office Manager. API head-office giữ nguyên và bổ sung reactivate. Mở lại yêu cầu nhân viên và cơ sở hợp lệ; không khôi phục token đã thu hồi.
- BR-119: hồ sơ cá nhân và thông tin cơ sở dùng chữ số, tùy chọn một dấu + đầu chuỗi, tối đa 30 ký tự theo cột lưu trữ. Không cho dấu cách ở giữa, ngoặc hoặc gạch nối.
- BR-177: API danh tính bổ sung readOnly. Cơ sở SUSPENDED vẫn xuất hiện trong danh sách quản lý và được đọc; cấm ghi nghiệp vụ, hiện MSG43 và khóa nút ghi. Kiểm tra trạng thái trong transaction với khóa chia sẻ của cơ sở để tránh ghi đồng thời khi tạm ngừng. Việc chọn cơ sở không tạo phân công mới; phân công mới vẫn cần ACTIVE (BR-132).
- BR-13/215/216/218: checkout tính thuê giờ theo số giây thực dùng, giới hạn từng chặng và khung giờ trong snapshot giá đã xác nhận; không lấy giá catalogue mới để thay đơn giá gốc. Combo trả sớm giữ giá đã mua. BR-169/170: miễn phí tổng quá giờ <=300 giây, nếu vượt thì tính toàn khoảng theo từng khung giá và làm tròn tiền lên đồng. Dừng đồng hồ dùng cùng phép tính checkout. Phí cũ chưa lập bill được lưu SUPERSEDED khi chốt lại, không sửa hóa đơn đã phát hành.
- BR-159, MSG71: order chưa phục vụ chỉ cảnh báo, cho tiếp tục sau xác nhận. Các dòng ACCEPTED/SERVED tính theo giá chốt, PENDING chưa xác nhận không tính tiền. Dòng thuộc combo có unit_price=0 không thu thêm (BR-172).
- Hợp đồng checkout bổ sung unservedConfirmed, quotedAt, quoteToken; overtime dùng seconds thay minutes. Preview trả unservedItemCount, quoteToken và tổng đã tính thời gian thực tế. Báo giá có chữ ký HMAC gắn người dùng, booking, thời điểm và số tiền, hiệu lực 5 phút. Lập bill chốt sử dụng tại thời điểm báo giá hiển thị; nếu khách tiếp tục dùng phòng thì cập nhật báo giá. Backend khởi động lại làm báo giá cũ mất hiệu lực và yêu cầu tải lại. Gửi lại bill đã lập trả bill hiện có (BR-164). Cần triển khai frontend/backend cùng nhau; không thêm migration.

Tra cứu BR trong code bằng `rg "BR-169|BR-170" backend/src/main frontend/src` hoặc tìm từng mã BR. Các điểm thực thi chính:

- `auth/service/AuthValidation`: BR-111/113/114/119/120 và MSG02/03/04/05/06/29; `AuthRepository`, `AuthService`: BR-126/130/131; `SessionFilter`, `AuthMapper`: BR-177.
- `accounts/service/AccountService`: BR-111/112/113/114/129/132/133/134/164; `AccountAccessService`: UC-06/07, BR-131/177/183; `AccountRepository`: phạm vi UC-04/05/07. `AccountProfileService`: BR-111/113/119/134.
- `branch/service/BranchAdministrationService`: BR-111/119/135/136/149/177/179/180/181/184; `BranchRoomService` và repository: BR-177. `audit/service/AuditService`: BR-163, MSG106.
- `booking/service/BookingPricingService`: BR-169/170/215; `BookingService.checkoutHourlyCharges`, `checkoutOvertime`, `settleCheckoutCharges`: BR-13/164/169/170/215/216/218. Giá thay đổi phòng/combo đã được xác nhận từ UC-39/40 được giữ theo các dòng phí nguồn; không áp thêm một phí đổi phòng lần hai ở checkout.
- `booking/service/CheckoutService`: BR-01/13/20/21/23/24/25/26/28/29/30/141/142/144/145/158/159/160/161/162/164/167/172/182/185/218/232. `CheckoutPage` và `BookingStayDialog`: xác nhận và hiển thị đúng báo giá server. BR-22/125 là hai mục hoãn ở trên.
