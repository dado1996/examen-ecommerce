import { TestBed } from '@angular/core/testing';
import { appConfig } from './app.config';
import { API_BASE_URL } from './core/api/api-base-url.token';

describe('appConfig', () => {
  it('expone la URL base del backend versionada', () => {
    TestBed.configureTestingModule({ providers: appConfig.providers });
    expect(TestBed.inject(API_BASE_URL)).toBe('http://localhost:8080/api/v1');
  });
});
