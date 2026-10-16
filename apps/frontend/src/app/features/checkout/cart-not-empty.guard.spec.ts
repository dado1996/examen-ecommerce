import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, provideRouter, RouterStateSnapshot, UrlTree } from '@angular/router';

import { cartNotEmptyGuard } from './cart-not-empty.guard';
import { CartStore } from '../cart/cart-store';

describe('cartNotEmptyGuard', () => {
  function evaluar(): boolean | UrlTree {
    return TestBed.runInInjectionContext(() =>
      cartNotEmptyGuard({} as ActivatedRouteSnapshot, {} as RouterStateSnapshot),
    ) as boolean | UrlTree;
  }

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideRouter([])] });
  });

  it('con el carrito vacío redirige a la selección de productos', () => {
    const resultado = evaluar();

    expect(resultado).toBeInstanceOf(UrlTree);
    expect(String(resultado)).toBe('/');
  });

  it('con productos en el carrito deja entrar al checkout', () => {
    TestBed.inject(CartStore).setQuantity(
      { id: 1, name: 'Laptop', unitPrice: '1200.00', category: 'Tecnología', availableStock: 5 },
      1,
    );

    expect(evaluar()).toBe(true);
  });
});
