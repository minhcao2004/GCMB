import { useEffect, useId, useState } from 'react';

export default function SidebarNavigationGroup({ label, isActive, children }) {
  const [expanded, setExpanded] = useState(isActive);
  const contentId = useId();

  useEffect(() => {
    if (isActive) setExpanded(true);
  }, [isActive]);

  return <div className="manager-nav-group">
    <button
      type="button"
      className="manager-nav-group__toggle"
      aria-controls={contentId}
      aria-expanded={expanded}
      onClick={() => setExpanded((value) => !value)}
    >
      {label}
    </button>
    <div id={contentId} className="manager-nav-group__content" hidden={!expanded}>
      {children}
    </div>
  </div>;
}
