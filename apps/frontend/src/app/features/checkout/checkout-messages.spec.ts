import { describeDiscount, errorMessage, isCouponError } from './checkout-messages';
import { AppliedDiscount } from '../../core/models/checkout';

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

describe('errorMessage', () => {
  it('explica un cupón no registrado o vencido', () => {
    expect(errorMessage({ code: 'INVALID_COUPON' }, () => undefined)).toBe('El cupón no existe. Revisa el código.');
    expect(errorMessage({ code: 'COUPON_EXPIRED' }, () => undefined)).toBe('El cupón ya venció.');
  });

  it('nombra el producto sin stock suficiente y cuántas unidades quedan', () => {
    const nombre = (id: number): string | undefined => (id === 6 ? 'Silla ergonómica' : undefined);

    expect(errorMessage({ code: 'INSUFFICIENT_STOCK', productId: 6, requested: 2, available: 1 }, nombre)).toBe(
      'No hay stock suficiente de Silla ergonómica: pediste 2 y quedan 1. Vuelve a productos para ajustar la cantidad.',
    );
  });

  it('usa un nombre genérico si el producto ya no está en el carrito', () => {
    expect(errorMessage({ code: 'PRODUCT_NOT_FOUND', productId: 9 }, () => undefined)).toBe(
      'Un producto de tu carrito ya no está disponible. Vuelve a productos para actualizar tu selección.',
    );
  });

  it('distingue un servicio caído de un error de red y de uno desconocido', () => {
    expect(errorMessage({ code: 'DATABASE_UNAVAILABLE' }, () => undefined)).toContain('Intenta de nuevo');
    expect(errorMessage({ code: 'NETWORK_ERROR' }, () => undefined)).toContain('conexión');
    expect(errorMessage({ code: 'UNKNOWN_ERROR' }, () => undefined)).toContain('No pudimos calcular');
  });

  it('tiene un mensaje para cada code del contrato', () => {
    const codes = [
      'VALIDATION_ERROR',
      'EMPTY_CART',
      'PRODUCT_NOT_FOUND',
      'INVALID_COUPON',
      'COUPON_EXPIRED',
      'INSUFFICIENT_STOCK',
      'DATA_INTEGRITY_VIOLATION',
      'DATABASE_UNAVAILABLE',
      'PERSISTENCE_ERROR',
    ] as const;

    for (const code of codes) {
      expect(errorMessage({ code }, () => undefined).length).toBeGreaterThan(10);
    }
  });
});

describe('isCouponError', () => {
  it('solo los errores del cupón se muestran junto al campo', () => {
    expect(isCouponError('INVALID_COUPON')).toBe(true);
    expect(isCouponError('COUPON_EXPIRED')).toBe(true);
    expect(isCouponError('INSUFFICIENT_STOCK')).toBe(false);
  });
});
