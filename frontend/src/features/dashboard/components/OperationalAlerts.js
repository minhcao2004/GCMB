import { useEffect, useId, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import { Pagination, usePagination } from '../../../shared/components/Pagination';
import '../styles/executive-alerts.css';

const BELL_SIZE = 52;
const EDGE = 12;
const PAGE_SIZE = 5;
const READ_LIMIT = 500;
const moves = { ArrowLeft: [-24, 0], ArrowRight: [24, 0], ArrowUp: [0, -24], ArrowDown: [0, 24] };

function alertKey(alert) {
  const content = JSON.stringify([alert.id, alert.branchId, alert.branch, alert.kind, alert.level, alert.detail, alert.from, alert.to]);
  let hash = 2166136261;
  for (let index = 0; index < content.length; index++) {
    hash = Math.imul(hash ^ content.charCodeAt(index), 16777619);
  }
  return `${alert.id}:${(hash >>> 0).toString(36)}`;
}

function readStoredKeys(storageKey) {
  if (!storageKey) return [];
  try {
    const keys = JSON.parse(window.localStorage.getItem(storageKey));
    return Array.isArray(keys) ? keys.filter((key) => typeof key === 'string').slice(-READ_LIMIT) : [];
  } catch { return []; }
}

const viewportSize = () => ({ width: document.documentElement.clientWidth, height: document.documentElement.clientHeight });
const withinScreen = (value, maximum) => Math.max(EDGE, Math.min(value, maximum));

export default function OperationalAlerts({ accountId, warnings, onAlertNavigate }) {
  const storageKey = accountId ? `gcmb-executive-alerts-read:${accountId}` : '';
  const [readKeys, setReadKeys] = useState(() => new Set(readStoredKeys(storageKey)));
  const [openedAlerts, setOpenedAlerts] = useState(null);
  const [position, setPosition] = useState(null);
  const [viewport, setViewport] = useState(viewportSize);
  const container = useRef(null);
  const trigger = useRef(null);
  const panel = useRef(null);
  const closeButton = useRef(null);
  const drag = useRef(null);
  const moved = useRef(false);
  const panelId = useId();
  const unread = warnings.map((alert) => ({ ...alert, readKey: alertKey(alert) }))
    .filter((alert) => !readKeys.has(alert.readKey));
  const expanded = openedAlerts !== null;
  const pagination = usePagination(openedAlerts || [], PAGE_SIZE, openedAlerts?.[0]?.readKey);
  const maxLeft = viewport.width - BELL_SIZE - EDGE;
  const maxTop = viewport.height - BELL_SIZE - EDGE;

  useEffect(() => {
    const resize = () => setViewport(viewportSize());
    window.addEventListener('resize', resize);
    return () => window.removeEventListener('resize', resize);
  }, []);

  useEffect(() => {
    if (!storageKey) return undefined;
    const updateReadKeys = (event) => {
      if (event.key === storageKey) setReadKeys(new Set(readStoredKeys(storageKey)));
    };
    window.addEventListener('storage', updateReadKeys);
    return () => window.removeEventListener('storage', updateReadKeys);
  }, [storageKey]);

  useEffect(() => {
    if (!expanded) return undefined;
    closeButton.current?.focus();
    const dismiss = (event) => {
      if (!container.current?.contains(event.target)) setOpenedAlerts(null);
    };
    document.addEventListener('pointerdown', dismiss);
    return () => document.removeEventListener('pointerdown', dismiss);
  }, [expanded]);

  useEffect(() => {
    const move = (event) => {
      const start = drag.current;
      if (start?.id !== event.pointerId) return;
      const dx = event.clientX - start.x, dy = event.clientY - start.y;
      if (Math.hypot(dx, dy) > 5) moved.current = true;
      if (moved.current) setPosition({ left: withinScreen(start.left + dx, maxLeft), top: withinScreen(start.top + dy, maxTop) });
    };
    const finish = (event) => {
      if (drag.current?.id !== event.pointerId) return;
      if (event.type === 'pointerup' && moved.current) move(event);
      drag.current = null;
    };
    window.addEventListener('pointermove', move);
    window.addEventListener('pointerup', finish);
    window.addEventListener('pointercancel', finish);
    return () => {
      window.removeEventListener('pointermove', move);
      window.removeEventListener('pointerup', finish);
      window.removeEventListener('pointercancel', finish);
    };
  }, [maxLeft, maxTop]);

  if (!unread.length && !expanded) return null;

  const left = withinScreen(position?.left ?? maxLeft - 8, maxLeft);
  const top = withinScreen(position?.top ?? Math.min(124, Math.round(viewport.height / 6)), maxTop);
  const panelWidth = Math.min(392, viewport.width - EDGE * 2);
  const roomBelow = viewport.height - top - BELL_SIZE - 8 - EDGE;
  const roomAbove = top - 8 - EDGE;
  const showBelow = roomBelow >= Math.min(400, roomAbove);
  const panelStyle = {
    width: panelWidth,
    left: withinScreen(left + BELL_SIZE - panelWidth, viewport.width - panelWidth - EDGE),
    maxHeight: Math.min(400, showBelow ? roomBelow : roomAbove),
    ...(showBelow ? { top: top + BELL_SIZE + 8 } : { bottom: viewport.height - top + 8 }),
  };

  function markPageRead(alerts, page) {
    const visibleAlerts = alerts.slice(page * PAGE_SIZE, (page + 1) * PAGE_SIZE);
    const next = new Set([...readKeys, ...readStoredKeys(storageKey), ...visibleAlerts.map((alert) => alert.readKey)]);
    const recent = [...next].slice(-READ_LIMIT);
    setReadKeys(new Set(recent));
    if (storageKey) {
      try { window.localStorage.setItem(storageKey, JSON.stringify(recent)); }
      catch { /* Reading still works for the current view when storage is unavailable. */ }
    }
  }

  function closePanel() {
    setOpenedAlerts(null);
    const focusTarget = unread.length ? trigger.current : document.getElementById('executive-title');
    focusTarget?.focus();
  }

  return createPortal(<div className="executive-alerts" ref={container} onKeyDown={(event) => {
    if (expanded && event.key === 'Escape') {
      event.stopPropagation();
      closePanel();
    }
  }}>
    <button ref={trigger} type="button" className={`executive-alert-bell${expanded ? '' : ' is-unread'}`}
      style={{ left, top, '--executive-bell-size': `${BELL_SIZE}px` }}
      aria-label={unread.length ? `Cảnh báo vận hành, ${unread.length} chưa đọc` : 'Cảnh báo vận hành'}
      aria-expanded={expanded} aria-controls={expanded ? panelId : undefined} title="Kéo hoặc dùng phím mũi tên để di chuyển chuông"
      onPointerDown={(event) => {
        if (event.button !== 0 || drag.current) return;
        moved.current = false;
        drag.current = { id: event.pointerId, x: event.clientX, y: event.clientY, left, top };
        event.currentTarget.setPointerCapture(event.pointerId);
      }}
      onKeyDown={(event) => {
        const move = moves[event.key];
        if (!move) return;
        event.preventDefault();
        setPosition({ left: withinScreen(left + move[0], maxLeft), top: withinScreen(top + move[1], maxTop) });
      }}
      onClick={(event) => {
        if (event.detail > 0 && moved.current) return;
        if (expanded) closePanel();
        else {
          setOpenedAlerts(unread);
          markPageRead(unread, 0);
        }
      }}>
      <svg aria-hidden="true" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" strokeLinecap="round" strokeLinejoin="round">
        <path d="M18 8a6 6 0 0 0-12 0c0 7-3 7-3 9h18c0-2-3-2-3-9m-8 12a2 2 0 0 0 4 0" />
      </svg>
      {!expanded && <span className="executive-alert-count" aria-hidden="true">{unread.length}</span>}
    </button>
    {expanded && <section ref={panel} id={panelId} className="executive-alert-panel" style={panelStyle} aria-labelledby={`${panelId}-title`}>
      <header className="executive-alert-heading">
        <h2 id={`${panelId}-title`}>Cảnh báo vận hành</h2>
        <button ref={closeButton} type="button" className="executive-alert-close" onClick={closePanel}>Đóng</button>
      </header>
      <ul className="executive-alert-list">
        {pagination.items.map((item) => <li key={item.readKey}>
          <button type="button" className="executive-alert-item" onClick={() => { closePanel(); onAlertNavigate?.(item); }}>
            <span className="executive-alert-item-copy">
              <strong>{item.kind}</strong>
              <span className="executive-alert-meta">
                <span className={`executive-alert-level${item.level === 'critical' ? ' is-critical' : ''}`}>{item.level === 'critical' ? 'Khẩn' : 'Cảnh báo'}</span>
                {item.branch}
              </span>
              <span>{item.detail}</span>
            </span>
            <svg aria-hidden="true" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="m9 6 6 6-6 6" /></svg>
          </button>
        </li>)}
      </ul>
      <Pagination {...pagination} label="Phân trang cảnh báo" onPageChange={(page) => {
        markPageRead(openedAlerts, page);
        pagination.setPage(page);
        if (panel.current) panel.current.scrollTop = 0;
      }} />
    </section>}
  </div>, document.body);
}
