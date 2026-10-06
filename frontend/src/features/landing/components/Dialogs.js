import { useEffect, useRef } from 'react';
import { branches } from '../data/siteData';
import { branchTypeName, formatPhone } from '../../../shared/utils/formatters';

function useDialog(isOpen, onClose) {
  const ref = useRef(null);
  useEffect(() => {
    const dialog = ref.current;
    if (!dialog) return undefined;
    if (isOpen && !dialog.open) dialog.showModal();
    if (!isOpen && dialog.open) dialog.close();
    document.body.style.overflow = isOpen ? 'hidden' : '';
    return () => { document.body.style.overflow = ''; };
  }, [isOpen]);
  useEffect(() => {
    const dialog = ref.current;
    const handleClose = () => onClose();
    dialog?.addEventListener('close', handleClose);
    return () => dialog?.removeEventListener('close', handleClose);
  }, [onClose]);
  return ref;
}

const closeFromBackdrop = (event) => {
  const rect = event.currentTarget.getBoundingClientRect();
  if (event.clientX < rect.left || event.clientX > rect.right || event.clientY < rect.top || event.clientY > rect.bottom) event.currentTarget.close();
};

export function RoomDialog({ room, onClose, onBook }) {
  const ref = useDialog(Boolean(room), onClose);
  return (
    <dialog ref={ref} id="room-dialog" aria-labelledby="room-dialog-title" onClick={closeFromBackdrop}>
      <button className="dialog-close" aria-label="Đóng chi tiết phòng" onClick={onClose}>×</button>
      {room && <div id="room-dialog-content"><img src={room.image} style={{ objectPosition: room.position }} alt={`Ảnh ${room.name}`} /><div className="room-dialog-text"><span className="eyebrow">{room.label}</span><h2 id="room-dialog-title">{room.name}</h2><p>{room.description}</p><small>Hình ảnh phòng thực tế. Concept, hạng phòng và trang thiết bị tùy cơ sở. Liên hệ để chọn đúng phòng mong muốn.</small><button className="button pink" onClick={() => { onClose(); onBook(); }}>Tư vấn phòng này <span>↗</span></button></div></div>}
    </dialog>
  );
}

export function BookingDialog({ isOpen, branchIndex, onBranchChange, onClose }) {
  const ref = useDialog(isOpen, onClose);
  const branch = branches[branchIndex] || branches[0];
  return (
    <dialog ref={ref} id="booking-dialog" aria-labelledby="booking-title" onClick={closeFromBackdrop}>
      <button className="dialog-close" aria-label="Đóng đặt phòng" onClick={onClose}>×</button><p className="eyebrow">LET&apos;S MAKE A PLAN</p><h2 id="booking-title">Hẹn nhau ở <span className="pink-text">GenZ.</span></h2><p>Chọn chi nhánh để liên hệ và xác nhận phòng trống.</p>
      <label htmlFor="booking-branch">Bạn muốn đến đâu?</label>
      <select id="booking-branch" value={branchIndex} onChange={(event) => onBranchChange(Number(event.target.value))}>{branches.map((item, index) => <option key={`${item.name}-${item.phone}`} value={index}>{item.city === 'hn' ? 'Hà Nội' : 'TP.HCM'} · {item.name}{item.type !== 'cinema' ? ` · ${branchTypeName(item.type)}` : ''}</option>)}</select>
      <div className="booking-info">{branch.address}</div><a className="button pink" href={`tel:${branch.phone}`}>Gọi {formatPhone(branch.phone)} <span>↗</span></a><a className="button outline" href="https://www.facebook.com/genzcinema/" target="_blank" rel="noopener noreferrer">Nhắn Facebook GenZ <span>↗</span></a><small>Phòng chỉ được giữ sau khi chi nhánh xác nhận với bạn.</small>
    </dialog>
  );
}
