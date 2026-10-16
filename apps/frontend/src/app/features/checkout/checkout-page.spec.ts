import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { CheckoutPage } from './checkout-page';
import { CartStore } from '../cart/cart-store';

describe('CheckoutPage', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [CheckoutPage], providers: [provideRouter([])] });
  });

  async function render(): Promise<HTMLElement> {
    const fixture = TestBed.createComponent(CheckoutPage);
    await fixture.whenStable();
    return fixture.nativeElement as HTMLElement;
  }

  it('muestra cuántas unidades llegaron desde la selección de productos', async () => {
    TestBed.inject(CartStore).setQuantity(
      { id: 1, name: 'Laptop', unitPrice: '1200.00', category: 'Tecnología', availableStock: 5 },
      3,
    );

    const page = await render();

    expect(page.textContent).toContain('3 unidades de 1 producto');
  });

  it('ofrece volver a la selección de productos', async () => {
    const page = await render();

    expect(page.querySelector('a[href="/"]')?.textContent).toContain('Volver a productos');
  });
});
