# Gobernanza de IA

> Cómo se usó la IA en este proyecto, qué se le delegó, qué se revisó y qué se corrigió. El desarrollador actúa como auditor de todo el código entregado.

## Herramientas

| Herramienta | Uso |
|---|---|
| Claude (chat) | Análisis del enunciado, supuestos y preparación de la sustentación |
| Claude Code | Generación de estructura, código y pruebas bajo las reglas de `CLAUDE.md` |

## 1. Skills / prompts automatizados

| Skill | Ubicación | Qué automatiza |
|---|---|---|
| `generate-unit-tests` | `.claude/skills/generate-unit-tests/SKILL.md` | Genera pruebas unitarias del backend (JUnit 5 + Mockito) a partir de la spec, cubriendo los casos de borde obligatorios. |

_(pendiente: ejemplo de invocación y resultado)_

## 2. Agentes / sub-agentes

| Agente | Ubicación | Rol y reglas |
|---|---|---|
| `discount-auditor` | `.claude/agents/discount-auditor.md` | Audita el motor de descuentos: precedencia, cascada multiplicativa, tope del 35%, uso de `BigDecimal` y precios tomados del servidor. Solo reporta; no modifica código. |
| `coverage-validator` | `.claude/agents/coverage-validator.md` | Ejecuta las pruebas de front y back, verifica el 80% de cobertura y lista los casos de borde sin prueba. |

## 3. Bitácora de co-creación

### Porcentaje de código sugerido por IA

_(pendiente: estimar al cierre, por módulo)_

| Módulo | % sugerido por IA | Lógica crítica implementada o reescrita manualmente |
|---|---|---|
| Estructura del proyecto | _(pendiente)_ | |
| Motor de descuentos | _(pendiente)_ | |
| Checkout / stock | _(pendiente)_ | |
| Frontend | _(pendiente)_ | |

### Registro

| # | Objetivo | Herramienta / agente | Prompt (resumen) | Resultado | Revisión o ajuste manual |
|---|---|---|---|---|---|
| 1 | Analizar el enunciado y detectar ambigüedades | Claude (chat) | "Analiza las reglas de negocio de la prueba y dime qué supuestos debo documentar" | Lista de supuestos; hallazgo de que el tope del 35% es inalcanzable con los valores actuales (máx. 27,325%) | Verifiqué el cálculo a mano y decidí parametrizar las reglas |
| 2 | Crear la estructura inicial del monorepo | Claude Code | "Crea la estructura del proyecto inicial" (Spring Boot + Angular) | Monorepo con capas, JaCoCo al 80%, agentes y skill | _(pendiente: qué revisé y qué cambié)_ |
| 3 | Definir el esquema SQLite (usuarios, productos, pivote de checkout con descuentos) | Claude Code | "Define el esquema de la base de datos según las reglas de negocio; menciona contradicciones antes de cambiar" | Detectó 4 contradicciones (stock en memoria, usuario fuera del contrato, cupón sin expiración, SQLite sin `DECIMAL`) y propuso cabecera + pivote + tabla de descuentos, dinero en `TEXT` y tablas `STRICT` | Elegí entre las opciones propuestas: stock inicial, `user_id` opcional, dinero en `TEXT`, 3 tablas. _(pendiente: revisión manual del esquema)_ |
| 4 | Completar el contrato de la API | Claude Code | "Completa el contrato: GET /list-products (productos en stock con stock inicial) y POST /checkout (productos y cupón)" | Detectó que mostrar el stock inicial contradice el filtro 'en stock', que el dinero como número JSON es flotante y que faltaban códigos de error para casos de borde obligatorios. Propuso rutas REST y un endpoint de previsualización | Acepté `/products` y `/checkouts`, `availableStock`, dinero como string y `POST /checkouts/preview`. _(pendiente: revisión manual del contrato)_ |
| 5 | Vista de selección de productos en Angular | Claude Code | "Lista los productos, permite elegir la cantidad de cada uno y un botón para ir al checkout; colores neutros, estilos sencillos y accesibilidad básica" | `CatalogApi`, `CartStore` con Signals, `CatalogPage` y un checkout provisional. 37 pruebas escritas antes del código y verificación en el navegador contra el backend real | _(pendiente: revisión manual)_ |
| 6 | Subtotal en tiempo real en la barra del carrito | Claude Code | "Agrega el subtotal en el cuadro del botón de checkout; debe actualizarse en tiempo real" | Señaló que contradecía la decisión de que el frontend no sumara dinero y propuso centavos `bigint` en lugar de `number` o de llamar al preview en cada cambio. 14 pruebas nuevas escritas antes del código | _(pendiente: revisión manual)_ |
| 7 | Vista de checkout | Claude Code | "Vista de checkout a la que solo se accede desde el botón, con los productos del carrito y campos para correo y cupón" | Señaló que el correo no está en el contrato ni alcanza para crear un usuario, y que "solo desde el botón" se resuelve con un guard de ruta. Al verificar en el navegador detectó que en celular la columna Total quedaba fuera de pantalla y lo corrigió | Decidí quitar el correo y dejar el desglose y la confirmación para el siguiente paso |
| 8 | Botón "Aplicar descuento" | Claude Code | "Agrega un botón para aplicar descuento que valide el código contra la tabla de códigos de descuento y también los otros descuentos" | Señaló que no existe una tabla de cupones (el cupón vive en la configuración por regla de `CLAUDE.md`) y propuso usar `POST /checkouts/preview`, que valida el cupón y calcula la cascada. Detectó que redondear el ajuste del tope por separado descuadraba el desglose (5.00 en vez de 5.01) y lo derivó del total del backend. 41 pruebas nuevas escritas antes del código, verificado contra el backend real | Elegí validar contra la configuración y mostrar el desglose con la alerta |
| 9 | Botón "Ir a pagar" y confirmación | Claude Code | "Agrega el botón de ir a pagar al final: debe llamar a POST /checkouts, validar la compra con productos y descuentos y devolver la confirmación con los totales desglosados exactos" | Señaló que no hay `GET /checkouts/{id}` (confirmación en memoria con guard), que no hay pasarela de pago ni idempotencia en el backend (protección de doble envío solo en el cliente). Extrajo el desglose a un componente compartido. 28 pruebas nuevas escritas antes del código y compra de prueba de punta a punta contra el backend local | _(pendiente: revisión manual)_ |
| 10 | Idempotency-Key en POST /checkouts | Claude Code (sesión paralela) | "Agrega soporte de clave de idempotencia a POST /checkouts para evitar compras dobles" (propuesta como seguimiento del botón de pago) | Header opcional `Idempotency-Key` (UUID) en el contrato, columnas en `checkouts`, búsqueda y guardado en la misma transacción, `IDEMPOTENCY_KEY_REUSED` (409) y una clave por intento de pago en el frontend. Verificado con `mvn verify`, `npm run test:coverage` y `verify.sh` | Pendiente: recrear `checkout.db` local, porque el esquema agrega columnas y `IF NOT EXISTS` no altera la tabla existente |

## 4. Sugerencias rechazadas o corregidas

> Mínimo dos ejemplos concretos, con el criterio técnico que justificó la corrección.

### Corrección 1

_(pendiente)_ — Sugerencia de la IA · Problema detectado · Corrección aplicada · Cómo se verificó.

### Corrección 2

_(pendiente)_
