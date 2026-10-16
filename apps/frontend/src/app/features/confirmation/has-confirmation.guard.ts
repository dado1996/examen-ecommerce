import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';

import { ConfirmationStore } from './confirmation-store';

/** Solo deja ver la confirmación si hay una compra recién confirmada; si no, vuelve a productos. */
export const hasConfirmationGuard: CanActivateFn = () =>
  inject(ConfirmationStore).checkout() ? true : inject(Router).createUrlTree(['/']);
