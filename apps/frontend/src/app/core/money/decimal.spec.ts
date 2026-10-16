import { formatRate, roundToCents } from './decimal';

describe('roundToCents', () => {
  it('deja igual un monto que ya tiene dos decimales', () => {
    expect(roundToCents('240.00')).toBe('240.00');
  });

  it('completa con ceros un monto entero o con un decimal', () => {
    expect(roundToCents('240')).toBe('240.00');
    expect(roundToCents('8.5')).toBe('8.50');
  });

  it('quita los ceros sobrantes de un monto intermedio del backend', () => {
    expect(roundToCents('240.0000')).toBe('240.00');
  });

  it('redondea HALF_EVEN: la mitad va al par más cercano', () => {
    expect(roundToCents('0.045')).toBe('0.04');
    expect(roundToCents('0.055')).toBe('0.06');
    expect(roundToCents('0.0451')).toBe('0.05');
    expect(roundToCents('2.675')).toBe('2.68');
  });

  it('propaga el acarreo al redondear hacia arriba', () => {
    expect(roundToCents('9.999')).toBe('10.00');
    expect(roundToCents('0.995')).toBe('1.00');
  });

  it('rechaza un valor que no es un decimal no negativo', () => {
    expect(() => roundToCents('-1.00')).toThrow('Decimal inválido: -1.00');
    expect(() => roundToCents('1e3')).toThrow();
  });
});

describe('formatRate', () => {
  it('convierte una tasa en porcentaje sin decimales sobrantes', () => {
    expect(formatRate('0.10')).toBe('10%');
    expect(formatRate('0.35')).toBe('35%');
    expect(formatRate('1')).toBe('100%');
  });

  it('conserva los decimales significativos del porcentaje', () => {
    expect(formatRate('0.055')).toBe('5.5%');
    expect(formatRate('0.27325')).toBe('27.325%');
  });

  it('muestra el valor original si no es una tasa válida', () => {
    expect(formatRate('abc')).toBe('abc');
  });
});
