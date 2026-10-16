# Core E-Commerce con Sistema de Descuentos Acumulativos

MVP de checkout para un e-commerce: un backend que valida stock, calcula descuentos acumulativos en cascada con tope del 35% y persiste órdenes, y un frontend reactivo para gestionar el carrito y ver el desglose.

> Estado: estructura inicial. Las secciones marcadas con _(pendiente)_ se completan a medida que avanza el desarrollo.

## Stack

| Capa | Tecnología |
|---|---|
| Backend | Java 21 · Spring Boot 3.5 · Maven · SQLite (órdenes) · stock en memoria |
| Frontend | Angular 21 · TypeScript estricto · Signals · Vitest |
| Contrato | OpenAPI 3 en `packages/contracts` (fuente única de tipos front/back) |
| Calidad | JaCoCo (backend) y cobertura de Vitest (frontend), umbral mínimo 80% |

La justificación de cada decisión está en [`docs/arquitectura.md`](docs/arquitectura.md) y la gobernanza de IA en [`docs/ia.md`](docs/ia.md).

## Estructura

```
examen-ecommerce/
├── apps/
│   ├── backend/        # API REST Spring Boot (dominio aislado del framework)
│   └── frontend/       # Aplicación Angular
├── packages/
│   ├── contracts/      # Contrato OpenAPI compartido
│   └── schema/         # Esquema SQLite, datos de demo y su verificación
├── docs/
│   ├── arquitectura.md
│   └── ia.md
├── .claude/            # Agentes y skills usados durante el desarrollo
├── CLAUDE.md           # Reglas del proyecto para el asistente de IA
└── README.md
```

## Requisitos

- JDK 21
- Maven 3.9+
- Node.js 20.19+ o 22.12+ y npm 10+

## Instalación y ejecución

### Backend

```bash
cd apps/backend
mvn spring-boot:run
```

La API queda en `http://localhost:8080`. La base SQLite se crea en `apps/backend/data/`.

### Frontend

```bash
cd apps/frontend
npm ci
npm start
```

La aplicación queda en `http://localhost:4200` y espera el backend en `http://localhost:8080/api/v1`.

| Ruta | Vista |
|---|---|
| `/` | Selección de productos: catálogo con stock, cantidad por producto y subtotal en tiempo real |
| `/checkout` | Checkout _(pendiente: cupón, desglose, alerta del 35% y confirmación)_ |

## Pruebas y cobertura

### Backend

```bash
cd apps/backend
mvn verify
```

`mvn verify` ejecuta las pruebas y falla si la cobertura de líneas baja del 80% (regla de JaCoCo). El reporte queda en `apps/backend/target/site/jacoco/index.html`.

### Frontend

```bash
cd apps/frontend
npm run test:coverage
```

El reporte queda en `apps/frontend/coverage/`.

### Esquema de base de datos

```bash
packages/schema/scripts/verify.sh
```

Carga `schema.sql` y `seed.sql` en una base en memoria y verifica que los datos corruptos se rechacen. Requiere `sqlite3` 3.37+.

## Configuración

| Variable / propiedad | Descripción | Valor por defecto |
|---|---|---|
| `SERVER_PORT` | Puerto del backend | `8080` |
| `APP_CORS_ALLOWED_ORIGINS` | Orígenes permitidos para CORS | `http://localhost:4200` |
| `discounts.*` | Porcentajes, umbral y tope de las reglas de descuento | Ver `application.yml` |

_(pendiente: completar a medida que se agreguen variables)_

## Endpoints

Base `http://localhost:8080/api/v1`. El contrato completo está en [`packages/contracts`](packages/contracts/README.md).

| Método | Ruta | Descripción |
|---|---|---|
| `GET` | `/products` | Lista los productos en stock |
| `POST` | `/checkouts/preview` | Calcula el desglose de descuentos sin confirmar |
| `POST` | `/checkouts` | Confirma la compra y guarda el checkout |

_(pendiente: implementación en el backend)_

## Supuestos

_(pendiente: se documentan en `docs/arquitectura.md`)_
