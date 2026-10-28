/**
 * Copyright (c) 2026 SEP490-G12.
 * Author: HuyDV
 * Created: 27/10/2026
 * Description: Thông báo yêu cầu hóa đơn chưa đọc cho kế toán.
 */
import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { taxInvoiceApi } from '../services/taxInvoiceApi';
export default function TaxInvoiceNotice() {
  const navigate = useNavigate();
  const [count, setCount] = useState(null);
  useEffect(() => {
    let active = true;
    const refresh = () =>
      taxInvoiceApi
        .unread()
        .then((data) => {
          if (active) setCount(data.count);
        })
        .catch(() => {
          if (active) setCount(null);
        });
    refresh();
    const timer = setInterval(refresh, 30000);
    window.addEventListener('tax-invoice-read', refresh);
    return () => {
      active = false;
      clearInterval(timer);
      window.removeEventListener('tax-invoice-read', refresh);
    };
  }, []);
  return (
    <button
      type="button"
      onClick={() => navigate('/ke-toan/hoa-don-thue')}
      aria-label={`Yêu cầu hóa đơn thuế${count === null ? '' : `: ${count} chưa đọc`}`}
    >
      Hóa đơn thuế {count === null ? '(chưa tải được)' : `(${count} mới)`}
    </button>
  );
}
