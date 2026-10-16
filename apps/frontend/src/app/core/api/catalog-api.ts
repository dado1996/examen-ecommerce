import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { API_BASE_URL } from './api-base-url.token';
import { Product } from '../models/product';

/** Cliente del catálogo (`GET /products`). */
@Injectable({ providedIn: 'root' })
export class CatalogApi {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = inject(API_BASE_URL);

  /** Productos activos con stock disponible, ordenados por id. */
  listProducts(): Observable<readonly Product[]> {
    return this.http.get<readonly Product[]>(`${this.baseUrl}/products`);
  }
}
