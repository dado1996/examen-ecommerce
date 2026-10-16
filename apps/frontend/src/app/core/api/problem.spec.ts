import { HttpErrorResponse } from '@angular/common/http';

import { readProblem } from './problem';

describe('readProblem', () => {
  it('lee el code y los campos extra de un Problem Details del backend', () => {
    const error = new HttpErrorResponse({
      status: 409,
      error: { title: 'Stock insuficiente', status: 409, code: 'INSUFFICIENT_STOCK', productId: 6, requested: 2, available: 0 },
    });

    expect(readProblem(error)).toEqual({ code: 'INSUFFICIENT_STOCK', productId: 6, requested: 2, available: 0 });
  });

  it('un error sin respuesta del servidor es un problema de red', () => {
    const error = new HttpErrorResponse({ status: 0, error: new ProgressEvent('error') });

    expect(readProblem(error)).toEqual({ code: 'NETWORK_ERROR' });
  });

  it('un code que no está en el contrato se trata como desconocido', () => {
    const error = new HttpErrorResponse({ status: 418, error: { title: 'x', status: 418, code: 'TEAPOT' } });

    expect(readProblem(error)).toEqual({ code: 'UNKNOWN_ERROR' });
  });

  it('un cuerpo que no es Problem Details se trata como desconocido', () => {
    expect(readProblem(new HttpErrorResponse({ status: 500, error: '<html>' }))).toEqual({ code: 'UNKNOWN_ERROR' });
    expect(readProblem(new Error('otra cosa'))).toEqual({ code: 'UNKNOWN_ERROR' });
  });

  it('ignora campos extra con un tipo distinto al esperado', () => {
    const error = new HttpErrorResponse({
      status: 422,
      error: { title: 'x', status: 422, code: 'PRODUCT_NOT_FOUND', productId: '999' },
    });

    expect(readProblem(error)).toEqual({ code: 'PRODUCT_NOT_FOUND' });
  });
});
