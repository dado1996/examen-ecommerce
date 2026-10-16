import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';

import { CartStore } from '../cart/cart-store';

/**
 * Checkout (HU2, HU4). Por ahora solo confirma la selección que llega desde el catálogo.
 * _(pendiente: cupón, desglose de descuentos con POST /checkouts/preview, alerta del tope y confirmación)_
 */
@Component({
  selector: 'app-checkout-page',
  imports: [RouterLink],
  template: `
    <section aria-labelledby="checkout-title">
      <h2 id="checkout-title">Checkout</h2>
      <p class="notice">{{ cart.summary() }}</p>
      <p>El desglose de descuentos y la confirmación de la compra se agregan en el siguiente paso.</p>
      <a class="button" routerLink="/">Volver a productos</a>
    </section>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CheckoutPage {
  protected readonly cart = inject(CartStore);
}
