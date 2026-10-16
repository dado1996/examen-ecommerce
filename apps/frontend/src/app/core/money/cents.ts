/**
 * Aritmética exacta de dinero en centavos enteros (`bigint`). Los montos del contrato llegan
 * como string ("89.90") y nunca se convierten a `number`, para no arrastrar errores de punto
 * flotante. Solo se usa para el subtotal antes de descuentos; los descuentos y el total los
 * calcula siempre el backend.
 */
const AMOUNT = /^(\d+)\.(\d{2})$/;

/** "89.90" → 8990n. Lanza un error si el monto no tiene exactamente dos decimales. */
export function toCents(amount: string): bigint {
  const match = AMOUNT.exec(amount);
  if (!match) {
    throw new Error(`Monto inválido: ${amount}`);
  }
  const [, units = '', cents = ''] = match;
  return BigInt(units) * 100n + BigInt(cents);
}

/** 8990n → "89.90", el mismo formato del contrato. */
export function centsToAmount(cents: bigint): string {
  const units = cents / 100n;
  const rest = cents % 100n;
  return `${units}.${rest.toString().padStart(2, '0')}`;
}

/** Precio unitario por cantidad, en centavos. */
export function lineCents(unitPrice: string, quantity: number): bigint {
  return toCents(unitPrice) * BigInt(quantity);
}
