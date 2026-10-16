/**
 * Utilidades para los decimales sin redondear del contrato (montos intermedios y tasas),
 * operando sobre el texto con `bigint`, nunca con `number`.
 */
const DECIMAL = /^(\d+)(?:\.(\d+))?$/;

function parse(value: string): { digits: bigint; scale: number } {
  const match = DECIMAL.exec(value);
  if (!match) {
    throw new Error(`Decimal inválido: ${value}`);
  }
  const [, units = '', fraction = ''] = match;
  return { digits: BigInt(units + fraction), scale: fraction.length };
}

/**
 * Redondea a 2 decimales con HALF_EVEN, la misma regla del backend ("120.136500" → "120.14").
 * Solo para mostrar: el total autoritativo siempre lo calcula el backend.
 */
export function roundToCents(value: string): string {
  const { digits, scale } = parse(value);
  let cents: bigint;
  if (scale <= 2) {
    cents = digits * 10n ** BigInt(2 - scale);
  } else {
    const divisor = 10n ** BigInt(scale - 2);
    cents = digits / divisor;
    const remainder = digits % divisor;
    const doubled = remainder * 2n;
    if (doubled > divisor || (doubled === divisor && cents % 2n === 1n)) {
      cents += 1n;
    }
  }
  return `${cents / 100n}.${(cents % 100n).toString().padStart(2, '0')}`;
}

/** Tasa como porcentaje sin ceros sobrantes ("0.10" → "10%", "0.055" → "5.5%"). */
export function formatRate(rate: string): string {
  let parsed: { digits: bigint; scale: number };
  try {
    parsed = parse(rate);
  } catch {
    return rate;
  }
  // × 100: la escala baja en 2 posiciones
  const scale = parsed.scale - 2;
  const text = scale <= 0 ? (parsed.digits * 10n ** BigInt(-scale)).toString() : parsed.digits.toString().padStart(scale + 1, '0');
  if (scale <= 0) {
    return `${text}%`;
  }
  const units = text.slice(0, -scale);
  const fraction = text.slice(-scale).replace(/0+$/, '');
  return `${units}${fraction ? `.${fraction}` : ''}%`;
}
