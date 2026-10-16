import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { of } from 'rxjs';

import { routes } from './app.routes';
import { CatalogApi } from './core/api/catalog-api';

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

  it('la ruta /checkout muestra el checkout', async () => {
    const harness = await RouterTestingHarness.create('/checkout');

    expect(harness.routeNativeElement?.querySelector('h2')?.textContent).toContain('Checkout');
  });

  it('una ruta desconocida redirige a la selección de productos', async () => {
    await RouterTestingHarness.create('/no-existe');

    expect(TestBed.inject(Router).url).toBe('/');
  });

  it('cada ruta define un título de página', () => {
    expect(routes.filter((r) => r.path !== '**').every((r) => typeof r.title === 'string')).toBe(true);
  });
});
