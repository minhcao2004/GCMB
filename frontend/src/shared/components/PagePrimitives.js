export function PageHeader({ title, description, actions, className }) {
  return (
    <header className={['gcmb-ui-page-header', className].filter(Boolean).join(' ')}>
      <div className="gcmb-ui-page-header__text">
        <h1 className="gcmb-ui-page-header__title">{title}</h1>
        {description && <p className="gcmb-ui-page-header__description">{description}</p>}
      </div>
      {actions && <div className="gcmb-ui-page-header__actions">{actions}</div>}
    </header>
  );
}

export function FilterBar({ label = 'Bộ lọc', children, className }) {
  return (
    <section
      className={['gcmb-ui-filter-bar', className].filter(Boolean).join(' ')}
      aria-label={label}
    >
      {children}
    </section>
  );
}

export function KpiCard({ label, value, detail, className }) {
  return (
    <article className={['gcmb-ui-kpi-card', className].filter(Boolean).join(' ')}>
      <h2 className="gcmb-ui-kpi-card__label">{label}</h2>
      <p className="gcmb-ui-kpi-card__value">{value}</p>
      {detail && <p className="gcmb-ui-kpi-card__detail">{detail}</p>}
    </article>
  );
}
