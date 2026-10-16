import { AppliedDiscount, CheckoutBreakdown } from '../../core/models/checkout';
import { centsToAmount, toCents } from '../../core/money/cents';
import { formatRate, roundToCents } from '../../core/money/decimal';
import { formatMoney } from '../../core/money/format-money';

export interface BreakdownRow {
  readonly label: string;
  readonly value: string;
  readonly kind: 'subtotal' | 'discount' | 'adjustment' | 'discount-total' | 'total';
}

/** Nombre legible de un paso de la cascada de descuentos. */
export function describeDiscount(discount: AppliedDiscount): string {
  const rate = formatRate(discount.rate);
  switch (discount.rule) {
    case 'CATEGORY':
      return `Categoría ${discount.reference ?? ''} (${rate})`;
    case 'VOLUME':
      return `Volumen: compra mayor que ${formatMoney(discount.reference ?? '')} (${rate})`;
    case 'COUPON':
      return `Cupón ${discount.reference ?? ''} (${rate})`;
    case 'CAP':
      return `Ajuste por tope máximo del ${rate}`;
  }
}

/**
 * Filas del desglose. Cada paso se muestra redondeado HALF_EVEN, y el ajuste del tope se
 * calcula como la suma de esos pasos menos el descuento total del backend, para que el
 * desglose cuadre a la vista.
 */
export function breakdownRows(breakdown: CheckoutBreakdown): BreakdownRow[] {
  const rows: BreakdownRow[] = [{ label: 'Subtotal', value: formatMoney(breakdown.subtotal), kind: 'subtotal' }];
  let roundedSum = 0n;
  for (const discount of breakdown.discounts) {
    if (discount.rule === 'CAP') {
      const adjustment = centsToAmount(roundedSum - toCents(breakdown.discountTotal));
      rows.push({ label: describeDiscount(discount), value: `+${formatMoney(adjustment)}`, kind: 'adjustment' });
    } else {
      const amount = roundToCents(discount.amount);
      roundedSum += toCents(amount);
      rows.push({ label: describeDiscount(discount), value: `−${formatMoney(amount)}`, kind: 'discount' });
    }
  }
  rows.push(
    { label: 'Descuento total', value: `−${formatMoney(breakdown.discountTotal)}`, kind: 'discount-total' },
    { label: 'Total a pagar', value: formatMoney(breakdown.total), kind: 'total' },
  );
  return rows;
}
