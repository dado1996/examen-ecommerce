import { TestBed } from '@angular/core/testing';

import { ConfirmationStore } from './confirmation-store';
import { Checkout } from '../../core/models/checkout';

const orden: Checkout = {
  id: 7,
  createdAt: '2026-10-15T19:30:00.000Z',
  couponCode: null,
  lines: [],
  discounts: [],
  subtotal: '40.00',
  discountTotal: '0.00',
  total: '40.00',
  maxDiscountRate: '0.35',
  capApplied: false,
};

describe('ConfirmationStore', () => {
  it('empieza sin compra confirmada', () => {
    expect(TestBed.inject(ConfirmationStore).checkout()).toBeNull();
  });

  it('guarda la última compra confirmada y la puede olvidar', () => {
    const store = TestBed.inject(ConfirmationStore);

    store.set(orden);
    expect(store.checkout()).toEqual(orden);

    store.clear();
    expect(store.checkout()).toBeNull();
  });
});
