/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: HuyDV
 * Created: 10/10/2026
 * Description: Hiển thị biểu trưng thương hiệu dùng chung gồm hình logo và chữ GenZ.
 */

import { Link } from 'react-router-dom';
import logo from '../../assets/images/logo.png';
import './brand.css';

export default function BrandWordmark({ to, destinationLabel = 'Tổng quan' }) {
  const wordmark = <>
      <img src={logo} alt="" />
      <span className="brand-gradient">GenZ</span>
  </>;

  return to
    ? <Link className="brand-wordmark" to={to} aria-label={`GenZ · Về ${destinationLabel}`}>{wordmark}</Link>
    : <div className="brand-wordmark">{wordmark}</div>;
}
