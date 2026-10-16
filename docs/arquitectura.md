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
| Stock | En memoria (requisito HU3) | Base de datos | Lo exige el enunciado. Decremento atómico por producto (`ConcurrentHashMap.compute` en `InMemoryStockRepository`) para evitar sobreventa con checkouts concurrentes. |

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
core/
  api/      # CatalogApi (GET /products), CheckoutApi (POST /checkouts/preview), readProblem y API_BASE_URL
  models/   # Product y tipos del checkout, escritos a mano según el contrato (pendiente: generarlos)
  money/    # formatMoney, cents (centavos bigint) y decimal (HALF_EVEN y porcentajes, sin number)
features/
  catalog/  # CatalogPage: lista de productos y selección de cantidades (HU1)
  cart/     # CartStore: estado del carrito con Signals (HU1)
  checkout/ # CheckoutPage (productos, cupón, desglose y alerta del tope), mensajes por code y cartNotEmptyGuard (pendiente: confirmar la compra)
shared/     # Componentes de UI reutilizables
```

- **El carrito guarda cantidades y muestra el subtotal.** `CartStore` guarda cada producto elegido con su cantidad. Al checkout solo envía `productId` y `quantity`. Muestra en tiempo real el subtotal antes de descuentos (precio × cantidad); los descuentos y el total final los calcula siempre el backend.
- **Subtotal exacto en centavos `bigint`.** Para no romper la regla de no usar flotantes, `"89.90"` se convierte a `8990n` y se opera como entero: 3 × 89.90 da exactamente `269.70` (con `number` daría `269.70000000000005`). Se descartó llamar a `POST /checkouts/preview` en cada cambio: sería una petición por clic, y el subtotal no necesita reglas de negocio.
- **Cantidades saneadas en el cliente.** Una cantidad negativa, decimal o no numérica se corrige a un entero entre 0 y `availableStock`. Al recargar el catálogo, `reconcile` recorta la selección al stock vigente y quita los productos que ya no están. Es una ayuda de UX: el backend vuelve a validar todo.
- **Al checkout solo se entra desde el botón.** `cartNotEmptyGuard` redirige a `/` si el carrito está vacío. Como el carrito vive en memoria, eso cubre una URL escrita a mano y una recarga de la página.
- **El cupón se guarda en el carrito sin validarlo.** `CartStore` guarda el texto tal como se escribió (hasta 50 caracteres, el máximo del contrato) y expone una versión sin espacios, o `null` si está en blanco, para enviarla. El backend decide si es válido o expiró.
- **"Aplicar descuento" usa el preview del backend.** No hay tabla de cupones: el cupón vive en `discounts.coupon` (regla de `CLAUDE.md`). El botón envía el carrito y el cupón a `POST /checkouts/preview`, que valida el cupón (`INVALID_COUPON`, `COUPON_EXPIRED`) y calcula la cascada completa: categoría, volumen, cupón y tope. No guarda la compra ni reserva stock. Sin cupón, calcula solo los demás descuentos.
- **El frontend no recalcula descuentos, solo los muestra.** Cada paso llega sin redondear y se muestra redondeado `HALF_EVEN` (la misma regla del backend) con aritmética `bigint`. El ajuste del tope se muestra como la suma de esos pasos menos el `discountTotal` del backend, para que el desglose siempre cuadre a la vista. Sin tope, la suma de los pasos redondeados puede diferir en un centavo del descuento total; el total autoritativo es siempre el del backend.
- **Errores por `code`, nunca por `detail`.** `readProblem` reduce cualquier error a un `code` del contrato, o a `NETWORK_ERROR` o `UNKNOWN_ERROR`. Los errores del cupón se muestran junto al campo (`aria-invalid` y `aria-describedby`). Los demás, como el stock insuficiente, se muestran como alerta y nombran el producto. Cambiar el cupón descarta el desglose y el error anteriores.
- **Alerta del tope (HU4).** Si `capApplied` es `true`, se muestra un `role="alert"` con el tope y el descuento limitado. Con la configuración actual no se alcanza (máximo 27,325%), así que solo la cubren las pruebas unitarias.
- **Sin correo electrónico.** Se evaluó pedirlo en el checkout, pero no está en el contrato (`CheckoutRequest` es cerrado) y `users` exige también el nombre. Se decidió no incluirlo.
- **Dinero solo como texto.** `formatMoney("1200.00")` da `"USD 1,200.00"` insertando separadores sobre el string, sin pasar por `number`.
- **Accesibilidad básica:** `lang="es"`, enlace para saltar al contenido, un título de página por ruta, foco visible, etiquetas que nombran el producto ("Cantidad de Cuaderno"), botones `−`/`+` con nombre accesible, estados de carga (`role="status"`) y de error (`role="alert"`) anunciados, resumen del carrito en una región `aria-live`, tabla del checkout con `caption` y encabezados con `scope` (en pantallas angostas oculta la columna de precio y muestra "2 × USD 1,200.00" bajo el nombre), áreas táctiles de 44 px y colores con contraste AA.

## 3. Aislamiento del motor de descuentos

- **Las reglas son dominio puro.** `domain/discount` no importa Spring, Jakarta ni otras capas (`ArchitectureTest` lo verifica). Cada regla (`CategoryDiscount`, `VolumeDiscount`, `CouponDiscount`) implementa `DiscountStrategy` y solo ve un `DiscountContext`: líneas con precio del catálogo, subtotal original, monto restante y cupón enviado.
- **El motor encadena y topa.** `DiscountEngine` ordena las reglas por `DiscountRule.step` (no por el orden de inyección), aplica la cascada multiplicativa, trunca en `max-total-rate` registrando el paso `CAP` y redondea solo los totales. Devuelve un `DiscountResult` sin saber de HTTP ni de SQL.
- **La configuración entra por una fábrica.** `DiscountEngineFactory` traduce `discounts.*` (`DiscountProperties`) a las reglas; es el único punto que conoce a la vez Spring y el dominio. El reloj (`Clock`) se inyecta para probar el vencimiento del cupón.
- **Los casos de uso orquestan.** `CheckoutService` arma el carrito, toma los precios de `ProductCatalog`, llama al motor y solo después reserva stock y guarda con `OrderRepository`. Los controladores de `api` solo traducen DTO ↔ comandos y resultados.
- Consecuencia: el motor se prueba sin Spring ni base (`DiscountEngineTest`) y cambiar un porcentaje es cambiar `application.yml`.

## 4. Patrones de diseño

| Patrón | Dónde | Problema que resuelve |
|---|---|---|
| Strategy | `domain/discount` | Cada regla de descuento es intercambiable y se prueba de forma aislada. |
| Factory | `infrastructure/config` | Construye las reglas a partir de la configuración, en el orden de precedencia. |
| Observer | Frontend (Signals) | La vista reacciona a los cambios del carrito sin sincronización manual. |

Referencias en el backend: Strategy en `DiscountStrategy` y sus tres implementaciones; Factory en `DiscountEngineFactory.create(DiscountProperties, Clock)`. Observer queda por documentar con el frontend.

## 5. SOLID

_(pendiente)_

## 6. Diseño de la API

Contrato en [`packages/contracts/openapi.yaml`](../packages/contracts/openapi.yaml). Detalle, ejemplos y decisiones en [`packages/contracts/README.md`](../packages/contracts/README.md).

| Método | Ruta | Qué hace |
|---|---|---|
| `GET` | `/api/v1/products` | Productos activos con stock disponible (vivo, no el inicial). |
| `POST` | `/api/v1/checkouts/preview` | Desglose de descuentos sin guardar ni descontar stock. |
| `POST` | `/api/v1/checkouts` | Reserva el stock, aplica la cascada y guarda la orden en una sola transacción. |
| `GET` | `/health` | Estado del servicio: `UP` (200) o `DOWN` (503). Es el health de Actuator publicado en la raíz, así que incluye la base de datos; no expone detalles. |

- **Request cerrado:** el cliente solo envía `productId`, `quantity` y `couponCode`. Cualquier otra propiedad, como un precio, se rechaza con `VALIDATION_ERROR`.
- **Dinero como string decimal** en el JSON, para que ningún extremo lo lea como flotante.
- **La respuesta es el desglose completo** (líneas, pasos de la cascada, totales y `capApplied`). El frontend no repite el cálculo.
- **Errores:** Problem Details con `code` estable: `VALIDATION_ERROR`, `EMPTY_CART`, `PRODUCT_NOT_FOUND`, `INVALID_COUPON`, `COUPON_EXPIRED`, `INSUFFICIENT_STOCK`, `DATA_INTEGRITY_VIOLATION`, `DATABASE_UNAVAILABLE` y `PERSISTENCE_ERROR`.

Implementación: `ProductController` y `CheckoutController` en `api`, con DTO en `api/dto`. El request se cierra con `spring.jackson.deserialization.fail-on-unknown-properties` y sin coerción de escalares (`"2"` o `1.5` como cantidad se rechazan). Las pruebas de `api` validan cada respuesta contra `openapi.yaml` con `swagger-request-validator-mockmvc`.

## 7. Supuestos y hallazgos

- **Orden de precedencia:** categoría → volumen → cupón → tope, en cascada multiplicativa sobre el subtotal original.
- **Umbral de volumen:** "supera los $100 USD" se interpreta como estrictamente mayor que 100, evaluado sobre el subtotal después del descuento de categoría.
- **Dinero:** `BigDecimal`, redondeo `HALF_EVEN` a 2 decimales solo en los totales finales.
- **El tope prevalece sobre `HALF_EVEN`.** Cuando se aplica el tope, el descuento total se redondea hacia abajo (`RoundingMode.DOWN`): con `HALF_EVEN` podía quedar medio centavo por encima. Ejemplo: subtotal 100.10, tope 35% = 35.035 → `HALF_EVEN` daría 35.04 (> 35%); se entrega **35.03** y el total es 65.07. Sin tope, se sigue usando `HALF_EVEN`.
- **Hallazgo — el cupón expirado no tenía fecha.** El caso de borde "cupón expirado" es obligatorio, pero `discounts.coupon` solo definía `code` y `rate`. Se agregó `discounts.coupon.expires-at` (instante ISO-8601, exclusivo: desde ese instante el cupón responde `COUPON_EXPIRED`) en lugar de una tabla de cupones, para mantener los valores en `discounts.*`. Hoy vale `2027-01-01T00:00:00Z`, es decir, todo 2026 en UTC.
- **Checkout sin usuario autenticado.** No hay autenticación en el alcance; `checkouts.user_id` es opcional para no exigirle al cliente más que `productId`, cantidad y cupón.
- **Hallazgo — el tope del 35% no se alcanza con los valores actuales.** El descuento máximo posible es un carrito 100% de Tecnología, sobre $100 y con cupón: 1 − (0,90 × 0,95 × 0,85) = **27,325%**. Por eso las reglas son parametrizables: el tope actúa como protección ante cambios futuros de porcentajes, y la alerta de HU4 se demuestra con una configuración alternativa. _(pendiente: definir el perfil de demo)_

## 8. Trade-offs asumidos

_(pendiente)_ Ej.: simplicidad vs. extensibilidad, SQLite vs. motor de producción, cálculo síncrono vs. velocidad de entrega.

## 9. Seguridad

_(pendiente)_ Precios siempre del catálogo del servidor, validación de entrada, CORS restringido, errores sin trazas internas.
