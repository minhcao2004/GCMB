import { useId } from 'react';

export default function BranchFilterSelect({ branches = [], value, multiple = false, onChange }) {
  const radioName = useId();
  const selectedIds = multiple
    ? (Array.isArray(value) ? value.map(String) : [])
    : value ? [String(value)] : [];
  const selectedNames = selectedIds
    .map((id) => branches.find((branch) => String(branch.id) === id)?.name)
    .filter(Boolean);
  const summary = !selectedIds.length
    ? 'Tất cả cơ sở'
    : multiple
      ? selectedIds.length === 1 ? selectedNames[0] || '1 cơ sở' : `${selectedIds.length} cơ sở`
      : selectedNames[0] || 'Cơ sở đã chọn';

  function changeMultiple(id, checked) {
    const next = new Set(selectedIds);
    if (checked) next.add(id);
    else next.delete(id);
    onChange([...next]);
  }

  function changeSingle(event) {
    onChange(event.currentTarget.value);
    const dropdown = event.currentTarget.closest('details');
    if (dropdown) dropdown.open = false;
  }

  return (
    <details className="finance-branch-filter">
      <summary aria-label={`Cơ sở: ${summary}`} title={summary}><span>{summary}</span></summary>
      <div className="finance-branch-filter__menu" role={multiple ? 'group' : 'radiogroup'} aria-label="Chọn cơ sở">
        {multiple ? <>
          <label className="finance-branch-filter__option">
            <input type="checkbox" checked={!selectedIds.length} onChange={() => onChange([])} />
            <span>Tất cả cơ sở</span>
          </label>
          {branches.map((branch) => {
            const id = String(branch.id);
            return <label className="finance-branch-filter__option" key={id}>
              <input type="checkbox" checked={selectedIds.includes(id)} onChange={(event) => changeMultiple(id, event.currentTarget.checked)} />
              <span>{branch.name}</span>
            </label>;
          })}
        </> : <>
          <label className="finance-branch-filter__option">
            <input type="radio" name={radioName} value="" checked={!selectedIds.length} onChange={changeSingle} />
            <span>Tất cả cơ sở</span>
          </label>
          {branches.map((branch) => {
            const id = String(branch.id);
            return <label className="finance-branch-filter__option" key={id}>
              <input type="radio" name={radioName} value={id} checked={selectedIds[0] === id} onChange={changeSingle} />
              <span>{branch.name}</span>
            </label>;
          })}
        </>}
      </div>
    </details>
  );
}
