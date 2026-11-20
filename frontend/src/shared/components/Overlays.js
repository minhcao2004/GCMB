import { useEffect, useId, useRef } from 'react';

function DialogSurface({
  kind,
  open = false,
  onClose,
  title,
  ariaLabel,
  closeLabel = 'Đóng',
  children,
  footer,
  className,
}) {
  const dialogRef = useRef(null);
  const titleId = useId();

  useEffect(() => {
    const dialog = dialogRef.current;
    if (!dialog) return;

    if (open && !dialog.open) dialog.showModal();
    if (!open && dialog.open) dialog.close();
  }, [open]);

  const handleCancel = (event) => {
    event.preventDefault();
    onClose?.();
  };

  return (
    <dialog
      ref={dialogRef}
      className={[
        'gcmb-ui-dialog',
        `gcmb-ui-dialog--${kind}`,
        className,
      ].filter(Boolean).join(' ')}
      aria-labelledby={title ? titleId : undefined}
      aria-label={title ? undefined : ariaLabel || 'Hộp thoại'}
      aria-modal="true"
      onCancel={handleCancel}
    >
      <div className="gcmb-ui-dialog__panel">
        <header className="gcmb-ui-dialog__header">
          {title && <h2 className="gcmb-ui-dialog__title" id={titleId}>{title}</h2>}
          <button
            type="button"
            className="gcmb-ui-dialog__close"
            aria-label={closeLabel}
            onClick={onClose}
          >
            <span className="gcmb-ui-dialog__close-icon" aria-hidden="true" />
          </button>
        </header>
        <div className="gcmb-ui-dialog__body">{children}</div>
        {footer && <footer className="gcmb-ui-dialog__footer">{footer}</footer>}
      </div>
    </dialog>
  );
}

export function Modal(props) {
  return <DialogSurface {...props} kind="modal" />;
}

export function Drawer(props) {
  return <DialogSurface {...props} kind="drawer" />;
}
