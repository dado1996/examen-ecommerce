import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { of } from 'rxjs';

import { routes } from './app.routes';
import { CatalogApi } from './core/api/catalog-api';
import { CartStore } from './features/cart/cart-store';

describe('routes', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideRouter(routes), { provide: CatalogApi, useValue: { listProducts: () => of([]) } }],
    });
  });

  it('la ruta raíz muestra la selección de productos', async () => {
    const harness = await RouterTestingHarness.create('/');

    expect(harness.routeNativeElement?.querySelector('h2')?.textContent).toContain('Productos');
  });

  it('la ruta /checkout muestra el checkout cuando hay productos en el carrito', async () => {
    TestBed.inject(CartStore).setQuantity(
      { id: 1, name: 'Laptop', unitPrice: '1200.00', category: 'Tecnología', availableStock: 5 },
      1,
    );

    const harness = await RouterTestingHarness.create('/checkout');

    expect(harness.routeNativeElement?.querySelector('h2')?.textContent).toContain('Checkout');
  });

  it('entrar a /checkout con el carrito vacío redirige a la selección de productos', async () => {
    const harness = await RouterTestingHarness.create('/checkout');

    expect(TestBed.inject(Router).url).toBe('/');
    expect(harness.routeNativeElement?.querySelector('h2')?.textContent).toContain('Productos');
  });

  it('una ruta desconocida redirige a la selección de productos', async () => {
    await RouterTestingHarness.create('/no-existe');

    expect(TestBed.inject(Router).url).toBe('/');
  });

  it('cada ruta define un título de página', () => {
    expect(routes.filter((r) => r.path !== '**').every((r) => typeof r.title === 'string')).toBe(true);
  });
});
