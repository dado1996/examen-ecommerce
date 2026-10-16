import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

import { breakdownRows } from './breakdown-rows';
import { CheckoutBreakdown } from '../../core/models/checkout';

let nextId = 0;

/** Panel con el desglose de descuentos y totales de un checkout, calculado por el backend. */
@Component({
  selector: 'app-breakdown-panel',
  template: `
    <section class="breakdown-panel" [attr.aria-labelledby]="titleId">
      <h3 [id]="titleId">{{ heading() }}</h3>
      @if (breakdown().discounts.length === 0) {
        <p class="breakdown-panel__empty">Ningún descuento aplica a este carrito.</p>
      }
      <dl class="breakdown">
        @for (row of rows(); track row.label) {
          <div [class]="'breakdown__row breakdown__row--' + row.kind">
            <dt>{{ row.label }}</dt>
            <dd>{{ row.value }}</dd>
          </div>
        }
      </dl>
    </section>
  `,
  styleUrl: './breakdown-panel.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class BreakdownPanel {
  readonly breakdown = input.required<CheckoutBreakdown>();
  readonly heading = input('Desglose de descuentos');

  protected readonly titleId = `breakdown-title-${nextId++}`;
  protected readonly rows = computed(() => breakdownRows(this.breakdown()));
}
