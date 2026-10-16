import { HttpClient, HttpHeaders } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { API_BASE_URL } from './api-base-url.token';
import { Checkout, CheckoutBreakdown, CheckoutRequest } from '../models/checkout';

/** Cliente del checkout. */
@Injectable({ providedIn: 'root' })
export class CheckoutApi {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = inject(API_BASE_URL);

  /**
   * Valida el carrito y el cupón y calcula el desglose de descuentos sin guardar la compra
   * ni reservar stock (`POST /checkouts/preview`).
   */
  preview(request: CheckoutRequest): Observable<CheckoutBreakdown> {
    return this.http.post<CheckoutBreakdown>(`${this.baseUrl}/checkouts/preview`, request);
  }

  /**
   * Confirma la compra (`POST /checkouts`): el backend vuelve a validar todo, reserva el stock,
   * aplica la cascada y guarda la orden en una sola transacción. Devuelve la orden con su desglose.
   * @param idempotencyKey UUID del intento de pago: si llega un reintento con la misma clave y el
   *   mismo pedido, el backend devuelve la orden ya creada en lugar de crear otra.
   */
  create(request: CheckoutRequest, idempotencyKey: string): Observable<Checkout> {
    return this.http.post<Checkout>(`${this.baseUrl}/checkouts`, request, {
      headers: new HttpHeaders({ 'Idempotency-Key': idempotencyKey }),
    });
  }
}
