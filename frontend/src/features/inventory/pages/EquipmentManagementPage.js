/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: TrongNV
 * Created: 28/10
 * Description: Điều hướng giữa danh mục nhóm và danh sách tài sản thiết bị UC60–UC69.
 */
import { useEffect, useState } from 'react';
import EquipmentAssetsPage from './EquipmentAssetsPage';
import EquipmentCategoriesPage from './EquipmentCategoriesPage';
import EquipmentIncidentsPage from './EquipmentIncidentsPage';
import EquipmentTransfersPage from './EquipmentTransfersPage';
import './equipment-management.css';

export default function EquipmentManagementPage({ user, headOffice = false, initialSection = '', initialIncidentId = '', initialBranchId = '' }) {
  const [section, setSection] = useState(() => ['assets', 'categories', 'incidents', 'transfers'].includes(initialSection) ? initialSection : 'assets');
  const selectSection = setSection;
  useEffect(() => {
    if (['assets', 'categories', 'incidents', 'transfers'].includes(initialSection)) setSection(initialSection);
  }, [initialSection]);
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
      : section === 'incidents' ? <EquipmentIncidentsPage user={user} headOffice={headOffice} initialIncidentId={initialIncidentId} initialBranchId={initialBranchId} />
      : <EquipmentTransfersPage user={user} headOffice={headOffice} />}
  </div>;
}
