/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: TrongNV
 * Created: 28/10
 * Description: Điều hướng giữa danh mục nhóm và danh sách tài sản thiết bị UC60–UC69.
 */
import { useEffect, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import EquipmentAssetsPage from './EquipmentAssetsPage';
import EquipmentCategoriesPage from './EquipmentCategoriesPage';
import EquipmentIncidentsPage from './EquipmentIncidentsPage';
import EquipmentTransfersPage from './EquipmentTransfersPage';
import './equipment-management.css';

export default function EquipmentManagementPage({ user, headOffice = false }) {
  const [searchParams, setSearchParams] = useSearchParams();
  const [headOfficeSection, setHeadOfficeSection] = useState('assets');
  const requestedTab = searchParams.get('equipmentTab');
  const section = headOffice ? headOfficeSection : ['incidents', 'transfers'].includes(requestedTab) ? requestedTab : 'assets';
  useEffect(() => {
    if (headOffice || !requestedTab || ['assets', 'incidents', 'transfers'].includes(requestedTab)) return;
    setSearchParams((current) => { const next = new URLSearchParams(current); next.delete('equipmentTab'); return next; }, { replace: true });
  }, [headOffice, requestedTab, setSearchParams]);
  const selectSection = (nextSection) => {
    if (headOffice) { setHeadOfficeSection(nextSection); return; }
    setSearchParams((current) => {
      const next = new URLSearchParams(current);
      if (nextSection === 'assets') next.delete('equipmentTab');
      else next.set('equipmentTab', nextSection);
      return next;
    });
  };
  // UC-61/UC-66: Cho phép người dùng chuyển giữa quản lý nhóm và danh sách thiết bị trong cùng phân hệ.
  return <div className="equipment-management">
    <nav className="em-tabs" aria-label="Quản lý thiết bị">
      <button className={section === 'assets' ? 'active' : ''} aria-current={section === 'assets' ? 'page' : undefined} onClick={() => selectSection('assets')}>Danh sách thiết bị</button>
      {headOffice && <button className={section === 'categories' ? 'active' : ''} aria-current={section === 'categories' ? 'page' : undefined} onClick={() => selectSection('categories')}>Nhóm thiết bị</button>}
      <button className={section === 'incidents' ? 'active' : ''} aria-current={section === 'incidents' ? 'page' : undefined} onClick={() => selectSection('incidents')}>Sự cố thiết bị</button>
      <button className={section === 'transfers' ? 'active' : ''} aria-current={section === 'transfers' ? 'page' : undefined} onClick={() => selectSection('transfers')}>Điều chuyển</button>
    </nav>
    {section === 'assets' ? <EquipmentAssetsPage user={user} headOffice={headOffice} />
      : section === 'categories' ? <EquipmentCategoriesPage user={user} headOffice={headOffice} />
      : section === 'incidents' ? <EquipmentIncidentsPage user={user} headOffice={headOffice} />
      : <EquipmentTransfersPage user={user} headOffice={headOffice} />}
  </div>;
}
