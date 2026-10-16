import { HttpErrorResponse } from '@angular/common/http';

import { PROBLEM_CODES, ProblemCode } from '../models/checkout';

/**
 * Error de la API reducido a lo que la vista necesita: el `code` estable y, si vienen, los
 * datos del producto afectado. `NETWORK_ERROR` y `UNKNOWN_ERROR` no son del contrato: cubren
 * una falla de conexión y una respuesta que no es Problem Details.
 */
export interface ApiProblem {
  readonly code: ProblemCode | 'NETWORK_ERROR' | 'UNKNOWN_ERROR';
  readonly productId?: number;
  readonly requested?: number;
  readonly available?: number;
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null;
}

function isProblemCode(value: unknown): value is ProblemCode {
  return typeof value === 'string' && (PROBLEM_CODES as readonly string[]).includes(value);
}

export function readProblem(error: unknown): ApiProblem {
  if (!(error instanceof HttpErrorResponse)) {
    return { code: 'UNKNOWN_ERROR' };
  }
  if (error.status === 0) {
    return { code: 'NETWORK_ERROR' };
  }
  const body: unknown = error.error;
  if (!isRecord(body) || !isProblemCode(body['code'])) {
    return { code: 'UNKNOWN_ERROR' };
  }
  const problem: { -readonly [K in keyof ApiProblem]: ApiProblem[K] } = { code: body['code'] };
  for (const key of ['productId', 'requested', 'available'] as const) {
    const value = body[key];
    if (typeof value === 'number') {
      problem[key] = value;
    }
  }
  return problem;
}
