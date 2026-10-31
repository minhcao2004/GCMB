/**
 * Author: HuyDV
 * Created: 27/10/2026
 * Description: Điều hướng UC-83..89 trong mục Ca làm & chấm công của quản lý cơ sở.
 */
import { useState } from 'react';
import ShiftSchedulePage from './ShiftSchedulePage';
import AttendancePage from './AttendancePage';
import '../styles/attendance.css';

export default function WorkforcePage(props) {
  const [tab, setTab] = useState('schedule');
  const [attendanceDate, setAttendanceDate] = useState(null);

  return (
    <div className="workforce-page">
      <nav className="workforce-tabs" aria-label="Ca làm và chấm công">
        {[
          ['schedule', 'Lịch ca tuần'],
          ['daily', 'Chấm công ngày / ca'],
          ['month', 'Bảng công tháng'],
        ].map(([key, label]) => (
          <button
            key={key}
            type="button"
            aria-current={tab === key ? 'page' : undefined}
            onClick={() => setTab(key)}
          >
            {label}
          </button>
        ))}
      </nav>
      {tab === 'schedule' ? (
        <ShiftSchedulePage {...props} />
      ) : (
        <AttendancePage
          {...props}
          key={tab}
          view={tab}
          initialDate={attendanceDate}
          onDaily={(date) => {
            setAttendanceDate(date);
            setTab('daily');
          }}
        />
      )}
    </div>
  );
}
