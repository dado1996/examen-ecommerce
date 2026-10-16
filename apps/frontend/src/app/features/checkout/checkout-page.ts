import { ChangeDetectionStrategy, Component, computed, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';

import { describeDiscount, errorMessage, isCouponError } from './checkout-messages';
import { CheckoutApi } from '../../core/api/checkout-api';
import { ApiProblem, readProblem } from '../../core/api/problem';
import { CheckoutBreakdown } from '../../core/models/checkout';
import { centsToAmount, toCents } from '../../core/money/cents';
import { formatRate, roundToCents } from '../../core/money/decimal';
import { formatMoney } from '../../core/money/format-money';
import { CartStore } from '../cart/cart-store';

type PreviewState =
  | { readonly status: 'idle' }
  | { readonly status: 'loading' }
  | { readonly status: 'applied'; readonly breakdown: CheckoutBreakdown }
  | { readonly status: 'error'; readonly problem: ApiProblem };

interface BreakdownRow {
  readonly label: string;
  readonly value: string;
  readonly kind: 'subtotal' | 'discount' | 'adjustment' | 'discount-total' | 'total';
}

/**
 * Checkout (HU2, HU4): productos del carrito, cupón y desglose de descuentos. "Aplicar
 * descuento" pide a `POST /checkouts/preview` que valide el cupón contra la configuración y
 * calcule la cascada completa (categoría, volumen, cupón y tope), sin guardar ni reservar.
 * Solo se llega con productos en el carrito (`cartNotEmptyGuard`).
 * _(pendiente: confirmar la compra con POST /checkouts)_
 */
@Component({
  selector: 'app-checkout-page',
  imports: [RouterLink],
  templateUrl: './checkout-page.html',
  styleUrl: './checkout-page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CheckoutPage {
  private readonly api = inject(CheckoutApi);
  private readonly destroyRef = inject(DestroyRef);
  protected readonly cart = inject(CartStore);
  protected readonly formatMoney = formatMoney;

  protected readonly preview = signal<PreviewState>({ status: 'idle' });

  protected readonly loading = computed(() => this.preview().status === 'loading');
  protected readonly breakdown = computed(() => {
    const state = this.preview();
    return state.status === 'applied' ? state.breakdown : null;
  });
  private readonly problem = computed(() => {
    const state = this.preview();
    return state.status === 'error' ? state.problem : null;
  });
  protected readonly couponError = computed(() => {
    const problem = this.problem();
    return problem && isCouponError(problem.code) ? errorMessage(problem, () => undefined) : null;
  });
  protected readonly generalError = computed(() => {
    const problem = this.problem();
    return problem && !isCouponError(problem.code) ? errorMessage(problem, (id) => this.productName(id)) : null;
  });
  protected readonly maxRate = computed(() => {
    const breakdown = this.breakdown();
    return breakdown ? formatRate(breakdown.maxDiscountRate) : '';
  });
  protected readonly rows = computed(() => {
    const breakdown = this.breakdown();
    return breakdown ? toRows(breakdown) : [];
  });

  protected onCouponInput(event: Event): void {
    this.cart.setCouponCode((event.target as HTMLInputElement).value);
    // El desglose o el error anteriores ya no corresponden al cupón escrito
    if (!this.loading()) {
      this.preview.set({ status: 'idle' });
    }
  }

  protected applyDiscount(event: Event): void {
    event.preventDefault();
    if (this.loading()) {
      return;
    }
    this.preview.set({ status: 'loading' });
    this.api
      .preview({ items: this.cart.lines(), couponCode: this.cart.normalizedCoupon() })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (breakdown) => this.preview.set({ status: 'applied', breakdown }),
        error: (error: unknown) => this.preview.set({ status: 'error', problem: readProblem(error) }),
      });
  }

  private productName(productId: number): string | undefined {
    return this.cart.items().find((item) => item.product.id === productId)?.product.name;
  }
}

/**
 * Filas del desglose. Cada paso se muestra redondeado HALF_EVEN, y el ajuste del tope se
 * calcula como la suma de esos pasos menos el descuento total del backend, para que el
 * desglose cuadre a la vista.
 */
function toRows(breakdown: CheckoutBreakdown): BreakdownRow[] {
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
