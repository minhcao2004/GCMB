# Reception room and check-in screens

This note records the current frontend scope for the three receptionist screens requested from the supplied designs.

## Routes

| Screen | Route | Data and behavior |
| --- | --- | --- |
| Active room details | `/le-tan/rooms` | Uses a fixed preview session for room P.201, including customer, elapsed time, F&B orders, deposit, and estimated bill. The elapsed timer and overview/order/history tabs work in the browser. |
| Change room dialog | Opened from the active room screen | Filters and room selection update the displayed price comparison and summary. Confirming shows that the selection is a preview only; it does not update a booking or room allocation. |
| Booking check-in panel | `/le-tan/bookings/:bookingId/check-in` | Loads the selected booking and saved charges through the existing booking API. Actual check-in time is shown from the browser clock. Confirming reports that the backend has no check-in endpoint and does not change booking status. |

The receptionist shell links the Room and Booking sections. Authentication and role checks continue to use the existing auth API.

## Backend boundary

The current booking API contract in [UC34–UC37 Booking API](UC34-37_Booking_API.md) supports booking options, availability, booking create/list/detail, updates before check-in, and cancellation. It does not define APIs for active room sessions, F&B room orders, room readiness, room changes during a session, check-in confirmation, checkout, or payment. Values on the active-room screen and destination-room list are therefore illustrative and must not be treated as persisted operational data.

When backend endpoints are added, replace the preview data and local selection state with server responses and submit the matching operations. Keep the UI messages that distinguish preview state from saved state until those endpoints are available.
