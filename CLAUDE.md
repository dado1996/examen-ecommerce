# Reglas del proyecto para asistentes de IA

Monorepo de un checkout con motor de descuentos acumulativos. Backend Spring Boot (`apps/backend`), frontend Angular (`apps/frontend`), contrato OpenAPI (`packages/contracts`). Las decisiones de arquitectura están en `docs/arquitectura.md`; respétalas y, si una propuesta las contradice, dilo en lugar de cambiarlas en silencio.

## Reglas de negocio (no negociables)

- Descuentos en cascada multiplicativa sobre el subtotal original, en este orden: categoría (10% solo sobre productos "Tecnología") → volumen (5% si el subtotal tras categoría supera 100 USD) → cupón `WELCOME2026` (15%) → tope.
- El descuento total nunca supera el 35% del subtotal original. Si lo supera, se trunca exactamente en el 35%.
- Los valores salen de la configuración (`discounts.*`), nunca quemados en el código.

## Reglas técnicas

- **Dinero:** `BigDecimal` en Java, nunca `double`/`float`. Redondeo `HALF_EVEN` a 2 decimales solo en los totales finales.
- **Precios:** el backend siempre toma el precio del catálogo propio. El cliente solo envía `productId`, cantidad y cupón.
- **Dominio aislado:** nada en `..domain..` importa Spring, Jakarta ni otras capas. `ArchitectureTest` lo verifica.
- **Tipado estricto:** prohibido `any` en TypeScript. Si es inevitable, se justifica con un comentario.
- **Estado del frontend:** Signals; componentes standalone; `inject()` para dependencias.
- **Errores:** Problem Details (RFC 9457) con un `code` de negocio estable.

## Pruebas

- Primero la prueba, luego la implementación, a partir de la spec.
- Cobertura mínima del 80% en front y back; la compuerta está en `mvn verify` y `npm run test:coverage`.
- Casos de borde obligatorios: tope exacto del 35% superado, carrito vacío o con datos corruptos, cupón no registrado o expirado, stock insuficiente.
- Nombres de pruebas en español, describiendo el comportamiento esperado.

## Git

- Commits pequeños e incrementales con Conventional Commits en español (`feat(backend): ...`, `test(frontend): ...`).
- Nunca versionar secretos ni archivos `.env`.

## Comandos

- Backend: `cd apps/backend && mvn verify`
- Frontend: `cd apps/frontend && npm run test:coverage`
