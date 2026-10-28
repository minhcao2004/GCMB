/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: TrongNV
 * Created: 28/10
 * Description: Điều hướng giữa danh mục nhóm và danh sách tài sản thiết bị UC60–UC69.
 */
import { useState } from 'react';
import EquipmentAssetsPage from './EquipmentAssetsPage';
import EquipmentCategoriesPage from './EquipmentCategoriesPage';
import EquipmentIncidentsPage from './EquipmentIncidentsPage';
import EquipmentTransfersPage from './EquipmentTransfersPage';
import './equipment-management.css';

export default function EquipmentManagementPage({ user, headOffice = false }) {
  const [section, setSection] = useState('assets');
  // UC-61/UC-66: Cho phép người dùng chuyển giữa quản lý nhóm và danh sách thiết bị trong cùng phân hệ.
  return <div className="equipment-management">
    <nav className="em-tabs" aria-label="Quản lý thiết bị">
      <button className={section === 'assets' ? 'active' : ''} onClick={() => setSection('assets')}>Danh sách thiết bị</button>
      <button className={section === 'categories' ? 'active' : ''} onClick={() => setSection('categories')}>Nhóm thiết bị</button>
      <button className={section === 'incidents' ? 'active' : ''} onClick={() => setSection('incidents')}>Sự cố thiết bị</button>
      <button className={section === 'transfers' ? 'active' : ''} onClick={() => setSection('transfers')}>Điều chuyển</button>
    </nav>
    {section === 'assets' ? <EquipmentAssetsPage user={user} headOffice={headOffice} />
      : section === 'categories' ? <EquipmentCategoriesPage user={user} headOffice={headOffice} />
      : section === 'incidents' ? <EquipmentIncidentsPage user={user} headOffice={headOffice} />
      : <EquipmentTransfersPage user={user} headOffice={headOffice} />}
  </div>;
}
