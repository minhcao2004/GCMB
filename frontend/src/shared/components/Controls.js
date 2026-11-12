import { Children, cloneElement, forwardRef, useId } from 'react';

function classes(...values) {
  return values.filter(Boolean).join(' ');
}

export const Button = forwardRef(function Button(
  {
    variant = 'primary',
    size = 'md',
    loading = false,
    disabled = false,
    type = 'button',
    className,
    children,
    ...props
  },
  ref
) {
  const buttonVariant = ['primary', 'secondary', 'ghost', 'danger'].includes(variant)
    ? variant
    : 'primary';
  const buttonSize = size === 'sm' ? 'sm' : 'md';

  return (
    <button
      {...props}
      ref={ref}
      type={type}
      className={classes(
        'gcmb-ui-button',
        `gcmb-ui-button--${buttonVariant}`,
        `gcmb-ui-button--${buttonSize}`,
        className
      )}
      disabled={disabled || loading}
      aria-busy={loading || undefined}
    >
      {loading && <span className="gcmb-ui-button__spinner" aria-hidden="true" />}
      <span className="gcmb-ui-button__content">{children}</span>
    </button>
  );
});

Button.displayName = 'Button';

export const Input = forwardRef(function Input({ className, type = 'text', ...props }, ref) {
  return (
    <input
      {...props}
      ref={ref}
      type={type}
      className={classes('gcmb-ui-control', className)}
    />
  );
});

Input.displayName = 'Input';

export const Select = forwardRef(function Select({ className, ...props }, ref) {
  return <select {...props} ref={ref} className={classes('gcmb-ui-control', className)} />;
});

Select.displayName = 'Select';

export const Textarea = forwardRef(function Textarea({ className, ...props }, ref) {
  return <textarea {...props} ref={ref} className={classes('gcmb-ui-control', className)} />;
});

Textarea.displayName = 'Textarea';

export function FormField({ id, label, hint, error, required = false, className, children }) {
  const generatedId = useId();
  const fieldId = id || `gcmb-ui-field-${generatedId}`;
  const control = Children.only(children);
  const controlIsRequired = required || control.props.required;
  const describedBy = [
    control.props['aria-describedby'],
    hint ? `${fieldId}-hint` : null,
    error ? `${fieldId}-error` : null,
  ]
    .filter(Boolean)
    .join(' ');

  return (
    <div className={classes('gcmb-ui-field', className)}>
      {label && (
        <label className="gcmb-ui-field__label" htmlFor={fieldId}>
          {label}
          {controlIsRequired && <span className="gcmb-ui-field__required" aria-hidden="true"> *</span>}
        </label>
      )}
      {cloneElement(control, {
        id: fieldId,
        required: controlIsRequired || undefined,
        'aria-describedby': describedBy || undefined,
        'aria-invalid': error ? true : control.props['aria-invalid'],
        'aria-required': controlIsRequired || undefined,
      })}
      {hint && <span className="gcmb-ui-field__hint" id={`${fieldId}-hint`}>{hint}</span>}
      {error && <span className="gcmb-ui-field__error" id={`${fieldId}-error`}>{error}</span>}
    </div>
  );
}
