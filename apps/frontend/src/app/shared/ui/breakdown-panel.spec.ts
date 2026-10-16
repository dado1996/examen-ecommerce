import { TestBed } from '@angular/core/testing';

import { BreakdownPanel } from './breakdown-panel';
import { CheckoutBreakdown } from '../../core/models/checkout';

const desglose: CheckoutBreakdown = {
  couponCode: null,
  lines: [],
  discounts: [{ step: 1, rule: 'CATEGORY', rate: '0.10', reference: 'Tecnología', baseAmount: '100.00', amount: '10.00' }],
  subtotal: '100.00',
  discountTotal: '10.00',
  total: '90.00',
  maxDiscountRate: '0.35',
  capApplied: false,
};

describe('BreakdownPanel', () => {
  async function render(breakdown: CheckoutBreakdown, heading = 'Desglose de descuentos'): Promise<HTMLElement> {
    const fixture = TestBed.createComponent(BreakdownPanel);
    fixture.componentRef.setInput('breakdown', breakdown);
    fixture.componentRef.setInput('heading', heading);
    await fixture.whenStable();
    return fixture.nativeElement as HTMLElement;
  }

  it('muestra el título recibido y una fila por concepto como lista de definiciones', async () => {
    const panel = await render(desglose, 'Totales de tu compra');

    expect(panel.querySelector('h3')?.textContent).toBe('Totales de tu compra');
    const filas = [...panel.querySelectorAll('.breakdown__row')].map((fila) => [
      fila.querySelector('dt')?.textContent?.trim(),
      fila.querySelector('dd')?.textContent?.trim(),
    ]);
    expect(filas).toEqual([
      ['Subtotal', 'USD 100.00'],
      ['Categoría Tecnología (10%)', '−USD 10.00'],
      ['Descuento total', '−USD 10.00'],
      ['Total a pagar', 'USD 90.00'],
    ]);
  });

  it('la sección se nombra con su título para los lectores de pantalla', async () => {
    const panel = await render(desglose);

    const section = panel.querySelector('section');
    const titulo = panel.querySelector(`#${section?.getAttribute('aria-labelledby')}`);
    expect(titulo?.textContent).toBe('Desglose de descuentos');
  });

  it('informa cuando ningún descuento aplica', async () => {
    const panel = await render({ ...desglose, discounts: [], discountTotal: '0.00', total: '100.00' });

    expect(panel.textContent).toContain('Ningún descuento aplica a este carrito');
  });
});
