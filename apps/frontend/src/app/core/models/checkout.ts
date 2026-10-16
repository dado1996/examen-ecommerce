/**
 * Tipos del checkout según packages/contracts/openapi.yaml (CheckoutRequest, CheckoutBreakdown,
 * AppliedDiscount, Checkout y ProblemCode). _(pendiente: generarlos del contrato en lugar de escribirlos a mano)_
 */

/** Lo único que el cliente envía: productos, cantidades y cupón. Nunca precios. */
export interface CheckoutRequest {
  readonly items: readonly { readonly productId: number; readonly quantity: number }[];
  readonly couponCode: string | null;
}

export type DiscountRule = 'CATEGORY' | 'VOLUME' | 'COUPON' | 'CAP';

/** Un paso de la cascada que tuvo efecto. `baseAmount` y `amount` vienen sin redondear. */
export interface AppliedDiscount {
  readonly step: number;
  readonly rule: DiscountRule;
  readonly rate: string;
  /** Categoría, umbral de volumen o código del cupón; `null` en el tope. */
  readonly reference: string | null;
  readonly baseAmount: string;
  readonly amount: string;
}

export interface CheckoutLine {
  readonly productId: number;
  readonly name: string;
  readonly category: string;
  readonly quantity: number;
  readonly unitPrice: string;
  readonly lineSubtotal: string;
}

/** Desglose calculado por el backend. Los totales vienen redondeados a 2 decimales. */
export interface CheckoutBreakdown {
  readonly couponCode: string | null;
  readonly lines: readonly CheckoutLine[];
  readonly discounts: readonly AppliedDiscount[];
  readonly subtotal: string;
  readonly discountTotal: string;
  readonly total: string;
  readonly maxDiscountRate: string;
  readonly capApplied: boolean;
}

/** Checkout confirmado y guardado (`POST /checkouts`): el desglose más su número y fecha. */
export interface Checkout extends CheckoutBreakdown {
  readonly id: number;
  /** ISO-8601 UTC asignado por el backend. */
  readonly createdAt: string;
}

/** Códigos de negocio estables de Problem Details (RFC 9457). */
export const PROBLEM_CODES = [
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

export type ProblemCode = (typeof PROBLEM_CODES)[number];
