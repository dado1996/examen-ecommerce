import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { Observable, of, Subject, throwError } from 'rxjs';

import { CheckoutPage } from './checkout-page';
import { CheckoutApi } from '../../core/api/checkout-api';
import { Checkout, CheckoutBreakdown, CheckoutRequest } from '../../core/models/checkout';
import { Product } from '../../core/models/product';
import { CartStore } from '../cart/cart-store';
import { ConfirmationStore } from '../confirmation/confirmation-store';

const laptop: Product = { id: 1, name: 'Laptop 14"', unitPrice: '1200.00', category: 'Tecnología', availableStock: 5 };
const teclado: Product = { id: 3, name: 'Teclado mecánico', unitPrice: '89.90', category: 'Tecnología', availableStock: 20 };

/** Desglose del backend para 2 laptops y 3 teclados con WELCOME2026 (montos intermedios sin redondear). */
const desgloseConCupon: CheckoutBreakdown = {
  couponCode: 'WELCOME2026',
  lines: [],
  discounts: [
    { step: 1, rule: 'CATEGORY', rate: '0.10', reference: 'Tecnología', baseAmount: '2669.70', amount: '266.9700' },
    { step: 2, rule: 'VOLUME', rate: '0.05', reference: '100.00', baseAmount: '2402.7300', amount: '120.136500' },
    { step: 3, rule: 'COUPON', rate: '0.15', reference: 'WELCOME2026', baseAmount: '2282.593500', amount: '342.38902500' },
  ],
  subtotal: '2669.70',
  discountTotal: '729.50',
  total: '1940.20',
  maxDiscountRate: '0.35',
  capApplied: false,
};

const desgloseConTope: CheckoutBreakdown = {
  ...desgloseConCupon,
  discounts: [
    { step: 1, rule: 'CATEGORY', rate: '0.40', reference: 'Tecnología', baseAmount: '100.10', amount: '40.0400' },
    { step: 4, rule: 'CAP', rate: '0.35', reference: null, baseAmount: '40.0400', amount: '5.005000' },
  ],
  couponCode: null,
  subtotal: '100.10',
  discountTotal: '35.03',
  total: '65.07',
  capApplied: true,
};

function problema(status: number, body: Record<string, unknown>): HttpErrorResponse {
  return new HttpErrorResponse({ status, error: { title: 'x', status, ...body } });
}

/** Doble de prueba del cliente: registra lo que se envía y devuelve lo que cada prueba decida. */
const ordenConfirmada: Checkout = { ...desgloseConCupon, id: 42, createdAt: '2026-10-15T19:30:00.000Z' };

class CheckoutApiFalso implements Pick<CheckoutApi, 'preview' | 'create'> {
  enviados: CheckoutRequest[] = [];
  compras: CheckoutRequest[] = [];
  claves: string[] = [];
  respuesta: () => Observable<CheckoutBreakdown> = () => of(desgloseConCupon);
  respuestaCompra: () => Observable<Checkout> = () => of(ordenConfirmada);
  preview(request: CheckoutRequest): Observable<CheckoutBreakdown> {
    this.enviados.push(request);
    return this.respuesta();
  }
  create(request: CheckoutRequest, idempotencyKey: string): Observable<Checkout> {
    this.compras.push(request);
    this.claves.push(idempotencyKey);
    return this.respuestaCompra();
  }
}

describe('CheckoutPage', () => {
  let api: CheckoutApiFalso;
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

  function botonAplicar(): HTMLButtonElement {
    const boton = page.querySelector<HTMLButtonElement>('button[type="submit"]');
    if (!boton) throw new Error('No se encontró el botón de aplicar descuento');
    return boton;
  }

  async function escribirCupon(valor: string): Promise<void> {
    const input = campoCupon();
    input.value = valor;
    input.dispatchEvent(new Event('input'));
    await fixture.whenStable();
  }

  function botonPagar(): HTMLButtonElement {
    const boton = [...page.querySelectorAll<HTMLButtonElement>('button')].find((b) =>
      b.textContent?.includes('pagar') || b.textContent?.includes('Procesando'),
    );
    if (!boton) throw new Error('No se encontró el botón de ir a pagar');
    return boton;
  }

  async function pagar(): Promise<void> {
    botonPagar().click();
    await fixture.whenStable();
  }

  async function aplicar(): Promise<void> {
    botonAplicar().click();
    await fixture.whenStable();
  }

  function desglose(): string[][] {
    return [...page.querySelectorAll('.breakdown__row')].map((fila) => [
      fila.querySelector('dt')?.textContent?.trim() ?? '',
      fila.querySelector('dd')?.textContent?.trim() ?? '',
    ]);
  }

  beforeEach(() => {
    api = new CheckoutApiFalso();
    TestBed.configureTestingModule({
      imports: [CheckoutPage],
      providers: [provideRouter([]), { provide: CheckoutApi, useValue: api }],
    });
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
  it('el botón de aplicar descuento envía solo productos, cantidades y el cupón sin espacios', async () => {
    await render();
    await escribirCupon('  WELCOME2026 ');

    await aplicar();

    expect(api.enviados).toEqual([
      {
        items: [
          { productId: 1, quantity: 2 },
          { productId: 3, quantity: 3 },
        ],
        couponCode: 'WELCOME2026',
      },
    ]);
  });

  it('sin cupón también calcula los otros descuentos y envía el cupón como nulo', async () => {
    await render();

    await aplicar();

    expect(api.enviados[0]?.couponCode).toBeNull();
  });

  it('con Enter en el campo del cupón también aplica el descuento', async () => {
    await render();

    page.querySelector('form')?.dispatchEvent(new Event('submit', { cancelable: true }));
    await fixture.whenStable();

    expect(api.enviados).toHaveLength(1);
  });

  it('muestra cada descuento aplicado con su monto redondeado y el total final', async () => {
    await render();

    await aplicar();

    expect(desglose()).toEqual([
      ['Subtotal', 'USD 2,669.70'],
      ['Categoría Tecnología (10%)', '−USD 266.97'],
      ['Volumen: compra mayor que USD 100.00 (5%)', '−USD 120.14'],
      ['Cupón WELCOME2026 (15%)', '−USD 342.39'],
      ['Descuento total', '−USD 729.50'],
      ['Total a pagar', 'USD 1,940.20'],
    ]);
  });

  it('confirma junto al campo que el cupón quedó aplicado', async () => {
    await render();
    await escribirCupon('welcome2026');

    await aplicar();

    const estado = page.querySelector('#coupon-status');
    expect(estado?.textContent).toContain('Cupón WELCOME2026 aplicado');
    expect(campoCupon().getAttribute('aria-invalid')).toBeNull();
  });

  it('el desglose se anuncia en una región viva', async () => {
    await render();

    await aplicar();

    expect(page.querySelector('.breakdown')?.closest('[aria-live="polite"]')).not.toBeNull();
  });

  it('informa cuando ningún descuento aplica al carrito', async () => {
    api.respuesta = () => of({ ...desgloseConCupon, couponCode: null, discounts: [], discountTotal: '0.00', total: '2669.70' });
    await render();

    await aplicar();

    expect(page.textContent).toContain('Ningún descuento aplica a este carrito');
    expect(desglose()).toContainEqual(['Total a pagar', 'USD 2,669.70']);
  });

  it('alerta cuando el descuento llegó al tope máximo y muestra el ajuste', async () => {
    api.respuesta = () => of(desgloseConTope);
    await render();

    await aplicar();

    const alerta = page.querySelector('[role="alert"]');
    expect(alerta?.textContent).toContain('Alcanzaste el descuento máximo del 35%');
    expect(alerta?.textContent).toContain('USD 35.03');
    expect(desglose()).toContainEqual(['Ajuste por tope máximo del 35%', '+USD 5.01']);
  });

  it('sin tope no muestra la alerta', async () => {
    await render();

    await aplicar();

    expect(page.querySelector('[role="alert"]')).toBeNull();
  });

  it('un cupón no registrado se marca como inválido junto al campo y no muestra desglose', async () => {
    api.respuesta = () => throwError(() => problema(422, { code: 'INVALID_COUPON' }));
    await render();
    await escribirCupon('NOEXISTE');

    await aplicar();

    const input = campoCupon();
    expect(input.getAttribute('aria-invalid')).toBe('true');
    const error = page.querySelector('#coupon-error');
    expect(error?.textContent).toContain('El cupón no existe');
    expect(input.getAttribute('aria-describedby')).toContain('coupon-error');
    expect(page.querySelector('.breakdown')).toBeNull();
  });

  it('un cupón vencido se informa junto al campo', async () => {
    api.respuesta = () => throwError(() => problema(422, { code: 'COUPON_EXPIRED' }));
    await render();
    await escribirCupon('WELCOME2026');

    await aplicar();

    expect(page.querySelector('#coupon-error')?.textContent).toContain('El cupón ya venció');
  });

  it('al corregir el cupón se limpia el error y el desglose anterior', async () => {
    api.respuesta = () => throwError(() => problema(422, { code: 'INVALID_COUPON' }));
    await render();
    await escribirCupon('NOEXISTE');
    await aplicar();

    await escribirCupon('WELCOME');

    expect(page.querySelector('#coupon-error')).toBeNull();
    expect(campoCupon().getAttribute('aria-invalid')).toBeNull();
  });

  it('cambiar el cupón después de aplicarlo descarta el desglose, que ya no corresponde', async () => {
    await render();
    await aplicar();
    expect(page.querySelector('.breakdown')).not.toBeNull();

    await escribirCupon('OTRO');

    expect(page.querySelector('.breakdown')).toBeNull();
  });

  it('el stock insuficiente se informa como alerta nombrando el producto', async () => {
    api.respuesta = () =>
      throwError(() => problema(409, { code: 'INSUFFICIENT_STOCK', productId: 3, requested: 3, available: 1 }));
    await render();

    await aplicar();

    const alerta = page.querySelector('[role="alert"]');
    expect(alerta?.textContent).toContain('No hay stock suficiente de Teclado mecánico: pediste 3 y quedan 1');
    expect(page.querySelector('#coupon-error')).toBeNull();
  });

  it('un error de red se informa como alerta sin detalles técnicos', async () => {
    api.respuesta = () => throwError(() => new HttpErrorResponse({ status: 0, error: new ProgressEvent('error') }));
    await render();

    await aplicar();

    expect(page.querySelector('[role="alert"]')?.textContent).toContain('Revisa tu conexión');
  });

  it('mientras calcula deshabilita el botón y lo anuncia', async () => {
    const pendiente = new Subject<CheckoutBreakdown>();
    api.respuesta = () => pendiente;
    await render();

    await aplicar();

    expect(botonAplicar().disabled).toBe(true);
    expect(botonAplicar().textContent).toContain('Calculando');
    pendiente.next(desgloseConCupon);
    pendiente.complete();
    await fixture.whenStable();
    expect(botonAplicar().disabled).toBe(false);
    expect(botonAplicar().textContent).toContain('Aplicar descuento');
  });
  it('el botón de ir a pagar está al final de la vista', async () => {
    await render();

    const botones = [...page.querySelectorAll('button')];
    expect(botones.at(-1)?.textContent?.trim()).toBe('Ir a pagar');
    expect(botonPagar().type).toBe('button');
  });

  it('ir a pagar envía a POST /checkouts solo productos, cantidades y el cupón escrito', async () => {
    vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    await render();
    await escribirCupon(' WELCOME2026 ');

    await pagar();

    expect(api.compras).toEqual([
      {
        items: [
          { productId: 1, quantity: 2 },
          { productId: 3, quantity: 3 },
        ],
        couponCode: 'WELCOME2026',
      },
    ]);
  });

  it('con la compra confirmada guarda la orden, vacía el carrito y lleva a la confirmación', async () => {
    const navegar = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    await render();

    await pagar();

    expect(TestBed.inject(ConfirmationStore).checkout()).toEqual(ordenConfirmada);
    expect(cart.isEmpty()).toBe(true);
    expect(navegar).toHaveBeenCalledWith('/confirmacion');
  });

  it('mientras procesa el pago deshabilita ambos botones para evitar una compra doble', async () => {
    const pendiente = new Subject<Checkout>();
    api.respuestaCompra = () => pendiente;
    vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    await render();

    await pagar();
    expect(botonPagar().disabled).toBe(true);
    expect(botonPagar().textContent).toContain('Procesando');
    expect(botonAplicar().disabled).toBe(true);
    botonPagar().click();
    expect(api.compras).toHaveLength(1);

    pendiente.next(ordenConfirmada);
    pendiente.complete();
    await fixture.whenStable();
  });

  it('si el pago falla por stock conserva el carrito y explica qué producto falta', async () => {
    const navegar = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    api.respuestaCompra = () =>
      throwError(() => problema(409, { code: 'INSUFFICIENT_STOCK', productId: 1, requested: 2, available: 1 }));
    await render();

    await pagar();

    expect(page.querySelector('[role="alert"]')?.textContent).toContain('No hay stock suficiente de Laptop 14"');
    expect(cart.isEmpty()).toBe(false);
    expect(navegar).not.toHaveBeenCalled();
    expect(botonPagar().disabled).toBe(false);
  });

  it('si el pago falla por el cupón lo marca junto al campo', async () => {
    api.respuestaCompra = () => throwError(() => problema(422, { code: 'COUPON_EXPIRED' }));
    await render();
    await escribirCupon('WELCOME2026');

    await pagar();

    expect(page.querySelector('#coupon-error')?.textContent).toContain('El cupón ya venció');
    expect(campoCupon().getAttribute('aria-invalid')).toBe('true');
  });

  describe('clave de idempotencia', () => {
    const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/;

    function fallaUnaVezYLuegoConfirma(): void {
      let intentos = 0;
      api.respuestaCompra = () =>
        ++intentos === 1 ? throwError(() => problema(0, {})) : of(ordenConfirmada);
    }

    beforeEach(() => {
      vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
      api.respuestaCompra = () => throwError(() => problema(503, { code: 'DATABASE_UNAVAILABLE' }));
    });

    it('cada pago envía una clave UUID generada en el navegador', async () => {
      await render();

      await pagar();

      expect(api.claves).toHaveLength(1);
      expect(api.claves[0]).toMatch(UUID);
    });

    it('un reintento del mismo pago reutiliza la clave para que el backend no cobre dos veces', async () => {
      fallaUnaVezYLuegoConfirma();
      await render();

      await pagar();
      await pagar();

      expect(api.claves).toHaveLength(2);
      expect(api.claves[1]).toBe(api.claves[0]);
    });

    it('cambiar el cupón descarta la clave y el siguiente pago usa una nueva', async () => {
      await render();
      await pagar();

      await escribirCupon('WELCOME2026');
      await pagar();

      expect(api.claves[1]).toMatch(UUID);
      expect(api.claves[1]).not.toBe(api.claves[0]);
    });

    it('espacios alrededor del cupón no cambian el pedido ni la clave', async () => {
      await render();
      await escribirCupon('WELCOME2026');
      await pagar();

      await escribirCupon(' WELCOME2026 ');
      await pagar();

      expect(api.claves[1]).toBe(api.claves[0]);
    });

    it('cambiar las cantidades del carrito descarta la clave', async () => {
      await render();
      await pagar();

      cart.setQuantity(teclado, 1);
      await pagar();

      expect(api.claves[1]).not.toBe(api.claves[0]);
    });

    it('si el backend rechaza la clave como reutilizada lo explica y el siguiente pago usa otra', async () => {
      let intentos = 0;
      api.respuestaCompra = () =>
        ++intentos === 1 ? throwError(() => problema(409, { code: 'IDEMPOTENCY_KEY_REUSED' })) : of(ordenConfirmada);
      await render();

      await pagar();
      expect(page.querySelector('[role="alert"]')?.textContent).toContain('Revisa tu carrito');

      await pagar();
      expect(api.claves[1]).not.toBe(api.claves[0]);
    });

    it('después de una compra confirmada el siguiente carrito usa otra clave', async () => {
      api.respuestaCompra = () => of(ordenConfirmada);
      await render();
      await pagar();

      cart.setQuantity(laptop, 2);
      cart.setQuantity(teclado, 3);
      await pagar();

      expect(api.claves[1]).not.toBe(api.claves[0]);
    });
  });

  it('si la base no está disponible permite reintentar el pago', async () => {
    let intentos = 0;
    api.respuestaCompra = () =>
      ++intentos === 1 ? throwError(() => problema(503, { code: 'DATABASE_UNAVAILABLE' })) : of(ordenConfirmada);
    const navegar = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    await render();

    await pagar();
    expect(page.querySelector('[role="alert"]')?.textContent).toContain('Intenta de nuevo');

    await pagar();
    expect(navegar).toHaveBeenCalledWith('/confirmacion');
  });
});
