# Booking API for UC34–UC37

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
| `POST /bookings/{bookingId}/check-in` | UC35 | Record the actual check-in time and move the reserved allocation into occupied state. |
| `PATCH /bookings/{bookingId}` | UC36 | Update contact details and/or the reserved room/time before check-in. Existing charge snapshots stay unchanged. |
| `POST /bookings/{bookingId}/cancel` | UC37 | Cancel a future booking, release its room, and either create a refund request or forfeit the deposit based on the cutoff. |

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

For local deployment, restart the backend from the updated checkout and verify Flyway applied `V3__booking_guest_count.sql` and `V4__reception_deposit_receipts.sql`. Updating only the React dev server leaves an old backend able to ignore the new JSON deposit fields. For a historical booking, the combo's `depositRequired` is a price policy, not proof of the amount actually received; use a receipt or bank record before entering any correction. If that booking was already cancelled, also reconcile whether the real payment should have been refunded or forfeited before adjusting its ledger.

`pricingMode` is `HOURLY` or `COMBO`. A combo booking must provide `comboId` and its duration must match the selected combo. `channel` is `WALK_IN`, `FACEBOOK`, `ZALO`, `PHONE`, or `OTHER`. Hourly prices and combo prices are calculated from database configuration; clients cannot submit an agreed amount.

## List filters

`status` accepts `DRAFT`, `CONFIRMED`, `IN_HOUSE`, `COMPLETED`, `CANCELLED`, or `NO_SHOW`; `PENDING` aliases `CONFIRMED` and `CHECKED_IN` aliases `IN_HOUSE`. `dateFrom` and `dateTo` are inclusive local calendar dates in the branch timezone. `search` checks booking code, contact name/phone, and room code. `hasDeposit` filters bookings with a positive current deposit balance. `page` starts at zero and `size` is 1–100 (default 0/20).

Booking summaries include `guestCount`, `roomTypeName`, and `roomOperatingStatus`. The detail response's `deposit` also includes the received `paymentMethod` (`BANK_TRANSFER`, `MANUAL_TRANSFER`, `CASH`, `CARD`, `QR`, or `MULTIPLE`) when a deposit has been collected.

Check-in uses `POST /bookings/{bookingId}/check-in` without a request body. It returns the refreshed booking detail after persisting the actual time.

## Update and cancellation behavior

Update is allowed only for confirmed, not-yet-started bookings. If room or time changes, the backend rechecks the room's operating status and overlapping allocations, releases the previous reservation, and adds a new allocation to preserve room history. Confirmed `booking_charge` rows are not recalculated.

The reception edit dialog opens over the booking list, like the cancellation dialog, and supports contact name, phone, note, room, and planned start/end. It uses `GET /bookings/{bookingId}/edit-options?plannedStart=...&plannedEnd=...` to list active rooms without counting the booking's own current reservation as a conflict. A room must fit the saved guest count. Editing does not change the guest count, confirmed charge snapshots, or deposit ledger. The edit-options endpoint only returns data for a confirmed booking whose start time has not passed.

Check-in is allowed for a confirmed booking from its planned start. The backend locks the booking, allocation, and room, verifies that the assigned room is operationally `AVAILABLE`, stores the actual check-in time, changes the booking to `IN_HOUSE`, and changes the allocation to `OCCUPIED`. Guest count is saved at booking creation and must fit the assigned room's capacity.

Cancellation is allowed before the planned start. At least the booking's snapshotted cutoff (40 minutes by default) before start, a booking with a positive deposit requires beneficiary name, bank account, and bank name. The cancellation transaction creates a `refund_request` in `REQUESTED` status and releases the room; it does not mark the refund as paid. Inside the cutoff, the remaining deposit balance is recorded as a `FORFEIT` entry and becomes cancellation revenue. A booking without a positive deposit reports `NO_DEPOSIT`.

The reception screen offers these cancellation reasons: `Khách thay đổi kế hoạch`, `Khách đổi thời gian nhận phòng`, `Khách không còn nhu cầu`, `Khách đặt nhầm booking`, `Khách không đồng ý giá hoặc phụ phí`, and `Khác`. The first reason is selected by default; there is no placeholder option. Selecting `Khác` reveals a required free-text field; the selected reason or entered text is sent as `reason` and saved in the booking history. The dialog waits for the detail API before displaying the received deposit balance or deciding the refund outcome.

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

Errors use the existing auth API envelope: `{ "message": "...", "errors": { "field": "..." } }`.

For a local receptionist account, rooms, pricing, and ready-to-use booking scenarios, see [UC34–UC37 demo test data](UC34-37_Test_Data.md).
