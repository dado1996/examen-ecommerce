import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';

import { CartStore } from '../cart/cart-store';

/**
 * Solo deja entrar al checkout con productos en el carrito. Como el carrito vive en memoria,
 * eso solo pasa al llegar desde el botón "Ir al checkout"; una URL escrita a mano o una
 * recarga vuelven a la selección de productos.
 */
export const cartNotEmptyGuard: CanActivateFn = () =>
  inject(CartStore).isEmpty() ? inject(Router).createUrlTree(['/']) : true;
