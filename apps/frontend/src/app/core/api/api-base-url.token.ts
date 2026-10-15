import { InjectionToken } from '@angular/core';

/** URL base del backend. Se inyecta para poder cambiarla por ambiente y en pruebas. */
export const API_BASE_URL = new InjectionToken<string>('API_BASE_URL');
