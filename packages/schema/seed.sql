-- =============================================================================
-- Datos de demostración. Se ejecuta después de schema.sql y es idempotente:
-- ON CONFLICT solo ignora filas que ya existen; una fila inválida sigue fallando.
-- Correos en example.com (dominio reservado, no son datos reales).
-- =============================================================================

INSERT INTO users (id, email, full_name) VALUES
    (1, 'ana@example.com',   'Ana Demo'),
    (2, 'luis@example.com',  'Luis Demo')
ON CONFLICT (id) DO NOTHING;

INSERT INTO products (id, name, category, unit_price, initial_stock) VALUES
    (1, 'Laptop 14"',            'Tecnología', '1200.00', 10),
    (2, 'Mouse inalámbrico',     'Tecnología',   '25.00', 50),
    (3, 'Teclado mecánico',      'Tecnología',   '89.90', 20),
    (4, 'Lámpara de escritorio', 'Hogar',        '40.00', 15),
    (5, 'Cuaderno',              'Papelería',     '4.50', 100),
    -- Sin stock: sirve para demostrar el caso de stock insuficiente.
    (6, 'Silla ergonómica',      'Hogar',       '310.00', 0)
ON CONFLICT (id) DO NOTHING;
