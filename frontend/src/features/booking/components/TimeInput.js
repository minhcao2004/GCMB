// Dùng ô văn bản để nhập giờ 24 tiếng thay cho bộ chọn giờ của trình duyệt.
export default function TimeInput(props) {
  const className = ['booking-time-input', props.className].filter(Boolean).join(' ');
  return (
    <input
      {...props}
      className={className}
      type="text"
      inputMode="text"
      maxLength={5}
      placeholder={props.placeholder || 'HH:mm'}
      autoComplete="off"
      spellCheck={false}
    />
  );
}
