import { useEffect, useRef, useState } from 'react';
import { PaginatedList } from '../../../shared/components/Pagination';

export default function ComboGiftPicker({ drinkSelections, setDrinkSelections, foodSelections, setFoodSelections, giftMenu, disabled = false }) {
  const [giftPicker, setGiftPicker] = useState(null);
  const [giftSearch, setGiftSearch] = useState('');
  const [giftCategoryFilter, setGiftCategoryFilter] = useState('all');
  const [giftStockFilter, setGiftStockFilter] = useState('all');
  const giftPickerTriggerRef = useRef(null);

  useEffect(() => {
    if (!giftPicker) return undefined;
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    return () => { document.body.style.overflow = previousOverflow; };
  }, [giftPicker]);

  const openGiftPicker = (event, label, prefix, index) => {
    giftPickerTriggerRef.current = event.currentTarget;
    setGiftSearch('');
    setGiftCategoryFilter('all');
    setGiftStockFilter('all');
    setGiftPicker({ label, prefix, index });
  };
  const closeGiftPicker = () => {
    setGiftPicker(null);
    window.requestAnimationFrame(() => {
      if (giftPickerTriggerRef.current?.isConnected) giftPickerTriggerRef.current.focus();
    });
  };
  const renderGiftSelectors = (label, selections, setSelections, prefix) => selections.map((selectedId, index) => {
    const selectedItem = giftMenu.find((item) => item.id === selectedId);
    return <div className="booking-gift-slot" key={`${prefix}-${index}`}>
      <span>{label} {index + 1}</span>
      <button className="booking-gift-picker-trigger" type="button" disabled={disabled} onClick={(event) => openGiftPicker(event, label, prefix, index)}>
        <span>{selectedItem?.name || `Chọn ${label.toLowerCase()}`}</span>
        <small>{selectedItem ? selectedItem.stockTracked ? `Tồn ${selectedItem.stockQuantity} ${selectedItem.unitName}` : 'Không theo dõi tồn kho' : 'Mở danh sách món'}</small>
      </button>
    </div>;
  });

  const pickerTypeKeyword = giftPicker?.prefix === 'drink' ? 'uống' : 'ăn';
  const pickerItems = giftPicker
    ? giftMenu.filter((item) => (item.category || '').toLocaleLowerCase('vi-VN').includes(pickerTypeKeyword))
    : [];
  const pickerSlots = giftPicker ? [...drinkSelections.map((id, i) => ({ id, key: `drink-${i}` })),
    ...foodSelections.map((id, i) => ({ id, key: `food-${i}` }))] : [];
  const pickerUsedElsewhere = pickerSlots
    .filter((slot) => slot.key !== `${giftPicker?.prefix}-${giftPicker?.index}` && slot.id)
    .reduce((counts, slot) => ({ ...counts, [slot.id]: (counts[slot.id] || 0) + 1 }), {});
  const pickerCategories = [...new Map(pickerItems.map((item) => [
    (item.category || '').trim().toLocaleLowerCase('vi-VN'), item.category,
  ]).filter(([key]) => key)).values()];
  const normalizedGiftSearch = giftSearch.trim().toLocaleLowerCase('vi-VN');
  const filteredPickerItems = pickerItems.filter((item) => {
    const selectable = !item.stockTracked || Number(item.stockQuantity || 0) > (pickerUsedElsewhere[item.id] || 0);
    const matchesSearch = !normalizedGiftSearch || `${item.name} ${item.category}`.toLocaleLowerCase('vi-VN').includes(normalizedGiftSearch);
    const matchesCategory = giftCategoryFilter === 'all' || (item.category || '').toLocaleLowerCase('vi-VN') === giftCategoryFilter;
    const matchesStock = giftStockFilter === 'all'
      || (giftStockFilter === 'available' && selectable)
      || (giftStockFilter === 'untracked' && !item.stockTracked)
      || (giftStockFilter === 'out' && !selectable);
    return matchesSearch && matchesCategory && matchesStock;
  });
  const chooseGift = (item) => {
    const setter = giftPicker?.prefix === 'drink' ? setDrinkSelections : setFoodSelections;
    const index = giftPicker?.index;
    setter((current) => current.map((id, position) => position === index ? item.id : id));
    closeGiftPicker();
  };

  return <>
    {drinkSelections.length > 0 && <div className="booking-checkin-gift-group"><strong>Đồ uống ({drinkSelections.length})</strong>{renderGiftSelectors('Đồ uống', drinkSelections, setDrinkSelections, 'drink')}</div>}
    {foodSelections.length > 0 && <div className="booking-checkin-gift-group"><strong>Đồ ăn ({foodSelections.length})</strong>{renderGiftSelectors('Đồ ăn', foodSelections, setFoodSelections, 'food')}</div>}
    {giftPicker && <div className="booking-gift-picker-backdrop" onClick={(event) => { if (event.target === event.currentTarget) closeGiftPicker(); }}>
      <section className="booking-gift-picker-dialog" role="dialog" aria-modal="true" aria-labelledby="booking-gift-picker-title" onKeyDown={(event) => {
        if (event.key === 'Escape') closeGiftPicker();
        if (event.key === 'Tab') {
          const focusable = event.currentTarget.querySelectorAll('button:not(:disabled),input,select');
          const first = focusable[0], last = focusable[focusable.length - 1];
          if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last?.focus(); }
          else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first?.focus(); }
        }
      }}>
        <header className="booking-gift-picker-heading">
          <div><h2 id="booking-gift-picker-title">Chọn {giftPicker.label.toLowerCase()}</h2></div>
          <button type="button" aria-label="Đóng danh sách món" onClick={closeGiftPicker}>×</button>
        </header>
        <label className="booking-gift-picker-search"><span>Tìm món</span><input autoFocus value={giftSearch} onChange={(event) => setGiftSearch(event.target.value)} placeholder="Nhập tên món…" /></label>
        <div className="booking-gift-picker-filters">
          <label><span>Danh mục</span><select value={giftCategoryFilter} onChange={(event) => setGiftCategoryFilter(event.target.value)}>
            <option value="all">Tất cả danh mục</option>
            {pickerCategories.map((category) => <option key={category} value={(category || '').toLocaleLowerCase('vi-VN')}>{category}</option>)}
          </select></label>
          {giftPicker.prefix === 'food' && <div className="booking-gift-picker-stock-filter" role="group" aria-label="Lọc theo tồn kho">
            {[['all', 'Tất cả'], ['available', 'Có thể chọn'], ['untracked', 'Không theo dõi tồn'], ['out', 'Hết tồn']].map(([value, label]) => <button key={value} type="button" className={giftStockFilter === value ? 'is-active' : ''} aria-pressed={giftStockFilter === value} onClick={() => setGiftStockFilter(value)}>{label}</button>)}
          </div>}
        </div>
        <PaginatedList pageSize={10} resetKey={`${giftSearch}:${giftCategoryFilter}:${giftStockFilter}`} className="booking-gift-picker-list" aria-live="polite">
          {filteredPickerItems.length ? filteredPickerItems.map((item) => {
            const selectable = !item.stockTracked || Number(item.stockQuantity || 0) > (pickerUsedElsewhere[item.id] || 0);
            const selected = (giftPicker.prefix === 'drink' ? drinkSelections : foodSelections)[giftPicker.index] === item.id;
            return <button key={item.id} type="button" className={`booking-gift-picker-item${selected ? ' is-selected' : ''}`} disabled={!selectable || disabled} onClick={() => chooseGift(item)}>
              <span><strong>{item.name}</strong><small>{item.category}</small></span>
              <span className={`booking-gift-picker-stock${item.stockTracked ? '' : ' is-untracked'}`}>{item.stockTracked ? selectable ? `Tồn ${item.stockQuantity} ${item.unitName}` : 'Hết tồn' : 'Không theo dõi tồn'}</span>
            </button>;
          }) : <p className="booking-gift-picker-empty">Không tìm thấy món phù hợp với bộ lọc.</p>}
        </PaginatedList>
        <footer className="booking-gift-picker-footer"><span>{filteredPickerItems.length} món</span><button type="button" onClick={closeGiftPicker}>Đóng</button></footer>
      </section>
    </div>}
  </>;
}
