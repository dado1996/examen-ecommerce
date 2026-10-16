import { ChangeDetectionStrategy, Component, computed, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Router, RouterLink } from '@angular/router';
import { finalize } from 'rxjs';

import { errorMessage, isCouponError } from './checkout-messages';
import { CheckoutApi } from '../../core/api/checkout-api';
import { ApiProblem, readProblem } from '../../core/api/problem';
import { CheckoutBreakdown, CheckoutRequest } from '../../core/models/checkout';
import { formatRate } from '../../core/money/decimal';
import { formatMoney } from '../../core/money/format-money';
import { BreakdownPanel } from '../../shared/ui/breakdown-panel';
import { CartStore } from '../cart/cart-store';
import { ConfirmationStore } from '../confirmation/confirmation-store';

type PreviewState =
  | { readonly status: 'idle' }
  | { readonly status: 'loading' }
  | { readonly status: 'applied'; readonly breakdown: CheckoutBreakdown }
  | { readonly status: 'error'; readonly problem: ApiProblem };

/**
 * Checkout (HU2, HU3, HU4): productos del carrito, cupón, desglose de descuentos y pago.
 * - "Aplicar descuento" pide a `POST /checkouts/preview` que valide el cupón contra la
 *   configuración y calcule la cascada completa, sin guardar ni reservar.
 * - "Ir a pagar" confirma con `POST /checkouts`, que vuelve a validar todo, reserva el stock y
 *   guarda la orden. Con éxito vacía el carrito y lleva a la confirmación.
 * Solo se llega con productos en el carrito (`cartNotEmptyGuard`).
 */
@Component({
  selector: 'app-checkout-page',
  imports: [RouterLink, BreakdownPanel],
  templateUrl: './checkout-page.html',
  styleUrl: './checkout-page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CheckoutPage {
  private readonly api = inject(CheckoutApi);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  private readonly confirmation = inject(ConfirmationStore);
  protected readonly cart = inject(CartStore);
  protected readonly formatMoney = formatMoney;

  protected readonly preview = signal<PreviewState>({ status: 'idle' });
  protected readonly paying = signal(false);
  private readonly paymentProblem = signal<ApiProblem | null>(null);

  protected readonly loading = computed(() => this.preview().status === 'loading');
  /** Hay una petición en curso: ningún botón puede disparar otra (evita una compra doble). */
  protected readonly busy = computed(() => this.loading() || this.paying());
  protected readonly breakdown = computed(() => {
    const state = this.preview();
    return state.status === 'applied' ? state.breakdown : null;
  });
  private readonly problem = computed(() => {
    const state = this.preview();
    return this.paymentProblem() ?? (state.status === 'error' ? state.problem : null);
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

  protected onCouponInput(event: Event): void {
    this.cart.setCouponCode((event.target as HTMLInputElement).value);
    // El desglose o el error anteriores ya no corresponden al cupón escrito
    this.paymentProblem.set(null);
    if (!this.loading()) {
      this.preview.set({ status: 'idle' });
    }
  }

  protected applyDiscount(event: Event): void {
    event.preventDefault();
    if (this.busy()) {
      return;
    }
    this.paymentProblem.set(null);
    this.preview.set({ status: 'loading' });
    this.api
      .preview(this.request())
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (breakdown) => this.preview.set({ status: 'applied', breakdown }),
        error: (error: unknown) => this.preview.set({ status: 'error', problem: readProblem(error) }),
      });
  }

  protected pay(): void {
    if (this.busy()) {
      return;
    }
    this.paymentProblem.set(null);
    this.paying.set(true);
    this.api
      .create(this.request())
      .pipe(
        finalize(() => this.paying.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (checkout) => {
          this.confirmation.set(checkout);
          this.cart.clear();
          void this.router.navigateByUrl('/confirmacion');
        },
        error: (error: unknown) => this.paymentProblem.set(readProblem(error)),
      });
  }

  /** Lo único que se envía: productos, cantidades y cupón. El backend pone los precios. */
  private request(): CheckoutRequest {
    return { items: this.cart.lines(), couponCode: this.cart.normalizedCoupon() };
  }

  private productName(productId: number): string | undefined {
    return this.cart.items().find((item) => item.product.id === productId)?.product.name;
  }
}
