# Booking API for UC34–UC37

Base path: `/api/v1/reception`. Every endpoint requires a receptionist session and the current branch assignment linked through `employee.user_id` and `employee_assignment`. The client does not choose `branchId` for a write or read operation.

Date-time values use ISO 8601 with a timezone offset, for example `2026-10-15T19:00:00+07:00`. Money values are VND amounts.

## Endpoints

| Method and path | Use | Purpose |
| --- | --- | --- |
| `GET /booking-options` | UC34 | Active room types, active combos, current branch and timezone. |
| `GET /availability` | UC34 | Rooms available for a room type and time range, plus a server-calculated price quote. |
| `POST /bookings` | UC34 | Recheck and create a confirmed booking, room allocation, price snapshot, deposit account and booking event in one transaction. |
| `GET /bookings` | UC35 | Branch-scoped booking list with status/date/search/deposit filters and pagination. |
| `GET /bookings/{bookingId}` | UC35 | Branch-scoped booking, allocation history, saved charges and deposit balance. |
| `PATCH /bookings/{bookingId}` | UC36 | Update contact details and/or the reserved room/time before check-in. Existing charge snapshots stay unchanged. |
| `POST /bookings/{bookingId}/cancel` | UC37 | Cancel a future booking and release its room allocation; reports deposit disposition. |

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
  "channel": "PHONE",
  "note": null
}
```

`pricingMode` is `HOURLY` or `COMBO`. A combo booking must provide `comboId` and its duration must match the selected combo. `channel` is `WALK_IN`, `FACEBOOK`, `ZALO`, `PHONE`, or `OTHER`. Hourly prices and combo prices are calculated from database configuration; clients cannot submit an agreed amount.

## List filters

`status` accepts `DRAFT`, `CONFIRMED`, `IN_HOUSE`, `COMPLETED`, `CANCELLED`, or `NO_SHOW`; `PENDING` aliases `CONFIRMED` and `CHECKED_IN` aliases `IN_HOUSE`. `dateFrom` and `dateTo` are inclusive local calendar dates in the branch timezone. `search` checks booking code, contact name/phone, and room code. `hasDeposit` filters bookings with a positive current deposit balance. `page` starts at zero and `size` is 1–100 (default 0/20).

## Update and cancellation behavior

Update is allowed only for confirmed, not-yet-started bookings. If room or time changes, the backend rechecks the room's operating status and overlapping allocations, releases the previous reservation, and adds a new allocation to preserve room history. Confirmed `booking_charge` rows are not recalculated.

Cancellation is allowed before the planned start. At least 40 minutes before start, an existing deposit remains eligible for a separate refund request; the cancellation endpoint does not pay it automatically. Inside the 40-minute cutoff, the remaining deposit balance is recorded as a `FORFEIT` entry. A booking without a positive deposit reports `NO_DEPOSIT`.

The current schema has `HOLIDAY` price-rule rows but no holiday calendar. Hourly quotes therefore use matching `SPECIFIC`, `WEEKDAY`, `WEEKEND`, and `ALL` rules; a holiday-only window has no resolvable hourly price until a holiday calendar is added.

Errors use the existing auth API envelope: `{ "message": "...", "errors": { "field": "..." } }`.
