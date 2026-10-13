# Booking API for UC34–UC38

Base path: `/api/v1/reception`. Every endpoint requires a receptionist session and the current branch assignment linked through `employee.user_id` and `employee_assignment`. The client does not choose `branchId` for a write or read operation.

Date-time values use ISO 8601 with a timezone offset, for example `2026-10-15T19:00:00+07:00`. Money values are VND amounts.

## Endpoints

| Method and path | Use | Purpose |
| --- | --- | --- |
| `GET /booking-options` | UC34 | Active room types, active combos, current branch and timezone. |
| `GET /availability` | UC34 | Rooms available for a room type and time range, plus a server-calculated price quote. |
| `POST /bookings` | UC34 | Recheck room capacity and atomically create the booking, room allocation, price snapshot, deposit account, optional received-deposit ledger entry and booking event. |
| `GET /bookings` | UC35 | Branch-scoped booking list with status/date/search/deposit filters and pagination. |
| `GET /bookings/{bookingId}` | UC35 | Branch-scoped booking, allocation history, saved charges and deposit balance. |
| `GET /bookings/{bookingId}/edit-options` | UC36 | Active rooms available for the requested edit period, excluding the booking's current allocation. |
| `POST /bookings/{bookingId}/check-in` | UC38 | Record the actual check-in time and move the reserved allocation into occupied state. |
| `PATCH /bookings/{bookingId}` | UC36 | Update contact details and/or the reserved room/time before check-in. Existing charge snapshots stay unchanged. |
| `POST /bookings/{bookingId}/cancel` | UC37 | Cancel a future booking, release its room, and either create a refund request or forfeit the deposit based on the cutoff. |
| `POST /bookings/{bookingId}/no-show` | UC37 | Mark an un-arrived confirmed booking as no-show after its start time, release the reserved room, and forfeit its remaining deposit balance. |

## Create booking request

```json
{
  "roomId": "00000000-0000-0000-0000-000000000000",
  "plannedStart": "2026-10-15T19:00:00+07:00",
  "plannedEnd": "2026-10-15T21:00:00+07:00",
  "pricingMode": "HOURLY",
  "comboId": null,
  "contactName": "Nguyen Van A",
  "contactPhone": "0900000000",
  "guestCount": 2,
  "channel": "PHONE",
  "note": null,
  "depositAmount": 200000,
  "paymentMethod": "BANK_TRANSFER",
  "paymentReference": "CK-245781"
}
```

`depositAmount` is optional and defaults to zero. When it is positive, the receptionist confirms that the amount has been received; the API records a `payment` and a `RECEIVE` deposit entry in the same transaction as booking creation. `paymentMethod` accepts `BANK_TRANSFER`, `CASH`, `CARD`, or `QR`. A receptionist-confirmed transfer is stored as `MANUAL_TRANSFER`, distinct from a transfer matched automatically by the bank webhook. `paymentReference` is optional and stores a transaction code or note for reconciliation. Cash receipts record the entered amount as tendered with zero change; all manual receipts keep the receptionist ID as `received_by`.

The displayed deposit balance is calculated from append-only deposit entries; a positive balance therefore has a corresponding `RECEIVE` entry. If receipt persistence fails, booking creation rolls back with it. “Có thu cọc” is off by default and must be enabled after money is actually received. The combo's deposit requirement may prefill the amount but never turns receipt confirmation on. The reception form requires a positive amount when that switch is enabled. Older bookings created by a backend version that ignored deposit fields cannot be reconstructed from the booking row alone; reconcile the actual receipt before recording a corrective ledger entry.

For local deployment, restart the backend from the updated checkout and verify Flyway applied `V7__booking_guest_count.sql` and `V8__reception_deposit_receipts.sql`. Updating only the React dev server leaves an old backend able to ignore the new JSON deposit fields. For a historical booking, the combo's `depositRequired` is a price policy, not proof of the amount actually received; use a receipt or bank record before entering any correction. If that booking was already cancelled, also reconcile whether the real payment should have been refunded or forfeited before adjusting its ledger.

`pricingMode` is `HOURLY` or `COMBO`. A combo booking must provide `comboId` and its duration must match the selected combo. `channel` is `WALK_IN`, `FACEBOOK`, `ZALO`, `PHONE`, or `OTHER`. Hourly prices and combo prices are calculated from database configuration; clients cannot submit an agreed amount.

## List filters

`status` accepts `DRAFT`, `CONFIRMED`, `IN_HOUSE`, `COMPLETED`, `CANCELLED`, or `NO_SHOW`; `PENDING` aliases `CONFIRMED` and `CHECKED_IN` aliases `IN_HOUSE`. `dateFrom` and `dateTo` are inclusive local calendar dates in the branch timezone. An `IN_HOUSE` booking that began before `dateFrom` is included while its planned end still overlaps the selected date, so overnight stays remain available for room operations. `search` checks booking code, contact name/phone, and room code. `hasDeposit` filters bookings with a recorded `RECEIVE` entry, including deposits later refunded or forfeited; for cancellation history missing that ledger entry, a positive recorded cancellation `depositAmount` also counts as a deposit. `page` starts at zero and `size` is 1–100 (default 0/20).

Booking summaries include `guestCount`, `roomTypeName`, `roomOperatingStatus`, `depositBalance` (the current ledger balance), and `depositReceivedAmount` (the cumulative amount collected, which remains visible after refund or forfeiture). If an older cancellation has no `RECEIVE` ledger entry, the recorded `depositAmount` on its cancellation event is used as a fallback. The detail response's `deposit` includes `balance`, `receivedAmount`, `forfeitedAmount`, `cancellationOutcome` (`NO_DEPOSIT`, `REFUND_ELIGIBLE`, or `NON_REFUNDABLE` when the booking was cancelled), and the received `paymentMethod` (`BANK_TRANSFER`, `MANUAL_TRANSFER`, `CASH`, `CARD`, or `MULTIPLE`) when a deposit has been collected. A cancelled non-refundable deposit shows the forfeited total from the ledger, with its recorded cancellation amount as a fallback, separately from the current balance, which is zero after forfeiture.

Check-in uses `POST /bookings/{bookingId}/check-in` without a request body. It returns the refreshed booking detail after persisting the actual time. Booking summaries expose `actualCheckin` separately from the scheduled `plannedStart`; for older rows with a missing column value, the API recovers actual check-in from the check-in event or the first allocation's recorded actual start. Early-check-in events likewise preserve the original appointment time in booking responses.

## Update and cancellation behavior

Update is allowed only for confirmed, not-yet-started bookings. If room or time changes, the backend rechecks the room's operating status and overlapping allocations, releases the previous reservation, and adds a new allocation to preserve room history. Confirmed `booking_charge` rows are not recalculated.

The reception edit dialog opens over the booking list, like the cancellation dialog, and supports contact name, phone, note, room, and planned start/end. It uses `GET /bookings/{bookingId}/edit-options?plannedStart=...&plannedEnd=...` to list active rooms without counting the booking's own current reservation as a conflict. A room must fit the saved guest count. Editing does not change the guest count, confirmed charge snapshots, or deposit ledger. The edit-options endpoint only returns data for a confirmed booking whose start time has not passed.

Check-in is allowed for a confirmed booking before its planned end when the assigned room is operationally `AVAILABLE`. If the guest arrives before the planned start, the backend checks for room conflicts, starts the occupied allocation at the actual check-in time, and shifts the planned end by the same amount so the guest keeps the full booked duration. The original `plannedStart` remains the appointment time; `actualCheckin` records when the guest actually arrived. For example, a 14:00–16:00 booking checked in at 13:30 keeps a 14:00 appointment, records 13:30 as the actual check-in, and moves its planned end to 15:30. Early check-in is rejected if the shifted allocation conflicts with another booking. A check-in at or after the planned start does not shift the booking window. Older early-check-in events are used to recover their original appointment time in booking responses. Guest count is saved at booking creation and must fit the assigned room's capacity.

Cancellation is allowed before the planned start. At least the booking's snapshotted cutoff (40 minutes by default) before start, a booking with a positive deposit requires beneficiary name, bank account, and bank name. The cancellation transaction creates a `refund_request` in `REQUESTED` status and releases the room; it does not mark the refund as paid. Inside the cutoff, the remaining deposit balance is recorded as a `FORFEIT` entry and becomes cancellation revenue. The cumulative `RECEIVE` amount remains available in booking summaries and details even after this balance reaches zero; the detail response exposes the saved cancellation outcome so reception can distinguish eligible and non-refundable deposits. A booking without a positive deposit reports `NO_DEPOSIT`.

No-show is separate from customer cancellation. `POST /bookings/{bookingId}/no-show` accepts an optional `{ "reason": "Khách không đến nhận phòng" }` body (the same default is used when omitted). It only accepts a `CONFIRMED` booking at or after its planned start and with a reserved allocation; it rejects future bookings, already checked-in bookings, and bookings whose room has already been released. In one transaction it records any positive remaining deposit balance as a `FORFEIT` ledger entry, releases the reserved allocation, changes the booking to `NO_SHOW`, and adds a `BOOKING_NO_SHOW` history event with the reason, timestamp, deposit outcome/amount, and released room ID. A booking without a positive deposit returns `NO_DEPOSIT`; otherwise the result is `NON_REFUNDABLE`. This policy is immediate at the planned start and does not add a grace period. The reception detail panel exposes the action after the start time, refreshes the deposit balance before confirmation, and explains the room/deposit effects.

The no-show response contains the refreshed booking detail, `depositOutcome`, forfeited `depositAmount`, `markedAt`, and `releasedRoomId`. The booking detail's `deposit.forfeitedAmount` includes no-show forfeitures from the ledger; `deposit.cancellationOutcome` remains the cancellation-specific outcome field.

Example request:

```json
{
  "reason": "Khách không đến nhận phòng"
}
```

  The reception booking list's `Xem` action opens the selected booking's detail/check-in panel; `Check-in`, `Sửa booking`, and `Hủy booking` are separate actions with distinct button styles. Guest count is shown with the customer contact rather than in a separate table column, and the detail panel keeps its deposit summary near the top while remaining available during scrolling. The side navigation stays pinned while the page scrolls, and scrolling over the detail panel can continue to scroll the page. The deposit summary preserves the total amount received after cancellation and shows whether it was eligible for refund, non-refundable, or had no deposit; if older cancellation history lacks an outcome event, the detail API derives it from the forfeiture ledger or deposit refund request. The reception screen offers these cancellation reasons: `Khách thay đổi kế hoạch`, `Khách đổi thời gian nhận phòng`, `Khách không còn nhu cầu`, `Khách đặt nhầm booking`, `Khách không đồng ý giá hoặc phụ phí`, and `Khác`. The first reason is selected by default; there is no placeholder option. Selecting `Khác` reveals a required free-text field; the selected reason or entered text is sent as `reason` and saved in the booking history. The dialog waits for the detail API before displaying the received deposit amount or deciding the refund outcome.

The cancellation result includes `refundRequestId` when a refund request was created; it is `null` for forfeiture and no-deposit cancellation.

For an eligible deposit, send the account fields with the reason:

```json
{
  "reason": "Khách thay đổi kế hoạch",
  "beneficiaryName": "NGUYEN VAN A",
  "bankAccount": "0123456789",
  "bankName": "Vietcombank"
}
```

The current schema has `HOLIDAY` price-rule rows but no holiday calendar. Hourly quotes therefore use matching `SPECIFIC`, `WEEKDAY`, `WEEKEND`, and `ALL` rules; a holiday-only window has no resolvable hourly price until a holiday calendar is added.

Errors use the existing auth API envelope: `{ "message": "...", "errors": { "field": "..." } }`. Unexpected server exceptions return a generic message to the client and log the exception stack trace in the backend output for diagnosis. Migration V9 schema-qualifies the PostgreSQL payment and deposit-ledger trigger references so reception deposits work with the application's default database search path; Flyway applies it when the backend starts.

For a local receptionist account, rooms, pricing, and ready-to-use booking scenarios, see [UC34–UC37 demo test data](UC34-37_Test_Data.md).
