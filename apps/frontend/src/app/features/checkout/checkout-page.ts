import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';

import { formatMoney } from '../../core/money/format-money';
import { CartStore } from '../cart/cart-store';

/**
 * Checkout (HU2, HU4): productos del carrito con su subtotal y el cupón. Solo se llega con
 * productos en el carrito (`cartNotEmptyGuard`).
 * _(pendiente: desglose de descuentos con POST /checkouts/preview, alerta del tope y confirmación)_
 */
@Component({
  selector: 'app-checkout-page',
  imports: [RouterLink],
  templateUrl: './checkout-page.html',
  styleUrl: './checkout-page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CheckoutPage {
  protected readonly cart = inject(CartStore);
  protected readonly formatMoney = formatMoney;

  protected onCouponInput(event: Event): void {
    this.cart.setCouponCode((event.target as HTMLInputElement).value);
  }
}
