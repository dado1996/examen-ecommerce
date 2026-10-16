# Arquitectura

> Documento vivo. Cada decisión registra el contexto, la alternativa descartada y su costo.

## 1. Stack tecnológico y por qué

| Decisión | Elegido | Alternativa considerada | Razón |
|---|---|---|---|
| Backend | Java 21 + Spring Boot 3.5 | NestJS, Quarkus | Stack de referencia en el dominio bancario; `BigDecimal` nativo para dinero; ecosistema maduro de pruebas (JUnit 5, Mockito, JaCoCo). |
| Frontend | Angular 21 con TypeScript estricto | React, Angular 22 | Estado reactivo con Signals sin librerías extra. Angular 21 en lugar de 22: soporta Node 20.19+/22.12+, lo que evita fallas de instalación en equipos con Node 22 no actualizado. |
| Tipado de extremo a extremo | Contrato OpenAPI en `packages/contracts` | Tipos duplicados a mano en front y back | Una sola fuente de verdad para los DTO; los tipos TypeScript se generan del contrato. _(pendiente: generación)_ |
| Persistencia de órdenes | SQLite con Spring JDBC | JSON en disco, memoria, JPA | Transaccional (ACID) y sin instalación para la demo local. JDBC en lugar de JPA: el modelo es pequeño y SQLite no tiene dialecto oficial en Hibernate. Esquema en `packages/schema` (ver sección 2). |
| Dinero en SQLite | `TEXT` decimal exacto en tablas `STRICT` | `REAL`, `INTEGER` en centavos | SQLite no tiene `DECIMAL`; `REAL` es flotante y los centavos obligan a redondear montos intermedios. `TEXT` se lee sin pérdida con `BigDecimal`. |
| Stock | En memoria (requisito HU3) | Base de datos | Lo exige el enunciado. Decremento atómico para evitar sobreventa con checkouts concurrentes. _(pendiente)_ |

## 2. Diseño de carpetas

### Backend (`apps/backend`)

Arquitectura por capas con el dominio aislado del framework (puertos y adaptadores, versión ligera):

```
com.kata.checkout
├── domain/          # Reglas de negocio puras. Sin dependencias de Spring.
│   ├── model/       # Producto, línea de carrito, orden
│   ├── discount/    # Motor de descuentos y reglas (Strategy)
│   ├── port/        # Interfaces de stock y órdenes que la infraestructura implementa
│   └── exception/   # Errores de negocio
├── application/     # Casos de uso (checkout, catálogo). Orquesta el dominio.
├── infrastructure/  # Adaptadores: SQLite, stock en memoria, configuración, fábrica de reglas
└── api/             # Controladores REST, DTO y manejo de errores (Problem Details)
```

**Regla de dependencias:** `api → application → domain ← infrastructure`. El dominio no conoce a nadie.

### Base de datos (`packages/schema`)

```
users ─0..1──< checkouts >──┬── checkout_items >── products
                            └── checkout_discounts
```

- `checkout_items` es la pivote checkout × producto, con una copia del precio y la categoría del momento.
- `checkout_discounts` guarda un registro por paso de la cascada (CATEGORY → VOLUME → COUPON → CAP), con la tasa aplicada y los montos sin redondear.
- `products.initial_stock` solo siembra el stock en memoria; el stock vivo no se persiste.
- `checkouts.user_id` es opcional (invitado), porque el cliente solo envía `productId`, cantidad y cupón.

Detalle, decisiones y pendientes de integración en [`packages/schema/README.md`](../packages/schema/README.md).

### Frontend (`apps/frontend/src/app`)

```
core/       # Cliente HTTP, modelos generados del contrato, interceptores
features/
  catalog/  # Lista de productos (HU1)
  cart/     # Estado del carrito con Signals (HU1)
  checkout/ # Cupón, desglose y alerta del 35% (HU2, HU4)
shared/     # Componentes de UI reutilizables
```

## 3. Aislamiento del motor de descuentos

_(pendiente)_ Cómo se separan las reglas matemáticas de la persistencia y de los controladores.

## 4. Patrones de diseño

| Patrón | Dónde | Problema que resuelve |
|---|---|---|
| Strategy | `domain/discount` | Cada regla de descuento es intercambiable y se prueba de forma aislada. |
| Factory | `infrastructure/config` | Construye las reglas a partir de la configuración, en el orden de precedencia. |
| Observer | Frontend (Signals) | La vista reacciona a los cambios del carrito sin sincronización manual. |

_(pendiente: confirmar y documentar con referencias al código)_

## 5. SOLID

_(pendiente)_

## 6. Diseño de la API

_(pendiente: endpoints, contratos y códigos de error)_

## 7. Supuestos y hallazgos

- **Orden de precedencia:** categoría → volumen → cupón → tope, en cascada multiplicativa sobre el subtotal original.
- **Umbral de volumen:** "supera los $100 USD" se interpreta como estrictamente mayor que 100, evaluado sobre el subtotal después del descuento de categoría.
- **Dinero:** `BigDecimal`, redondeo `HALF_EVEN` a 2 decimales solo en los totales finales.
- **Hallazgo — el cupón expirado no tiene fecha.** El caso de borde "cupón expirado" es obligatorio, pero `discounts.coupon` solo define `code` y `rate`. _(pendiente: agregar `discounts.coupon.expires-at` a la configuración, no una tabla de cupones, para mantener los valores en `discounts.*`)_
- **Checkout sin usuario autenticado.** No hay autenticación en el alcance; `checkouts.user_id` es opcional para no exigirle al cliente más que `productId`, cantidad y cupón.
- **Hallazgo — el tope del 35% no se alcanza con los valores actuales.** El descuento máximo posible es un carrito 100% de Tecnología, sobre $100 y con cupón: 1 − (0,90 × 0,95 × 0,85) = **27,325%**. Por eso las reglas son parametrizables: el tope actúa como protección ante cambios futuros de porcentajes, y la alerta de HU4 se demuestra con una configuración alternativa. _(pendiente: definir el perfil de demo)_

## 8. Trade-offs asumidos

_(pendiente)_ Ej.: simplicidad vs. extensibilidad, SQLite vs. motor de producción, cálculo síncrono vs. velocidad de entrega.

## 9. Seguridad

_(pendiente)_ Precios siempre del catálogo del servidor, validación de entrada, CORS restringido, errores sin trazas internas.
