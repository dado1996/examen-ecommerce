import { centsToAmount, lineCents, toCents } from './cents';

describe('cents', () => {
  it('convierte un monto con dos decimales a centavos enteros', () => {
    expect(toCents('1200.00')).toBe(120000n);
    expect(toCents('89.90')).toBe(8990n);
    expect(toCents('0.05')).toBe(5n);
  });

  it('rechaza un monto que no tiene exactamente dos decimales', () => {
    expect(() => toCents('12.5')).toThrow('Monto inválido: 12.5');
    expect(() => toCents('-1.00')).toThrow();
    expect(() => toCents('1e3')).toThrow();
  });

  it('convierte centavos de vuelta al formato del contrato', () => {
    expect(centsToAmount(120000n)).toBe('1200.00');
    expect(centsToAmount(5n)).toBe('0.05');
    expect(centsToAmount(0n)).toBe('0.00');
  });

  it('multiplica el precio por la cantidad sin pérdida', () => {
    expect(lineCents('89.90', 3)).toBe(26970n);
  });

  it('es exacto donde un double fallaría', () => {
    // En double, 0.1 + 0.2 = 0.30000000000000004 y 1.10 × 3 = 3.3000000000000003
    expect(centsToAmount(toCents('0.10') + toCents('0.20'))).toBe('0.30');
    expect(centsToAmount(lineCents('1.10', 3))).toBe('3.30');
  });

  it('conserva montos más grandes que el máximo entero seguro de JavaScript', () => {
    expect(centsToAmount(lineCents('90071992547409.93', 1000))).toBe('90071992547409930.00');
  });
});
