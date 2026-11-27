import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import '@testing-library/jest-dom';
import ExpenseCategoriesTab from './ExpenseCategoriesTab';

test('simplifies category editing and retains the classification of an existing category', async () => {
  HTMLDialogElement.prototype.showModal = function () { this.setAttribute('open', ''); };
  const onSave = jest.fn().mockResolvedValue(true);
  render(<ExpenseCategoriesTab busy={false} onSave={onSave} categories={[{
    id: 'category-1', name: 'Thiết bị', active: true, category_group: 'EQUIPMENT',
    cost_type: 'CAPEX', cost_nature: 'FIXED', recognition_mode: 'ALLOCATED', default_allocation_months: 6,
    invoice_required: true, reimbursement_allowed: false,
  }]}/>);

  expect(screen.queryByText('Thêm nhanh từ mẫu')).not.toBeInTheDocument();
  expect(screen.queryByRole('columnheader', { name: 'Loại' })).not.toBeInTheDocument();
  fireEvent.click(screen.getByRole('button', { name: 'Sửa Thiết bị' }));
  const drawer = within(screen.getByRole('dialog', { name: 'Sửa hạng mục' }));
  expect(drawer.queryByLabelText('Loại chi phí')).not.toBeInTheDocument();
  expect(drawer.queryByLabelText('Tính chất')).not.toBeInTheDocument();
  fireEvent.change(drawer.getByLabelText('Số tháng mặc định'), { target: { value: '12' } });
  fireEvent.click(drawer.getByRole('button', { name: 'Lưu thay đổi' }));

  await waitFor(() => expect(onSave).toHaveBeenCalledWith(expect.objectContaining({
    id: 'category-1', costType: 'CAPEX', costNature: 'FIXED', defaultAllocationMonths: 12,
  })));
});
