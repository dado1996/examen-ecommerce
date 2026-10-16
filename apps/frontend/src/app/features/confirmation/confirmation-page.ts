import {
  afterNextRender,
  ChangeDetectionStrategy,
  Component,
  computed,
  ElementRef,
  inject,
  viewChild,
} from '@angular/core';
import { RouterLink } from '@angular/router';

import { ConfirmationStore } from './confirmation-store';
import { formatRate } from '../../core/money/decimal';
import { formatMoney } from '../../core/money/format-money';
import { BreakdownPanel } from '../../shared/ui/breakdown-panel';

const DATE_FORMAT = new Intl.DateTimeFormat('es', { dateStyle: 'long', timeStyle: 'short' });

/**
 * Confirmación de la compra con los totales exactos que guardó el backend (`POST /checkouts`).
 * Solo se llega con una compra recién confirmada (`hasConfirmationGuard`).
 */
@Component({
  selector: 'app-confirmation-page',
  imports: [RouterLink, BreakdownPanel],
  templateUrl: './confirmation-page.html',
  styleUrl: './confirmation-page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ConfirmationPage {
  private readonly title = viewChild.required<ElementRef<HTMLElement>>('title');
  protected readonly checkout = inject(ConfirmationStore).checkout;
  protected readonly formatMoney = formatMoney;

  protected readonly createdAt = computed(() => {
    const checkout = this.checkout();
    return checkout ? DATE_FORMAT.format(new Date(checkout.createdAt)) : '';
  });
  protected readonly maxRate = computed(() => {
    const checkout = this.checkout();
    return checkout ? formatRate(checkout.maxDiscountRate) : '';
  });

  constructor() {
    // Al llegar desde el checkout, el foco va al título para que se anuncie la confirmación
    afterNextRender(() => this.title().nativeElement.focus());
  }
}
