import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';

import { API_BASE_URL } from './api-base-url.token';
import { CheckoutApi } from './checkout-api';
import { CheckoutBreakdown } from '../models/checkout';

describe('CheckoutApi', () => {
  let api: CheckoutApi;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: API_BASE_URL, useValue: 'http://api.test/api/v1' },
      ],
    });
    api = TestBed.inject(CheckoutApi);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('pide el desglose a POST /checkouts/preview con solo productos, cantidades y cupón', () => {
    const desglose: CheckoutBreakdown = {
      couponCode: 'WELCOME2026',
      lines: [],
      discounts: [],
      subtotal: '40.00',
      discountTotal: '0.00',
      total: '40.00',
      maxDiscountRate: '0.35',
      capApplied: false,
    };
    let recibido: CheckoutBreakdown | undefined;

    api.preview({ items: [{ productId: 4, quantity: 1 }], couponCode: 'WELCOME2026' }).subscribe((b) => (recibido = b));
    const request = http.expectOne('http://api.test/api/v1/checkouts/preview');
    request.flush(desglose);

    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ items: [{ productId: 4, quantity: 1 }], couponCode: 'WELCOME2026' });
    expect(recibido).toEqual(desglose);
  });
});
