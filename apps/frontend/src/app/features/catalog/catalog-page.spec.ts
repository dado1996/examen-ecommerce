import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { Observable, of, Subject, throwError } from 'rxjs';

import { CatalogPage } from './catalog-page';
import { CatalogApi } from '../../core/api/catalog-api';
import { Product } from '../../core/models/product';
import { CartStore } from '../cart/cart-store';

const productos: Product[] = [
  { id: 1, name: 'Laptop 14"', unitPrice: '1200.00', category: 'Tecnología', availableStock: 2 },
  { id: 4, name: 'Lámpara de escritorio', unitPrice: '40.00', category: 'Hogar', availableStock: 15 },
];

/** Doble de prueba del cliente HTTP: cada prueba decide qué devuelve el backend. */
class CatalogApiFalso implements Pick<CatalogApi, 'listProducts'> {
  respuesta: () => Observable<readonly Product[]> = () => of(productos);
  listProducts(): Observable<readonly Product[]> {
    return this.respuesta();
  }
}

describe('CatalogPage', () => {
  let api: CatalogApiFalso;
  let fixture: ComponentFixture<CatalogPage>;
  let page: HTMLElement;

  async function render(): Promise<void> {
    fixture = TestBed.createComponent(CatalogPage);
    page = fixture.nativeElement as HTMLElement;
    await fixture.whenStable();
  }

  function tarjeta(nombre: string): HTMLElement {
    const card = [...page.querySelectorAll<HTMLElement>('li.product-card')].find((li) =>
      li.querySelector('h3')?.textContent?.includes(nombre),
    );
    if (!card) throw new Error(`No se encontró la tarjeta de ${nombre}`);
    return card;
  }

  function boton(scope: ParentNode, nombreAccesible: string): HTMLButtonElement {
    const found = [...scope.querySelectorAll<HTMLButtonElement>('button')].find(
      (b) => (b.getAttribute('aria-label') ?? b.textContent?.trim()) === nombreAccesible,
    );
    if (!found) throw new Error(`No se encontró el botón "${nombreAccesible}"`);
    return found;
  }

  function campoCantidad(nombre: string): HTMLInputElement {
    const input = tarjeta(nombre).querySelector<HTMLInputElement>('input[type="number"]');
    if (!input) throw new Error(`No se encontró el campo de cantidad de ${nombre}`);
    return input;
  }

  async function escribirCantidad(nombre: string, valor: string): Promise<void> {
    const input = campoCantidad(nombre);
    input.value = valor;
    input.dispatchEvent(new Event('change'));
    await fixture.whenStable();
  }

  beforeEach(() => {
    api = new CatalogApiFalso();
    TestBed.configureTestingModule({
      imports: [CatalogPage],
      providers: [provideRouter([]), { provide: CatalogApi, useValue: api }],
    });
  });

  it('anuncia que está cargando mientras llegan los productos', async () => {
    const pendiente = new Subject<readonly Product[]>();
    api.respuesta = () => pendiente;

    await render();

    expect(page.querySelector('[role="status"]')?.textContent).toContain('Cargando productos');
    pendiente.next(productos);
    await fixture.whenStable();
    expect(page.querySelector('[role="status"]')).toBeNull();
  });

  it('lista cada producto con su nombre, categoría, precio y stock disponible', async () => {
    await render();

    const laptop = tarjeta('Laptop 14"');
    expect(laptop.textContent).toContain('Tecnología');
    expect(laptop.textContent).toContain('USD 1,200.00');
    expect(laptop.textContent).toContain('2 disponibles');
    expect(page.querySelectorAll('li.product-card')).toHaveLength(2);
  });

  it('cada campo de cantidad tiene una etiqueta que nombra al producto', async () => {
    await render();

    const input = campoCantidad('Lámpara de escritorio');
    const label = page.querySelector<HTMLLabelElement>(`label[for="${input.id}"]`);
    expect(label?.textContent?.replace(/\s+/g, ' ').trim()).toBe('Cantidad de Lámpara de escritorio');
    expect(input.min).toBe('0');
    expect(input.max).toBe('15');
  });

  it('suma y resta unidades con los botones, sin pasar del stock ni bajar de cero', async () => {
    await render();
    const laptop = tarjeta('Laptop 14"');
    const sumar = boton(laptop, 'Agregar una unidad de Laptop 14"');
    const restar = boton(laptop, 'Quitar una unidad de Laptop 14"');

    expect(restar.disabled).toBe(true);
    sumar.click();
    sumar.click();
    await fixture.whenStable();

    expect(campoCantidad('Laptop 14"').value).toBe('2');
    expect(sumar.disabled).toBe(true);
    restar.click();
    await fixture.whenStable();
    expect(campoCantidad('Laptop 14"').value).toBe('1');
  });

  it('una cantidad escrita mayor que el stock se recorta al disponible', async () => {
    await render();

    await escribirCantidad('Laptop 14"', '50');

    expect(campoCantidad('Laptop 14"').value).toBe('2');
    expect(TestBed.inject(CartStore).quantityOf(1)).toBe(2);
  });

  it('una cantidad escrita inválida o negativa se corrige a cero', async () => {
    await render();

    await escribirCantidad('Lámpara de escritorio', '-3');
    expect(campoCantidad('Lámpara de escritorio').value).toBe('0');

    await escribirCantidad('Lámpara de escritorio', '');
    expect(campoCantidad('Lámpara de escritorio').value).toBe('0');
    expect(TestBed.inject(CartStore).isEmpty()).toBe(true);
  });

  it('resume la selección en una región que anuncian los lectores de pantalla', async () => {
    await render();
    const resumen = page.querySelector('[aria-live="polite"]');
    expect(resumen?.textContent).toContain('Tu carrito está vacío');

    boton(tarjeta('Laptop 14"'), 'Agregar una unidad de Laptop 14"').click();
    await escribirCantidad('Lámpara de escritorio', '3');

    expect(resumen?.textContent).toContain('4 unidades de 2 productos');
  });

  it('usa singular cuando hay una sola unidad de un solo producto', async () => {
    await render();

    boton(tarjeta('Laptop 14"'), 'Agregar una unidad de Laptop 14"').click();
    await fixture.whenStable();

    expect(page.querySelector('[aria-live="polite"]')?.textContent).toContain('1 unidad de 1 producto');
  });

  it('muestra el subtotal en cero con el carrito vacío', async () => {
    await render();

    expect(page.querySelector('.cart-bar__subtotal')?.textContent).toContain('USD 0.00');
  });

  it('actualiza el subtotal en tiempo real al cambiar las cantidades', async () => {
    await render();
    const subtotal = (): string => page.querySelector('.cart-bar__subtotal')?.textContent ?? '';

    boton(tarjeta('Laptop 14"'), 'Agregar una unidad de Laptop 14"').click();
    await fixture.whenStable();
    expect(subtotal()).toContain('USD 1,200.00');

    await escribirCantidad('Lámpara de escritorio', '3');
    expect(subtotal()).toContain('USD 1,320.00');

    boton(tarjeta('Laptop 14"'), 'Quitar una unidad de Laptop 14"').click();
    await fixture.whenStable();
    expect(subtotal()).toContain('USD 120.00');
  });

  it('aclara que el subtotal es antes de descuentos y lo anuncia junto con el resumen', async () => {
    await render();

    const regionViva = page.querySelector('[aria-live="polite"]');
    expect(regionViva?.querySelector('.cart-bar__subtotal')).not.toBeNull();
    expect(regionViva?.textContent).toContain('antes de descuentos');
  });

  it('el botón de checkout está deshabilitado con el carrito vacío y explica por qué', async () => {
    await render();

    const irAlCheckout = boton(page, 'Ir al checkout');
    expect(irAlCheckout.disabled).toBe(true);
    const descripcion = page.querySelector(`#${irAlCheckout.getAttribute('aria-describedby')}`);
    expect(descripcion?.textContent).toContain('Tu carrito está vacío');
  });

  it('con productos seleccionados el botón lleva al checkout', async () => {
    await render();
    const navegar = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);

    boton(tarjeta('Lámpara de escritorio'), 'Agregar una unidad de Lámpara de escritorio').click();
    await fixture.whenStable();
    const irAlCheckout = boton(page, 'Ir al checkout');
    expect(irAlCheckout.disabled).toBe(false);
    irAlCheckout.click();

    expect(navegar).toHaveBeenCalledWith('/checkout');
  });

  it('ajusta una selección previa al stock vigente del catálogo', async () => {
    const cart = TestBed.inject(CartStore);
    cart.setQuantity({ ...productos[1]!, availableStock: 99 }, 30);
    cart.setQuantity({ id: 999, name: 'Retirado', unitPrice: '1.00', category: 'Hogar', availableStock: 5 }, 2);

    await render();

    expect(cart.lines()).toEqual([{ productId: 4, quantity: 15 }]);
    expect(campoCantidad('Lámpara de escritorio').value).toBe('15');
  });

  it('informa que no hay productos cuando el catálogo está vacío', async () => {
    api.respuesta = () => of([]);

    await render();

    expect(page.textContent).toContain('No hay productos con stock disponible');
    expect(page.querySelector('li.product-card')).toBeNull();
  });

  it('muestra un error accesible y permite reintentar si el backend falla', async () => {
    let intentos = 0;
    api.respuesta = () => (++intentos === 1 ? throwError(() => new Error('caído')) : of(productos));

    await render();
    const alerta = page.querySelector('[role="alert"]');
    expect(alerta?.textContent).toContain('No pudimos cargar los productos');

    boton(page, 'Reintentar').click();
    await fixture.whenStable();

    expect(page.querySelector('[role="alert"]')).toBeNull();
    expect(page.querySelectorAll('li.product-card')).toHaveLength(2);
  });
});
