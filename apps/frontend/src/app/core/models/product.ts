/**
 * Producto del catálogo tal como lo devuelve `GET /products`.
 * Refleja el esquema `Product` de packages/contracts/openapi.yaml.
 * _(pendiente: generarlo del contrato en lugar de escribirlo a mano)_
 */
export interface Product {
  readonly id: number;
  readonly name: string;
  /** USD como string decimal con 2 decimales ("1200.00"). Nunca se convierte a number. */
  readonly unitPrice: string;
  readonly category: string;
  /** Stock vivo del backend; es el máximo que se puede pedir. */
  readonly availableStock: number;
}
