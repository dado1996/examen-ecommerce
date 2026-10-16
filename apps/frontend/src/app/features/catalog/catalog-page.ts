import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Router } from '@angular/router';

import { CatalogApi } from '../../core/api/catalog-api';
import { Product } from '../../core/models/product';
import { formatMoney } from '../../core/money/format-money';
import { CartStore } from '../cart/cart-store';

type LoadState = 'loading' | 'ready' | 'error';

/** Selección de productos (HU1): lista el catálogo y permite elegir la cantidad de cada uno. */
@Component({
  selector: 'app-catalog-page',
  templateUrl: './catalog-page.html',
  styleUrl: './catalog-page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CatalogPage {
  private readonly api = inject(CatalogApi);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  protected readonly cart = inject(CartStore);

  protected readonly state = signal<LoadState>('loading');
  protected readonly products = signal<readonly Product[]>([]);
  protected readonly formatMoney = formatMoney;

  constructor() {
    this.load();
  }

  protected load(): void {
    this.state.set('loading');
    this.api
      .listProducts()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (products) => {
          this.cart.reconcile(products);
          this.products.set(products);
          this.state.set('ready');
        },
        error: () => this.state.set('error'),
      });
  }

  protected onQuantityChange(product: Product, event: Event): void {
    const input = event.target as HTMLInputElement;
    this.cart.setQuantity(product, input.valueAsNumber);
    // Si el valor recortado coincide con el anterior, el binding no cambia: se corrige a mano
    input.value = String(this.cart.quantityOf(product.id));
  }

  protected goToCheckout(): void {
    if (!this.cart.isEmpty()) {
      void this.router.navigateByUrl('/checkout');
    }
  }
}
