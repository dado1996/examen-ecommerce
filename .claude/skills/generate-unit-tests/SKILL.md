---
name: generate-unit-tests
description: Genera pruebas unitarias del backend (JUnit 5, AssertJ y Mockito) para una clase del dominio o de la capa de aplicación, a partir de la spec y cubriendo los casos de borde obligatorios.
---

# Generar pruebas unitarias del backend

Úsala con el nombre de la clase a probar, por ejemplo: `/generate-unit-tests CheckoutService`.

## Pasos

1. Lee la clase indicada y sus dependencias directas.
2. Lee las reglas de negocio en `CLAUDE.md` y los supuestos en `docs/arquitectura.md`. Las pruebas se derivan de la spec, **no** de la implementación actual: si el código contradice la spec, escribe la prueba según la spec y señala la diferencia.
3. Identifica los colaboradores:
   - Puertos del dominio (`domain/port`): créalos con **Mockito** (`@Mock`) y verifica las interacciones relevantes (por ejemplo, que no se descuente stock si el checkout falla).
   - Objetos de valor y reglas puras: usa instancias reales, no mocks.
4. Escribe una prueba por comportamiento, con nombre en español que lo describa, siguiendo Arrange / Act / Assert.
5. Incluye siempre, cuando apliquen a la clase:
   - Tope del 35% superado → descuento truncado exactamente en el tope.
   - Carrito vacío.
   - Datos corruptos: cantidad cero o negativa, producto inexistente.
   - Cupón no registrado o expirado.
   - Stock insuficiente.
6. Para dinero usa `isEqualByComparingTo` con valores en `String` (`"12.34"`), nunca `double`.
7. Usa `@ParameterizedTest` cuando varios casos difieran solo en los datos.
8. Ejecuta `mvn test` y corrige solo las pruebas que fallen por errores propios de la prueba. Si falla por un error del código de producción, **no lo corrijas**: repórtalo.

## Salida

El archivo de pruebas en `src/test/java` con el mismo paquete que la clase, más un resumen: casos cubiertos, casos de borde incluidos y discrepancias encontradas entre la spec y el código.
