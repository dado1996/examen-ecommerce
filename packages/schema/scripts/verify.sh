#!/usr/bin/env bash
# Verifica el esquema SQLite: carga schema.sql + seed.sql en una base en memoria,
# comprueba que un checkout válido se acepta y que los datos corruptos se rechazan.
# Uso: packages/schema/scripts/verify.sh
set -euo pipefail

SCHEMA_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SCHEMA="$SCHEMA_DIR/schema.sql"
SEED="$SCHEMA_DIR/seed.sql"
passed=0
failed=0

# Checkout válido reutilizado por varios casos: 2 laptops (Tecnología) + 1 lámpara,
# con descuentos de categoría, volumen y cupón (tasas de application.yml).
VALID_CHECKOUT="
INSERT INTO checkouts (id, user_id, coupon_code, subtotal, discount_total, total, max_discount_rate)
VALUES (1, 1, 'WELCOME2026', '2440.00', '663.50', '1776.50', '0.35');
INSERT INTO checkout_items (checkout_id, product_id, quantity, unit_price, category, line_subtotal)
VALUES (1, 1, 2, '1200.00', 'Tecnología', '2400.00'),
       (1, 4, 1, '40.00', 'Hogar', '40.00');
INSERT INTO checkout_discounts (checkout_id, step, rule, rate, reference, base_amount, amount)
VALUES (1, 1, 'CATEGORY', '0.10', 'Tecnología', '2400.00', '240.00'),
       (1, 2, 'VOLUME', '0.05', '100.00', '2200.00', '110.00'),
       (1, 3, 'COUPON', '0.15', 'WELCOME2026', '2090.00', '313.50');
"

run_sql() {
  { cat "$SCHEMA" "$SEED"; printf '%s\n' "$1"; } | sqlite3 -bail ':memory:' 2>&1
}

debe_aceptar() {
  local nombre="$1" sql="$2" esperado="${3:-}" salida
  if salida="$(run_sql "$sql")" && [[ -z "$esperado" || "$salida" == "$esperado" ]]; then
    passed=$((passed + 1)); echo "  ok   $nombre"
  else
    failed=$((failed + 1)); echo "  FAIL $nombre"; echo "       salida: $salida"
  fi
}

debe_rechazar() {
  local nombre="$1" sql="$2" salida
  # Solo cuenta como rechazo un error de restricción o de tipo, no cualquier fallo de carga.
  if salida="$(run_sql "$sql")"; then
    failed=$((failed + 1)); echo "  FAIL $nombre (se aceptó)"
  elif [[ "$salida" =~ (constraint\ failed|cannot\ store) ]]; then
    passed=$((passed + 1)); echo "  ok   $nombre"
  else
    failed=$((failed + 1)); echo "  FAIL $nombre (error inesperado)"; echo "       salida: $salida"
  fi
}

echo "Casos válidos"
debe_aceptar "carga el esquema y el seed" "SELECT 1;" "1"
debe_aceptar "acepta un checkout con descuentos en cascada" "$VALID_CHECKOUT"
debe_aceptar "acepta un checkout de invitado sin usuario" \
  "INSERT INTO checkouts (user_id, subtotal, discount_total, total, max_discount_rate)
   VALUES (NULL, '40.00', '0.00', '40.00', '0.35');"
debe_aceptar "el resumen marca cap_applied=0 si no hubo tope" \
  "$VALID_CHECKOUT SELECT cap_applied FROM checkout_summary WHERE id = 1;" "0"
debe_aceptar "el resumen marca cap_applied=1 si se aplicó el tope" \
  "INSERT INTO checkouts (id, subtotal, discount_total, total, max_discount_rate)
   VALUES (2, '100.00', '20.00', '80.00', '0.20');
   INSERT INTO checkout_items VALUES (2, 1, 1, '100.00', 'Tecnología', '100.00');
   INSERT INTO checkout_discounts (checkout_id, step, rule, rate, reference, base_amount, amount)
   VALUES (2, 1, 'CATEGORY', '0.10', 'Tecnología', '100.00', '10.00'),
          (2, 3, 'COUPON', '0.15', 'WELCOME2026', '90.00', '13.50'),
          (2, 4, 'CAP', '0.20', NULL, '23.50', '3.50');
   SELECT cap_applied FROM checkout_summary WHERE id = 2;" "1"
debe_aceptar "conserva montos intermedios sin redondear" \
  "$VALID_CHECKOUT INSERT INTO checkouts (id, subtotal, discount_total, total, max_discount_rate)
   VALUES (3, '85.50', '8.55', '76.95', '0.35');
   INSERT INTO checkout_discounts (checkout_id, step, rule, rate, reference, base_amount, amount)
   VALUES (3, 1, 'CATEGORY', '0.10', 'Tecnología', '85.50', '8.550');
   SELECT amount FROM checkout_discounts WHERE checkout_id = 3;" "8.550"

debe_aceptar "es idempotente: se puede cargar dos veces" \
  "$(cat "$SCHEMA") SELECT count(*) FROM pragma_table_list WHERE name = 'checkouts' AND strict = 1;" "1"
debe_aceptar "acepta un checkout con clave de idempotencia y hash del request" \
  "INSERT INTO checkouts (subtotal, discount_total, total, max_discount_rate, idempotency_key, request_hash)
   VALUES ('40.00', '0.00', '40.00', '0.35', '3f0c9d2e-7b1a-4c5e-9f8d-2a6b4e1c0d7f', 'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa');
   SELECT id FROM checkouts WHERE idempotency_key = '3f0c9d2e-7b1a-4c5e-9f8d-2a6b4e1c0d7f';" "1"
debe_aceptar "acepta varios checkouts sin clave de idempotencia" \
  "INSERT INTO checkouts (subtotal, discount_total, total, max_discount_rate)
   VALUES ('40.00', '0.00', '40.00', '0.35'), ('40.00', '0.00', '40.00', '0.35');
   SELECT count(*) FROM checkouts WHERE idempotency_key IS NULL;" "2"

echo "Datos corruptos"
debe_rechazar "rechaza un precio guardado como REAL" \
  "INSERT INTO products (name, category, unit_price, initial_stock) VALUES ('X', 'Hogar', 10.5, 1);"
debe_rechazar "rechaza un precio con más de 2 decimales" \
  "INSERT INTO products (name, category, unit_price, initial_stock) VALUES ('X', 'Hogar', '10.505', 1);"
debe_rechazar "rechaza un precio en cero" \
  "INSERT INTO products (name, category, unit_price, initial_stock) VALUES ('X', 'Hogar', '0.00', 1);"
debe_rechazar "rechaza un precio negativo" \
  "INSERT INTO products (name, category, unit_price, initial_stock) VALUES ('X', 'Hogar', '-1.00', 1);"
debe_rechazar "rechaza un stock inicial negativo" \
  "INSERT INTO products (name, category, unit_price, initial_stock) VALUES ('X', 'Hogar', '1.00', -1);"
debe_rechazar "rechaza una categoría vacía" \
  "INSERT INTO products (name, category, unit_price, initial_stock) VALUES ('X', '  ', '1.00', 1);"
debe_rechazar "rechaza un correo duplicado sin importar mayúsculas" \
  "INSERT INTO users (email, full_name) VALUES ('ANA@EXAMPLE.COM', 'Otra');"
debe_rechazar "rechaza un correo sin arroba" \
  "INSERT INTO users (email, full_name) VALUES ('sin-arroba', 'X');"
debe_rechazar "rechaza una línea con cantidad cero" \
  "$VALID_CHECKOUT INSERT INTO checkout_items VALUES (1, 2, 0, '25.00', 'Tecnología', '0.00');"
debe_rechazar "rechaza un producto repetido en el mismo checkout" \
  "$VALID_CHECKOUT INSERT INTO checkout_items VALUES (1, 1, 1, '1200.00', 'Tecnología', '1200.00');"
debe_rechazar "rechaza una línea con producto inexistente" \
  "$VALID_CHECKOUT INSERT INTO checkout_items VALUES (1, 999, 1, '1.00', 'Hogar', '1.00');"
debe_rechazar "rechaza un checkout con usuario inexistente" \
  "INSERT INTO checkouts (user_id, subtotal, discount_total, total, max_discount_rate)
   VALUES (999, '40.00', '0.00', '40.00', '0.35');"
debe_rechazar "rechaza un total que no cuadra con subtotal menos descuento" \
  "INSERT INTO checkouts (subtotal, discount_total, total, max_discount_rate)
   VALUES ('100.00', '10.00', '95.00', '0.35');"
debe_rechazar "rechaza un descuento total que supera el tope registrado" \
  "INSERT INTO checkouts (subtotal, discount_total, total, max_discount_rate)
   VALUES ('100.00', '36.00', '64.00', '0.35');"
debe_rechazar "rechaza una tasa de tope mayor que 1" \
  "INSERT INTO checkouts (subtotal, discount_total, total, max_discount_rate)
   VALUES ('100.00', '0.00', '100.00', '1.50');"
debe_rechazar "rechaza una regla de descuento desconocida" \
  "$VALID_CHECKOUT INSERT INTO checkout_discounts VALUES (1, 4, 'BLACKFRIDAY', '0.50', NULL, '1.00', '0.50');"
debe_rechazar "rechaza una regla fuera de su paso en la cascada" \
  "$VALID_CHECKOUT INSERT INTO checkout_discounts VALUES (1, 1, 'CAP', '0.35', NULL, '1.00', '0.50');"
debe_rechazar "rechaza la misma regla dos veces en un checkout" \
  "$VALID_CHECKOUT INSERT INTO checkout_discounts VALUES (1, 3, 'COUPON', '0.15', 'WELCOME2026', '1.00', '0.15');"
debe_rechazar "rechaza un descuento con monto cero" \
  "INSERT INTO checkouts (id, subtotal, discount_total, total, max_discount_rate)
   VALUES (5, '10.00', '0.00', '10.00', '0.35');
   INSERT INTO checkout_discounts VALUES (5, 1, 'CATEGORY', '0.10', 'Tecnología', '0.00', '0');"
debe_rechazar "rechaza un descuento con tasa mayor que 1" \
  "INSERT INTO checkouts (id, subtotal, discount_total, total, max_discount_rate)
   VALUES (5, '10.00', '1.00', '9.00', '0.35');
   INSERT INTO checkout_discounts VALUES (5, 1, 'CATEGORY', '1.10', 'Tecnología', '10.00', '1.00');"
debe_rechazar "rechaza una clave de idempotencia repetida" \
  "INSERT INTO checkouts (subtotal, discount_total, total, max_discount_rate, idempotency_key, request_hash)
   VALUES ('40.00', '0.00', '40.00', '0.35', '3f0c9d2e-7b1a-4c5e-9f8d-2a6b4e1c0d7f', 'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa'),
          ('25.00', '0.00', '25.00', '0.35', '3f0c9d2e-7b1a-4c5e-9f8d-2a6b4e1c0d7f', 'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa');"
debe_rechazar "rechaza una clave de idempotencia sin hash del request" \
  "INSERT INTO checkouts (subtotal, discount_total, total, max_discount_rate, idempotency_key)
   VALUES ('40.00', '0.00', '40.00', '0.35', '3f0c9d2e-7b1a-4c5e-9f8d-2a6b4e1c0d7f');"
debe_rechazar "rechaza un hash del request sin clave de idempotencia" \
  "INSERT INTO checkouts (subtotal, discount_total, total, max_discount_rate, request_hash)
   VALUES ('40.00', '0.00', '40.00', '0.35', 'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa');"
debe_rechazar "rechaza una clave de idempotencia que no es un UUID en minúsculas" \
  "INSERT INTO checkouts (subtotal, discount_total, total, max_discount_rate, idempotency_key, request_hash)
   VALUES ('40.00', '0.00', '40.00', '0.35', '3F0C9D2E-7B1A-4C5E-9F8D-2A6B4E1C0D7F', 'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa');"
debe_rechazar "rechaza un hash del request que no es SHA-256 hexadecimal" \
  "INSERT INTO checkouts (subtotal, discount_total, total, max_discount_rate, idempotency_key, request_hash)
   VALUES ('40.00', '0.00', '40.00', '0.35', '3f0c9d2e-7b1a-4c5e-9f8d-2a6b4e1c0d7f', 'abc');"
debe_rechazar "impide borrar un producto que ya se vendió" \
  "$VALID_CHECKOUT DELETE FROM products WHERE id = 1;"
debe_rechazar "impide borrar un usuario con checkouts" \
  "$VALID_CHECKOUT DELETE FROM users WHERE id = 1;"

echo
echo "Resultado: $passed ok, $failed fallidas"
[[ $failed -eq 0 ]]
