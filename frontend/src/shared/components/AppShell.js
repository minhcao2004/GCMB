import { useEffect, useState } from 'react';

function readCollapsed(audience) {
  try {
    return window.localStorage.getItem(`gcmb:${audience}:sidebar-collapsed`) === 'true';
  } catch {
    return false;
  }
}

export default function AppShell({
  audience,
  className = '',
  sidebarLabel,
  brand,
  roleLabel,
  navigation,
  footer,
  renderTopbar,
  mainClassName = '',
  mainRef,
  mainKey,
  children,
}) {
  const [collapsed, setCollapsed] = useState(() => readCollapsed(audience));

  useEffect(() => {
    try {
      window.localStorage.setItem(`gcmb:${audience}:sidebar-collapsed`, String(collapsed));
    } catch {
      // Keep the preference for this session when browser storage is unavailable.
    }
  }, [audience, collapsed]);

  return (
    <div className={`gcmb-ui-app-shell${collapsed ? ' gcmb-ui-app-shell--collapsed' : ''} ${className}`.trim()}>
      <aside id="app-sidebar" className="gcmb-ui-app-shell__sidebar" aria-label={sidebarLabel}>
        {brand && <div className="gcmb-ui-app-shell__brand">{brand}</div>}
        {roleLabel && <p className="gcmb-ui-app-shell__role">{roleLabel}</p>}
        {navigation}
        {footer && <div className="gcmb-ui-app-shell__footer">{footer}</div>}
      </aside>
      <div className="gcmb-ui-app-shell__workspace">
        {renderTopbar?.({
          onToggleSidebar: () => setCollapsed((value) => !value),
          sidebarExpanded: !collapsed,
        })}
        <main
          id="main"
          ref={mainRef}
          key={mainKey}
          className={`gcmb-ui-app-shell__main ${mainClassName}`.trim()}
        >
          {children}
        </main>
      </div>
    </div>
  );
}
