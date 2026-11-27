import { PaginatedList } from '../../../shared/components/Pagination';
/** HuyDV: trợ lý QLCC, lưu lịch sử riêng theo tài khoản trên trình duyệt. */
import { useEffect, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import api from '../../../api/client';
import { apiErrors } from '../../auth/services/authApi';
import { readStale, staleKey, writeStale } from '../../../shared/utils/staleCache';
import '../styles/business-assistant.css';

const base = '/head-office/business-assistant';
const currentMonth = () => new Intl.DateTimeFormat('en-CA', {
  timeZone: 'Asia/Ho_Chi_Minh', year: 'numeric', month: '2-digit',
}).format(new Date()).replace(/^(\d{2})\/(\d{4})$/, '$2-$1');
const suggestions = [
  'Phân tích tình hình kinh doanh và ba việc cần ưu tiên.',
  'Cơ sở nào nên thử kết hợp Music Box và Cinema?',
  'Có nên bổ sung phòng hoặc thiết bị? Cần kiểm tra dữ liệu gì?',
];
const emptyMessages = [];
const historyKey = (id) => `gcmb-business-chats-v1:${id}`;
const savedMessages = (messages) => messages.slice(-60).map(({ role, content, displayAnswer }) => {
  if (role === 'assistant' && typeof displayAnswer === 'string' && displayAnswer.length <= 24000) {
    return { role, content, displayAnswer };
  }
  return { role, content };
});
function readHistory(id) {
  if (!id) return [];
  try {
    const data = JSON.parse(localStorage.getItem(historyKey(id)) || '[]');
    if (!Array.isArray(data)) return [];
    return data.filter((item) => typeof item?.id === 'string' && typeof item.updatedAt === 'string' &&
      Array.isArray(item.messages) && item.messages.length && item.messages.length <= 60 && item.messages.every((message) =>
        ['user', 'assistant'].includes(message?.role) && typeof message.content === 'string' && message.content.length <= 24000))
      .slice(0, 20).map((item) => ({ id: item.id, updatedAt: item.updatedAt, messages: savedMessages(item.messages) }));
  } catch { return []; }
}
function Answer({ text }) {
  const content = text.replace(/\s*\(\s*`?(?:occupied_hours|room_line_amount)`?\s*\)/gi, '')
    .replace(/\boccupied_hours\b/gi, 'số giờ sử dụng phòng')
    .replace(/\broom_line_amount\b/gi, 'tiền phòng đã tất toán')
    .replace(/`/g, '')
    .replace(/(?:[ \t]*\[\s*S\d+\s*\])(?:[ \t]*[,;]?[ \t]*\[\s*S\d+\s*\])*/gi, '');
  const inline = (value) => value.split(/(\*\*[^*]+\*\*)/g).map((part, index) =>
    part.startsWith('**') && part.endsWith('**') ? <strong key={index}>{part.slice(2, -2)}</strong> : part);
  return <div className="business-assistant-answer">{content.split(/\n\s*\n/).filter(Boolean).map((block, index) => {
    const lines = block.split('\n');
    if (lines.every((line) => /^\s*([-*•]|\d+[.)])\s+/.test(line))) {
      return <ul key={index}>{lines.map((line, indexItem) => <li key={indexItem}>{inline(line.replace(/^\s*([-*•]|\d+[.)])\s+/, ''))}</li>)}</ul>;
    }
    return <p key={index}>{inline(block.replace(/^#{1,6}\s+/gm, ''))}</p>;
  })}</div>;
}

function Robot({ className = '' }) {
  return <svg className={className} viewBox="0 0 48 48" fill="none" aria-hidden="true">
    <path d="M24 5v6M20 5h8" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" />
    <rect x="8" y="12" width="32" height="28" rx="10" fill="currentColor" fillOpacity=".12" stroke="currentColor" strokeWidth="2" />
    <path d="M5 22v9M43 22v9" stroke="currentColor" strokeWidth="3" strokeLinecap="round" />
    <rect x="13" y="19" width="22" height="13" rx="6" fill="currentColor" />
    <path className="business-assistant-eyes" d="M19 24v3M29 24v3" stroke="white" strokeWidth="2.5" strokeLinecap="round" />
    <path d="M21 36h6" stroke="currentColor" strokeWidth="2" strokeLinecap="round" />
  </svg>;
}

export default function BusinessAssistant({ user }) {
  const [open, setOpen] = useState(false);
  const month = currentMonth();
  const [context, setContext] = useState(null);
  const [loading, setLoading] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [draft, setDraft] = useState('');
  const [messages, setMessages] = useState([]);
  const [threads, setThreads] = useState(() => readHistory(user?.id));
  const [threadId, setThreadId] = useState(null);
  const [showHistory, setShowHistory] = useState(false);
  const [storageError, setStorageError] = useState('');
  const [expanded, setExpanded] = useState(false);
  const [size, setSize] = useState(null);
  const [launcherPosition, setLauncherPosition] = useState(null);
  const launcherDrag = useRef(null), launcherMoved = useRef(false);
  const [refresh, setRefresh] = useState(0);
  const launcher = useRef(null), composer = useRef(null), conversation = useRef(null), sending = useRef(false);
  const mounted = useRef(true);
  const panel = useRef(null), resize = useRef(null);
  useEffect(() => { mounted.current = true; return () => { mounted.current = false; }; }, []);

  useEffect(() => {
    if (!open || user?.role !== 'HEAD_OFFICE_MANAGER') return undefined;
    let active = true;
    setError('');
    // Hiện ngay ngữ cảnh lần trước (nếu có) trong lúc tải bản mới ở nền.
    const cacheKey = staleKey('assistant-context', month);
    const cached = readStale(cacheKey);
    if (cached) { setContext(cached); setLoading(false); } else setLoading(true);
    api.get(`${base}/context`, { params: { month } })
      .then(({ data }) => { writeStale(cacheKey, data); if (active) setContext(data); })
      .catch((failure) => { if (active) { setContext(null); setError(Object.values(apiErrors(failure)).join(' ')); } })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [open, month, refresh, user?.role]);
  useEffect(() => {
    if (!open) return undefined;
    const frame = window.requestAnimationFrame(() => composer.current?.focus());
    return () => window.cancelAnimationFrame(frame);
  }, [open, showHistory]);
  useEffect(() => {
    if (conversation.current) conversation.current.scrollTop = conversation.current.scrollHeight;
  }, [messages, busy]);

  const moveLauncher = (position) => {
    const rect = launcher.current?.getBoundingClientRect();
    if (!rect) return;
    setLauncherPosition({
      x: Math.max(8, Math.min(window.innerWidth - rect.width - 8, position.x)),
      y: Math.max(8, Math.min(window.innerHeight - rect.height - 8, position.y)),
    });
  };
  useEffect(() => {
    const keepVisible = () => {
      const rect = launcher.current?.getBoundingClientRect();
      if (rect) moveLauncher({ x: rect.left, y: rect.top });
    };
    window.addEventListener('resize', keepVisible);
    return () => window.removeEventListener('resize', keepVisible);
  }, []);

  if (user?.role !== 'HEAD_OFFICE_MANAGER') return null;
  const close = () => {
    setOpen(false); launcher.current?.focus();
    window.requestAnimationFrame(() => launcher.current?.focus());
  };
  const saveThreads = (next) => {
    const bounded = next.slice(0, 20);
    while (bounded.length > 1 && JSON.stringify(bounded).length > 2000000) bounded.pop();
    setThreads(bounded);
    if (!user?.id) return;
    try { localStorage.setItem(historyKey(user.id), JSON.stringify(bounded)); setStorageError(''); }
    catch { setStorageError('Không thể lưu lịch sử trên trình duyệt này. Cuộc chat hiện tại vẫn dùng được.'); }
  };
  const newChat = () => {
    if (sending.current) return;
    setThreadId(null); setMessages([]); setDraft(''); setError(''); setShowHistory(false);
    composer.current?.focus();
  };
  const resizePanel = (width, height) => {
    const rect = panel.current.getBoundingClientRect();
    setSize({ width: Math.max(320, Math.min(window.innerWidth - (window.innerWidth - rect.right) - 12, width)),
      height: Math.max(360, Math.min(window.innerHeight - (window.innerHeight - rect.bottom) - 12, height)) });
  };
  const submit = async (event) => {
    event.preventDefault();
    const question = draft.trim();
    if (!question || loading || !context?.configured || sending.current) return;
    sending.current = true; setBusy(true); setError(''); setDraft('');
    const history = [];
    let used = 0;
    // displayAnswer is for the local conversation only. Model history uses content.
    for (const message of messages.slice(-8).reverse()) {
      const content = message.content.slice(0, message.role === 'user' ? 2000 : 4000);
      if (used + content.length > 12000) break;
      used += content.length; history.unshift({ role: message.role, content });
    }
    if (history[0]?.role === 'assistant') history.shift();
    const pending = [...messages, { role: 'user', content: question }];
    setMessages(pending);
    try {
      const { data: csrf } = await api.get('/auth/csrf');
      const { data } = await api.post(`${base}/chat`, { month, branchId: null, message: question, history }, {
        headers: { [csrf.headerName]: csrf.token }, timeout: 60000,
      });
      if (mounted.current) {
        setContext(data.context);
        const completed = savedMessages([...pending, { role: 'assistant', content: data.answer, displayAnswer: data.displayAnswer }]);
        const id = threadId || window.crypto?.randomUUID?.() || `${Date.now()}-${Math.random().toString(36).slice(2)}`;
        setThreadId(id); setMessages(completed);
        saveThreads([{ id, updatedAt: new Date().toISOString(), messages: savedMessages(completed) }, ...threads.filter((thread) => thread.id !== id)]);
      }
    } catch (failure) {
      if (mounted.current) {
        setError(Object.values(apiErrors(failure)).join(' ') || 'Không nhận được phản hồi. Vui lòng thử lại.');
        setMessages(messages); setDraft(question);
      }
    } finally { sending.current = false; if (mounted.current) setBusy(false); }
  };

  return createPortal(<div className="business-assistant">
    {open && <section ref={panel} id="business-assistant-panel" className={`business-assistant-panel${expanded ? ' is-expanded' : ''}`} role="dialog" aria-modal="false" aria-labelledby="business-assistant-title"
      style={size && !expanded ? { '--assistant-width': `${size.width}px`, '--assistant-height': `${size.height}px` } : undefined}
      onKeyDown={(event) => { if (event.key === 'Escape') { event.stopPropagation(); close(); } }}>
      <header className="business-assistant-header">
        <Robot className="business-assistant-avatar" />
        <div><h2 id="business-assistant-title">Trợ lý kinh doanh</h2><p>GenZ AI · Dành cho QLCC</p></div>
        <div className="business-assistant-header-actions">
          <button type="button" aria-label={expanded ? 'Thu nhỏ khung chat' : 'Mở rộng khung chat'} onClick={() => setExpanded((value) => !value)}>
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" aria-hidden="true"><path d={expanded ? 'M9 3v6H3m18 6h-6v6M9 9 3 3m12 12 6 6' : 'M3 9V3h6m6 18h6v-6M3 3l6 6m6 6 6 6'} /></svg>
          </button>
          <button type="button" aria-label="Đóng trợ lý" onClick={close}>×</button>
        </div>
      </header>
      <nav className="business-assistant-toolbar" aria-label="Cuộc trò chuyện">
        <button type="button" aria-pressed={showHistory} disabled={busy} onClick={() => setShowHistory((value) => !value)}>{showHistory ? 'Quay lại chat' : 'Lịch sử trò chuyện'}</button>
        <button type="button" disabled={busy} onClick={newChat}>Trò chuyện mới</button>
      </nav>
      {showHistory ? <div className="business-assistant-history">
        <h3>Lịch sử trò chuyện</h3>
        {!threads.length && <p>Chưa có cuộc trò chuyện nào.</p>}
        {<PaginatedList pageSize={10}>{threads.map((thread) => <div key={thread.id} className="business-assistant-history-row">
          <button type="button" aria-current={threadId === thread.id ? 'true' : undefined} onClick={() => {
            setThreadId(thread.id); setMessages(thread.messages || emptyMessages); setDraft(''); setError(''); setShowHistory(false);
          }}><span>{thread.messages.find((message) => message.role === 'user')?.content.slice(0, 90) || 'Cuộc trò chuyện'}</span>
            <time dateTime={thread.updatedAt}>{new Date(thread.updatedAt).toLocaleString('vi-VN', { timeZone: 'Asia/Ho_Chi_Minh' })}</time></button>
          <button type="button" aria-label={`Xóa cuộc trò chuyện ${thread.messages[0]?.content.slice(0, 40)}`} onClick={() => {
            saveThreads(threads.filter((threadItem) => threadItem.id !== thread.id)); if (thread.id === threadId) { setThreadId(null); setMessages([]); setDraft(''); setError(''); }
          }}>Xóa</button>
        </div>)}</PaginatedList>}
        {storageError && <p className="business-assistant-error" role="alert">{storageError}</p>}
      </div> : <>
      <div className="business-assistant-conversation" ref={conversation}>
        {loading && <p className="business-assistant-status" role="status">Đang lấy số liệu…</p>}
        {!loading && context && !context.configured && <p className="business-assistant-setup" role="status">
          Chưa kết nối Gemini. Vui lòng cấu hình API key trên máy chủ để trò chuyện.
        </p>}
        {!loading && context && !messages.length && <>
          <div className="business-assistant-welcome"><h3>Bạn muốn tìm hiểu điều gì?</h3><p>Hỏi về kinh doanh, so sánh Cinema/Music Box hoặc những việc nên ưu tiên tại cơ sở.</p></div>
          <div className="business-assistant-suggestions">{suggestions.map((text) =>
            <button type="button" key={text} onClick={() => { setDraft(text); composer.current?.focus(); }}>{text}</button>)}</div>
        </>}
        <div className="business-assistant-messages" aria-live="polite" aria-relevant="additions">
          {messages.map((message, index) => <article key={index} className={`business-assistant-message ${message.role}`}>
            <span>{message.role === 'user' ? 'Bạn' : 'GenZ AI'}</span>
            {message.role === 'assistant' ? <Answer text={message.displayAnswer || message.content} /> : <p>{message.content}</p>}

          </article>)}
        </div>
        {busy && <p role="status" className="business-assistant-status">Đang phân tích số liệu…</p>}
        {error && <div className="business-assistant-error" role="alert"><p>{error}</p>
          {!context && <button type="button" onClick={() => setRefresh((previousRefresh) => previousRefresh + 1)}>Tải lại số liệu</button>}</div>}
        {storageError && <p className="business-assistant-error" role="alert">{storageError}</p>}
      </div>
      <footer className="business-assistant-footer">
        <form onSubmit={submit}>
          <label className="business-assistant-input-label" htmlFor="business-assistant-question">Câu hỏi của bạn</label>
          <textarea id="business-assistant-question" ref={composer} rows={2} maxLength={2000} value={draft} disabled={busy}
            onChange={(event) => setDraft(event.target.value)}
            onKeyDown={(event) => {
              if (event.key !== 'Enter' || event.shiftKey || event.nativeEvent.isComposing || event.keyCode === 229) return;
              event.preventDefault();
              if (!event.repeat) event.currentTarget.form?.requestSubmit();
            }} />
          <div className="business-assistant-composer-actions">
            <button type="submit" className="business-assistant-send" disabled={busy || loading || !context?.configured || !draft.trim()}>
              {busy ? 'Đang trả lời…' : 'Gửi câu hỏi'}</button></div>
        </form>
      </footer>
      </>}
      {!expanded && <button type="button" className="business-assistant-resize" aria-label="Thay đổi kích thước khung chat" title="Kéo để đổi kích thước; dùng phím mũi tên khi chọn nút"
        onPointerDown={(event) => { const rect = panel.current.getBoundingClientRect(); resize.current = { x: event.clientX, y: event.clientY, width: rect.width, height: rect.height }; event.currentTarget.setPointerCapture(event.pointerId); }}
        onPointerMove={(event) => { if (resize.current) resizePanel(resize.current.width + resize.current.x - event.clientX, resize.current.height + resize.current.y - event.clientY); }}
        onPointerUp={() => { resize.current = null; }} onPointerCancel={() => { resize.current = null; }} onLostPointerCapture={() => { resize.current = null; }}
        onKeyDown={(event) => { if (!['ArrowLeft', 'ArrowRight', 'ArrowUp', 'ArrowDown'].includes(event.key)) return; event.preventDefault(); const rect = panel.current.getBoundingClientRect(); resizePanel(rect.width + (event.key === 'ArrowLeft' ? 24 : event.key === 'ArrowRight' ? -24 : 0), rect.height + (event.key === 'ArrowUp' ? 24 : event.key === 'ArrowDown' ? -24 : 0)); }}>
        <svg viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="1.5" aria-hidden="true"><path d="M4 12V4h8M8 12V8h4" /></svg>
      </button>}
    </section>}
    <button type="button" ref={launcher} className={`business-assistant-launcher${open ? ' is-open' : ''}`}
      style={launcherPosition ? { left: launcherPosition.x, top: launcherPosition.y, right: 'auto', bottom: 'auto' } : undefined}
      title="Kéo để di chuyển; dùng phím mũi tên khi chọn nút"
      onPointerDown={(event) => {
        if (event.button !== 0) return;
        const rect = event.currentTarget.getBoundingClientRect();
        launcherMoved.current = false;
        launcherDrag.current = { pointerId: event.pointerId, x: event.clientX, y: event.clientY, left: rect.left, top: rect.top };
        event.currentTarget.setPointerCapture(event.pointerId);
      }}
      onPointerMove={(event) => {
        const drag = launcherDrag.current;
        if (!drag || drag.pointerId !== event.pointerId) return;
        const dx = event.clientX - drag.x, dy = event.clientY - drag.y;
        if (Math.hypot(dx, dy) > 5) launcherMoved.current = true;
        if (launcherMoved.current) moveLauncher({ x: drag.left + dx, y: drag.top + dy });
      }}
      onPointerUp={() => { launcherDrag.current = null; }}
      onPointerCancel={() => { launcherDrag.current = null; }}
      onLostPointerCapture={() => { launcherDrag.current = null; }}
      onKeyDown={(event) => {
        if (!['ArrowLeft', 'ArrowRight', 'ArrowUp', 'ArrowDown'].includes(event.key)) return;
        event.preventDefault();
        const rect = event.currentTarget.getBoundingClientRect();
        moveLauncher({ x: rect.left + (event.key === 'ArrowLeft' ? -24 : event.key === 'ArrowRight' ? 24 : 0),
          y: rect.top + (event.key === 'ArrowUp' ? -24 : event.key === 'ArrowDown' ? 24 : 0) });
      }}
      onClick={(event) => { if (event.detail > 0 && launcherMoved.current) return; open ? close() : setOpen(true); }}
      aria-label={open ? 'Thu gọn trợ lý AI' : 'Mở trợ lý AI kinh doanh'} aria-expanded={open} aria-controls={open ? 'business-assistant-panel' : undefined}>
      <Robot /><span>Trợ lý AI</span>
    </button>
  </div>, document.body);
}
