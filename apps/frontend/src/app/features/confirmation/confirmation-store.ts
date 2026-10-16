import { Injectable, signal } from '@angular/core';

import { Checkout } from '../../core/models/checkout';

/**
 * Última compra confirmada. Vive en memoria porque el contrato no expone
 * `GET /checkouts/{id}`: al recargar la página, la confirmación ya no está disponible.
 */
@Injectable({ providedIn: 'root' })
export class ConfirmationStore {
  private readonly confirmed = signal<Checkout | null>(null);

  readonly checkout = this.confirmed.asReadonly();

  set(checkout: Checkout): void {
    this.confirmed.set(checkout);
  }

  clear(): void {
    this.confirmed.set(null);
  }
}
