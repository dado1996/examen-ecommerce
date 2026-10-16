import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';

import { API_BASE_URL } from './api-base-url.token';
import { CatalogApi } from './catalog-api';
import { Product } from '../models/product';

describe('CatalogApi', () => {
  let api: CatalogApi;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: API_BASE_URL, useValue: 'http://api.test/api/v1' },
      ],
    });
    api = TestBed.inject(CatalogApi);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('pide los productos en stock a GET /products de la URL base', () => {
    const productos: Product[] = [
      { id: 1, name: 'Laptop', unitPrice: '1200.00', category: 'Tecnología', availableStock: 10 },
    ];
    let recibidos: readonly Product[] | undefined;

    api.listProducts().subscribe((p) => (recibidos = p));
    const request = http.expectOne('http://api.test/api/v1/products');
    request.flush(productos);

    expect(request.request.method).toBe('GET');
    expect(recibidos).toEqual(productos);
  });

  it('mantiene el precio como texto decimal exacto', () => {
    let precio: string | undefined;

    api.listProducts().subscribe((p) => (precio = p[0]?.unitPrice));
    http
      .expectOne('http://api.test/api/v1/products')
      .flush([{ id: 3, name: 'Teclado', unitPrice: '89.90', category: 'Tecnología', availableStock: 1 }]);

    expect(precio).toBe('89.90');
  });
});
