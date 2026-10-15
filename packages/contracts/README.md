# contracts

Contrato OpenAPI compartido entre `apps/backend` y `apps/frontend`.

- **Fuente de verdad:** `openapi.yaml`. Cualquier cambio en la API empieza aquí.
- **Frontend:** los tipos TypeScript se generan del contrato, para que front y back no se desalineen. _(pendiente: script de generación)_
- **Backend:** los DTO se validan contra el contrato con pruebas de la capa `api`. _(pendiente)_
