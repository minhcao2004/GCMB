/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: HuyDV
 * Created: 10/10/2026
 * Description: Hiển thị biểu trưng thương hiệu dùng chung gồm hình logo và chữ GenZ.
 */

import logo from '../../assets/images/logo.png';
import './brand.css';

// Hiển thị tên thương hiệu dùng chung.
export default function BrandWordmark() {
  return <div className="brand-wordmark"><img src={logo} alt="" /><span className="brand-gradient">GenZ</span></div>;
}
