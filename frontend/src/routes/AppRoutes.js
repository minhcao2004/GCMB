import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom';
import { useState } from 'react';
import AuthPage from '../features/auth/pages/AuthPage';
import RoleHomePage from '../features/auth/pages/RoleHomePage';
import BookingManagementPage from '../features/booking/pages/BookingManagementPage';
import RoomOverviewPage from '../features/booking/pages/RoomOverviewPage';
import DepositManagementPage from '../features/booking/pages/DepositManagementPage';

// Khai báo các đường dẫn công khai và màn hình theo vai trò.
export default function AppRoutes() {
  const [recovery, setRecovery] = useState(null);
  return <BrowserRouter><Routes>
    {/*<Route path="/" element={<HomePage />} />*/}
    <Route path="/" element={<Navigate to="/dang-nhap" replace />} />
    <Route path="/dang-nhap" element={<AuthPage key="login" mode="login" recovery={recovery} onRecovery={setRecovery} />} />
    <Route path="/quen-mat-khau" element={<AuthPage key="forgot" mode="forgot" recovery={recovery} onRecovery={setRecovery} />} />
    <Route path="/dat-lai-mat-khau" element={<AuthPage key="reset" mode="reset" recovery={recovery} onRecovery={setRecovery} />} />
    <Route path="/quan-ly-he-thong" element={<RoleHomePage role="HEAD_OFFICE_MANAGER" />} />
    <Route path="/quan-ly-chi-nhanh" element={<RoleHomePage role="BRANCH_MANAGER" />} />
    <Route path="/le-tan/bookings/new" element={<BookingManagementPage />} />
    <Route path="/le-tan/bookings/:bookingId/edit" element={<BookingManagementPage />} />
    <Route path="/le-tan/bookings/:bookingId/check-in" element={<BookingManagementPage />} />
    <Route path="/le-tan/phong" element={<RoomOverviewPage />} />
    <Route path="/le-tan/deposits" element={<DepositManagementPage />} />
    <Route path="/le-tan" element={<BookingManagementPage />} />
    <Route path="/le-tan/bookings" element={<BookingManagementPage />} />
    <Route path="/ke-toan" element={<RoleHomePage role="ACCOUNTANT" />} />
    <Route path="/login" element={<Navigate to="/dang-nhap" replace />} />
    <Route path="*" element={<Navigate to="/" replace />} />
  </Routes></BrowserRouter>;
}
