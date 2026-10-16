import { formatMoney } from './format-money';

describe('formatMoney', () => {
  it('agrega separadores de miles y el prefijo de la moneda', () => {
    expect(formatMoney('1200.00')).toBe('USD 1,200.00');
  });

  it('no agrega separador a montos menores que mil', () => {
    expect(formatMoney('40.00')).toBe('USD 40.00');
  });

  it('separa cada grupo de tres cifras en montos grandes', () => {
    expect(formatMoney('1234567.89')).toBe('USD 1,234,567.89');
  });

  it('conserva el texto exacto sin pasar por punto flotante', () => {
    // 0.1 + 0.2 en double es 0.30000000000000004; aquí no se opera, se formatea el texto
    expect(formatMoney('9007199254740993.10')).toBe('USD 9,007,199,254,740,993.10');
  });

  it('devuelve el valor original si no es un monto con dos decimales', () => {
    expect(formatMoney('12.5')).toBe('12.5');
    expect(formatMoney('abc')).toBe('abc');
  });
});
