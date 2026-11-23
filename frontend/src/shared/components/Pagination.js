import { Children, cloneElement, isValidElement, useState } from 'react';
import '../styles/pagination.css';

function contentKey(value) {
  if (!isValidElement(value)) return String(value ?? '');
  if (value.key != null && String(value.key).includes('$') && !/\$\d+$/.test(value.key)) return String(value.key);
  return `${value.key ?? ''}:${Children.toArray(value.props.children).map(contentKey).join('|')}`;
}

export function usePagination(items, pageSize = 10, resetKey = '') {
  const signature = `${pageSize}:${resetKey}:${items.map((item) =>
    isValidElement(item) ? contentKey(item) : item?.id ?? item?.code ?? JSON.stringify(item),
  ).join('|')}`;
  const [position, setPosition] = useState({ signature, page: 0 });
  const pageCount = Math.max(1, Math.ceil(items.length / pageSize));
  const page = position.signature === signature ? Math.min(position.page, pageCount - 1) : 0;
  const setPage = (value) => setPosition({
    signature,
    page: Math.max(0, Math.min(value, pageCount - 1)),
  });
  return { items: items.slice(page * pageSize, (page + 1) * pageSize), page, pageSize,
    total: items.length, setPage, pageCount };
}

export function Pagination({ page, pageSize = 10, total, onPageChange, disabled = false,
  label = 'Phân trang danh sách' }) {
  if (total <= pageSize) return null;
  const pageCount = Math.max(1, Math.ceil(total / pageSize));
  const current = Math.max(0, Math.min(page, pageCount - 1));
  return <nav className="gcmb-pagination" aria-label={label}>
    <span className="gcmb-pagination-range" aria-live="polite">
      {current * pageSize + 1}–{Math.min((current + 1) * pageSize, total)} / {total} mục
    </span>
    <div className="gcmb-pagination-controls">
      <button type="button" disabled={disabled || current === 0}
        onClick={() => onPageChange(current - 1)} aria-label={`${label}: trang trước`}>Trang trước</button>
      <span className="gcmb-pagination-position">Trang {current + 1} / {pageCount}</span>
      <button type="button" disabled={disabled || current + 1 >= pageCount}
        onClick={() => onPageChange(current + 1)} aria-label={`${label}: trang sau`}>Trang sau</button>
    </div>
  </nav>;
}

export function PaginatedTable({ children, pageSize = 10, resetKey = '', paginationLabel,
  ...props }) {
  const sections = Children.toArray(children);
  const body = sections.find((section) => isValidElement(section) && section.type === 'tbody');
  const rows = Children.toArray(body?.props.children);
  const pagination = usePagination(rows, pageSize, resetKey);
  return <>
    <table {...props}>{sections.map((section) => section === body
      ? cloneElement(section, {}, pagination.items) : section)}</table>
    <Pagination {...pagination} onPageChange={pagination.setPage} label={paginationLabel} />
  </>;
}

export function PaginatedList({ as: Tag = 'div', children, pageSize = 10, resetKey = '',
  paginationLabel, ...props }) {
  const pagination = usePagination(Children.toArray(children), pageSize, resetKey);
  const pager = <Pagination {...pagination} onPageChange={pagination.setPage} label={paginationLabel} />;
  if (!['ul', 'ol'].includes(Tag) && props.role !== 'rowgroup') {
    return <Tag {...props}>{pagination.items}{pager}</Tag>;
  }
  return <>
    <Tag {...props}>{pagination.items}</Tag>
    {pager}
  </>;
}
