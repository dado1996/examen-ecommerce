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

## 4. Sugerencias rechazadas o corregidas

> Mínimo dos ejemplos concretos, con el criterio técnico que justificó la corrección.

### Corrección 1

_(pendiente)_ — Sugerencia de la IA · Problema detectado · Corrección aplicada · Cómo se verificó.

### Corrección 2

_(pendiente)_
