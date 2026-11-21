import { useId } from 'react';

// Suggestions are editable because a beneficiary may use a bank absent from this local list.
const banks = [
  'ABBank', 'ACB', 'Agribank', 'Bac A Bank', 'BaoVietBank', 'BIDV', 'BVBank',
  'Cake by VPBank', 'CIMB Vietnam', 'Co-opBank', 'Eximbank', 'GPBank', 'HDBank',
  'Hong Leong Bank', 'HSBC', 'IBK', 'Indovina Bank', 'KBank', 'KB Kookmin Bank',
  'KEB Hana Bank', 'KienlongBank', 'LPBank',
  'MB Bank', 'MBV', 'MSB', 'Nam A Bank', 'NCB', 'OCB', 'PGBank', 'Public Bank Vietnam',
  'PVcomBank', 'Sacombank', 'Saigonbank', 'SCB', 'SeABank', 'SHB', 'Shinhan Bank',
  'Sinopac Bank', 'Standard Chartered', 'Techcombank', 'TPBank', 'UOB Vietnam',
  'VCBNeo', 'VIB', 'Viet A Bank', 'VietBank', 'Vietcombank', 'VietinBank',
  'Vikki Bank', 'VPBank', 'VRB', 'Woori Bank',
];

export default function BankNameInput(props) {
  const id = useId();
  return <><input {...props} list={id} /><datalist id={id}>{banks.map((name) =>
    <option key={name} value={name} />)}</datalist></>;
}
