import { computed, Injectable, signal } from '@angular/core';

import { centsToAmount, lineCents } from '../../core/money/cents';
import { Product } from '../../core/models/product';

/** Una línea del carrito: lo único que el checkout envía al backend junto con el cupón. */
export interface CartLine {
  readonly productId: number;
  readonly quantity: number;
}

interface Selection {
  readonly product: Product;
  readonly quantity: number;
}

/** Producto elegido con su cantidad y el total de la línea antes de descuentos ("269.70"). */
export interface CartItem extends Selection {
  readonly lineTotal: string;
}

/** Largo máximo de `couponCode` en el contrato (CheckoutRequest). */
const COUPON_MAX_LENGTH = 50;

/**
 * Estado del carrito con Signals. Guarda la cantidad elegida de cada producto y el producto
 * mismo para mostrar el subtotal. Los descuentos y el total siempre los calcula el backend.
 */
@Injectable({ providedIn: 'root' })
export class CartStore {
  private readonly selections = signal<ReadonlyMap<number, Selection>>(new Map());
  private readonly coupon = signal('');

  /** Cupón tal como lo escribió el usuario. */
  readonly couponCode = this.coupon.asReadonly();
  /** Cupón listo para enviar: sin espacios al inicio ni al final, o `null` si está en blanco. */
  readonly normalizedCoupon = computed(() => this.coupon().trim() || null);

  readonly items = computed<readonly CartItem[]>(() =>
    [...this.selections().values()].map(({ product, quantity }) => ({
      product,
      quantity,
      lineTotal: centsToAmount(lineCents(product.unitPrice, quantity)),
    })),
  );
  readonly lines = computed<readonly CartLine[]>(() =>
    [...this.selections().values()].map(({ product, quantity }) => ({ productId: product.id, quantity })),
  );
  readonly totalUnits = computed(() => this.lines().reduce((sum, line) => sum + line.quantity, 0));
  readonly isEmpty = computed(() => this.selections().size === 0);
  /** Suma de precio × cantidad antes de descuentos, exacta en centavos ("2520.00"). */
  readonly subtotal = computed(() =>
    centsToAmount(
      [...this.selections().values()].reduce(
        (sum, { product, quantity }) => sum + lineCents(product.unitPrice, quantity),
        0n,
      ),
    ),
  );
  /** Resumen legible de la selección, pensado para anunciarse en una región aria-live. */
  readonly summary = computed(() => {
    if (this.isEmpty()) {
      return 'Tu carrito está vacío';
    }
    const units = this.totalUnits();
    const products = this.selections().size;
    return `${units} ${units === 1 ? 'unidad' : 'unidades'} de ${products} ${products === 1 ? 'producto' : 'productos'}`;
  });

  quantityOf(productId: number): number {
    return this.selections().get(productId)?.quantity ?? 0;
  }

  /**
   * Fija la cantidad de un producto entre 0 y su stock disponible. Un valor negativo, no
   * numérico o infinito se trata como 0, y uno decimal se trunca.
   */
  setQuantity(product: Product, quantity: number): void {
    const sanitized = Number.isFinite(quantity) ? Math.trunc(quantity) : 0;
    const clamped = Math.min(Math.max(sanitized, 0), product.availableStock);
    this.selections.update((current) => {
      const next = new Map(current);
      if (clamped === 0) {
        next.delete(product.id);
      } else {
        next.set(product.id, { product, quantity: clamped });
      }
      return next;
    });
  }

  increment(product: Product): void {
    this.setQuantity(product, this.quantityOf(product.id) + 1);
  }

  decrement(product: Product): void {
    this.setQuantity(product, this.quantityOf(product.id) - 1);
  }

  /**
   * Ajusta la selección al catálogo vigente: usa el precio y el stock actuales, recorta la
   * cantidad al stock y quita los productos que ya no están.
   */
  reconcile(products: readonly Product[]): void {
    const byId = new Map(products.map((p) => [p.id, p]));
    this.selections.update((current) => {
      const next = new Map<number, Selection>();
      for (const [productId, { quantity }] of current) {
        const product = byId.get(productId);
        const clamped = product ? Math.min(quantity, product.availableStock) : 0;
        if (product && clamped > 0) {
          next.set(productId, { product, quantity: clamped });
        }
      }
      return next;
    });
  }

  setCouponCode(code: string): void {
    this.coupon.set(code.slice(0, COUPON_MAX_LENGTH));
  }

  clear(): void {
    this.selections.set(new Map());
    this.coupon.set('');
  }
}
