const DRAFT_PREFIX = 'gcmb.reception.booking-create.v1';

function draftKey(user) {
  return user?.id ? `${DRAFT_PREFIX}:${user.id}` : null;
}

export function readBookingDraft(user) {
  const key = draftKey(user);
  if (!key) return null;
  try {
    const draft = JSON.parse(window.sessionStorage.getItem(key));
    return draft?.form && typeof draft.form === 'object' ? draft : null;
  } catch {
    return null;
  }
}

export function saveBookingDraft(user, form, depositAmountEdited) {
  const key = draftKey(user);
  if (!key) return;
  try {
    window.sessionStorage.setItem(key, JSON.stringify({ form, depositAmountEdited }));
  } catch {
    // The form stays usable when browser storage is unavailable.
  }
}

export function clearBookingDraft(user) {
  const key = draftKey(user);
  if (!key) return;
  try { window.sessionStorage.removeItem(key); } catch { /* Storage can be disabled. */ }
}
