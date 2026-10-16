import { errorMessage, isCouponError } from './checkout-messages';

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

  it('si la clave del pago se reutilizó con otro pedido pide revisar el carrito y volver a intentar', () => {
    expect(errorMessage({ code: 'IDEMPOTENCY_KEY_REUSED' }, () => undefined)).toContain('Revisa tu carrito');
  });

  it('tiene un mensaje para cada code del contrato', () => {
    const codes = [
      'VALIDATION_ERROR',
      'EMPTY_CART',
      'PRODUCT_NOT_FOUND',
      'INVALID_COUPON',
      'COUPON_EXPIRED',
      'INSUFFICIENT_STOCK',
      'IDEMPOTENCY_KEY_REUSED',
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
