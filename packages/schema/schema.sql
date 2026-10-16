-- =============================================================================
-- Esquema SQLite del checkout con descuentos acumulativos.
-- Documentación completa: packages/schema/README.md
--
-- Convenciones:
--   * Tablas STRICT: SQLite rechaza valores de un tipo distinto al declarado.
--   * Dinero y tasas en TEXT con el decimal exacto ('1200.00', '0.15'). Se leen
--     con BigDecimal; nunca se guardan como REAL (punto flotante).
--   * Los CAST(... AS REAL) de los CHECK son solo guardas de consistencia con
--     tolerancia; el cálculo autoritativo es el del dominio con BigDecimal.
--   * Fechas en TEXT ISO-8601 UTC.
--   * Ningún valor de las reglas (10%, 100 USD, WELCOME2026, 35%) está fijo aquí:
--     salen de discounts.* y cada checkout guarda una copia de lo aplicado.
--   * Idempotente (IF NOT EXISTS): se puede ejecutar en cada arranque.
-- =============================================================================

-- SQLite no aplica las llaves foráneas si no se activa en cada conexión.
PRAGMA foreign_keys = ON;

-- -----------------------------------------------------------------------------
-- Usuarios. No hay autenticación en el alcance actual, por eso no hay contraseña.
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS users (
    id          INTEGER PRIMARY KEY,
    email       TEXT    NOT NULL COLLATE NOCASE UNIQUE
                CHECK (email LIKE '_%@_%'),
    full_name   TEXT    NOT NULL CHECK (length(trim(full_name)) > 0),
    created_at  TEXT    NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now'))
) STRICT;

-- -----------------------------------------------------------------------------
-- Catálogo de productos. Es la única fuente de precios: el cliente nunca envía
-- el precio. initial_stock solo siembra el stock en memoria al arrancar; el stock
-- vivo no se guarda aquí (ver docs/arquitectura.md, HU3).
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS products (
    id             INTEGER PRIMARY KEY,
    name           TEXT    NOT NULL CHECK (length(trim(name)) > 0),
    -- Se compara contra discounts.category.name (p. ej. 'Tecnología'), en Unicode NFC.
    category       TEXT    NOT NULL CHECK (length(trim(category)) > 0),
    -- USD con exactamente 2 decimales y mayor que cero.
    unit_price     TEXT    NOT NULL
                   CHECK (unit_price GLOB '[0-9]*.[0-9][0-9]'
                          AND unit_price NOT GLOB '*[^0-9.]*'
                          AND unit_price NOT GLOB '*.*.*'
                          AND CAST(unit_price AS REAL) > 0),
    initial_stock  INTEGER NOT NULL CHECK (initial_stock >= 0),
    -- Un producto vendido no se borra (lo protege la FK); se desactiva.
    active         INTEGER NOT NULL DEFAULT 1 CHECK (active IN (0, 1)),
    created_at     TEXT    NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now'))
) STRICT;

CREATE INDEX IF NOT EXISTS idx_products_category ON products (category);

-- -----------------------------------------------------------------------------
-- Cabecera del checkout (la orden confirmada). Totales finales redondeados
-- HALF_EVEN a 2 decimales.
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS checkouts (
    id                 INTEGER PRIMARY KEY,
    -- Opcional: checkout de invitado mientras no exista autenticación.
    user_id            INTEGER REFERENCES users (id) ON DELETE RESTRICT,
    -- Cupón aplicado, tal como estaba en discounts.coupon.code. NULL si no hubo cupón.
    coupon_code        TEXT    CHECK (coupon_code IS NULL OR length(trim(coupon_code)) > 0),
    -- Suma de las líneas, antes de descuentos.
    subtotal           TEXT    NOT NULL
                       CHECK (subtotal GLOB '[0-9]*.[0-9][0-9]'
                              AND subtotal NOT GLOB '*[^0-9.]*'
                              AND subtotal NOT GLOB '*.*.*'
                              AND CAST(subtotal AS REAL) > 0),
    discount_total     TEXT    NOT NULL
                       CHECK (discount_total GLOB '[0-9]*.[0-9][0-9]'
                              AND discount_total NOT GLOB '*[^0-9.]*'
                              AND discount_total NOT GLOB '*.*.*'),
    total              TEXT    NOT NULL
                       CHECK (total GLOB '[0-9]*.[0-9][0-9]'
                              AND total NOT GLOB '*[^0-9.]*'
                              AND total NOT GLOB '*.*.*'),
    -- Copia de discounts.max-total-rate vigente al confirmar.
    max_discount_rate  TEXT    NOT NULL
                       CHECK (max_discount_rate GLOB '[0-9]*'
                              AND max_discount_rate NOT GLOB '*[^0-9.]*'
                              AND max_discount_rate NOT GLOB '*.*.*'
                              AND max_discount_rate NOT GLOB '*.'
                              AND CAST(max_discount_rate AS REAL) BETWEEN 0 AND 1),
    created_at         TEXT    NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now')),

    -- total = subtotal - discount_total. Con 2 decimales, cualquier diferencia real es >= 0.01.
    CHECK (abs(CAST(subtotal AS REAL) - CAST(discount_total AS REAL) - CAST(total AS REAL)) < 0.005),
    -- El descuento nunca supera el tope. Se tolera medio centavo por el redondeo
    -- final HALF_EVEN, más un margen mínimo por la representación en REAL.
    CHECK (CAST(discount_total AS REAL)
           <= CAST(subtotal AS REAL) * CAST(max_discount_rate AS REAL) + 0.005 + 1e-9)
) STRICT;

CREATE INDEX IF NOT EXISTS idx_checkouts_user_id    ON checkouts (user_id);
CREATE INDEX IF NOT EXISTS idx_checkouts_created_at ON checkouts (created_at);

-- -----------------------------------------------------------------------------
-- Tabla pivote checkout × producto. Guarda una copia del precio y la categoría
-- del momento: si el catálogo cambia, la orden sigue siendo auditable.
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS checkout_items (
    checkout_id    INTEGER NOT NULL REFERENCES checkouts (id) ON DELETE CASCADE,
    product_id     INTEGER NOT NULL REFERENCES products (id) ON DELETE RESTRICT,
    quantity       INTEGER NOT NULL CHECK (quantity > 0),
    unit_price     TEXT    NOT NULL
                   CHECK (unit_price GLOB '[0-9]*.[0-9][0-9]'
                          AND unit_price NOT GLOB '*[^0-9.]*'
                          AND unit_price NOT GLOB '*.*.*'
                          AND CAST(unit_price AS REAL) > 0),
    category       TEXT    NOT NULL CHECK (length(trim(category)) > 0),
    -- unit_price × quantity, exacto con 2 decimales.
    line_subtotal  TEXT    NOT NULL
                   CHECK (line_subtotal GLOB '[0-9]*.[0-9][0-9]'
                          AND line_subtotal NOT GLOB '*[^0-9.]*'
                          AND line_subtotal NOT GLOB '*.*.*'),

    -- Un producto aparece una sola vez por checkout: las cantidades se consolidan.
    PRIMARY KEY (checkout_id, product_id),
    CHECK (abs(CAST(unit_price AS REAL) * quantity - CAST(line_subtotal AS REAL)) < 0.005)
) STRICT;

CREATE INDEX IF NOT EXISTS idx_checkout_items_product_id ON checkout_items (product_id);

-- -----------------------------------------------------------------------------
-- Descuentos aplicados: un registro por paso de la cascada que tuvo efecto.
--   step 1 CATEGORY  rate = discounts.category.rate   reference = nombre de la categoría
--   step 2 VOLUME    rate = discounts.volume.rate     reference = umbral (p. ej. '100.00')
--   step 3 COUPON    rate = discounts.coupon.rate     reference = código del cupón
--   step 4 CAP       rate = discounts.max-total-rate  reference = NULL
-- base_amount y amount van SIN redondear (el redondeo es solo de los totales finales).
-- En CAP, base_amount es el descuento acumulado antes del tope y amount es el
-- exceso recortado; por eso discount_total = suma(pasos 1..3) - CAP, redondeado.
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS checkout_discounts (
    checkout_id  INTEGER NOT NULL REFERENCES checkouts (id) ON DELETE CASCADE,
    step         INTEGER NOT NULL,
    rule         TEXT    NOT NULL,
    rate         TEXT    NOT NULL
                 CHECK (rate GLOB '[0-9]*'
                        AND rate NOT GLOB '*[^0-9.]*'
                        AND rate NOT GLOB '*.*.*'
                        AND rate NOT GLOB '*.'
                        AND CAST(rate AS REAL) > 0
                        AND CAST(rate AS REAL) <= 1),
    reference    TEXT,
    base_amount  TEXT    NOT NULL
                 CHECK (base_amount GLOB '[0-9]*'
                        AND base_amount NOT GLOB '*[^0-9.]*'
                        AND base_amount NOT GLOB '*.*.*'
                        AND base_amount NOT GLOB '*.'
                        AND CAST(base_amount AS REAL) > 0),
    amount       TEXT    NOT NULL
                 CHECK (amount GLOB '[0-9]*'
                        AND amount NOT GLOB '*[^0-9.]*'
                        AND amount NOT GLOB '*.*.*'
                        AND amount NOT GLOB '*.'
                        AND CAST(amount AS REAL) > 0),

    -- Cada regla aparece a lo sumo una vez y siempre en su posición de la cascada.
    PRIMARY KEY (checkout_id, step),
    CHECK ((rule = 'CATEGORY' AND step = 1)
        OR (rule = 'VOLUME'   AND step = 2)
        OR (rule = 'COUPON'   AND step = 3)
        OR (rule = 'CAP'      AND step = 4)),
    CHECK (CAST(amount AS REAL) <= CAST(base_amount AS REAL))
) STRICT;

-- -----------------------------------------------------------------------------
-- Resumen para el desglose y la alerta de HU4. cap_applied se deriva de la
-- existencia del paso CAP, para no duplicar el dato.
-- -----------------------------------------------------------------------------
CREATE VIEW IF NOT EXISTS checkout_summary AS
SELECT c.id,
       c.user_id,
       c.coupon_code,
       c.subtotal,
       c.discount_total,
       c.total,
       c.max_discount_rate,
       EXISTS (SELECT 1
                 FROM checkout_discounts d
                WHERE d.checkout_id = c.id
                  AND d.rule = 'CAP')                                   AS cap_applied,
       (SELECT coalesce(sum(i.quantity), 0)
          FROM checkout_items i
         WHERE i.checkout_id = c.id)                                    AS item_count,
       c.created_at
  FROM checkouts c;
