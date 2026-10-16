import { ApiProblem } from '../../core/api/problem';

/** Errores que se muestran junto al campo del cupón en lugar de una alerta general. */
export function isCouponError(code: ApiProblem['code']): boolean {
  return code === 'INVALID_COUPON' || code === 'COUPON_EXPIRED';
}

/**
 * Mensaje para el usuario según el `code` estable del backend, nunca según su `detail`.
 * @param productName nombre de un producto del carrito, si sigue en él
 */
export function errorMessage(problem: ApiProblem, productName: (id: number) => string | undefined): string {
  const name = (problem.productId !== undefined && productName(problem.productId)) || 'un producto';
  switch (problem.code) {
    case 'INVALID_COUPON':
      return 'El cupón no existe. Revisa el código.';
    case 'COUPON_EXPIRED':
      return 'El cupón ya venció.';
    case 'INSUFFICIENT_STOCK':
      return `No hay stock suficiente de ${name}: pediste ${problem.requested ?? '?'} y quedan ${problem.available ?? '?'}. Vuelve a productos para ajustar la cantidad.`;
    case 'PRODUCT_NOT_FOUND':
      return 'Un producto de tu carrito ya no está disponible. Vuelve a productos para actualizar tu selección.';
    case 'EMPTY_CART':
      return 'Tu carrito está vacío. Vuelve a productos para agregar alguno.';
    case 'VALIDATION_ERROR':
    case 'DATA_INTEGRITY_VIOLATION':
      return 'Hay datos inválidos en tu carrito. Vuelve a productos y revisa tu selección.';
    case 'DATABASE_UNAVAILABLE':
      return 'El servicio no está disponible en este momento. Intenta de nuevo en unos segundos.';
    case 'NETWORK_ERROR':
      return 'No pudimos conectarnos con la tienda. Revisa tu conexión e intenta de nuevo.';
    case 'PERSISTENCE_ERROR':
    case 'UNKNOWN_ERROR':
      return 'No pudimos calcular los descuentos. Intenta de nuevo.';
  }
}
