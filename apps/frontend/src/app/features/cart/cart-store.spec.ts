import { TestBed } from '@angular/core/testing';

import { CartStore } from './cart-store';
import { Product } from '../../core/models/product';

const laptop: Product = { id: 1, name: 'Laptop', unitPrice: '1200.00', category: 'Tecnología', availableStock: 3 };
const lampara: Product = { id: 4, name: 'Lámpara', unitPrice: '40.00', category: 'Hogar', availableStock: 15 };

describe('CartStore', () => {
  let cart: CartStore;

  beforeEach(() => {
    cart = TestBed.inject(CartStore);
  });

  it('empieza vacío', () => {
    expect(cart.isEmpty()).toBe(true);
    expect(cart.lines()).toEqual([]);
    expect(cart.totalUnits()).toBe(0);
    expect(cart.quantityOf(laptop.id)).toBe(0);
  });

  it('guarda la cantidad elegida de cada producto', () => {
    cart.setQuantity(laptop, 2);
    cart.setQuantity(lampara, 1);

    expect(cart.lines()).toEqual([
      { productId: 1, quantity: 2 },
      { productId: 4, quantity: 1 },
    ]);
    expect(cart.totalUnits()).toBe(3);
    expect(cart.isEmpty()).toBe(false);
  });

  it('no permite elegir más unidades que el stock disponible', () => {
    cart.setQuantity(laptop, 99);

    expect(cart.quantityOf(laptop.id)).toBe(3);
  });

  it('quita el producto del carrito cuando la cantidad llega a cero', () => {
    cart.setQuantity(laptop, 2);
    cart.setQuantity(laptop, 0);

    expect(cart.lines()).toEqual([]);
    expect(cart.isEmpty()).toBe(true);
  });

  it('trata una cantidad negativa, decimal o no numérica como dato corrupto', () => {
    cart.setQuantity(laptop, -2);
    expect(cart.quantityOf(laptop.id)).toBe(0);

    cart.setQuantity(laptop, 2.7);
    expect(cart.quantityOf(laptop.id)).toBe(2);

    cart.setQuantity(laptop, Number.NaN);
    expect(cart.quantityOf(laptop.id)).toBe(0);

    cart.setQuantity(laptop, Number.POSITIVE_INFINITY);
    expect(cart.quantityOf(laptop.id)).toBe(0);
  });

  it('suma y resta de a una unidad sin salirse de los límites', () => {
    cart.increment(laptop);
    cart.increment(laptop);
    cart.increment(laptop);
    cart.increment(laptop);
    expect(cart.quantityOf(laptop.id)).toBe(3);

    cart.decrement(laptop);
    cart.decrement(laptop);
    cart.decrement(laptop);
    cart.decrement(laptop);
    expect(cart.quantityOf(laptop.id)).toBe(0);
  });

  it('ajusta el carrito al catálogo vigente: recorta al stock y quita lo que ya no está', () => {
    cart.setQuantity(laptop, 3);
    cart.setQuantity(lampara, 5);

    cart.reconcile([{ ...laptop, availableStock: 1 }]);

    expect(cart.lines()).toEqual([{ productId: 1, quantity: 1 }]);
  });

  it('el subtotal de un carrito vacío es cero', () => {
    expect(cart.subtotal()).toBe('0.00');
  });

  it('el subtotal suma precio por cantidad de cada producto, exacto al centavo', () => {
    cart.setQuantity(laptop, 2);
    cart.setQuantity(lampara, 3);

    expect(cart.subtotal()).toBe('2520.00');
  });

  it('el subtotal se recalcula al cambiar o quitar una cantidad', () => {
    cart.setQuantity(laptop, 1);
    expect(cart.subtotal()).toBe('1200.00');

    cart.increment(lampara);
    expect(cart.subtotal()).toBe('1240.00');

    cart.setQuantity(laptop, 0);
    expect(cart.subtotal()).toBe('40.00');
  });

  it('el subtotal no acumula errores de punto flotante', () => {
    const cuaderno: Product = { id: 5, name: 'Cuaderno', unitPrice: '0.10', category: 'Papelería', availableStock: 10 };
    const lapiz: Product = { id: 6, name: 'Lápiz', unitPrice: '0.20', category: 'Papelería', availableStock: 10 };

    cart.setQuantity(cuaderno, 1);
    cart.setQuantity(lapiz, 1);

    expect(cart.subtotal()).toBe('0.30');
  });

  it('al ajustar al catálogo vigente el subtotal usa el precio actualizado', () => {
    cart.setQuantity(laptop, 2);

    cart.reconcile([{ ...laptop, unitPrice: '1000.00' }]);

    expect(cart.subtotal()).toBe('2000.00');
  });

  it('expone cada producto elegido con su cantidad y el total de la línea', () => {
    cart.setQuantity(lampara, 3);
    cart.setQuantity(laptop, 2);

    expect(cart.items()).toEqual([
      { product: lampara, quantity: 3, lineTotal: '120.00' },
      { product: laptop, quantity: 2, lineTotal: '2400.00' },
    ]);
  });

  it('empieza sin cupón', () => {
    expect(cart.couponCode()).toBe('');
    expect(cart.normalizedCoupon()).toBeNull();
  });

  it('guarda el cupón escrito y lo normaliza quitando espacios al inicio y al final', () => {
    cart.setCouponCode('  welcome2026 ');

    expect(cart.couponCode()).toBe('  welcome2026 ');
    expect(cart.normalizedCoupon()).toBe('welcome2026');
  });

  it('un cupón en blanco equivale a no enviar cupón', () => {
    cart.setCouponCode('   ');

    expect(cart.normalizedCoupon()).toBeNull();
  });

  it('recorta un cupón más largo que el máximo del contrato', () => {
    cart.setCouponCode('X'.repeat(80));

    expect(cart.couponCode()).toHaveLength(50);
  });

  it('vacía el carrito y el cupón', () => {
    cart.setQuantity(laptop, 1);
    cart.setCouponCode('WELCOME2026');
    cart.clear();

    expect(cart.isEmpty()).toBe(true);
    expect(cart.couponCode()).toBe('');
  });

  it('vacía el carrito', () => {
    cart.setQuantity(laptop, 1);
    cart.clear();

    expect(cart.isEmpty()).toBe(true);
  });
});
