/**
 * Author: HuyDV
 * Created: 03/11/2026
 * Description: UC-90 / BR-60/61, xem ca, công thực tế và chi tiết cá nhân theo tháng; MSG01/26.
 */
import { useEffect, useState } from 'react';
import { apiErrors } from '../../auth/services/authApi';
import { attendanceApi, dateTimeLabel, hoursLabel } from '../services/attendanceApi';
import { dateLabel, today } from '../services/shiftApi';

export default function PersonalWorkRecordsPage({ user }) {
  const [month, setMonth] = useState(() => today().slice(0, 7));
  const [data, setData] = useState(null);
  const [search, setSearch] = useState('');
  const [status, setStatus] = useState('ALL');
  const [selected, setSelected] = useState(null);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);
  const [revision, setRevision] = useState(0);
  useEffect(() => {
    let active = true;
    setLoading(true);
    setData(null);
    setSelected(null);
    setError('');
    attendanceApi
      .personal(month)
      .then((result) => {
        if (active) setData(result);
      })
      .catch((e) => {
        if (active) setError(Object.values(apiErrors(e)).join(' '));
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, [month, revision, user.id]);
  const label = (row) =>
    !row.attendanceId
      ? 'Chưa ghi nhận'
      : !row.valid
        ? 'Cần kiểm tra'
        : row.status === 'ABSENT'
          ? 'Vắng mặt'
          : 'Đi làm';
  const rows = (data?.rows || []).filter((row) => {
    const matches = `${dateLabel(row.workDate)} ${row.branchName} ${row.note}`
      .toLowerCase()
      .includes(search.trim().toLowerCase());
    return (
      matches &&
      (status === 'ALL' ||
        (status === 'MISSING'
          ? !row.attendanceId
          : status === 'INVALID'
            ? row.attendanceId && !row.valid
            : row.valid && row.status === status))
    );
  });
  const details = data?.rows.find((row) => row.assignmentId === selected);
  return (
    <section className="attendance-page" aria-labelledby="personal-work-title">
      <header className="shift-heading">
        <div>
          <h1 id="personal-work-title">Công cá nhân</h1>
          <p>
            Xem ca được phân công và giờ công thực tế. Liên hệ quản lý cơ sở nếu cần điều chỉnh.
          </p>
        </div>
        <button type="button" disabled={loading} onClick={() => setRevision((value) => value + 1)}>
          Tải lại
        </button>
      </header>
      <div className="shift-toolbar attendance-filters">
        <input
          aria-label="Tìm công cá nhân"
          placeholder="Ngày / cơ sở / ghi chú…"
          value={search}
          maxLength={200}
          onChange={(e) => setSearch(e.target.value)}
        />
        <input
          type="month"
          aria-label="Tháng công cá nhân"
          value={month}
          min="0001-01"
          max="9998-12"
          onChange={(e) => {
            if (e.target.value) setMonth(e.target.value);
          }}
        />
        <select
          aria-label="Lọc tình trạng công cá nhân"
          value={status}
          onChange={(e) => setStatus(e.target.value)}
        >
          <option value="ALL">Tất cả tình trạng</option>
          <option value="WORKED">Đi làm</option>
          <option value="ABSENT">Vắng mặt</option>
          <option value="MISSING">Chưa ghi nhận</option>
          <option value="INVALID">Cần kiểm tra</option>
        </select>
      </div>
      {error && (
        <p className="shift-message error" role="alert">
          {error}
        </p>
      )}
      {loading ? (
        <p role="status">Đang tải công cá nhân…</p>
      ) : (
        data && (
          <>
            <div className="shift-stats">
              <span>
                <strong>{data.assignmentCount}</strong> ca được phân công
              </span>
              <span>
                <strong>{hoursLabel(data.workedMinutes)}</strong> công thực tế hợp lệ
              </span>
              <span>
                <strong>{data.missing}</strong> ca chưa ghi nhận
              </span>
            </div>
            <div className="attendance-table-scroll">
              <table className="attendance-table">
                <caption className="shift-sr-only">Công cá nhân tháng {month}</caption>
                <thead>
                  <tr>
                    <th scope="col">Ngày / cơ sở</th>
                    <th scope="col">Ca dự kiến</th>
                    <th scope="col">Tình trạng</th>
                    <th scope="col">Giờ công</th>
                    <th scope="col">Kỳ công</th>
                    <th scope="col">Thao tác</th>
                  </tr>
                </thead>
                <tbody>
                  {rows.map((row) => (
                    <tr key={row.assignmentId}>
                      <td>
                        {dateLabel(row.workDate)}
                        <small>{row.branchName}</small>
                      </td>
                      <td>
                        {dateTimeLabel(row.startsAt, data.timezone)}
                        <small>Đến {dateTimeLabel(row.endsAt, data.timezone)}</small>
                      </td>
                      <td>{label(row)}</td>
                      <td>{row.valid ? hoursLabel(row.workedMinutes) : '—'}</td>
                      <td>{row.periodStatus === 'CLOSED' ? 'Đã chốt' : 'Chưa chốt'}</td>
                      <td>
                        <button
                          type="button"
                          className="attendance-link"
                          aria-expanded={selected === row.assignmentId}
                          onClick={() =>
                            setSelected(selected === row.assignmentId ? null : row.assignmentId)
                          }
                        >
                          Chi tiết
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            {!rows.length && (
              <p className="attendance-empty" role="status">
                Không tìm thấy kết quả phù hợp. Hãy thử thay đổi từ khóa hoặc bộ lọc.
              </p>
            )}
            {details && (
              <section className="attendance-summary" aria-label="Chi tiết công cá nhân">
                <strong>
                  {dateLabel(details.workDate)} · {details.branchName} · {label(details)}
                </strong>
                <span>Vào thực tế: {dateTimeLabel(details.actualIn, data.timezone)}</span>
                <span>Ra thực tế: {dateTimeLabel(details.actualOut, data.timezone)}</span>
                <span>
                  Giờ công:{' '}
                  {details.valid ? hoursLabel(details.workedMinutes) : 'Chưa có dữ liệu hợp lệ'}
                </span>
                <span>Ngoại lệ: {details.exceptions.join(', ') || 'Không'}</span>
                <span>Ghi chú: {details.note || '—'}</span>
                <button type="button" onClick={() => setSelected(null)}>
                  Đóng chi tiết
                </button>
              </section>
            )}
          </>
        )
      )}
    </section>
  );
}
