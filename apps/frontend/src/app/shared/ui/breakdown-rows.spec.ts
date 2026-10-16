import { breakdownRows, describeDiscount } from './breakdown-rows';
import { AppliedDiscount, CheckoutBreakdown } from '../../core/models/checkout';

function paso(parcial: Partial<AppliedDiscount> & Pick<AppliedDiscount, 'rule'>): AppliedDiscount {
  return { step: 1, rate: '0.10', reference: null, baseAmount: '100.00', amount: '10.00', ...parcial };
}

describe('describeDiscount', () => {
  it('nombra el descuento de categoría con la categoría y el porcentaje', () => {
    expect(describeDiscount(paso({ rule: 'CATEGORY', rate: '0.10', reference: 'Tecnología' }))).toBe(
      'Categoría Tecnología (10%)',
    );
  });

  it('nombra el descuento por volumen con su umbral', () => {
    expect(describeDiscount(paso({ rule: 'VOLUME', rate: '0.05', reference: '100.00' }))).toBe(
      'Volumen: compra mayor que USD 100.00 (5%)',
    );
  });

  it('nombra el cupón con su código', () => {
    expect(describeDiscount(paso({ rule: 'COUPON', rate: '0.15', reference: 'WELCOME2026' }))).toBe(
      'Cupón WELCOME2026 (15%)',
    );
  });

  it('explica el ajuste del tope máximo', () => {
    expect(describeDiscount(paso({ rule: 'CAP', rate: '0.35' }))).toBe('Ajuste por tope máximo del 35%');
  });
});

const base: CheckoutBreakdown = {
  couponCode: null,
  lines: [],
  discounts: [],
  subtotal: '40.00',
  discountTotal: '0.00',
  total: '40.00',
  maxDiscountRate: '0.35',
  capApplied: false,
};

describe('breakdownRows', () => {
  it('sin descuentos muestra subtotal, descuento en cero y total', () => {
    expect(breakdownRows(base)).toEqual([
      { label: 'Subtotal', value: 'USD 40.00', kind: 'subtotal' },
      { label: 'Descuento total', value: '−USD 0.00', kind: 'discount-total' },
      { label: 'Total a pagar', value: 'USD 40.00', kind: 'total' },
    ]);
  });

  it('muestra cada paso redondeado HALF_EVEN en el orden de la cascada', () => {
    const rows = breakdownRows({
      ...base,
      couponCode: 'WELCOME2026',
      discounts: [
        { step: 1, rule: 'CATEGORY', rate: '0.10', reference: 'Tecnología', baseAmount: '2669.70', amount: '266.9700' },
        { step: 2, rule: 'VOLUME', rate: '0.05', reference: '100.00', baseAmount: '2402.7300', amount: '120.136500' },
        { step: 3, rule: 'COUPON', rate: '0.15', reference: 'WELCOME2026', baseAmount: '2282.593500', amount: '342.38902500' },
      ],
      subtotal: '2669.70',
      discountTotal: '729.50',
      total: '1940.20',
    });

    expect(rows.map((r) => [r.label, r.value])).toEqual([
      ['Subtotal', 'USD 2,669.70'],
      ['Categoría Tecnología (10%)', '−USD 266.97'],
      ['Volumen: compra mayor que USD 100.00 (5%)', '−USD 120.14'],
      ['Cupón WELCOME2026 (15%)', '−USD 342.39'],
      ['Descuento total', '−USD 729.50'],
      ['Total a pagar', 'USD 1,940.20'],
    ]);
  });

  it('el ajuste del tope cuadra el desglose con el descuento total del backend', () => {
    // 40.04 redondeado − ajuste = 35.03: el ajuste es 5.01 aunque 5.005 redondeado daría 5.00
    const rows = breakdownRows({
      ...base,
      discounts: [
        { step: 1, rule: 'CATEGORY', rate: '0.40', reference: 'Tecnología', baseAmount: '100.10', amount: '40.0400' },
        { step: 4, rule: 'CAP', rate: '0.35', reference: null, baseAmount: '40.0400', amount: '5.005000' },
      ],
      subtotal: '100.10',
      discountTotal: '35.03',
      total: '65.07',
      capApplied: true,
    });

    expect(rows).toContainEqual({ label: 'Ajuste por tope máximo del 35%', value: '+USD 5.01', kind: 'adjustment' });
    expect(rows.at(-2)).toEqual({ label: 'Descuento total', value: '−USD 35.03', kind: 'discount-total' });
  });
});
