/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: TrongNV
 * Created: 12/10/2026
 * Updated: HuyDV - 14/10/2026 (UC-14 đến UC-18).
 * Description: Màn hình quản lý hạng phòng, quy tắc giá và combo.
 */

import { useEffect, useMemo, useState } from 'react';
import { catalogError, roomCatalogApi } from '../services/roomCatalogApi';
import '../styles/room-catalog.css';
const tabs = ['Hạng phòng', 'Giá theo thời gian', 'Combo'];
const weekdayLabels = ['T2', 'T3', 'T4', 'T5', 'T6', 'T7', 'CN'];
const blankRoom = {
  code: '',
  name: '',
  serviceType: 'CINEMA',
  capacity: '',
  description: ''
};
const blankCombo = {
  code: '',
  name: '',
  description: '',
  durationMinutes: 120,
  minuteFrom: 0,
  minuteTo: 1440,
  price: '',
  serviceType: 'CINEMA'
};
const blankRule = {
  code: '',
  name: '',
  roomTypeId: '',
  weekdays: [1, 2, 3, 4, 5],
  validFrom: today(),
  validTo: '',
  minuteFrom: 1080,
  minuteTo: 1440,
  hourlyPrice: ''
};
const money = value => new Intl.NumberFormat('vi-VN').format(Number(value || 0)) + 'đ';
const capacityLabel = item => `${item.capacity} khách`;
export default function RoomCatalogPage() {
  const [tab, setTab] = useState(tabs[0]);
  const [rooms, setRooms] = useState([]);
  const [prices, setPrices] = useState([]);
  const [rules, setRules] = useState([]);
  const [query, setQuery] = useState('');
  const [service, setService] = useState('ALL');
  const [selectedRoom, setSelectedRoom] = useState(null);
  const [roomFilterId, setRoomFilterId] = useState('');
  const [statusFilter, setStatusFilter] = useState('active');
  const [editor, setEditor] = useState(null);
  const [form, setForm] = useState(blankRoom);
  const [pendingDelete, setPendingDelete] = useState(null);
  const [pendingDeactivate, setPendingDeactivate] = useState(null);
  const [page, setPage] = useState(0);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  // Giữ focus trong hộp thoại; Escape hủy khi chưa gửi yêu cầu.
  useEffect(() => {
    if (!editor && !pendingDelete && !pendingDeactivate) return undefined;
    const previous = document.activeElement;
    const modal = document.querySelector('.catalog-confirm-dialog, .catalog-editor');
    if (!modal) return undefined;
    const focusable = () => [...modal.querySelectorAll('button:not(:disabled), input:not(:disabled), select:not(:disabled), textarea:not(:disabled)')];
    focusable()[0]?.focus();
    const handleKey = event => {
      if (event.key === 'Escape' && !busy) {
        setEditor(null);
        setPendingDelete(null);
        setPendingDeactivate(null);
        setError('');
      }
      if (event.key === 'Tab') {
        const items = focusable();
        const target = event.shiftKey ? items[items.length - 1] : items[0];
        if ((event.shiftKey && document.activeElement === items[0]) || (!event.shiftKey && document.activeElement === items[items.length - 1])) {
          event.preventDefault();
          target?.focus();
        }
      }
    };
    document.addEventListener('keydown', handleKey);
    return () => {
      document.removeEventListener('keydown', handleKey);
      previous?.focus();
    };
  }, [editor, pendingDelete, pendingDeactivate, busy]);
  const refresh = async () => {
    setLoading(true);
    setError('');
    try {
      const [roomData, priceData, ruleData] = await Promise.all([roomCatalogApi.roomTypes(), roomCatalogApi.prices(), roomCatalogApi.priceRules()]);
      setRooms(roomData);
      setPrices(priceData);
      setRules(ruleData);
      setSelectedRoom(current => roomData.find(r => r.id === current?.id && r.active) || roomData.find(r => r.code === 'RT-QN' && r.active) || roomData.find(r => r.active) || null);
    } catch (e) {
      setError(catalogError(e));
    } finally {
      setLoading(false);
    }
  };
  useEffect(() => {
    refresh();
  }, []);
  useEffect(() => {
    if (!notice) return undefined;
    const timeout = window.setTimeout(() => setNotice(''), 4500);
    return () => window.clearTimeout(timeout);
  }, [notice]);
  const visibleRooms = useMemo(() => rooms.filter(room => `${room.code} ${room.name}`.toLowerCase().includes(query.toLowerCase()) && (service === 'ALL' || room.serviceType === service) && matchesStatus(room.active, statusFilter)), [rooms, query, service, statusFilter]);
  const visibleRules = useMemo(() => rules.filter(rule => (!roomFilterId || rule.roomTypeId === roomFilterId) && (service === 'ALL' || rooms.find(r => r.id === rule.roomTypeId)?.serviceType === service) && matchesStatus(rule.active, statusFilter) && `${rule.code} ${rule.name} ${rule.roomTypeName}`.toLowerCase().includes(query.toLowerCase())), [rules, roomFilterId, service, statusFilter, rooms, query]);
  const visibleCombos = useMemo(() => prices.filter(price => price.kind === 'COMBO' && (!roomFilterId || price.roomTypeId === roomFilterId) && (service === 'ALL' || rooms.find(r => r.id === price.roomTypeId)?.serviceType === service) && matchesStatus(price.active, statusFilter) && `${price.code} ${price.name} ${price.roomTypeName} ${price.description}`.toLowerCase().includes(query.toLowerCase())), [prices, roomFilterId, service, statusFilter, rooms, query]);
  useEffect(() => {
    setPage(0);
  }, [query, service, statusFilter, rooms]);
  const openRoom = (item = null) => {
    setError('');
    setEditor({
      type: 'room',
      item
    });
    setForm(item ? {
      code: item.code,
      name: item.name,
      serviceType: item.serviceType,
      capacity: item.capacity,
      description: item.description || ''
    } : {
      ...blankRoom
    });
  };
  const openRule = (item = null) => {
    setEditor({
      type: 'rule',
      item
    });
    setForm(item ? {
      code: item.code,
      name: item.name,
      roomTypeId: item.roomTypeId,
      weekdays: item.weekdays,
      validFrom: item.validFrom,
      validTo: item.validTo || '',
      minuteFrom: item.minuteFrom,
      minuteTo: item.minuteTo,
      hourlyPrice: item.hourlyPrice
    } : {
      ...blankRule,
      roomTypeId: selectedRoom?.id || rooms.find(r => r.serviceType === 'CINEMA')?.id || ''
    });
  };
  const openCombo = (item = null) => {
    setEditor({
      type: 'combo',
      item
    });
    const room = rooms.find(r => r.id === (item?.roomTypeId || selectedRoom?.id)) || rooms.find(r => r.active);
    setForm(item ? {
      code: item.code,
      name: item.name,
      description: item.description || '',
      durationMinutes: item.durationMinutes,
      minuteFrom: item.minuteFrom,
      minuteTo: item.minuteTo,
      price: item.price,
      roomTypeId: item.roomTypeId,
      serviceType: room?.serviceType || 'CINEMA'
    } : {
      ...blankCombo,
      roomTypeId: room?.id || '',
      serviceType: room?.serviceType || 'CINEMA'
    });
  };
  const submit = async event => {
    event.preventDefault();
    if (!editor || busy) return;
    setBusy(true);
    setError('');
    setNotice('');
    let successMessage = '';
    try {
      if (editor.type === 'room') {
        const payload = {
          ...form,
          capacity: Number(form.capacity)
        };
        if (editor.item) await roomCatalogApi.updateRoomType(editor.item.id, payload);else await roomCatalogApi.createRoomType(payload);
        successMessage = editor.item ? 'Sửa hạng phòng thành công.' : 'Thêm hạng phòng thành công.';
      } else if (editor.type === 'rule') {
        const payload = {
          ...form,
          roomTypeId: form.roomTypeId,
          weekdays: form.weekdays,
          validTo: form.validTo || null,
          minuteFrom: Number(form.minuteFrom),
          minuteTo: Number(form.minuteTo),
          hourlyPrice: Number(form.hourlyPrice)
        };
        if (editor.item) await roomCatalogApi.updatePriceRule(editor.item.id, payload);else await roomCatalogApi.createPriceRule(payload);
        successMessage = editor.item ? 'Sửa quy tắc giá thành công.' : 'Thêm quy tắc giá thành công.';
      } else {
        const payload = {
          roomTypeId: form.roomTypeId,
          code: form.code.trim().toUpperCase(),
          name: form.name,
          description: form.description,
          durationMinutes: Number(form.durationMinutes),
          minuteFrom: Number(form.minuteFrom),
          minuteTo: Number(form.minuteTo),
          price: Number(form.price)
        };
        if (editor.item) await roomCatalogApi.updatePrice('COMBO', editor.item.id, payload);else await roomCatalogApi.createCombo(form.roomTypeId, payload);
        successMessage = editor.item ? 'Sửa combo thành công.' : 'Thêm combo thành công.';
      }
      setEditor(null);
      await refresh();
      setNotice(successMessage);
    } catch (e) {
      setError(catalogError(e));
    } finally {
      setBusy(false);
    }
  };
  const remove = async (type, item) => {
    const noun = type === 'room' ? 'hạng phòng' : type === 'rule' ? 'quy tắc giá' : 'combo';
    setBusy(true);
    setError('');
    setNotice('');
    try {
      if (type === 'room') await roomCatalogApi.deleteRoomType(item.id);else if (type === 'rule') await roomCatalogApi.deletePriceRule(item.id);else await roomCatalogApi.deletePrice('COMBO', item.id);
      setPendingDelete(null);
      await refresh();
      setNotice(`Xóa ${noun} thành công.`);
    } catch (e) {
      setPendingDelete(null);
      setError(catalogError(e));
    } finally {
      setBusy(false);
    }
  };

  // Chỉ gửi yêu cầu sau khi người dùng xác nhận.
  const deactivate = async () => {
    if (busy || !pendingDeactivate) return;
    setBusy(true);
    setError('');
    try {
      await roomCatalogApi.deactivateRoomType(pendingDeactivate.id);
      setPendingDeactivate(null);
      await refresh();
      setNotice('Đã ngừng sử dụng hạng phòng. Dữ liệu liên quan được giữ nguyên.');
    } catch (e) {
      setPendingDeactivate(null);
      setError(catalogError(e));
    } finally {
      setBusy(false);
    }
  };
  if (editor && editor.type !== 'room') return <EditorPage editor={editor} form={form} setForm={setForm} rooms={rooms} busy={busy} error={error} onSubmit={submit} onCancel={() => {
    setEditor(null);
    setError('');
  }} />;
  const add = () => tab === 'Hạng phòng' ? openRoom() : tab === 'Giá theo thời gian' ? openRule() : openCombo();
  const rows = tab === 'Hạng phòng' ? visibleRooms : tab === 'Giá theo thời gian' ? visibleRules : visibleCombos;
  const title = tab === 'Hạng phòng' ? 'Hạng phòng' : tab === 'Giá theo thời gian' ? 'Giá theo thời gian' : 'Combo';
  return <div className="room-catalog-page">
    <div className="room-catalog-title-row">
      <div>
        <h1>{title}</h1>
        <p>{tab === 'Hạng phòng' ? 'Chuẩn hóa hạng phòng và thông tin dùng chung' : tab === 'Giá theo thời gian' ? 'Cấu hình mức giá theo hạng phòng, ngày và khung giờ' : 'Quản lý gói dịch vụ áp dụng cho booking mới'}</p>
      </div>
      <button className="catalog-primary" onClick={add}>{tab === 'Hạng phòng' ? 'Thêm hạng phòng' : tab === 'Giá theo thời gian' ? 'Thêm quy tắc giá' : 'Thêm combo'}</button>
    </div>
    <div className="room-catalog-tabs" role="tablist" aria-label="Danh mục phòng và giá">{tabs.map(item => <button key={item} role="tab" aria-selected={tab === item} className={tab === item ? 'selected' : ''} onClick={() => {
        setTab(item);
        setQuery('');
        setError('');
      }}>{item}</button>)}</div>
    <div className="room-catalog-filters">
      <label className="room-search">
        <span aria-hidden="true">⌕</span>
        <input value={query} onChange={e => setQuery(e.target.value)} placeholder={tab === 'Hạng phòng' ? 'Tìm mã hoặc tên hạng' : tab === 'Combo' ? 'Tìm mã / tên combo' : 'Tìm tên quy tắc'} />
      </label>
      <select aria-label="Loại dịch vụ" value={service} onChange={e => setService(e.target.value)}>
        <option value="ALL">Loại dịch vụ: Tất cả</option>
        <option value="CINEMA">Cinema</option>
        <option value="MUSIC_BOX">Music Box</option>
      </select>
      {tab !== 'Hạng phòng' && <select aria-label="Hạng phòng" value={tab === 'Hạng phòng' ? selectedRoom?.id || '' : roomFilterId} onChange={e => tab === 'Hạng phòng' ? setSelectedRoom(rooms.find(r => r.id === e.target.value) || null) : setRoomFilterId(e.target.value)}>
        <option value="">Tất cả hạng phòng</option>
        {rooms.filter(r => r.active).map(r => <option key={r.id} value={r.id}>{r.name}</option>)}
      </select>}
      <select aria-label="Trạng thái" value={statusFilter} onChange={e => setStatusFilter(e.target.value)}>
        <option value="active">Đang áp dụng</option>
        <option value="inactive">Đã ngừng</option>
        <option value="all">Trạng thái: Tất cả</option>
      </select>
    </div>
    {error && <div className="catalog-message error" role="alert">{error}</div>}
    {notice && <div className="catalog-toast" role="status">{notice}</div>}
    <section className="room-catalog-table-card">
      {loading ? <div className="catalog-loading">Đang tải dữ liệu…</div> : tab === 'Hạng phòng' ? <div className="catalog-table-scroll">
        <table className="catalog-table">
          <thead>
            <tr>
              <th>Mã / tên hạng</th>
              <th>Loại dịch vụ</th>
              <th>Sức chứa</th>
              <th>Mô tả</th>
              <th>Trạng thái</th>
              <th>Thao tác</th>
            </tr>
          </thead>
          <tbody>
      {visibleRooms.slice(page * 10, (page + 1) * 10).map(room => <tr key={room.id} className={`${room.id === selectedRoom?.id ? 'highlighted' : ''} ${!room.active ? 'inactive-row' : ''}`} onClick={() => setSelectedRoom(room)}>
              <td>
                <strong>{room.code}</strong>
                <small>{room.name}</small>
              </td>
              <td>{room.serviceType === 'CINEMA' ? 'Cinema' : 'Music Box'}</td>
              <td>{capacityLabel(room)}</td>
              <td>{room.description || '—'}</td>
              <td>
                <Status active={room.active} activeText="Đang dùng" />
              </td>
              <td className="catalog-row-actions">
                <button className="catalog-link" onClick={e => {
                  e.stopPropagation();
                  openRoom(room);
                }}>Sửa</button>
                <button className="catalog-link" disabled={busy || !room.active} onClick={e => {
                  e.stopPropagation();
                  setPendingDeactivate(room);
                }}>Ngừng sử dụng</button>
                <button className="catalog-link muted" disabled={busy} onClick={e => {
                  e.stopPropagation();
                  setPendingDelete({
                    type: 'room',
                    item: room
                  });
                }}>Xóa</button>
              </td>
            </tr>)}
      {visibleRooms.length === 0 && <Empty colSpan={6} />}</tbody>
        </table>
      </div> : tab === 'Giá theo thời gian' ? <div className="catalog-table-scroll">
        <table className="catalog-table price-table">
          <thead>
            <tr>
              <th>Quy tắc</th>
              <th>Hạng phòng</th>
              <th>Ngày áp dụng</th>
              <th>Khung giờ</th>
              <th>Giá</th>
              <th>Hiệu lực</th>
              <th>Thao tác</th>
            </tr>
          </thead>
          <tbody>
      {visibleRules.map(rule => <tr key={rule.id} className={!rule.active ? 'inactive-row' : ''}>
              <td>
                <strong>{rule.code}</strong>
                <small>{rule.roomTypeName} · {rule.name}</small>
              </td>
              <td>{rule.roomTypeName}</td>
              <td>{rule.weekdays.map(d => weekdayLabels[d - 1]).join(', ')}</td>
              <td>{timeLabel(rule.minuteFrom)}–{timeLabel(rule.minuteTo)}</td>
              <td>
                <strong>{money(rule.hourlyPrice)} / giờ</strong>
              </td>
              <td>
                <Status active={rule.active} activeText="Đang hiệu lực" />
              </td>
              <td className="catalog-row-actions">
                <button className="catalog-link" onClick={() => openRule(rule)}>Sửa</button>
                <button className="catalog-link muted" disabled={busy || !rule.active} onClick={() => setPendingDelete({
                  type: 'rule',
                  item: rule
                })}>Xóa</button>
              </td>
            </tr>)}
      {visibleRules.length === 0 && <Empty colSpan={7} />}</tbody>
        </table>
      </div> : <div className="catalog-table-scroll">
        <table className="catalog-table price-table">
          <thead>
            <tr>
              <th>Mã / tên combo</th>
              <th>Hạng phòng</th>
              <th>Thành phần</th>
              <th>Giá combo</th>
              <th>Trạng thái</th>
              <th>Thao tác</th>
            </tr>
          </thead>
          <tbody>
      {visibleCombos.map(combo => <tr key={combo.id} className={!combo.active ? 'inactive-row' : ''}>
              <td>
                <strong>{combo.code}</strong>
                <small>{combo.name}</small>
              </td>
              <td>{combo.roomTypeName}</td>
              <td>{combo.description || `${combo.durationMinutes} phút phòng`}</td>
              <td>
                <strong>{money(combo.price)}</strong>
              </td>
              <td>
                <Status active={combo.active} activeText="Đang dùng" />
              </td>
              <td className="catalog-row-actions">
                <button className="catalog-link" onClick={() => openCombo(combo)}>Sửa</button>
                <button className="catalog-link muted" disabled={busy || !combo.active} onClick={() => setPendingDelete({
                  type: 'combo',
                  item: combo
                })}>Xóa</button>
              </td>
            </tr>)}
      {visibleCombos.length === 0 && <Empty colSpan={6} />}</tbody>
        </table>
      </div>}
      <div className="catalog-table-footer">
        <span>{rows.length ? `Hiển thị ${tab === 'Hạng phòng' ? page * 10 + 1 : 1}–${tab === 'Hạng phòng' ? Math.min((page + 1) * 10, rows.length) : rows.length} / ${rows.length} dòng` : 'Không có dữ liệu phù hợp'}</span>
        <span>{tab === 'Hạng phòng' && <><button disabled={page === 0} onClick={() => setPage(page - 1)}>Trước</button> <span>Trang {page + 1}</span> <button disabled={(page + 1) * 10 >= rows.length} onClick={() => setPage(page + 1)}>Sau</button></>}</span>
      </div>
    </section>
    {tab === 'Hạng phòng' && selectedRoom && <button className="catalog-view-prices" onClick={() => {
      setRoomFilterId(selectedRoom.id);
      setTab('Giá theo thời gian');
    }}>Xem bảng giá hạng {selectedRoom.name}</button>}
    {pendingDeactivate && <div className="catalog-confirm-backdrop">
      <section className="catalog-confirm-dialog" role="alertdialog" aria-modal="true" aria-labelledby="deactivate-title">
        <h2 id="deactivate-title">Ngừng sử dụng hạng phòng?</h2>
        <p>
          <strong>{pendingDeactivate.code} · {pendingDeactivate.name}</strong>
        </p>
        <p>Hạng phòng sẽ không còn được chọn khi tạo phòng hoặc đổi sang hạng này. Phòng hiện có, giá, combo và lịch sử vẫn được giữ nguyên.</p>
        <div>
          <button type="button" autoFocus disabled={busy} onClick={() => setPendingDeactivate(null)}>Hủy</button>
          <button type="button" className="catalog-confirm-delete" disabled={busy} onClick={deactivate}>{busy ? 'Đang lưu…' : 'Ngừng sử dụng'}</button>
        </div>
      </section>
    </div>}
    {pendingDelete && <DeleteDialog target={pendingDelete} busy={busy} onCancel={() => setPendingDelete(null)} onConfirm={() => remove(pendingDelete.type, pendingDelete.item)} />}
    {editor?.type === 'room' && <EditorPage editor={editor} form={form} setForm={setForm} rooms={rooms} busy={busy} error={error} onSubmit={submit} onCancel={() => {
      setEditor(null);
      setError('');
    }} />}
  </div>;
}
function EditorPage({
  editor,
  form,
  setForm,
  rooms,
  busy,
  error,
  onSubmit,
  onCancel
}) {
  const isRoom = editor.type === 'room';
  const isRule = editor.type === 'rule';
  const item = editor.item;
  const room = rooms.find(r => r.id === (form.roomTypeId || item?.roomTypeId));
  const heading = isRoom ? item ? 'Sửa hạng phòng' : 'Thêm hạng phòng' : isRule ? item ? 'Sửa quy tắc giá' : 'Thêm quy tắc giá' : item ? 'Sửa combo' : 'Thêm combo';
  const toggleDay = day => setForm({
    ...form,
    weekdays: form.weekdays.includes(day) ? form.weekdays.filter(d => d !== day) : [...form.weekdays, day].sort((a, b) => a - b)
  });
  return <div className={`catalog-editor-page ${isRoom ? 'room-drawer-page' : ''}`}>
    {isRoom && <button className="catalog-drawer-scrim" aria-label="Đóng biểu mẫu" disabled={busy} onClick={onCancel} />}
    <form role={isRoom ? "dialog" : undefined} aria-modal={isRoom ? "true" : undefined} aria-label={heading} className={`catalog-editor ${isRoom ? 'catalog-drawer' : 'catalog-editor-full'}`} onSubmit={onSubmit}>
      <div className="catalog-editor-header">
        <div>
          <h1>{heading}</h1>
          <p>{isRoom ? 'Cấu hình dùng chung trong hệ thống' : isRule ? 'Cấu hình theo hạng phòng, ngày và khung giờ áp dụng' : 'Chọn thành phần và giá gói phù hợp với dịch vụ'}</p>
        </div>
        <button type="button" className="catalog-modal-close" aria-label="Đóng" disabled={busy} onClick={onCancel}>×</button>
      </div>
      <div className="catalog-editor-body">{isRoom ? <div className="catalog-editor-fields room-form">
          <label>
Mã hạng phòng
<input autoFocus={!item} required pattern="[A-Za-z0-9_-]{2,60}" title="Mã gồm 2–60 chữ cái, số, gạch ngang hoặc gạch dưới" maxLength="60" readOnly={Boolean(item)} value={form.code} onChange={e => setForm({
              ...form,
              code: e.target.value.toUpperCase()
            })} placeholder="Nhập thông tin..." />
          </label>
          <label>
Tên hạng phòng
<input required maxLength="200" value={form.name} onChange={e => setForm({
              ...form,
              name: e.target.value
            })} placeholder="Nhập thông tin..." />
          </label>
          <label>
Loại dịch vụ
<select value={form.serviceType} onChange={e => setForm({
              ...form,
              serviceType: e.target.value
            })}>
              <option value="CINEMA">Cinema</option>
              <option value="MUSIC_BOX">Music Box</option>
            </select>
          </label>
          <label>
Sức chứa
<input required min="1" type="number" value={form.capacity} onChange={e => setForm({
              ...form,
              capacity: e.target.value
            })} placeholder="Nhập thông tin..." />
          </label>
          <label>
Mô tả / tiện ích
<textarea rows="4" value={form.description} onChange={e => setForm({
              ...form,
              description: e.target.value
            })} />
          </label>
          <aside className="catalog-info-box">
            <strong>Giá được cấu hình riêng</strong>
            <span>Đặt giá áp dụng tại mục Giá theo thời gian.</span>
          </aside>
        </div> : isRule ? <div className="catalog-form-layout">
          <div className="catalog-form-card">
            <h2>Thông tin quy tắc</h2>
            <div className="catalog-editor-fields two-col">
              <label>
Mã quy tắc
<input required maxLength="60" disabled={Boolean(item)} value={form.code} onChange={e => setForm({
                  ...form,
                  code: e.target.value.toUpperCase()
                })} placeholder="BG-QN-01" />
              </label>
              <label>
Tên quy tắc
<input required value={form.name} onChange={e => setForm({
                  ...form,
                  name: e.target.value
                })} placeholder="Queen · Buổi tối" />
              </label>
              <label>
Hạng phòng
<select required value={form.roomTypeId} onChange={e => setForm({
                  ...form,
                  roomTypeId: e.target.value
                })}>{rooms.filter(r => r.serviceType === 'CINEMA' && r.active).map(r => <option key={r.id} value={r.id}>{r.name}</option>)}</select>
              </label>
              <label>
Giá áp dụng / giờ
<input required min="0" type="number" value={form.hourlyPrice} onChange={e => setForm({
                  ...form,
                  hourlyPrice: e.target.value
                })} />
              </label>
              <label>
Ngày bắt đầu
<input required type="date" value={form.validFrom} onChange={e => setForm({
                  ...form,
                  validFrom: e.target.value
                })} />
              </label>
              <label>
Ngày kết thúc
<input type="date" min={form.validFrom} value={form.validTo} onChange={e => setForm({
                  ...form,
                  validTo: e.target.value
                })} />
              </label>
              <div className="catalog-days">
                <span>Ngày trong tuần</span>
                <div>{weekdayLabels.map((day, index) => <button type="button" key={day} className={form.weekdays.includes(index + 1) ? 'selected' : ''} onClick={() => toggleDay(index + 1)}>{day}</button>)}</div>
              </div>
              <label>
Bắt đầu
<select value={form.minuteFrom} onChange={e => setForm({
                  ...form,
                  minuteFrom: e.target.value
                })}>{timeOptions().map(t => <option value={t.value} key={t.value}>{t.label}</option>)}</select>
              </label>
              <label>
Kết thúc
<select value={form.minuteTo} onChange={e => setForm({
                  ...form,
                  minuteTo: e.target.value
                })}>{timeOptions(true).map(t => <option value={t.value} key={t.value}>{t.label}</option>)}</select>
              </label>
            </div>
          </div>
          <aside className="catalog-preview-card">
            <h2>Xem trước quy tắc</h2>
            <p>Hạng / thời điểm mẫu</p>
            <strong>{room?.name || 'Chọn hạng phòng'} · {form.validFrom || 'Chọn ngày'} · {timeLabel(Number(form.minuteFrom))}</strong>
            <p>Giá áp dụng</p>
            <strong>{money(form.hourlyPrice)} / giờ</strong>
            <p>Phạm vi ngày</p>
            <strong>{(form.weekdays || []).map(d => weekdayLabels[d - 1]).join(', ') || 'Chưa chọn ngày'}</strong>
            <div className="catalog-info-box">
              <strong>Kiểm tra thời gian áp dụng</strong>
              <span>{form.validTo ? `Áp dụng đến ${form.validTo}.` : `Quy tắc bắt đầu từ ${form.validFrom || 'ngày đã chọn'}.`}</span>
            </div>
          </aside>
        </div> : <div className="catalog-form-layout">
          <div className="catalog-form-card">
            <h2>Thông tin combo</h2>
            <div className="catalog-editor-fields two-col">
              <label>
Mã combo
<input required pattern="[A-Za-z0-9_-]{2,60}" title="Mã gồm 2–60 ký tự: chữ cái, số, dấu gạch ngang hoặc gạch dưới." maxLength="60" disabled={Boolean(item)} value={form.code} onChange={e => setForm({
                  ...form,
                  code: e.target.value.toUpperCase()
                })} placeholder="CB4-KING-4H" />
              </label>
              <label>
Tên combo
<input required value={form.name} onChange={e => setForm({
                  ...form,
                  name: e.target.value
                })} placeholder="Queen 90 phút" />
              </label>
              <label>
Loại dịch vụ
<select value={form.serviceType || room?.serviceType || 'CINEMA'} onChange={e => {
                  const serviceType = e.target.value;
                  const firstRoom = rooms.find(r => r.active && r.serviceType === serviceType);
                  setForm({
                    ...form,
                    serviceType,
                    roomTypeId: firstRoom?.id || ''
                  });
                }}>
                  <option value="CINEMA">Cinema</option>
                  <option value="MUSIC_BOX">Music Box</option>
                </select>
              </label>
              <label>
Hạng áp dụng
<select required value={form.roomTypeId || ''} onChange={e => {
                  const selected = rooms.find(r => r.id === e.target.value);
                  setForm({
                    ...form,
                    roomTypeId: e.target.value,
                    serviceType: selected?.serviceType || form.serviceType
                  });
                }}>{rooms.filter(r => r.serviceType === (form.serviceType || room?.serviceType || 'CINEMA') && r.active).map(r => <option key={r.id} value={r.id}>{r.name}</option>)}</select>
              </label>
              <label className="span-two">
Mô tả / thành phần
<input value={form.description} onChange={e => setForm({
                  ...form,
                  description: e.target.value
                })} placeholder="2 giờ phòng; đồ ăn; đồ uống..." />
              </label>
              <label>
Thời lượng (phút)
<input required min="1" type="number" value={form.durationMinutes} onChange={e => setForm({
                  ...form,
                  durationMinutes: e.target.value
                })} />
              </label>
              <label>
Giá combo
<input required min="0" type="number" value={form.price} onChange={e => setForm({
                  ...form,
                  price: e.target.value
                })} />
              </label>
              <label>
Bắt đầu
<select value={form.minuteFrom} onChange={e => setForm({
                  ...form,
                  minuteFrom: e.target.value
                })}>
                  <option value="0">Cả ngày</option>
                  <option value="420">07:00</option>
                  <option value="1380">23:00</option>
                </select>
              </label>
              <label>
Kết thúc
<select value={form.minuteTo} onChange={e => setForm({
                  ...form,
                  minuteTo: e.target.value
                })}>
                  <option value="1440">24:00</option>
                  <option value="720">12:00</option>
                  <option value="420">07:00</option>
                </select>
              </label>
            </div>
          </div>
          <aside className="catalog-preview-card">
            <h2>Xem trước hiển thị</h2>
            <div className="catalog-combo-preview">
              <small>{room?.serviceType === 'MUSIC_BOX' ? 'MUSIC BOX' : 'CINEMA'} · {room?.name || 'Hạng phòng'}</small>
              <strong>{form.name || 'Tên combo'}</strong>
              <span>{form.durationMinutes || 0} phút sử dụng phòng</span>
            </div>
            <b className="catalog-preview-price">{money(form.price)}</b>
            <div className="catalog-info-box">
              <strong>Cấu hình combo</strong>
              <span>Combo mới áp dụng cho booking mới. Hóa đơn đã chốt giữ nguyên dữ liệu lịch sử.</span>
            </div>
          </aside>
        </div>}</div>
      {error && <div className="catalog-message error" role="alert">{error}</div>}
      <div className="catalog-editor-footer">
        <button type="button" disabled={busy} onClick={onCancel}>Hủy</button>
        <button className="catalog-primary" disabled={busy}>{busy ? 'Đang lưu…' : isRoom ? 'Lưu hạng phòng' : isRule ? 'Lưu quy tắc giá' : 'Lưu combo'}</button>
      </div>
    </form>
  </div>;
}
function Status({
  active,
  activeText
}) {
  return <span className={`catalog-status ${active ? 'on' : 'off'}`}>{active ? activeText : 'Đã ngừng'}</span>;
}
function DeleteDialog({
  target,
  busy,
  onCancel,
  onConfirm
}) {
  const noun = target.type === 'room' ? 'hạng phòng' : target.type === 'rule' ? 'quy tắc giá' : 'combo';
  return <div className="catalog-confirm-backdrop" role="presentation" onMouseDown={e => {
    if (e.target === e.currentTarget && !busy) onCancel();
  }}>
    <section className="catalog-confirm-dialog" role="alertdialog" aria-modal="true" aria-labelledby="catalog-confirm-title">
      <h2 id="catalog-confirm-title">Xóa {noun}?</h2>
      <p>Bạn có chắc muốn xóa “{target.item.name}” không? Bản ghi sẽ bị xóa khỏi cơ sở dữ liệu. Nếu hạng phòng, combo hoặc quy tắc giá đang được dữ liệu khác sử dụng, hệ thống sẽ từ chối xóa và thông báo lý do.</p>
      <div>
        <button type="button" disabled={busy} onClick={onCancel}>Hủy</button>
        <button type="button" className="catalog-confirm-delete" disabled={busy} onClick={onConfirm}>{busy ? 'Đang xóa…' : 'Xóa'}</button>
      </div>
    </section>
  </div>;
}
function Empty({
  colSpan
}) {
  return <tr>
    <td colSpan={colSpan} className="catalog-empty">Không tìm thấy dữ liệu phù hợp.</td>
  </tr>;
}
function matchesStatus(active, status) {
  return status === 'all' || (status === 'active' ? active : !active);
}
function timeLabel(minute) {
  if (minute === 1440) return '24:00';
  return `${String(Math.floor((minute || 0) / 60)).padStart(2, '0')}:${String((minute || 0) % 60).padStart(2, '0')}`;
}
function timeOptions(includeEnd = false) {
  const values = Array.from({
    length: 24
  }, (_, i) => ({
    value: i * 60,
    label: `${String(i).padStart(2, '0')}:00`
  }));
  return includeEnd ? [...values, {
    value: 1440,
    label: '24:00'
  }] : values;
}
function today() {
  const date = new Date();
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;
}
