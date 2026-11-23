import { forwardRef, useLayoutEffect, useRef, useState } from 'react';

const digitsOf = (value, signed) => `${signed && String(value ?? '').startsWith('-') ? '-' : ''}${String(value ?? '').replace(/\D/g, '')}`;
const format = (value, signed) => digitsOf(value, signed).replace(/\B(?=(\d{3})+(?!\d))/g, '.');

const MoneyInput = forwardRef(function MoneyInput({ value, onValueChange, signed = false, min, maxDigits = 18, onBlur, ...props }, forwardedRef) {
  const inputRef = useRef(null);
  const composing = useRef(false);
  const [preview, setPreview] = useState('');

  // Giữ giá trị DOM trong lúc bộ gõ Telex composition để React không chèn lặp chữ số.
  useLayoutEffect(() => {
    const input = inputRef.current;
    if (input && !composing.current && digitsOf(input.value, signed) !== digitsOf(value, signed)) {
      input.value = format(value, signed);
    }
  }, [value, signed]);

  const commit = (input) => {
    const raw = digitsOf(input.value, signed);
    const next = `${raw.startsWith('-') ? '-' : ''}${raw.replace(/\D/g, '').slice(0, maxDigits)}`;
    const before = input.value.slice(0, input.selectionStart ?? input.value.length);
    const position = before.replace(/\D/g, '').length;
    input.value = format(next, signed);
    let cursor = 0;
    let seen = 0;
    while (cursor < input.value.length && seen < position) {
      if (/\d/.test(input.value[cursor])) seen += 1;
      cursor += 1;
    }
    if (next.startsWith('-') && cursor === 0) cursor = 1;
    input.setSelectionRange(cursor, cursor);
    onValueChange(next);
  };

  return <><input {...props} ref={(node) => {
    inputRef.current = node;
    if (typeof forwardedRef === 'function') forwardedRef(node);
    else if (forwardedRef) forwardedRef.current = node;
  }} type="text" inputMode={signed ? 'text' : 'numeric'}
    pattern={signed ? '-?[0-9]{1,3}([.][0-9]{3})*' : Number(min) > 0
      ? '(?!0+(?:[.]0{3})*$)[0-9]{1,3}([.][0-9]{3})*' : '[0-9]{1,3}([.][0-9]{3})*'}
    defaultValue={format(value, signed)}
    onInput={(event) => {
      if (composing.current || event.nativeEvent.isComposing) setPreview(format(event.currentTarget.value, signed));
      else commit(event.currentTarget);
    }}
    onCompositionStart={() => { composing.current = true; }}
    onCompositionEnd={(event) => { composing.current = false; commit(event.currentTarget); setPreview(''); }}
    onBlur={(event) => { if (!composing.current) commit(event.currentTarget); setPreview(''); onBlur?.(event); }} />
    {preview && <small className="gcmb-money-input-preview">Đang nhập: {preview} đ</small>}
  </>;
});

export default MoneyInput;
