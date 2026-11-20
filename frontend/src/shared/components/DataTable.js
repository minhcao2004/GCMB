function rowKeyFor(row, index, rowKey) {
  if (typeof rowKey === 'function') return rowKey(row, index);
  return row?.[rowKey] ?? index;
}

export function DataTable({
  columns = [],
  rows = [],
  rowKey = 'id',
  loading = false,
  loadingMessage = 'Đang tải dữ liệu…',
  emptyMessage = 'Không có dữ liệu.',
  ariaLabel = 'Bảng dữ liệu',
  className,
}) {
  const columnCount = Math.max(columns.length, 1);

  return (
    <div
      className={['gcmb-ui-data-table', className].filter(Boolean).join(' ')}
      role="region"
      aria-label={ariaLabel}
      aria-busy={loading}
      tabIndex={0}
    >
      <table className="gcmb-ui-data-table__table" aria-label={ariaLabel}>
        <thead>
          <tr>
            {columns.map((column) => (
              <th
                key={column.key}
                scope="col"
                data-align={column.numeric || column.align === 'right' || column.align === 'numeric' ? 'right' : undefined}
              >
                {column.label}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {loading ? (
            <tr>
              <td colSpan={columnCount}>
                <span className="gcmb-ui-data-table__state" role="status">{loadingMessage}</span>
              </td>
            </tr>
          ) : rows.length === 0 ? (
            <tr>
              <td colSpan={columnCount}>
                <span className="gcmb-ui-data-table__state" role="status">{emptyMessage}</span>
              </td>
            </tr>
          ) : (
            rows.map((row, rowIndex) => (
              <tr key={rowKeyFor(row, rowIndex, rowKey)}>
                {columns.map((column) => (
                  <td
                    key={column.key}
                    data-align={column.numeric || column.align === 'right' || column.align === 'numeric' ? 'right' : undefined}
                  >
                    {column.render ? column.render(row[column.key], row, rowIndex) : row[column.key]}
                  </td>
                ))}
              </tr>
            ))
          )}
        </tbody>
      </table>
    </div>
  );
}
