# contracts

Contrato OpenAPI compartido entre `apps/backend` y `apps/frontend`.

- **Fuente de verdad:** `openapi.yaml`. Cualquier cambio en la API empieza aquí.
- **Frontend:** los tipos TypeScript (`core/models`) se escriben a mano según el contrato. _(pendiente: generarlos con un script, para que front y back no se desalineen)_
- **Backend:** las pruebas de la capa `api` validan cada respuesta contra el contrato con `swagger-request-validator-mockmvc` (`ApiIntegrationTest` en `apps/backend`).

Base: `http://localhost:8080/api/v1`.

## Endpoints

| Método | Ruta | `operationId` | Respuesta OK | Qué hace |
|---|---|---|---|---|
| `GET` | `/products` | `listProducts` | `200 Product[]` | Lista los productos activos con stock disponible mayor que cero. |
| `POST` | `/checkouts/preview` | `previewCheckout` | `200 CheckoutBreakdown` | Calcula el desglose sin guardar ni descontar stock. |
| `POST` | `/checkouts` | `createCheckout` | `201 Checkout` | Reserva el stock, aplica la cascada y guarda la orden en una sola transacción. |
| `GET` | `/health` (fuera de `/api/v1`) | `getHealth` | `200 {"status": "UP"}` | Estado del servicio para monitoreo. Responde `503` con `DOWN` si la base no está disponible. Sin detalles internos. |

### `GET /products`

```json
[
  { "id": 1, "name": "Laptop 14\"", "unitPrice": "1200.00", "category": "Tecnología", "availableStock": 10 }
]
```

`availableStock` es el stock vivo en memoria, que ya descuenta las ventas. No es `products.initial_stock`, porque ese valor no cambia y dejaría que el frontend ofreciera unidades que ya se vendieron. Los productos sin stock no aparecen.

### `POST /checkouts` y `POST /checkouts/preview`

Los dos reciben lo mismo:

```json
{
  "items": [
    { "productId": 1, "quantity": 2 },
    { "productId": 4, "quantity": 1 }
  ],
  "couponCode": "WELCOME2026"
}
```

- `items` necesita al menos un elemento. Si un `productId` se repite, las cantidades se suman.
- `couponCode` es opcional. Se compara ignorando mayúsculas y los espacios al inicio y al final.
- Se rechaza cualquier propiedad que no sea `productId`, `quantity` o `couponCode` (`additionalProperties: false`). Así el cliente no puede enviar un precio: el backend siempre lo toma de su catálogo.

La respuesta es el desglose. El checkout confirmado agrega `id` y `createdAt`:

```json
{
  "id": 1,
  "createdAt": "2026-10-15T19:30:00.000Z",
  "couponCode": "WELCOME2026",
  "lines": [
    { "productId": 1, "name": "Laptop 14\"", "category": "Tecnología", "quantity": 2, "unitPrice": "1200.00", "lineSubtotal": "2400.00" },
    { "productId": 4, "name": "Lámpara de escritorio", "category": "Hogar", "quantity": 1, "unitPrice": "40.00", "lineSubtotal": "40.00" }
  ],
  "discounts": [
    { "step": 1, "rule": "CATEGORY", "rate": "0.10", "reference": "Tecnología",  "baseAmount": "2400.00", "amount": "240.00" },
    { "step": 2, "rule": "VOLUME",   "rate": "0.05", "reference": "100.00",      "baseAmount": "2200.00", "amount": "110.00" },
    { "step": 3, "rule": "COUPON",   "rate": "0.15", "reference": "WELCOME2026", "baseAmount": "2090.00", "amount": "313.50" }
  ],
  "subtotal": "2440.00",
  "discountTotal": "663.50",
  "total": "1776.50",
  "maxDiscountRate": "0.35",
  "capApplied": false
}
```

- `discounts` solo trae los pasos que tuvieron efecto, ordenados por `step`. Si se aplicó el tope, aparece el paso `CAP`: su `baseAmount` es el descuento acumulado antes del tope y su `amount` es el exceso recortado. Es el mismo modelo que la tabla `checkout_discounts` de `packages/schema`.
- `capApplied: true` activa la alerta de HU4.
- La previsualización no reserva unidades. Un checkout puede fallar con `INSUFFICIENT_STOCK` aunque la previsualización haya funcionado.

## Tipos de dinero

| Esquema | Formato | Uso |
|---|---|---|
| `Money` | `^\d+\.\d{2}$` | Precios y totales finales, redondeados `HALF_EVEN`. Excepción: si se aplicó el tope, `discountTotal` se redondea hacia abajo para que nunca lo supere (subtotal 100.10 con tope 35% → `35.03`, no `35.04`). |
| `Decimal` | `^\d+(\.\d+)?$` | Montos intermedios de la cascada, sin redondear. El frontend los redondea solo para mostrarlos. |
| `Rate` | `0` a `1` | Tasas (`"0.15"` = 15%). |

Todos viajan como **string**. Si viajaran como número JSON, JavaScript los leería como `double`, lo que rompe la regla de no usar flotantes para el dinero. El backend los lee con `new BigDecimal(...)` y el frontend los opera con una librería decimal o en centavos enteros.

## Errores

Problem Details (RFC 9457) con `Content-Type: application/problem+json` y un `code` estable. El frontend elige el mensaje por `code`, nunca por `detail`. Ninguna respuesta incluye el mensaje de la base, el SQL ni la traza.

| `code` | HTTP | Cuándo | Campos extra |
|---|---|---|---|
| `VALIDATION_ERROR` | 400 | JSON corrupto, tipos incorrectos, cantidad < 1, más de 100 líneas en `items`, una propiedad no permitida (p. ej. un precio) o una `Idempotency-Key` que no es UUID. | `errors[]` con `field` y `message` |
| `EMPTY_CART` | 400 | `items` vacío. | — |
| `PRODUCT_NOT_FOUND` | 422 | El producto no existe o está inactivo. | `productId` |
| `INVALID_COUPON` | 422 | El cupón no está registrado. | — |
| `COUPON_EXPIRED` | 422 | El cupón está vencido: el instante actual es igual o posterior a `discounts.coupon.expires-at`. | — |
| `INSUFFICIENT_STOCK` | 409 | No alcanzan las unidades; no se reservó nada. | `productId`, `requested`, `available` |
| `IDEMPOTENCY_KEY_REUSED` | 409 | La `Idempotency-Key` ya se usó en `POST /checkouts` con otro cuerpo; no se creó otra orden. | — |
| `DATA_INTEGRITY_VIOLATION` | 422 | La base rechazó la orden por inconsistente; no se guardó. | — |
| `DATABASE_UNAVAILABLE` | 503 | La base no está disponible por ahora; se puede reintentar. | — |
| `PERSISTENCE_ERROR` | 500 | Error interno al procesar los datos. | — |
| `PAYLOAD_TOO_LARGE` | 413 | El cuerpo supera 16 KB (`app.limits.max-body-size`). Se rechaza antes de leer el JSON. | — |
| `TOO_MANY_REQUESTS` | 429 | La IP superó 60 peticiones por minuto entre `POST /checkouts` y `/checkouts/preview`, o acumuló 10 cupones rechazados en 15 minutos (`app.limits.*`). | Header `Retry-After` (segundos) |

## Decisiones

| Decisión | Elegido | Alternativa descartada | Razón |
|---|---|---|---|
| Rutas | `GET /products`, `POST /checkouts` | `/list-products`, `/checkout` | Convención REST: el recurso va en plural y la acción la da el método HTTP. |
| Stock en el catálogo | `availableStock` (vivo) | `initialStock` | El filtro "en stock" usa el stock vivo. Mostrar el inicial sería incoherente después de la primera venta. |
| Dinero en JSON | String decimal | Número JSON | Exacto de punta a punta, sin flotantes. |
| Previsualización | `POST /checkouts/preview` | Solo dos endpoints | Sin ella, el desglose y la alerta del tope solo se ven después de comprar. |
| Respuesta del checkout | Desglose completo con `capApplied` | Solo el `id` | El frontend no repite el cálculo: muestra lo que decidió el backend. |
| Request cerrado | `additionalProperties: false` | Ignorar campos extra | Un precio enviado por el cliente se rechaza en lugar de ignorarse en silencio. |
| Compra doble | Header opcional `Idempotency-Key` (UUID) en `POST /checkouts` | Bloquear solo en el cliente | Un reintento de red o un doble clic que el cliente no alcance a bloquear devuelve la misma orden (201, mismo `id`) sin reservar stock otra vez. "Mismo cuerpo" se compara por un hash del carrito normalizado (cantidades del mismo producto sumadas y ordenadas por `productId`) y el cupón; otro cuerpo con la misma clave es `IDEMPOTENCY_KEY_REUSED` (409). |

| Límites contra abuso | `maxItems: 100`, cuerpo de 16 KB, rate limiting por IP con 429 y `Retry-After` | Sin límites | Sin ellos, un cliente puede forzar trabajo con cuerpos enormes o adivinar cupones, porque `INVALID_COUPON` confirma si un código existe. Tras 10 cupones rechazados la IP se bloquea un tiempo también con un cupón válido, así que la respuesta deja de servir como oráculo. |

## Pendientes

- Generar los tipos TypeScript del contrato.
- Validar el contrato con un linter (p. ej. `@redocly/cli lint`). Hoy se verificó con un script local que resuelve las `$ref` y valida los ejemplos contra sus esquemas.
