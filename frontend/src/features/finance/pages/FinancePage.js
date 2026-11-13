import { useCallback, useEffect, useRef, useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { createPortal } from 'react-dom';
import apiClient from '../../../api/client';
import { apiErrors, authApi } from '../../auth/services/authApi';
import { financeApi } from '../services/financeApi';
import '../styles/finance.css';
import DrawerFundingPanel from '../components/DrawerFundingPanel';
import ExpenseCategoriesTab from '../components/ExpenseCategoriesTab';
import ExpenseAllocationFields from '../components/ExpenseAllocationFields';
import DirectExpenseDialog from '../components/DirectExpenseDialog';
import RecurringExpensesTab from '../components/RecurringExpensesTab';
import { calculateExpenseAllocation } from '../utils/expenseAllocation';

const statusName = { WAITING_CONFIRMATION:'Chờ kế toán xác nhận', RECEIVED:'Đã nhận', WAITING_ACCOUNTANT:'Chờ kế toán duyệt', WAITING_HEAD_OFFICE:'Chờ quản lý cấp cao duyệt', APPROVED_TO_PAY:'Đã duyệt · chờ thanh toán', REJECTED:'Từ chối', PAID:'Đã thanh toán' };
const historyName = { SUBMITTED:'Đã gửi đề nghị', APPROVED:'Đã duyệt', REJECTED:'Đã từ chối', PAID:'Đã thanh toán' };
const money = (value) => `${Number(value || 0).toLocaleString('vi-VN')} ₫`;
const today = () => new Date().toLocaleDateString('en-CA');
const localDate = (value) => {
  if (!value) return '—';
  if (Array.isArray(value) && value.length >= 3) {
    const [year,month,day]=value;
    return new Date(Number(year),Number(month)-1,Number(day)).toLocaleDateString('vi-VN');
  }
  if ((typeof value==='number'||/^\d{10,13}$/.test(String(value)))&&Number.isFinite(Number(value)))
    return new Date(Number(value)).toLocaleDateString('vi-VN');
  const dateOnly = String(value).match(/^\d{4}-\d{2}-\d{2}/)?.[0];
  if (dateOnly) { const [year,month,day]=dateOnly.split('-'); return `${day}/${month}/${year}`; }
  const parsed = new Date(value);
  return Number.isNaN(parsed.getTime()) ? '—' : parsed.toLocaleDateString('vi-VN');
};
const localDateTime = (value) => {
  if (!value) return '—';
  if (Array.isArray(value) && value.length >= 3) {
    const [year,month,day,hour=0,minute=0,second=0]=value;
    return new Date(Number(year),Number(month)-1,Number(day),Number(hour),Number(minute),Number(second)).toLocaleString('vi-VN');
  }
  const normalized=(typeof value==='number'||/^\d{10,13}$/.test(String(value)))?Number(value):(String(value).includes('T')?value:String(value).replace(' ','T'));
  const parsed = new Date(normalized);
  return Number.isNaN(parsed.getTime()) ? String(value) : parsed.toLocaleString('vi-VN');
};
const message = (error) => { const errors=apiErrors(error); return errors.form || Object.values(errors)[0] || 'Không thể hoàn tất thao tác. Vui lòng thử lại.'; };
const fileAccept = 'image/jpeg,.jpg,.jpeg,image/png,.png,image/webp,.webp,image/heic,.heic,image/heif,.heif,application/pdf,.pdf';
const monthValue = (value) => {
  if (Array.isArray(value) && value.length >= 2) return `${value[0]}-${String(value[1]).padStart(2,'0')}`;
  return String(value || '').match(/^\d{4}-\d{2}/)?.[0] || '';
};
const monthLabel = (value) => {
  const month = monthValue(value);
  return month ? `${month.slice(5,7)}/${month.slice(0,4)}` : '—';
};

function Attachments({ rows=[], compact=false }) {
  const [preview,setPreview]=useState(null);
  const [opening,setOpening]=useState(false);
  const [previewError,setPreviewError]=useState('');
  useEffect(()=>{if(!preview)return undefined;return()=>URL.revokeObjectURL(preview.url);},[preview]);
  if (!rows.length) return <small>Chưa có chứng từ đính kèm</small>;
  async function openAttachment(item) {
    setOpening(true);setPreviewError('');
    try { const response=await apiClient.get(`/finance/attachments/${item.id}`,{responseType:'blob'});setPreview({url:URL.createObjectURL(response.data),name:item.original_name,mimeType:response.data.type||item.mime_type}); }
    catch { setPreviewError('Không tải được chứng từ. Vui lòng làm mới trang và thử lại.'); }
    finally { setOpening(false); }
  }
const kindLabel=(item)=>item.kind==='INVOICE'?'Hóa đơn':item.kind==='PAYMENT_PROOF'?'Chứng từ thanh toán':'Chứng từ chuyển khoản';
  return <>
    <div className={`finance-files${compact?' finance-files-compact':''}`}>{rows.map((item)=><span key={item.id}>
      {item.mime_type?.startsWith('image/')&&<button type="button" className={`finance-thumb${compact?' finance-thumb-compact':''}`} aria-label={`Xem ảnh ${item.original_name}`} onClick={()=>openAttachment(item)} disabled={opening}><img src={apiClient.getUri({url:`/finance/attachments/${item.id}`})} alt={item.original_name}/></button>}
      <button type="button" className="finance-attachment-link" onClick={()=>openAttachment(item)} disabled={opening}>{opening?'Đang tải…':`${kindLabel(item)} · ${item.original_name}`}</button>
    </span>)}</div>
    {previewError&&<small className="finance-preview-error" role="alert">{previewError}</small>}
    {preview&&createPortal(<div className="finance-preview-backdrop" role="presentation" onMouseDown={(event)=>event.target===event.currentTarget&&setPreview(null)}>
      <section className="finance-preview-dialog" role="dialog" aria-modal="true" aria-label={`Xem chứng từ ${preview.name}`}>
        <header><strong>{preview.name}</strong><button type="button" onClick={()=>setPreview(null)} aria-label="Đóng ảnh">×</button></header>
        {preview.mimeType?.startsWith('image/')?<img className="finance-preview-image" src={preview.url} alt={preview.name}/>:preview.mimeType==='application/pdf'?<iframe title={preview.name} src={preview.url}/>:<p>Định dạng tệp này chưa hỗ trợ xem trước.</p>}
        <footer><a href={preview.url} download={preview.name}>Tải tệp</a><button type="button" onClick={()=>setPreview(null)}>Đóng</button></footer>
      </section>
    </div>,document.body)}
  </>;
}

function RequestTable({ rows, role, busy, onReview, onPay, paymentForms, setPaymentForms, requestFiles, setRequestFiles, onUploadRequestFile }) {
  const [expandedAllocation, setExpandedAllocation] = useState(null);
  return <div className="finance-table-wrap"><table><thead><tr><th>Mã · Cơ sở</th><th>Ngày · Hạng mục</th><th>Nội dung · Lịch sử</th><th>Số tiền</th><th>Người nhận · Ngân hàng</th><th>Trạng thái</th><th>Chứng từ / thao tác</th></tr></thead><tbody>
    {rows.map((row)=>{
      const form = paymentForms[row.id] || {};
      const months = form.allocationMonths ?? row.allocation_months ?? '';
      const startMonth = form.allocationStart ?? monthValue(row.expense_on);
      const allocated = row.recognition_mode === 'ALLOCATED';
      const allocationValid = !allocated || !calculateExpenseAllocation(row.amount, months, startMonth).error;
      return <tr key={row.id}>
      <td><strong>{row.code}</strong><small>{row.branch_name} · {row.kind==='REIMBURSEMENT'?'Hoàn ứng':'Thanh toán'}{row.origin==='ACCOUNTANT_DIRECT'?' · Chi trực tiếp':''}</small></td>
      <td>{localDate(row.expense_on)}<small>{row.category_name}</small>{allocated&&<small><button type="button" className="finance-allocation-toggle" aria-expanded={expandedAllocation===row.id} onClick={()=>setExpandedAllocation(expandedAllocation===row.id?null:row.id)}>Phân bổ {row.allocation_months} tháng</button></small>}
        {allocated&&expandedAllocation===row.id&&<div className="finance-allocation-detail"><table><thead><tr><th>Tháng</th><th>Số tiền</th></tr></thead><tbody>{(row.allocations||[]).map((line)=><tr key={line.id}><td>{monthLabel(line.period_month)}</td><td>{money(line.amount)}</td></tr>)}</tbody></table></div>}
      </td>
      <td>{row.description}<small>Gửi bởi {row.requested_by_name}</small>{row.history?.map((event)=><small className="finance-history" key={event.id}>{historyName[event.action]||event.action} · {event.actor_name} · {localDateTime(event.created_at)}{event.note?` · ${event.note}`:''}</small>)}</td>
      <td><strong>{money(row.amount)}</strong></td>
      <td>{row.beneficiary_name}<small>{row.bank_name} · {row.bank_account}</small></td>
      <td><span className={`finance-status ${row.status.toLowerCase()}`}>{statusName[row.status]}</span>{row.paid_at&&<small>{localDateTime(row.paid_at)}</small>}</td>
      <td><Attachments rows={row.attachments}/>
        {role==='BRANCH_MANAGER'&&!['PAID','REJECTED'].includes(row.status)&&<div className="finance-missing-docs">
          {['INVOICE',...(row.kind==='PAYMENT'?['TRANSFER_PROOF']:[])].filter((kind)=>!row.attachments?.some((item)=>item.kind===kind)).map((kind)=><form key={kind} className="finance-pay-form" onSubmit={(event)=>onUploadRequestFile(event,row.id,kind)}>
            <small>Thiếu {kind==='INVOICE'?'hóa đơn':'chứng từ chuyển khoản'}</small>
            <input required type="file" accept={fileAccept} aria-label={`Bổ sung ${kind==='INVOICE'?'hóa đơn':'chứng từ chuyển khoản'}`} onChange={(event)=>setRequestFiles({...requestFiles,[`${row.id}:${kind}`]:event.target.files?.[0]||null})}/>
            <button disabled={busy}>Lưu chứng từ</button>
          </form>)}
        </div>}
        {role==='HEAD_OFFICE_MANAGER'&&row.status==='WAITING_HEAD_OFFICE'&&<div className="finance-actions"><button disabled={busy} onClick={()=>onReview(row.id,true)}>Duyệt</button><button disabled={busy} onClick={()=>onReview(row.id,false)}>Từ chối</button></div>}
        {role==='ACCOUNTANT'&&row.status==='WAITING_ACCOUNTANT'&&<div className="finance-actions"><button disabled={busy} onClick={()=>onReview(row.id,true)}>Duyệt</button><button disabled={busy} onClick={()=>onReview(row.id,false)}>Từ chối</button></div>}
        {role==='ACCOUNTANT'&&row.status==='APPROVED_TO_PAY'&&<form className="finance-pay-form" onSubmit={(event)=>onPay(event,row.id)}>
          <input placeholder="Mã giao dịch" value={paymentForms[row.id]?.reference||''} onChange={(event)=>setPaymentForms({...paymentForms,[row.id]:{...paymentForms[row.id],reference:event.target.value}})}/>
          {allocated&&<ExpenseAllocationFields amount={row.amount} months={months} startMonth={startMonth}
            onMonthsChange={(value)=>setPaymentForms({...paymentForms,[row.id]:{...paymentForms[row.id],allocationMonths:value}})}
            onStartMonthChange={(value)=>setPaymentForms({...paymentForms,[row.id]:{...paymentForms[row.id],allocationStart:value}})} disabled={busy}/>}
          <input aria-label="Chứng từ chuyển khoản đã thanh toán" required type="file" accept={fileAccept} onChange={(event)=>setPaymentForms({...paymentForms,[row.id]:{...paymentForms[row.id],proof:event.target.files?.[0]||null}})}/>
          <small>Chứng từ thanh toán thực tế, tối đa 20 MB.</small><button disabled={busy||!allocationValid}>Ghi nhận đã thanh toán</button>
        </form>}
      </td>
    </tr>})}
    {!rows.length&&<tr><td colSpan="7" className="finance-empty">Chưa có đề nghị chi nào.</td></tr>}
  </tbody></table></div>;
}

function BranchFilter({ branches, value, onChange }) {
  return <label className="finance-branch-filter">Cơ sở
    <select value={value} onChange={(event)=>onChange(event.target.value)}>
      <option value="ALL">Tất cả cơ sở ({branches.length})</option>
      {branches.map((branch)=><option value={branch.id} key={branch.id}>{branch.name}</option>)}
    </select>
  </label>;
}

function FundTransferTable({ rows, busy, fundFiles, setFundFiles, onUpload, onConfirm }) {
  return <div className="finance-fund-grid-outer" aria-label="Danh sách yêu cầu chuyển tiền" role="table">
    <div className="finance-fund-grid-inner">
        <div className="finance-fund-fixed-head" role="row"><span role="columnheader">Người gửi</span><span role="columnheader">Ngày gửi</span><span role="columnheader">Số tiền</span><span role="columnheader">Mã giao dịch · Chứng từ</span><span role="columnheader">Trạng thái</span></div>
      <div className="finance-fund-grid-scroll" role="rowgroup">{rows.map((item)=><div className="finance-fund-grid-row" role="row" key={item.id}>
        <div className="finance-fund-grid-cell finance-fund-sender" role="cell" title={`${item.sent_by_name||'Quản lý cấp cao'} · ${item.code}`}><strong>{item.sent_by_name||'Quản lý cấp cao'} · {item.code}</strong></div>
        <div className="finance-fund-grid-cell" role="cell" title={localDate(item.sent_on)}><strong>{localDate(item.sent_on)}</strong></div>
        <div className="finance-fund-grid-cell finance-fund-grid-amount" role="cell"><strong>{money(item.amount)}</strong></div>
        <div className="finance-fund-grid-cell finance-fund-bill" role="cell"><span className="finance-fund-reference" title={item.transfer_reference||'—'}>{item.transfer_reference||'—'}</span>{item.has_proof&&<Attachments compact rows={[{id:item.attachment_id,kind:'TRANSFER_PROOF',original_name:item.attachment_name,mime_type:item.attachment_mime}]} />}{!item.has_proof&&item.status==='WAITING_CONFIRMATION'&&onUpload&&<form className="finance-fund-upload" onSubmit={(event)=>onUpload(event,item.id)}><input aria-label={`Chọn chứng từ cho ${item.code}`} type="file" required accept={fileAccept} onChange={(event)=>setFundFiles({...fundFiles,[item.id]:{proof:event.target.files?.[0]||null}})}/><button disabled={busy}>{busy?'Lưu…':'Tải chứng từ'}</button></form>}{!item.has_proof&&item.status!=='WAITING_CONFIRMATION'&&<small>Chưa có chứng từ</small>}</div>
        <div className="finance-fund-grid-cell finance-fund-grid-status" role="cell"><span className={`finance-status ${item.status.toLowerCase()}`}>{statusName[item.status]}</span>{item.status==='WAITING_CONFIRMATION'&&onConfirm&&<button disabled={busy} onClick={()=>onConfirm(item.id)}>Đã nhận</button>}</div>
      </div>)}{!rows.length&&<div className="finance-empty">Chưa có yêu cầu chuyển tiền.</div>}</div>
    </div>
  </div>;
}

function FinanceWorkspace({ user }) {
  const role=user?.role;
  const location=useLocation();
  const navigate=useNavigate();
  const initialParams=new URLSearchParams(location.search);
  const initialTab=initialParams.get('section');
  const accountantSection=initialTab||'plus';
  const requestedTab=initialParams.get('tab');
  const [accountTab,setAccountTab]=useState(accountantSection==='payments'?'incoming':accountantSection==='inventory'?'categories':['requests','recurring','drawer','history','categories'].includes(requestedTab)?requestedTab:'requests');
  const [headOfficeTab,setHeadOfficeTab]=useState('fund');
  const [fundDialogOpen,setFundDialogOpen]=useState(false);
  const [directDialogOpen,setDirectDialogOpen]=useState(false);
  const [recurringPayPeriod,setRecurringPayPeriod]=useState(null);
  const [recurringRevision,setRecurringRevision]=useState(0);
  const fundDialogRef=useRef(null); const fundDialogFirstFieldRef=useRef(null); const fundDialogTriggerRef=useRef(null);
  const [selectedBranch,setSelectedBranch]=useState('ALL');
  const [data,setData]=useState(null); const [loading,setLoading]=useState(true); const [busy,setBusy]=useState(false);
  const [error,setError]=useState(''); const [notice,setNotice]=useState('');
  const [reviewDialog,setReviewDialog]=useState(null);
  const busyRef=useRef(false); busyRef.current=busy;
  const [requestKind,setRequestKind]=useState('PAYMENT');
  const [request,setRequest]=useState({categoryId:'',amount:'',expenseOn:today(),description:'',beneficiaryName:'',bankAccount:'',bankName:'',invoice:null,transferProof:null});
  const [requestFiles,setRequestFiles]=useState({}); const [fund,setFund]=useState({amount:'50000000',sentOn:today(),reference:'',proof:null});
  const [fundFiles,setFundFiles]=useState({}); const [paymentForms,setPaymentForms]=useState({});
  const reload=useCallback(async()=>{setLoading(true);setError('');try{setData(await financeApi.load(role));}catch(e){setError(message(e));}finally{setLoading(false);}},[role]);
  useEffect(()=>{reload();},[reload]);
  useEffect(()=>{
    const params=new URLSearchParams(location.search);
    const section=params.get('section')||'plus';
    if(section==='inventory') {
      params.set('section','plus'); params.set('tab','categories');
      navigate(`${location.pathname}?${params.toString()}`,{replace:true});
      setAccountTab('categories');
      return;
    }
    const requestedTab=params.get('tab');
    setAccountTab(section==='payments'?'incoming':['requests','recurring','drawer','history','categories'].includes(requestedTab)?requestedTab:'requests');
  },[location.pathname,location.search,navigate]);
  useEffect(()=>{
    if(!fundDialogOpen)return;
    const trigger = fundDialogTriggerRef.current;
    fundDialogFirstFieldRef.current?.focus();
    const onKeyDown=(event)=>{
      if(event.key==='Escape'&&!busyRef.current){setFundDialogOpen(false);return;}
      if(event.key!=='Tab'||!fundDialogRef.current)return;
      const controls=[...fundDialogRef.current.querySelectorAll('button:not([disabled]),input:not([disabled]),select:not([disabled]),textarea:not([disabled]),a[href]')];
      if(!controls.length)return;
      const first=controls[0],last=controls[controls.length-1];
      if(event.shiftKey&&document.activeElement===first){event.preventDefault();last.focus();}
      else if(!event.shiftKey&&document.activeElement===last){event.preventDefault();first.focus();}
    };
    document.addEventListener('keydown',onKeyDown);
    return()=>{document.removeEventListener('keydown',onKeyDown);trigger?.focus();};
  },[fundDialogOpen]);

  function changeExpenseTab(tab){
    setAccountTab(tab);
    const params=new URLSearchParams(location.search);params.set('section','plus');
    params.set('tab',tab);
    navigate(`${location.pathname}?${params.toString()}`,{replace:true});
  }
  async function act(fn,success){setBusy(true);setError('');setNotice('');try{await fn();setNotice(success);await reload();return true;}catch(e){setError(message(e));return false;}finally{setBusy(false);}}
  async function saveCategory(value){const {id,...payload}=value;return act(()=>id?financeApi.updateCategory(id,payload):financeApi.createCategory(payload),id?'Đã cập nhật hạng mục.':'Đã tạo hạng mục.');}
  async function setCategoryActive(id,active){return act(()=>financeApi.setCategoryActive(id,active),active?'Đã kích hoạt lại hạng mục.':'Đã ngừng sử dụng hạng mục.');}
  async function deleteCategory(id){return act(()=>financeApi.deleteCategory(id),'Đã xóa hạng mục.');}
  async function saveRecurringExpense(id,command){const saved=await act(()=>id?financeApi.updateRecurringExpense(id,command):financeApi.createRecurringExpense(command),id?'Đã cập nhật khoản chi định kỳ.':'Đã tạo khoản chi định kỳ.');if(saved)setRecurringRevision((value)=>value+1);return saved;}
  async function terminateRecurringExpense(id){const saved=await act(()=>financeApi.terminateRecurringExpense(id),'Đã chấm dứt khoản chi định kỳ; lịch sử các kỳ được giữ lại.');if(saved)setRecurringRevision((value)=>value+1);return saved;}
  async function skipRecurringPeriod(id,reason){const saved=await act(()=>financeApi.skipRecurringPeriod(id,reason),'Đã bỏ qua kỳ chi và lưu lý do.');if(saved)setRecurringRevision((value)=>value+1);return saved;}
async function createFund(event){event.preventDefault();const formElement=event.currentTarget;setBusy(true);setError('');setNotice('');try{const created=await financeApi.sendFund({amount:fund.amount,sentOn:fund.sentOn,reference:fund.reference});let proofError='';if(fund.proof){try{await financeApi.uploadFundProof(created.id,fund.proof);}catch(e){proofError=message(e);}}formElement.reset();setFund({amount:'50000000',sentOn:today(),reference:'',proof:null});setFundDialogOpen(false);await reload();if(proofError){setError(`Yêu cầu đã gửi, nhưng chứng từ chưa tải lên được. Có thể thử tải lại trong danh sách cấp quỹ. ${proofError}`);}else{setNotice('Đã gửi yêu cầu cấp quỹ cho kế toán xác nhận. Số dư tăng sau khi kế toán xác nhận đã nhận tiền.');}}catch(e){setError(message(e));}finally{setBusy(false);}}
async function uploadFundProof(event,id){event.preventDefault();const proof=fundFiles[id]?.proof;if(!proof){setError('Chọn chứng từ chuyển khoản trước khi tải lên.');return;}await act(()=>financeApi.uploadFundProof(id,proof),'Đã lưu chứng từ trên hệ thống.');setFundFiles({...fundFiles,[id]:{proof:null}});}
async function createRequest(event){event.preventDefault();const formElement=event.currentTarget;const kind=requestKind;const metadata={kind,categoryId:request.categoryId,amount:request.amount,expenseOn:request.expenseOn,description:request.description,beneficiaryName:request.beneficiaryName,bankAccount:request.bankAccount,bankName:request.bankName};setBusy(true);setError('');setNotice('');try{const created=await financeApi.createRequest(metadata);const uploadErrors=[];if(request.invoice){try{await financeApi.uploadRequestAttachment(created.id,'INVOICE',request.invoice);}catch(e){uploadErrors.push(`Hóa đơn: ${message(e)}`);}}if(kind==='PAYMENT'&&request.transferProof){try{await financeApi.uploadRequestAttachment(created.id,'TRANSFER_PROOF',request.transferProof);}catch(e){uploadErrors.push(`Chứng từ chuyển khoản: ${message(e)}`);}}setRequest({...request,amount:'',description:'',invoice:null,transferProof:null});formElement.reset();await reload();setNotice(uploadErrors.length?'Đã gửi đề nghị sang kế toán. Chứng từ chưa lưu hết vào máy chủ; có thể bổ sung ở danh sách bên dưới.':'Đã gửi đề nghị sang kế toán.');if(uploadErrors.length)setError(uploadErrors.join(' · '));}catch(e){setError(message(e));}finally{setBusy(false);}}
  async function uploadRequestDoc(event,id,kind){event.preventDefault();const key=`${id}:${kind}`;const file=requestFiles[key];if(!file){setError('Chọn tệp chứng từ trước khi tải lên.');return;}await act(()=>financeApi.uploadRequestAttachment(id,kind,file),'Đã lưu chứng từ trên hệ thống.');setRequestFiles({...requestFiles,[key]:null});}
  function chooseRequestKind(kind){setRequestKind(kind);if(kind==='REIMBURSEMENT'&&selectedRequestCategory&&!selectedRequestCategory.reimbursement_allowed)setRequest({...request,categoryId:''});}
  function review(id,approve){setReviewDialog({id,approve,note:''});}
  async function confirmReview(event){event.preventDefault();if(!reviewDialog)return;if(!reviewDialog.approve&&!reviewDialog.note.trim()){setError('Vui lòng nhập lý do từ chối.');return;}setBusy(true);setError('');setNotice('');try{await financeApi.review(role,reviewDialog.id,reviewDialog.approve,reviewDialog.note);setNotice(reviewDialog.approve?'Đã duyệt đề nghị.':'Đã từ chối đề nghị.');setReviewDialog(null);await reload();}catch(e){setError(message(e));}finally{setBusy(false);}}
  async function pay(event,id){event.preventDefault();const form=paymentForms[id]||{};const proof=form.proof;if(!proof){setError('Vui lòng tải lên chứng từ chuyển khoản thực tế.');return false;}const row=requests.find((item)=>item.id===id);const allocated=row?.recognition_mode==='ALLOCATED';const months=allocated?(form.allocationMonths??row.allocation_months):null;const startMonth=allocated?(form.allocationStart??monthValue(row.expense_on)):null;if(allocated){const preview=calculateExpenseAllocation(row.amount,months,startMonth);if(preview.error){setError(preview.error);return false;}}return act(()=>financeApi.pay(id,form.reference||'',proof,months,startMonth?`${startMonth}-01`:null),'Đã ghi nhận thanh toán và trừ quỹ.');}
  async function createDirectExpense(command,proof,invoice){
    if(recurringPayPeriod){const saved=await act(()=>financeApi.payRecurringPeriod(recurringPayPeriod.id,command,proof,invoice),'Đã thanh toán kỳ chi định kỳ, trừ quỹ và lưu lịch sử.');if(saved)setRecurringRevision((value)=>value+1);return saved;}
    return act(()=>financeApi.createDirectExpense(command,proof,invoice),'Đã ghi nhận chi trực tiếp, trừ quỹ và lưu lịch sử.');
  }

  const categories=data?.categories||[];const requests=data?.requests||[];const transfers=data?.fundTransfers||[];const branches=data?.branches||[];
  const branchRequests=selectedBranch==='ALL'?requests:requests.filter((item)=>String(item.branch_id)===String(selectedBranch));
  const receivedTotal=transfers.filter((item)=>item.status==='RECEIVED').reduce((sum,item)=>sum+Number(item.amount||0),0);
  const waitingTotal=transfers.filter((item)=>item.status==='WAITING_CONFIRMATION').reduce((sum,item)=>sum+Number(item.amount||0),0);
  const openRequests=requests.filter((item)=>!['PAID','REJECTED'].includes(item.status));
  const closedRequests=requests.filter((item)=>['PAID','REJECTED'].includes(item.status));
  const selectedRequestCategory=categories.find((item)=>String(item.id)===String(request.categoryId));
  const accountantExpenseTabs=[['requests','Đề nghị thanh toán và hoàn ứng'],['recurring','Chi định kỳ'],['drawer','Cấp tiền két'],['history','Lịch sử thanh toán và hoàn ứng'],['categories','Hạng mục chi phí']];
  const headOfficeTabs=[['fund','Cấp quỹ'],['expense_requests','Đề nghị thanh toán và hoàn ứng']];

  return <main className={`finance-page${role==='HEAD_OFFICE_MANAGER'?' finance-page-head-office':''}`}>
    <header className="finance-heading"><div><h1>{role==='HEAD_OFFICE_MANAGER'?'Quỹ & duyệt chi toàn chuỗi':role==='ACCOUNTANT'?accountantSection==='payments'?'Quản lý thu và cấp quỹ':'Chi và hoàn ứng':'Chi phí cơ sở'}</h1></div><div className="finance-heading-actions">{role==='ACCOUNTANT'&&accountantSection==='plus'&&<button type="button" className="finance-primary-action" onClick={()=>{setRecurringPayPeriod(null);setDirectDialogOpen(true);}}>＋ Ghi nhận chi trực tiếp</button>}<button type="button" onClick={()=>{reload();setRecurringRevision((value)=>value+1);}} disabled={loading}>Làm mới</button></div></header>
    {notice&&<p className="finance-notice" role="status">{notice}</p>}{error&&<p className="finance-error" role="alert">{error}</p>}

    {role==='ACCOUNTANT'&&accountantSection==='payments'&&<>
      <section className="finance-balance"><div><span>Quỹ chi của kế toán (toàn chuỗi)</span><strong>{loading?'Đang tải…':money(data?.balance)}</strong></div><p>Nguồn chi lương và chi phí là quỹ QLCC cấp cho kế toán. Quỹ này tách biệt với báo cáo thu ngân cuối ngày của cơ sở.</p></section>
      {accountTab==='incoming'&&<section className="finance-panel finance-workspace-panel"><div className="finance-section-heading"><div><h2>Lịch sử tiền vào</h2></div><div className="finance-mini-total"><span>Đã xác nhận</span><strong>{money(receivedTotal)}</strong></div><div className="finance-mini-total pending"><span>Chờ xác nhận</span><strong>{money(waitingTotal)}</strong></div></div>
        <FundTransferTable rows={transfers} busy={busy} onConfirm={(id)=>act(()=>financeApi.confirmFund(id),'Đã xác nhận nhận tiền; số dư quỹ đã cập nhật.')}/>
      </section>}
    </>}
    {role==='ACCOUNTANT'&&accountantSection==='plus'&&<>
      <nav className="finance-tabs" aria-label="Chi và hoàn ứng">{accountantExpenseTabs.map(([key,label])=><button type="button" key={key} className={accountTab===key?'active':''} aria-pressed={accountTab===key} onClick={()=>changeExpenseTab(key)}>{label}</button>)}</nav>
      {accountTab==='requests'&&<section className="finance-panel finance-workspace-panel"><div className="finance-section-heading"><div><h2>Đề nghị chờ xử lý</h2></div><div className="finance-mini-total pending"><span>Đang chờ xử lý</span><strong>{branchRequests.filter((item)=>!['PAID','REJECTED'].includes(item.status)).length} đề nghị</strong></div></div><BranchFilter branches={branches} value={selectedBranch} onChange={setSelectedBranch}/>
        <RequestTable rows={branchRequests.filter((item)=>!['PAID','REJECTED'].includes(item.status))} role={role} busy={busy} onReview={review} onPay={pay} paymentForms={paymentForms} setPaymentForms={setPaymentForms} requestFiles={requestFiles} setRequestFiles={setRequestFiles} onUploadRequestFile={uploadRequestDoc}/>
      </section>}
      {accountTab==='recurring'&&<RecurringExpensesTab branches={branches} categories={categories} busy={busy} revision={recurringRevision} onSave={saveRecurringExpense} onTerminate={terminateRecurringExpense} onSkip={skipRecurringPeriod} onPay={(period)=>{setRecurringPayPeriod(period);setDirectDialogOpen(true);}}/>}
      {accountTab==='drawer'&&<DrawerFundingPanel user={user} onChange={reload}/>}
      {accountTab==='history'&&<section className="finance-panel finance-workspace-panel"><div className="finance-section-heading"><div><h2>Lịch sử thanh toán và hoàn ứng</h2></div><div className="finance-mini-total"><span>Đã đóng</span><strong>{branchRequests.filter((item)=>['PAID','REJECTED'].includes(item.status)).length} đề nghị</strong></div></div><BranchFilter branches={branches} value={selectedBranch} onChange={setSelectedBranch}/>
        <RequestTable rows={branchRequests.filter((item)=>['PAID','REJECTED'].includes(item.status))} role={role} busy={busy} onReview={review} onPay={pay} paymentForms={paymentForms} setPaymentForms={setPaymentForms} requestFiles={requestFiles} setRequestFiles={setRequestFiles} onUploadRequestFile={uploadRequestDoc}/>
      </section>}
      {accountTab==='categories'&&<ExpenseCategoriesTab categories={categories} busy={busy} onSave={saveCategory} onSetActive={setCategoryActive} onDelete={deleteCategory}/>}
    </>}

    {directDialogOpen&&role==='ACCOUNTANT'&&accountantSection==='plus'&&<DirectExpenseDialog key={recurringPayPeriod?.id||'direct'} branches={branches} categories={categories} busy={busy} recurringPeriod={recurringPayPeriod} onClose={()=>{setDirectDialogOpen(false);setRecurringPayPeriod(null);}} onSubmit={createDirectExpense}/>}

    {role==='HEAD_OFFICE_MANAGER'&&<>
      <nav className="finance-tabs" aria-label="Phê duyệt chi toàn chuỗi">{headOfficeTabs.map(([key,label])=><button type="button" key={key} className={headOfficeTab===key?'active':''} aria-pressed={headOfficeTab===key} onClick={()=>setHeadOfficeTab(key)}>{label}</button>)}</nav>
      {headOfficeTab==='fund'&&<section className="finance-panel finance-fund-panel">
        <div className="finance-section-heading"><div><h2>Cấp quỹ</h2></div><button ref={fundDialogTriggerRef} type="button" className="finance-primary-action" onClick={()=>{setError('');setNotice('');setFund({amount:'50000000',sentOn:today(),reference:'',proof:null});setFundDialogOpen(true);}}>＋ Tạo yêu cầu chuyển tiền</button></div>
        <div className="finance-fund-summary"><article><span>Tổng số yêu cầu</span><strong>{transfers.length}</strong></article><article className="pending"><span>Chờ kế toán xác nhận</span><strong>{money(waitingTotal)}</strong></article><article className="received"><span>Đã xác nhận vào quỹ</span><strong>{money(receivedTotal)}</strong></article></div>
        <div className="finance-section-heading finance-list-heading"><div><h3>Yêu cầu chuyển tiền</h3><p>{transfers.length} yêu cầu</p></div></div>
        <FundTransferTable rows={transfers} busy={busy} fundFiles={fundFiles} setFundFiles={setFundFiles} onUpload={uploadFundProof}/>
      </section>}
      {headOfficeTab==='expense_requests'&&<section className="finance-panel"><div className="finance-section-heading"><div><h2>Đề nghị thanh toán và hoàn ứng</h2><p>Đề nghị trên 3.000.000 ₫ cần quản lý cấp cao duyệt.</p></div></div><BranchFilter branches={branches} value={selectedBranch} onChange={setSelectedBranch}/><RequestTable rows={branchRequests} role={role} busy={busy} onReview={review} onPay={pay} paymentForms={paymentForms} setPaymentForms={setPaymentForms} requestFiles={requestFiles} setRequestFiles={setRequestFiles} onUploadRequestFile={uploadRequestDoc}/></section>}
    </>}

    {fundDialogOpen&&<div className="finance-confirm-backdrop" role="presentation" onMouseDown={(event)=>{if(event.target===event.currentTarget&&!busy)setFundDialogOpen(false);}}>
      <section ref={fundDialogRef} className="finance-confirm-dialog finance-fund-dialog" role="dialog" aria-modal="true" aria-labelledby="finance-fund-title" aria-describedby="finance-fund-description">
        <div className="finance-fund-dialog-heading"><div><span className="finance-dialog-eyebrow">QUỸ DÙNG CHUNG TOÀN CHUỖI</span><h2 id="finance-fund-title">Tạo yêu cầu chuyển tiền</h2></div><button type="button" aria-label="Đóng cửa sổ tạo yêu cầu" disabled={busy} onClick={()=>setFundDialogOpen(false)}>×</button></div>
        <p id="finance-fund-description">Gửi thông tin khoản đã chuyển cho kế toán đối chiếu và xác nhận nhận tiền.</p>
        <form className="finance-form finance-fund-form" onSubmit={createFund}>
          <label>Số tiền chuyển *<input ref={fundDialogFirstFieldRef} required min="1" step="1" type="number" inputMode="numeric" value={fund.amount} onChange={(event)=>setFund({...fund,amount:event.target.value})}/><small>Nhập số tiền bằng đồng Việt Nam.</small></label>
          <label>Ngày gửi *<input required type="date" value={fund.sentOn} onChange={(event)=>setFund({...fund,sentOn:event.target.value})}/></label>
          <label>Mã giao dịch ngân hàng<input maxLength="200" value={fund.reference} onChange={(event)=>setFund({...fund,reference:event.target.value})}/></label>
          <label>Chứng từ chuyển khoản<input type="file" accept={fileAccept} onChange={(event)=>setFund({...fund,proof:event.target.files?.[0]||null})}/><small>{fund.proof?fund.proof.name:'Ảnh hoặc PDF · tối đa 20 MB · lưu nội bộ'}</small></label>
          <div className="finance-fund-dialog-note">Số dư kế toán chỉ tăng sau khi kế toán xác nhận đã nhận khoản tiền.</div>
          <div className="finance-confirm-actions"><button type="button" disabled={busy} onClick={()=>setFundDialogOpen(false)}>Hủy</button><button type="submit" className="confirm-approve" disabled={busy}>{busy?'Đang gửi…':'Gửi yêu cầu cho kế toán'}</button></div>
        </form>
      </section>
    </div>}

    {role==='BRANCH_MANAGER'&&<>
      {!user.readOnly&&<section className="finance-panel finance-workspace-panel finance-request-panel"><div className="finance-section-heading"><div><h2>Tạo đề nghị chi</h2></div><div className="finance-mini-total"><span>Đề nghị đang mở</span><strong>{openRequests.length}</strong></div></div><div className="finance-switch"><button type="button" className={requestKind==='PAYMENT'?'active':''} onClick={()=>chooseRequestKind('PAYMENT')}>Đề nghị thanh toán</button><button type="button" className={requestKind==='REIMBURSEMENT'?'active':''} onClick={()=>chooseRequestKind('REIMBURSEMENT')}>Hoàn ứng tiền đã chi</button></div>
        <form className="finance-form" onSubmit={createRequest}><label>Loại chi phí *<select required value={request.categoryId} onChange={(event)=>setRequest({...request,categoryId:event.target.value})}><option value="">Chọn hạng mục</option>{categories.filter((item)=>requestKind!=='REIMBURSEMENT'||item.reimbursement_allowed).map((item)=><option value={item.id} key={item.id}>{item.name}</option>)}</select></label><label>Số tiền *<input required type="number" min="1" step="1" value={request.amount} onChange={(event)=>setRequest({...request,amount:event.target.value})}/></label>
          {selectedRequestCategory?.recognition_mode==='ALLOCATED'&&<p className="finance-category-hint wide">Sẽ phân bổ {selectedRequestCategory.default_allocation_months} tháng; kế toán xác nhận số tháng và tháng bắt đầu khi thanh toán.</p>}
          <label>Ngày chi/dự kiến chi *<input required type="date" value={request.expenseOn} onChange={(event)=>setRequest({...request,expenseOn:event.target.value})}/></label><label>Người nhận tiền *<input required maxLength="160" value={request.beneficiaryName} onChange={(event)=>setRequest({...request,beneficiaryName:event.target.value})}/></label><label>Số tài khoản *<input required maxLength="80" value={request.bankAccount} onChange={(event)=>setRequest({...request,bankAccount:event.target.value})}/></label><label>Ngân hàng *<input required maxLength="120" value={request.bankName} onChange={(event)=>setRequest({...request,bankName:event.target.value})}/></label><label className="wide">Nội dung/hạng mục mua *<textarea required maxLength="1000" rows="3" value={request.description} onChange={(event)=>setRequest({...request,description:event.target.value})}/></label>
          <label>Hóa đơn/ảnh chứng minh<input required={Boolean(selectedRequestCategory?.invoice_required)} type="file" accept={fileAccept} onChange={(event)=>setRequest({...request,invoice:event.target.files?.[0]||null})}/><small>{selectedRequestCategory?.invoice_required?'Hạng mục này bắt buộc hóa đơn.':'Có thể bổ sung sau'} · tối đa 20 MB.</small></label>{requestKind==='PAYMENT'&&<label>Chứng từ chuyển khoản/báo giá<input type="file" accept={fileAccept} onChange={(event)=>setRequest({...request,transferProof:event.target.files?.[0]||null})}/><small>Có thể bổ sung sau · tối đa 20 MB.</small></label>}
          <button disabled={busy}>Gửi {requestKind==='PAYMENT'?'đề nghị thanh toán':'đề nghị hoàn ứng'}</button>
        </form></section>}
      <section className="finance-panel finance-workspace-panel"><div className="finance-section-heading"><div><h2>Lịch sử đề nghị và khoản chi</h2></div><div className="finance-mini-total"><span>Tổng đề nghị</span><strong>{requests.length}</strong></div><div className="finance-mini-total pending"><span>Đang xử lý</span><strong>{openRequests.length}</strong></div><div className="finance-mini-total"><span>Đã đóng</span><strong>{closedRequests.length}</strong></div></div><RequestTable rows={requests} role={role} busy={busy} onReview={review} onPay={pay} paymentForms={paymentForms} setPaymentForms={setPaymentForms} requestFiles={requestFiles} setRequestFiles={setRequestFiles} onUploadRequestFile={uploadRequestDoc}/></section>
    </>}
    {reviewDialog&&<div className="finance-confirm-backdrop" role="presentation" onMouseDown={(event)=>{if(event.target===event.currentTarget&&!busy)setReviewDialog(null);}}>
      <section className="finance-confirm-dialog" role="dialog" aria-modal="true" aria-labelledby="finance-confirm-title">
        <div className="finance-confirm-icon" aria-hidden="true">{reviewDialog.approve?'✓':'!'}</div>
        <h2 id="finance-confirm-title">{reviewDialog.approve?'Xác nhận duyệt đề nghị?':'Xác nhận từ chối đề nghị?'}</h2>
        <p>{reviewDialog.approve?'Đề nghị sẽ được chuyển sang bước thanh toán.':'Đề nghị sẽ bị từ chối và lưu vào lịch sử xử lý.'}</p>
        <form onSubmit={confirmReview}>
          <label>{reviewDialog.approve?'Ghi chú duyệt (không bắt buộc)':'Lý do từ chối *'}<textarea rows="3" required={!reviewDialog.approve} maxLength="1000" value={reviewDialog.note} onChange={(event)=>setReviewDialog({...reviewDialog,note:event.target.value})}/></label>
          <div className="finance-confirm-actions"><button type="button" disabled={busy} onClick={()=>setReviewDialog(null)}>Hủy</button><button type="submit" className={reviewDialog.approve?'confirm-approve':'confirm-reject'} disabled={busy}>{busy?'Đang lưu…':reviewDialog.approve?'Xác nhận duyệt':'Xác nhận từ chối'}</button></div>
        </form>
      </section>
    </div>}
  </main>;
}

export default function FinancePage({ user: initialUser }) {
  const [user,setUser]=useState(initialUser||null);const [error,setError]=useState('');const navigate=useNavigate();
  useEffect(()=>{if(initialUser)return;let alive=true;authApi.me().then((identity)=>{if(!alive)return;if(identity.role!=='ACCOUNTANT'){navigate(identity.destination,{replace:true});return;}setUser(identity);}).catch((e)=>{if(alive)setError(message(e));});return()=>{alive=false;};},[initialUser,navigate]);
  if(error)return <p className="finance-error" role="alert">{error}</p>;
  if(!user)return <p className="finance-empty">Đang kiểm tra phiên đăng nhập…</p>;
  return <FinanceWorkspace user={user}/>;

}
