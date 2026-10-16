const MONEY = /^(\d+)\.(\d{2})$/;

/**
 * Da formato a un monto del contrato ("1200.00" → "USD 1,200.00") operando sobre el texto,
 * sin convertirlo a number: así el valor mostrado es exactamente el que calculó el backend.
 * Si el valor no tiene la forma esperada, se muestra tal cual.
 */
export function formatMoney(amount: string): string {
  const match = MONEY.exec(amount);
  if (!match) {
    return amount;
  }
  const [, integer = '', cents = ''] = match;
  return `USD ${integer.replace(/\B(?=(\d{3})+(?!\d))/g, ',')}.${cents}`;
}
