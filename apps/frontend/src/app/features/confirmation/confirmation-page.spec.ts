import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { ConfirmationPage } from './confirmation-page';
import { ConfirmationStore } from './confirmation-store';
import { Checkout } from '../../core/models/checkout';

/** Respuesta de POST /checkouts para 2 laptops y 1 lámpara con WELCOME2026. */
const orden: Checkout = {
  id: 42,
  createdAt: '2026-10-15T19:30:00.000Z',
  couponCode: 'WELCOME2026',
  lines: [
    { productId: 1, name: 'Laptop 14"', category: 'Tecnología', quantity: 2, unitPrice: '1200.00', lineSubtotal: '2400.00' },
    { productId: 4, name: 'Lámpara de escritorio', category: 'Hogar', quantity: 1, unitPrice: '40.00', lineSubtotal: '40.00' },
  ],
  discounts: [
    { step: 1, rule: 'CATEGORY', rate: '0.10', reference: 'Tecnología', baseAmount: '2400.00', amount: '240.0000' },
    { step: 2, rule: 'VOLUME', rate: '0.05', reference: '100.00', baseAmount: '2200.0000', amount: '110.000000' },
    { step: 3, rule: 'COUPON', rate: '0.15', reference: 'WELCOME2026', baseAmount: '2090.000000', amount: '313.50000000' },
  ],
  subtotal: '2440.00',
  discountTotal: '663.50',
  total: '1776.50',
  maxDiscountRate: '0.35',
  capApplied: false,
};

describe('ConfirmationPage', () => {
  let fixture: ComponentFixture<ConfirmationPage>;
  let page: HTMLElement;

  async function render(checkout: Checkout = orden): Promise<void> {
    TestBed.inject(ConfirmationStore).set(checkout);
    fixture = TestBed.createComponent(ConfirmationPage);
    page = fixture.nativeElement as HTMLElement;
    await fixture.whenStable();
  }

  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [ConfirmationPage], providers: [provideRouter([])] });
  });

  it('confirma la compra con su número de orden', async () => {
    await render();

    expect(page.querySelector('h2')?.textContent).toContain('Compra confirmada');
    expect(page.textContent).toContain('Orden n.º 42');
  });

  it('muestra la fecha de la compra legible', async () => {
    await render();

    const fecha = page.querySelector('time');
    expect(fecha?.getAttribute('datetime')).toBe('2026-10-15T19:30:00.000Z');
    expect(fecha?.textContent).toMatch(/2026/);
  });

  it('lista los productos comprados con los precios que guardó el backend', async () => {
    await render();

    const filas = [...page.querySelectorAll('tbody tr')].map((tr) =>
      [...tr.querySelectorAll('th, td')].map((celda) => celda.textContent?.trim()),
    );
    expect(filas).toEqual([
      ['Laptop 14"', '2', 'USD 1,200.00', 'USD 2,400.00'],
      ['Lámpara de escritorio', '1', 'USD 40.00', 'USD 40.00'],
    ]);
    expect(page.querySelector('table caption')?.textContent).toContain('Productos comprados');
  });

  it('desglosa los totales exactos de la orden', async () => {
    await render();

    const filas = [...page.querySelectorAll('.breakdown__row')].map((fila) => [
      fila.querySelector('dt')?.textContent?.trim(),
      fila.querySelector('dd')?.textContent?.trim(),
    ]);
    expect(filas).toEqual([
      ['Subtotal', 'USD 2,440.00'],
      ['Categoría Tecnología (10%)', '−USD 240.00'],
      ['Volumen: compra mayor que USD 100.00 (5%)', '−USD 110.00'],
      ['Cupón WELCOME2026 (15%)', '−USD 313.50'],
      ['Descuento total', '−USD 663.50'],
      ['Total a pagar', 'USD 1,776.50'],
    ]);
  });

  it('avisa si el descuento se limitó por el tope máximo', async () => {
    await render({ ...orden, capApplied: true, discountTotal: '854.00', total: '1586.00' });

    expect(page.querySelector('.notice--warning')?.textContent).toContain('descuento máximo del 35%');
  });

  it('sin tope no muestra el aviso', async () => {
    await render();

    expect(page.querySelector('.notice--warning')).toBeNull();
  });

  it('ofrece seguir comprando', async () => {
    await render();

    expect(page.querySelector('a[href="/"]')?.textContent).toContain('Seguir comprando');
  });

  it('lleva el foco al título para que los lectores de pantalla anuncien la confirmación', async () => {
    await render();

    expect(document.activeElement).toBe(page.querySelector('h2'));
  });
});
