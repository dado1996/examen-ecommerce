import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { CheckoutPage } from './checkout-page';
import { Product } from '../../core/models/product';
import { CartStore } from '../cart/cart-store';

const laptop: Product = { id: 1, name: 'Laptop 14"', unitPrice: '1200.00', category: 'Tecnología', availableStock: 5 };
const teclado: Product = { id: 3, name: 'Teclado mecánico', unitPrice: '89.90', category: 'Tecnología', availableStock: 20 };

describe('CheckoutPage', () => {
  let cart: CartStore;
  let fixture: ComponentFixture<CheckoutPage>;
  let page: HTMLElement;

  async function render(): Promise<void> {
    fixture = TestBed.createComponent(CheckoutPage);
    page = fixture.nativeElement as HTMLElement;
    await fixture.whenStable();
  }

  function filas(): string[][] {
    return [...page.querySelectorAll('tbody tr')].map((tr) =>
      [...tr.querySelectorAll('th, td')].map((celda) => {
        // El detalle "2 × precio" solo se ve en pantallas angostas; aquí se lee la celda sin él
        const copia = celda.cloneNode(true) as HTMLElement;
        copia.querySelector('.cart-table__unit-detail')?.remove();
        return copia.textContent?.trim() ?? '';
      }),
    );
  }

  function campoCupon(): HTMLInputElement {
    const input = page.querySelector<HTMLInputElement>('input#coupon-code');
    if (!input) throw new Error('No se encontró el campo del cupón');
    return input;
  }

  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [CheckoutPage], providers: [provideRouter([])] });
    cart = TestBed.inject(CartStore);
    cart.setQuantity(laptop, 2);
    cart.setQuantity(teclado, 3);
  });

  it('lista cada producto del carrito con su precio, cantidad y total de la línea', async () => {
    await render();

    expect(filas()).toEqual([
      ['Laptop 14"', 'USD 1,200.00', '2', 'USD 2,400.00'],
      ['Teclado mecánico', 'USD 89.90', '3', 'USD 269.70'],
    ]);
  });

  it('bajo cada nombre incluye cantidad por precio para cuando se oculta la columna de precio', async () => {
    await render();

    const detalles = [...page.querySelectorAll('.cart-table__unit-detail')].map((d) => d.textContent?.trim());
    expect(detalles).toEqual(['2 × USD 1,200.00', '3 × USD 89.90']);
  });

  it('la tabla tiene título y encabezados de columna para lectores de pantalla', async () => {
    await render();

    expect(page.querySelector('table caption')?.textContent).toContain('Productos en tu carrito');
    const encabezados = [...page.querySelectorAll('thead th')].map((th) => th.textContent?.trim());
    expect(encabezados).toEqual(['Producto', 'Precio unitario', 'Cantidad', 'Total']);
    expect([...page.querySelectorAll('thead th')].every((th) => th.getAttribute('scope') === 'col')).toBe(true);
    expect([...page.querySelectorAll('tbody th')].every((th) => th.getAttribute('scope') === 'row')).toBe(true);
  });

  it('muestra el subtotal exacto de la selección antes de descuentos', async () => {
    await render();

    const pie = page.querySelector('tfoot')?.textContent ?? '';
    expect(pie).toContain('Subtotal');
    expect(pie).toContain('USD 2,669.70');
    expect(page.textContent).toContain('antes de descuentos');
  });

  it('resume las unidades de la selección', async () => {
    await render();

    expect(page.textContent).toContain('5 unidades de 2 productos');
  });

  it('tiene un campo de cupón con etiqueta y el largo máximo del contrato', async () => {
    await render();

    const input = campoCupon();
    expect(page.querySelector(`label[for="${input.id}"]`)?.textContent).toContain('Código de cupón');
    expect(input.maxLength).toBe(50);
    expect(input.getAttribute('autocomplete')).toBe('off');
  });

  it('guarda el cupón escrito en el carrito para el siguiente paso', async () => {
    await render();

    const input = campoCupon();
    input.value = ' WELCOME2026 ';
    input.dispatchEvent(new Event('input'));
    await fixture.whenStable();

    expect(cart.normalizedCoupon()).toBe('WELCOME2026');
  });

  it('conserva el cupón ya escrito al volver a la vista', async () => {
    cart.setCouponCode('WELCOME2026');

    await render();

    expect(campoCupon().value).toBe('WELCOME2026');
  });

  it('no pide correo electrónico', async () => {
    await render();

    expect(page.querySelector('input[type="email"]')).toBeNull();
  });

  it('ofrece volver a la selección de productos para cambiar cantidades', async () => {
    await render();

    expect(page.querySelector('a[href="/"]')?.textContent).toContain('Volver a productos');
  });
});
