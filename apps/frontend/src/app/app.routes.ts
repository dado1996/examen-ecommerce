import { Routes } from '@angular/router';

import { cartNotEmptyGuard } from './features/checkout/cart-not-empty.guard';

export const routes: Routes = [
  {
    path: '',
    pathMatch: 'full',
    title: 'Productos · Core E-Commerce',
    loadComponent: () => import('./features/catalog/catalog-page').then((m) => m.CatalogPage),
  },
  {
    path: 'checkout',
    title: 'Checkout · Core E-Commerce',
    canActivate: [cartNotEmptyGuard],
    loadComponent: () => import('./features/checkout/checkout-page').then((m) => m.CheckoutPage),
  },
  { path: '**', redirectTo: '' },
];
