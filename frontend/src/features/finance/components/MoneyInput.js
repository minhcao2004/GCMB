/** HuyDV: hiển thị dấu chấm hàng nghìn, giữ giá trị tiền nguyên để gửi API. */
export default function MoneyInput({ value, onValueChange, ...props }) {
  return (
    <input
      {...props}
      type="text"
      inputMode="numeric"
      pattern="[0-9]+(\.[0-9]{3})*"
      maxLength={19}
      value={String(value ?? '').replace(/\B(?=(\d{3})+(?!\d))/g, '.')}
      onChange={(event) => {
        const digits = event.target.value.replace(/\./g, '');
        if (/^\d{0,15}$/.test(digits)) {
          onValueChange(digits.replace(/^0+(?=\d)/, ''));
        }
      }}
    />
  );
}
