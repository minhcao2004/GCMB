import { forwardRef, useEffect, useImperativeHandle, useLayoutEffect, useRef } from 'react';

/** HuyDV: dấu chấm chỉ để hiển thị; giữ số thuần, giới hạn và focus của ô gốc. */
export default forwardRef(function MoneyInput({ value, onValueChange, min, max,
  maxDigits = 15, allowNegative = false, ...props }, ref) {
  const inputRef = useRef(null);
  const caret = useRef(null);
  useImperativeHandle(ref, () => inputRef.current, []);
  useEffect(() => {
    const amount = Number(value);
    inputRef.current?.setCustomValidity(value !== '' && value != null &&
      ((min != null && amount < Number(min)) || (max != null && amount > Number(max)))
      ? (min != null && amount < Number(min)
        ? `Số tiền tối thiểu là ${Number(min).toLocaleString('vi-VN')} đồng.`
        : `Số tiền tối đa là ${Number(max).toLocaleString('vi-VN')} đồng.`) : '');
  }, [value, min, max]);
  useLayoutEffect(() => {
    if (caret.current == null || document.activeElement !== inputRef.current) return;
    let position = 0, remaining = caret.current;
    while (position < inputRef.current.value.length && remaining > 0) {
      if (inputRef.current.value[position] !== '.') remaining--;
      position++;
    }
    inputRef.current.setSelectionRange(position, position);
    caret.current = null;
  }, [value]);
  return (
    <input
      {...props}
      ref={inputRef}
      type="text"
      inputMode={allowNegative ? 'text' : 'numeric'}
      pattern={`${allowNegative ? '-?' : ''}[0-9]+([.][0-9]{3})*`}
      maxLength={maxDigits + Math.floor((maxDigits - 1) / 3) + (allowNegative ? 1 : 0)}
      value={String(value ?? '').replace(/\B(?=(\d{3})+(?!\d))/g, '.')}
      onChange={(event) => {
        const digits = event.target.value.replace(/\./g, '');
        if (new RegExp(`^${allowNegative ? '-?' : ''}\\d{0,${maxDigits}}$`).test(digits)) {
          caret.current = event.target.value.slice(0, event.target.selectionStart).replace(/\./g, '').length;
          onValueChange(digits.replace(/^(-?)0+(?=\d)/, '$1'));
        }
      }}
    />
  );
});
