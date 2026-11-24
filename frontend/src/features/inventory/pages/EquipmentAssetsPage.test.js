import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import '@testing-library/jest-dom';
import apiClient from '../../../api/client';
import { equipmentAssetApi } from '../services/equipmentAssetApi';
import EquipmentAssetsPage from './EquipmentAssetsPage';

jest.mock('../../../api/client', () => ({ get: jest.fn() }));
jest.mock('../services/equipmentAssetApi', () => ({
  equipmentAssetApi: { list: jest.fn(), options: jest.fn(), create: jest.fn() },
}));

test.each([false, true])('shows the final equipment code after saving (head office: %s)', async (headOffice) => {
  const branch = { id: 'hoa-bang', name: 'Hoa Bằng' };
  const previewCode = 'ASSET-HN_HOA_BANG-0004';
  const finalCode = 'ASSET-HN_HOA_BANG-0005';
  apiClient.get.mockResolvedValue({ data: [{ id: 'another-branch', name: 'Cơ sở khác' }, branch] });
  equipmentAssetApi.list.mockResolvedValue([]);
  equipmentAssetApi.options.mockImplementation(async (_, branchId) => ({
    categories: [{ id: 'audio', name: 'Âm thanh', active: true }],
    rooms: [],
    codePrefix: branchId === branch.id ? 'ASSET-HN_HOA_BANG-' : 'ASSET-OTHER-',
    nextCode: branchId === branch.id ? previewCode : 'ASSET-OTHER-0001',
  }));
  equipmentAssetApi.create.mockResolvedValue({ id: 'speaker', code: finalCode });

  render(<EquipmentAssetsPage headOffice={headOffice} user={headOffice ? {} : { branchId: branch.id }} />);
  if (headOffice) {
    await screen.findByRole('option', { name: branch.name });
    fireEvent.change(screen.getByLabelText('Cơ sở'), { target: { value: branch.id } });
  }
  fireEvent.click(screen.getByRole('button', { name: /Thêm thiết bị/ }));

  const drawer = within(screen.getByRole('complementary', { name: 'Hồ sơ thiết bị' }));
  const code = drawer.getByLabelText('Mã thiết bị');
  await waitFor(() => expect(code).toHaveValue(previewCode));
  expect(code).toHaveAttribute('readonly');
  expect(code).toHaveAccessibleDescription(/Mã dự kiến/);

  fireEvent.change(drawer.getByLabelText(/Tên thiết bị/), { target: { value: 'Loa' } });
  fireEvent.change(drawer.getByLabelText(/Nhóm/), { target: { value: 'audio' } });
  fireEvent.change(drawer.getByLabelText(/Khu vực \/ vị trí khác/), { target: { value: 'Kho' } });
  fireEvent.click(drawer.getByRole('button', { name: 'Thêm thiết bị' }));

  expect(await screen.findByRole('status')).toHaveTextContent(finalCode);
  const lastCreateCall = equipmentAssetApi.create.mock.calls.at(-1);
  expect(lastCreateCall).toEqual([headOffice, branch.id, expect.objectContaining({ name: 'Loa', categoryId: 'audio' })]);
  expect(lastCreateCall[2]).not.toHaveProperty('code');
});

test('asks for the first code, then displays the next code as read-only', async () => {
  const prefix = 'ASSET-HN_HOA_BANG-';
  const first = `${prefix}0025`;
  let next = null;
  equipmentAssetApi.list.mockResolvedValue([]);
  equipmentAssetApi.options.mockImplementation(async () => ({
    categories: [{ id: 'audio', name: 'Âm thanh', active: true }], rooms: [], codePrefix: prefix, nextCode: next,
  }));
  equipmentAssetApi.create.mockImplementation(async () => {
    next = `${prefix}0026`;
    return { id: 'speaker', code: first };
  });

  render(<EquipmentAssetsPage user={{ branchId: 'hoa-bang' }} />);
  fireEvent.click(screen.getByRole('button', { name: /Thêm thiết bị/ }));
  const drawer = within(screen.getByRole('complementary', { name: 'Hồ sơ thiết bị' }));
  const code = await drawer.findByLabelText('Mã thiết bị *');
  expect(code).toBeRequired();
  expect(code).not.toHaveAttribute('readonly');
  fireEvent.change(code, { target: { value: first } });
  fireEvent.change(drawer.getByLabelText(/Tên thiết bị/), { target: { value: 'Loa' } });
  fireEvent.change(drawer.getByLabelText(/Nhóm/), { target: { value: 'audio' } });
  fireEvent.change(drawer.getByLabelText(/Khu vực \/ vị trí khác/), { target: { value: 'Kho' } });
  fireEvent.click(drawer.getByRole('button', { name: 'Thêm thiết bị' }));
  expect(await screen.findByRole('status')).toHaveTextContent(first);
  expect(equipmentAssetApi.create).toHaveBeenLastCalledWith(false, 'hoa-bang', expect.objectContaining({ code: first }));

  fireEvent.click(screen.getByRole('button', { name: /Thêm thiết bị/ }));
  const nextCode = await screen.findByDisplayValue(next);
  expect(nextCode).toHaveAttribute('readonly');
  expect(nextCode).not.toBeRequired();
});

test('explains how to load the code rules when the backend still serves the old API', async () => {
  equipmentAssetApi.list.mockResolvedValue([]);
  equipmentAssetApi.options.mockResolvedValue({ categories: [], rooms: [] });

  render(<EquipmentAssetsPage user={{ branchId: 'hoa-bang' }} />);
  fireEvent.click(screen.getByRole('button', { name: /Thêm thiết bị/ }));
  const drawer = within(screen.getByRole('complementary', { name: 'Hồ sơ thiết bị' }));

  expect(await drawer.findByText(/Khởi động lại backend/)).toBeVisible();
  expect(drawer.getByRole('button', { name: 'Thêm thiết bị' })).toBeDisabled();
});
