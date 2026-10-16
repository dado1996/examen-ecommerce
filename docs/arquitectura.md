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
├── infrastructure/  # Adaptadores: SQLite, stock en memoria, configuración, CORS, headers de seguridad
└── api/             # Controladores REST, DTO, errores (Problem Details) y límites de las peticiones
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
  api/      # CatalogApi (GET /products), CheckoutApi (preview y POST /checkouts), readProblem y API_BASE_URL
  models/   # Product y tipos del checkout, escritos a mano según el contrato (pendiente: generarlos)
  money/    # formatMoney, cents (centavos bigint) y decimal (HALF_EVEN y porcentajes, sin number)
features/
  catalog/  # CatalogPage: lista de productos y selección de cantidades (HU1)
  cart/     # CartStore: estado del carrito con Signals (HU1)
  checkout/ # CheckoutPage (productos, cupón, desglose, alerta del tope y pago), mensajes por code y cartNotEmptyGuard
  confirmation/ # ConfirmationPage, ConfirmationStore y hasConfirmationGuard
shared/ui/  # BreakdownPanel y breakdownRows: el desglose que comparten checkout y confirmación
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
- **"Ir a pagar" confirma con `POST /checkouts`.** El backend vuelve a validar todo (productos, stock, cupón y descuentos), reserva el stock y guarda la orden en una sola transacción. No hace falta aplicar el descuento antes: si hay un cupón escrito se envía igual. Con éxito, la respuesta (orden con id, fecha y desglose) se guarda en `ConfirmationStore`, se vacía el carrito y se navega a `/confirmacion`. Si falla, el carrito se conserva y el error se muestra igual que en el preview. No hay pasarela de pago: el botón registra la orden, no cobra.
- **Sin compra doble.** Mientras hay una petición en curso, "Aplicar descuento" e "Ir a pagar" se deshabilitan. Eso no cubre un reintento de red ni un doble clic que llegue antes de deshabilitar, así que cada intento de pago envía además una `Idempotency-Key` (`crypto.randomUUID()`). La clave sale de un `computed` sobre las líneas del carrito y el cupón normalizado: los reintentos del mismo intento la reutilizan y el backend devuelve la orden ya creada; cambiar productos, cantidades o cupón la descarta. Si el backend responde `IDEMPOTENCY_KEY_REUSED`, se muestra el error y el siguiente intento usa otra clave.
- **La confirmación vive en memoria.** El contrato no expone `GET /checkouts/{id}`, así que `/confirmacion` muestra la respuesta guardada en `ConfirmationStore`. `hasConfirmationGuard` redirige a productos si no hay una compra recién confirmada, por ejemplo al recargar la página. Al llegar, el foco va al título para que los lectores de pantalla anuncien la confirmación.
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
| `POST` | `/api/v1/checkouts` | Reserva el stock, aplica la cascada y guarda la orden en una sola transacción. Acepta `Idempotency-Key` (UUID) para que un reintento no cree otra orden. |
| `GET` | `/health` | Estado del servicio: `UP` (200) o `DOWN` (503). Es el health de Actuator publicado en la raíz, así que incluye la base de datos; no expone detalles. |

- **Request cerrado:** el cliente solo envía `productId`, `quantity` y `couponCode`. Cualquier otra propiedad, como un precio, se rechaza con `VALIDATION_ERROR`.
- **Dinero como string decimal** en el JSON, para que ningún extremo lo lea como flotante.
- **La respuesta es el desglose completo** (líneas, pasos de la cascada, totales y `capApplied`). El frontend no repite el cálculo.
- **Idempotencia del checkout:** con el header opcional `Idempotency-Key`, la misma clave y el mismo pedido devuelven la orden ya creada (201, mismo `id`) sin validar ni reservar stock otra vez. La misma clave con otro pedido responde 409 `IDEMPOTENCY_KEY_REUSED`. "Mismo pedido" se compara con un SHA-256 del carrito normalizado (cantidades sumadas por producto y ordenadas) y el cupón, guardado en `checkouts.request_hash` junto a `checkouts.idempotency_key` (`UNIQUE`). `CheckoutService` busca la clave, reserva y guarda dentro de una sola transacción (`UnitOfWork`, implementado con `TransactionTemplate`); como el pool de SQLite tiene una conexión, dos peticiones simultáneas con la misma clave se serializan y solo una reserva stock. Un intento que falla no guarda la clave, así que se puede reintentar con ella.
- **Límites contra abuso:** `items` admite como máximo 100 líneas (`VALIDATION_ERROR`), el cuerpo de `/api/**` como máximo 16 KB (413 `PAYLOAD_TOO_LARGE`), y `POST /checkouts` y `/checkouts/preview` tienen rate limiting por IP (429 `TOO_MANY_REQUESTS` con `Retry-After`). Detalle en la sección 9.
- **Errores:** Problem Details con `code` estable: `VALIDATION_ERROR`, `EMPTY_CART`, `PRODUCT_NOT_FOUND`, `INVALID_COUPON`, `COUPON_EXPIRED`, `INSUFFICIENT_STOCK`, `IDEMPOTENCY_KEY_REUSED`, `DATA_INTEGRITY_VIOLATION`, `DATABASE_UNAVAILABLE`, `PERSISTENCE_ERROR`, `PAYLOAD_TOO_LARGE` y `TOO_MANY_REQUESTS`.

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

Cada decisión ganó algo a cambio de algo. La columna "Cuándo revisarlo" indica qué cambio en los requisitos la vuelve incorrecta.

| Decisión | Qué ganamos | Qué cedimos | Cuándo revisarlo |
|---|---|---|---|
| **SQLite** en lugar de un motor de producción (PostgreSQL) | Cero instalación para la demo, transacciones ACID y un archivo que se recrea en segundos. | Un solo escritor a la vez (pool de 1 conexión para evitar `SQLITE_BUSY`), sin `DECIMAL` nativo y sin escalar a varias instancias. Las columnas nuevas obligan a recrear la base, porque `IF NOT EXISTS` no altera tablas. | Con más de una instancia del backend o escrituras concurrentes reales. El esquema y el SQL son estándar en su mayoría, salvo `STRICT` y las guardas con `GLOB`. |
| **Spring JDBC** en lugar de JPA | Control total del SQL, sin dialecto no oficial de Hibernate, y lectura de dinero exacta con `SqliteMoney`. | Más código de mapeo escrito a mano (`RowMapper`, inserts por tabla). | Si el modelo crece mucho en entidades y relaciones. |
| **Stock en memoria** (lo exige HU3) | Reserva atómica por producto (`ConcurrentHashMap.compute`) sin bloqueos de base, y respuestas rápidas. | **Al reiniciar, el stock vuelve a `initial_stock`**: las unidades vendidas reaparecen aunque las órdenes estén guardadas. No se comparte entre instancias. | Fuera de la demo: persistir el stock o reconstruirlo restando las líneas guardadas al arrancar. |
| **Dinero como texto decimal** (`TEXT` en SQLite y string en JSON) | Exactitud de punta a punta: ningún extremo lo lee como flotante. | Más código: `SqliteMoney` en el backend, `cents` y `decimal` (bigint) en el frontend, y guardas `CHECK` con `CAST(... AS REAL)` y tolerancia, que no son aritmética exacta. | No se revisaría: es una regla del proyecto. |
| **Copias en cada orden** (precio, categoría, tasas y referencias) | Una orden sigue siendo auditable aunque cambien el catálogo o la configuración. | Datos duplicados entre `products`, `checkout_items` y `checkout_discounts`. | No se revisaría: es el costo normal de un registro financiero. |
| **Reglas en `discounts.*`** (también el cupón) en lugar de una tabla | Una sola fuente para todas las reglas, validada al arrancar y versionada en git. | Un solo cupón. Cambiarlo exige reiniciar, y el negocio no lo administra sin un desarrollador. | Con varios cupones, campañas o límites de uso: tabla `coupons` detrás de un puerto `CouponRepository`. |
| **Strategy + Factory** para las reglas | Cada regla se prueba aislada y se agrega una nueva sin tocar el motor. | Más clases que un único método con cuatro `if`. | — |
| **Redondeo `DOWN` solo con el tope** | La regla "nunca supera el 35%" se cumple al centavo. | Una excepción a la regla general `HALF_EVEN`, que hay que conocer (documentada en `CLAUDE.md`). | — |
| **Cálculo síncrono en cada petición**, sin cotización guardada | El desglose siempre usa precios, stock y configuración vigentes, y no hay estado de cotización que expire. | Preview y checkout calculan dos veces. El preview no reserva stock, así que el pago puede fallar con `INSUFFICIENT_STOCK` aunque el preview haya funcionado. | Si el cálculo se vuelve costoso o hace falta "congelar" un precio por un tiempo. |
| **Subtotal calculado en el frontend** (bigint) en lugar de llamar al preview en cada cambio | Respuesta instantánea y ninguna petición por clic. | El frontend suma dinero, aunque solo precio × cantidad y nunca descuentos. | Si el subtotal llegara a depender de reglas de negocio. |
| **Confirmación en memoria** en lugar de `GET /checkouts/{id}` | Contrato más pequeño y sin exponer órdenes por id sin autenticación. | Al recargar `/confirmacion` se pierde la vista, aunque la orden sigue guardada. | Cuando exista autenticación: agregar el endpoint restringido al dueño de la orden. |
| **Clave de idempotencia generada por el cliente** | Un reintento de red o un doble clic no crea dos órdenes ni reserva stock dos veces. | Una columna, una huella SHA-256 del pedido y un código de error más (`IDEMPOTENCY_KEY_REUSED`). | — |
| **Rate limiting en memoria** (GCRA propio, sin Bucket4j ni Redis) | Sin dependencias nuevas: un instante por IP en un `ConcurrentHashMap`, probado con un reloj falso. | Cada instancia lleva su propia cuenta y se pierde al reiniciar. Se limita por IP, así que una oficina detrás de un NAT comparte el límite y un atacante con muchas IPs lo esquiva. | Con más de una instancia: mover la cuenta a Redis o al API gateway. |
| **Tipos del frontend escritos a mano** según el contrato | Avanzar sin configurar un generador. | Riesgo de desalineación: el backend valida sus respuestas contra `openapi.yaml`, pero el frontend no. | Antes de que el contrato crezca: generar los tipos (pendiente desde la sección 1). |

## 9. Seguridad

### Medidas implementadas

| Riesgo | Medida | Dónde | Cómo se verifica |
|---|---|---|---|
| **Manipulación de precios** desde el cliente | El cliente solo envía `productId`, `quantity` y `couponCode`. El precio, la categoría y los descuentos salen siempre del catálogo y la configuración del servidor. | `CheckoutService`, `JdbcProductCatalog` | `elPrecioSiempreSaleDelCatalogo` |
| **Campos inyectados** en el pedido (p. ej. un precio o un total) | Request cerrado: `additionalProperties: false` en el contrato, `fail-on-unknown-properties: true` en Jackson y sin coerción de tipos (`"2"` o `1.5` no se aceptan como cantidad). | `openapi.yaml`, `application.yml` | `rechazaUnPrecioEnviadoPorElCliente`, `unaCantidadConTipoIncorrectoSeRechaza` y la prueba de mutación de casos de borde |
| **Datos corruptos o fuera de rango** | Validación en tres capas: Bean Validation en los DTO (cantidad ≥ 1, cupón de 1 a 50 caracteres), invariantes del dominio (`Cart`, `OrderLine`, `Order`) y restricciones de la base (`STRICT`, `CHECK`, llaves foráneas activas en cada conexión). | `api/dto`, `domain/model`, `schema.sql` | `verify.sh` (36 casos) y pruebas de dominio y persistencia |
| **Inyección SQL** | Todas las consultas usan parámetros `?` de `JdbcTemplate`. Solo se concatenan constantes del propio código, nunca datos del usuario. | `infrastructure/persistence` | Revisión del código |
| **Fuga de información en errores** | Problem Details (RFC 9457) con un `code` estable. Las respuestas nunca incluyen el mensaje de SQLite, el SQL ni la traza; el detalle completo va solo al log del servidor. | `ApiExceptionHandler` | `laRespuestaDeIntegridadNoFiltraElMensajeDeSqliteNiLaTraza`, `unJsonCorruptoSeRechazaSinFiltrarDetallesInternos` |
| **Acceso desde otros sitios** | CORS restringido a `APP_CORS_ALLOWED_ORIGINS` (por defecto `http://localhost:4200`), solo `GET` y `POST`, y solo los headers `Content-Type` e `Idempotency-Key`. Expone `Retry-After` para que el frontend pueda leerlo en una 429. | `WebConfig` | `CorsApiTest`: rechaza otro origen y otro header, y expone `Retry-After` |
| **Pedidos enormes** que fuerzan trabajo | `items` admite como máximo 100 líneas (`@Size` en el DTO y `maxItems` en el contrato): el backend busca cada una en el catálogo, y un carrito real usa pocas líneas porque las unidades van en `quantity`. Más de 100 es `VALIDATION_ERROR`. | `CheckoutRequest`, `openapi.yaml` | `TamanoDelPedidoApiTest`: 101 líneas se rechazan y 100 pasan |
| **Cuerpos grandes** | Máximo 16 KB en `/api/**` (`app.limits.max-body-size`); 100 líneas ocupan unos 4 KB. Si el `Content-Length` lo supera, se rechaza antes de que Jackson lea un byte; si no se declara (envío por partes), la lectura se corta al pasar el límite. Responde 413 `PAYLOAD_TOO_LARGE`. | `BodySizeLimitFilter`, `ApiExceptionHandler` | `TamanoDelPedidoApiTest` (16 KB exactos pasan, uno más no, y se mide en bytes) y `BodySizeLimitFilterTest` (sin `Content-Length`) |
| **Abuso del checkout** con muchas peticiones | Rate limiting por IP en `POST /checkouts` y `/checkouts/preview`, con una sola cuenta para ambos: 60 por minuto, ráfagas incluidas (GCRA en memoria, sin dependencias). Responde 429 `TOO_MANY_REQUESTS` con `Retry-After` en segundos. El listado de productos y los preflight CORS no cuentan. | `RateLimiter`, `RateLimitInterceptor`, `ApiLimitsConfig` | `RateLimiterTest` (con reloj falso) y `RateLimitingApiTest` |
| **Enumeración de cupones** (`INVALID_COUPON` confirma si un código existe) | Cada cupón rechazado (`INVALID_COUPON` o `COUPON_EXPIRED`) cuenta para la IP; con 10 en 15 minutos queda bloqueada en ambos endpoints, **también con un cupón válido**, así que la respuesta deja de servir para confirmar códigos. Un usuario que se equivoca al tipear no llega al límite. | `RateLimitInterceptor`, `ApiExceptionHandler` | `variosCuponesRechazadosBloqueanLaIpAunqueLuegoUseUnCuponValido` |
| **Respuestas interpretadas como otra cosa** (MIME sniffing, incrustación en otro sitio) | Todas las respuestas, incluidos errores y `/health`, llevan `X-Content-Type-Options: nosniff`, `Referrer-Policy: no-referrer` y `Content-Security-Policy: default-src 'none'; frame-ancestors 'none'; base-uri 'none'; form-action 'none'`: la API solo devuelve JSON, así que no hay nada que cargar ni ejecutar. Swagger UI recibe una política limitada a su propio origen para poder cargar sus scripts. | `SecurityHeadersFilter` | `HeadersDeSeguridadApiTest` |
| **Exposición de internos operativos** | Actuator expone solo `/health`, con `show-details: never`: responde `UP` o `DOWN` sin componentes, rutas ni versiones. | `application.yml` | `elEstadoDelServicioNoExponeDetallesInternos` |
| **Compra doble** (reintentos o doble clic) | `Idempotency-Key` (UUID) verificada en la misma transacción que guarda la orden. Una clave reutilizada con otro pedido responde 409. En el frontend, los botones se deshabilitan mientras hay una petición en curso. | `CheckoutService`, `CheckoutPage` | `IdempotenciaConcurrenteTest` y pruebas de `CheckoutPage` |
| **Sobreventa** con checkouts concurrentes | Reserva atómica por producto; si falla una línea o el guardado, se devuelve lo reservado. | `InMemoryStockRepository`, `CheckoutService` | `lasReservasConcurrentesNuncaVendenMasDelStockDisponible` |
| **Configuración inválida** | `DiscountProperties` con `@Validated`: tasas fuera de [0, 1], un tope en 0 o un cupón sin código impiden arrancar. Los decimales se leen como texto para no pasar por `double`. | `DiscountProperties`, `application.yml` | `CheckoutApplicationTests`, `ReglasDeDescuentoTest` |
| **XSS en el frontend** | Todo se muestra con la interpolación de Angular, que escapa el contenido. No se usa `innerHTML` ni `bypassSecurityTrust*`. | `apps/frontend` | Revisión del código |
| **Secretos versionados** | `.env` y `.env.*` en `.gitignore`. No hay secretos en el repositorio: la configuración sensible entra por variables de entorno. | `.gitignore` | `git ls-files` sin archivos `.env`, de claves ni de certificados |

### Riesgos conocidos y fuera de alcance

Son riesgos reales que el MVP no resuelve. Quedan documentados para no presentarlos como cubiertos.

- **Sin autenticación ni autorización.** Cualquiera que llegue al backend puede comprar. `checkouts.user_id` existe pero el checkout es de invitado. Por eso no hay `GET /checkouts/{id}`: sin dueño, una orden sería legible por quien conozca su id.
- **Alcance del rate limiting.** La cuenta vive en memoria de cada instancia y se pierde al reiniciar. Es por IP: un atacante con muchas IPs puede repartir intentos de cupones, y varios usuarios detrás de un mismo NAT comparten el límite. Detrás de un proxy hay que activar `server.forward-headers-strategy` para que `getRemoteAddr()` sea la IP del cliente y no la del proxy (y solo si el proxy es de confianza, porque `X-Forwarded-For` se puede falsificar). Con varias instancias, la cuenta debe ir a Redis o al API gateway.
- **Sin HTTPS.** La demo corre en `http://localhost`. En producción haría falta TLS y, con él, `Strict-Transport-Security`, que no se envía porque sobre HTTP no tiene efecto.
- **Alcance de la clave de idempotencia.** La clave es global, no por usuario. Un UUID v4 no se puede adivinar en la práctica, pero quien conozca la clave y el pedido exacto de otro cliente recibiría esa orden. Con autenticación, la clave debe quedar asociada al usuario.
- **Pago simulado.** "Ir a pagar" registra la orden y reserva stock, pero no hay pasarela de pago, así que no se manejan datos de tarjeta. Integrar una implicaría cumplir PCI DSS, idealmente delegando los datos de pago al proveedor.
- **Dependencias.** Las versiones están fijadas en `pom.xml` y `package-lock.json`, pero no hay un escaneo automático de vulnerabilidades. _(pendiente: `npm audit` y OWASP Dependency-Check o Dependabot en CI)_
