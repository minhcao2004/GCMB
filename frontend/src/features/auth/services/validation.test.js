import { emailError, validate } from './validation';

const valid = { username: 'nhanvien', password: '  mat khau moi  ', confirmation: '  mat khau moi  ', code: '038291', email: 'nhanvien@example.com' };
test('email removes surrounding whitespace, checks format and database length', () => {
  expect(emailError('  user@example.com  ')).toBe('');
  for (const input of ['', '   ', 'abc', 'a@', 'a..b@example.com', '.abc@example.com', 'a'.repeat(255) + '@example.com']) expect(emailError(input)).not.toBe('');
});
test('keeps leading and trailing password whitespace and a leading zero in the code', () => {
  expect(validate('reset', valid)).toEqual({});
  expect(valid.password).toBe('  mat khau moi  ');
  expect(validate('reset', { ...valid, confirmation: valid.password.trim() })).toHaveProperty('confirmation');
});
test.each(['12345', '1234567', ' 12345', '１２３４５６', '12345a', '123\n45'])('rejects non-six-ASCII-digit code %p', (code) => {
  expect(validate('reset', { ...valid, code })).toHaveProperty('code');
});
test('checks required fields and database username length without an extra password policy', () => {
  expect(Object.keys(validate('reset', { password: '  ', confirmation: '', code: '' }))).toHaveLength(3);
  expect(validate('reset', { ...valid, password: 'short', confirmation: 'short' })).toEqual({});
  expect(validate('login', { ...valid, username: 'x'.repeat(101) })).toHaveProperty('username');
  expect(validate('login', { ...valid, password: 'x'.repeat(129) })).toEqual({});
});
