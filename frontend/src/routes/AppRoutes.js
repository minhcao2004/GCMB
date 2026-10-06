import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom';
import HomePage from '../features/landing/pages/HomePage';

export default function AppRoutes() {
  return <BrowserRouter><Routes><Route path="/" element={<HomePage />} /><Route path="*" element={<Navigate to="/" replace />} /></Routes></BrowserRouter>;
}
