import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, provideRouter, RouterStateSnapshot, UrlTree } from '@angular/router';

import { ConfirmationStore } from './confirmation-store';
import { hasConfirmationGuard } from './has-confirmation.guard';

describe('hasConfirmationGuard', () => {
  function evaluar(): boolean | UrlTree {
    return TestBed.runInInjectionContext(() =>
      hasConfirmationGuard({} as ActivatedRouteSnapshot, {} as RouterStateSnapshot),
    ) as boolean | UrlTree;
  }

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideRouter([])] });
  });

  it('sin una compra confirmada redirige a la selección de productos', () => {
    expect(String(evaluar())).toBe('/');
  });

  it('con una compra confirmada deja ver la confirmación', () => {
    TestBed.inject(ConfirmationStore).set({
      id: 1,
      createdAt: '2026-10-15T19:30:00.000Z',
      couponCode: null,
      lines: [],
      discounts: [],
      subtotal: '40.00',
      discountTotal: '0.00',
      total: '40.00',
      maxDiscountRate: '0.35',
      capApplied: false,
    });

    expect(evaluar()).toBe(true);
  });
});
