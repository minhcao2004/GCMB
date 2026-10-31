import { useMemo } from 'react';

const SIZE = 37;
const DATA_CODEWORDS = 108;
const ECC_CODEWORDS = 26;

function multiply(a, b) {
  let result = 0;
  for (let i = 7; i >= 0; i -= 1) {
    result = (result << 1) ^ (((result >>> 7) & 1) * 0x11d);
    if (((b >>> i) & 1) !== 0) result ^= a;
  }
  return result;
}

function generatorPolynomial(degree) {
  let polynomial = [1];
  let root = 1;
  for (let i = 0; i < degree; i += 1) {
    const next = Array(polynomial.length + 1).fill(0);
    for (let index = 0; index < polynomial.length; index += 1) {
      const coefficient = polynomial[index];
      next[index] ^= coefficient;
      next[index + 1] ^= multiply(coefficient, root);
    }
    polynomial = next;
    root = multiply(root, 2);
  }
  return polynomial;
}

function errorCorrection(data) {
  const polynomial = generatorPolynomial(ECC_CODEWORDS);
  const remainder = [...data, ...Array(ECC_CODEWORDS).fill(0)];
  for (let i = 0; i < data.length; i += 1) {
    const factor = remainder[i];
    if (factor === 0) continue;
    for (let j = 0; j < polynomial.length; j += 1) remainder[i + j] ^= multiply(polynomial[j], factor);
  }
  return remainder.slice(data.length);
}

function bit(value, count, target) {
  for (let index = count - 1; index >= 0; index -= 1) target.push((value >>> index) & 1);
}

function formatBits(mask) {
  const value = (1 << 3) | mask;
  let remainder = value << 10;
  for (let index = 14; index >= 10; index -= 1) {
    if (((remainder >>> index) & 1) !== 0) remainder ^= 0x537 << (index - 10);
  }
  return ((value << 10) | remainder) ^ 0x5412;
}

function encodeMatrix(value) {
  const bytes = Array.from(new TextEncoder().encode(value));
  if (bytes.length > 106) throw new Error('Đường dẫn phòng quá dài để tạo mã QR.');

  const bits = [];
  bit(0b0100, 4, bits);
  bit(bytes.length, 8, bits);
  bytes.forEach((byte) => bit(byte, 8, bits));
  bit(0, Math.min(4, DATA_CODEWORDS * 8 - bits.length), bits);
  while (bits.length % 8 !== 0) bits.push(0);
  const data = [];
  for (let index = 0; index < bits.length; index += 8) {
    data.push(bits.slice(index, index + 8).reduce((valueSoFar, next) => (valueSoFar << 1) | next, 0));
  }
  for (let pad = 0; data.length < DATA_CODEWORDS; pad += 1) data.push(pad % 2 === 0 ? 0xec : 0x11);
  const codewords = [...data, ...errorCorrection(data)];
  const matrix = Array.from({ length: SIZE }, () => Array(SIZE).fill(null));

  const finder = (top, left) => {
    for (let dy = -1; dy <= 7; dy += 1) {
      for (let dx = -1; dx <= 7; dx += 1) {
        const row = top + dy;
        const col = left + dx;
        if (row < 0 || row >= SIZE || col < 0 || col >= SIZE) continue;
        const inside = dy >= 0 && dy <= 6 && dx >= 0 && dx <= 6;
        matrix[row][col] = inside && (dy === 0 || dy === 6 || dx === 0 || dx === 6 || (dy >= 2 && dy <= 4 && dx >= 2 && dx <= 4));
      }
    }
  };
  finder(0, 0);
  finder(0, SIZE - 7);
  finder(SIZE - 7, 0);

  for (let dy = -2; dy <= 2; dy += 1) {
    for (let dx = -2; dx <= 2; dx += 1) {
      matrix[30 + dy][30 + dx] = Math.max(Math.abs(dx), Math.abs(dy)) !== 1;
    }
  }
  for (let index = 8; index < SIZE - 8; index += 1) {
    if (matrix[6][index] === null) matrix[6][index] = index % 2 === 0;
    if (matrix[index][6] === null) matrix[index][6] = index % 2 === 0;
  }

  const format = formatBits(0);
  for (let index = 0; index <= 5; index += 1) matrix[index][8] = ((format >>> index) & 1) !== 0;
  matrix[7][8] = ((format >>> 6) & 1) !== 0;
  matrix[8][8] = ((format >>> 7) & 1) !== 0;
  matrix[8][7] = ((format >>> 8) & 1) !== 0;
  for (let index = 9; index < 15; index += 1) matrix[8][14 - index] = ((format >>> index) & 1) !== 0;
  for (let index = 0; index < 8; index += 1) matrix[8][SIZE - 1 - index] = ((format >>> index) & 1) !== 0;
  for (let index = 8; index < 15; index += 1) matrix[SIZE - 15 + index][8] = ((format >>> index) & 1) !== 0;
  matrix[SIZE - 8][8] = true;

  let bitIndex = 0;
  let upward = true;
  for (let right = SIZE - 1; right >= 1; right -= 2) {
    if (right === 6) right -= 1;
    for (let vertical = 0; vertical < SIZE; vertical += 1) {
      const row = upward ? SIZE - 1 - vertical : vertical;
      for (let offset = 0; offset < 2; offset += 1) {
        const col = right - offset;
        if (matrix[row][col] !== null) continue;
        const rawBit = bitIndex < codewords.length * 8
          ? (codewords[Math.floor(bitIndex / 8)] >>> (7 - (bitIndex % 8))) & 1
          : 0;
        matrix[row][col] = (rawBit === 1) !== ((row + col) % 2 === 0);
        bitIndex += 1;
      }
    }
    upward = !upward;
  }
  return matrix;
}

export default function QrCodeSvg({ value, title = 'Mã QR' }) {
  const matrix = useMemo(() => {
    try { return encodeMatrix(value); } catch { return null; }
  }, [value]);
  if (!matrix) return <span className="mr-qr-error">Không thể tạo mã QR cho đường dẫn này.</span>;
  const modules = [];
  matrix.forEach((row, y) => row.forEach((dark, x) => { if (dark) modules.push(`M${x + 4},${y + 4}h1v1h-1z`); }));
  return <svg className="mr-qr-code" viewBox="0 0 45 45" role="img" aria-label={title} shapeRendering="crispEdges">
    <rect width="45" height="45" fill="#fff" />
    <path d={modules.join('')} fill="#131416" />
  </svg>;
}
