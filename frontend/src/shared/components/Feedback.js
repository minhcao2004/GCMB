function variantClass(variant) {
  return ['primary', 'info', 'success', 'warning', 'danger'].includes(variant)
    ? variant
    : 'neutral';
}

export function Badge({ variant = 'neutral', className, children, ...props }) {
  return (
    <span
      {...props}
      className={[
        'gcmb-ui-badge',
        `gcmb-ui-badge--${variantClass(variant)}`,
        className,
      ].filter(Boolean).join(' ')}
    >
      {children}
    </span>
  );
}

export function StatusPill(props) {
  return <Badge {...props} />;
}

export function Toast({
  open = false,
  variant = 'info',
  title,
  message,
  children,
  onClose,
  closeLabel = 'Đóng thông báo',
  className,
}) {
  if (!open) return null;

  const toastVariant = variantClass(variant);

  return (
    <div
      className={[
        'gcmb-ui-toast',
        `gcmb-ui-toast--${toastVariant}`,
        className,
      ].filter(Boolean).join(' ')}
      role={toastVariant === 'danger' ? 'alert' : 'status'}
      aria-live={toastVariant === 'danger' ? 'assertive' : 'polite'}
      aria-atomic="true"
    >
      <div className="gcmb-ui-toast__content">
        {title && <strong className="gcmb-ui-toast__title">{title}</strong>}
        {(children || message) && <div className="gcmb-ui-toast__message">{children || message}</div>}
      </div>
      {onClose && (
        <button type="button" className="gcmb-ui-toast__close" aria-label={closeLabel} onClick={onClose}>
          <span className="gcmb-ui-toast__close-icon" aria-hidden="true" />
        </button>
      )}
    </div>
  );
}

export function EmptyState({ title, description, action, className }) {
  return (
    <div className={['gcmb-ui-empty-state', className].filter(Boolean).join(' ')} role="status">
      {title && <h2 className="gcmb-ui-empty-state__title">{title}</h2>}
      {description && <p className="gcmb-ui-empty-state__description">{description}</p>}
      {action && <div className="gcmb-ui-empty-state__action">{action}</div>}
    </div>
  );
}
