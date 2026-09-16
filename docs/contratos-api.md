# Contratos de API — chat-registro

Referencia de los endpoints HTTP que expone el microservicio **chat-registro**, pensada para
que un cliente (frontend web, app móvil, otro servicio) los consuma sin leer el código.

La fuente de verdad ejecutable es la especificación **OpenAPI** que genera el propio
servicio; este documento la resume y añade las notas de integración que no caben en las
anotaciones.

- Swagger UI: `http://<host>:8081/swagger-ui.html`
- OpenAPI JSON: `http://<host>:8081/v3/api-docs`

---

## 1. Convenciones generales

| Aspecto | Valor |
|---|---|
| Prefijo de versión | `/api/v1` (un cambio incompatible sube a `/api/v2`) |
| Formato de cuerpo | JSON (`application/json`) en peticiones y respuestas correctas |
| Formato de errores | `application/problem+json` (RFC 9457) |
| Codificación | UTF-8 |
| Fechas y horas | ISO-8601 en UTC, con precisión de microsegundos — ej. `2026-09-09T03:13:36.766818Z` |
| Autenticación | Ninguna exigida hoy por `POST /api/v1/registro` (es el propio alta). El servicio es quien habla con Firebase Auth, no el cliente. |
| CORS | Habilitado para `/api/**`. Orígenes permitidos vía `CORS_ALLOWED_ORIGINS` (lista separada por comas). Métodos `GET,POST,PUT,PATCH,DELETE,OPTIONS`; sin credenciales por defecto (`CORS_ALLOW_CREDENTIALS=false`). Un origen fuera de la lista recibe `403` sin cabeceras `Access-Control-*`. |

### Entornos

| Entorno | Base URL |
|---|---|
| Local | `http://localhost:8081` |
| Otros | definidos por infraestructura (el servicio escucha en el puerto `8081`) |

---

## 2. Formato de errores (RFC 9457 *Problem Details*)

Toda respuesta con código `4xx` o `5xx` tiene `Content-Type: application/problem+json` y este
cuerpo:

```json
{
  "type": "urn:problem-type:validation-error",
  "title": "Datos invalidos",
  "status": 400,
  "detail": "El cuerpo de la peticion no supero la validacion",
  "instance": "/api/v1/registro",
  "timestamp": "2026-09-09T03:10:00.123456Z"
}
```

| Campo | Tipo | Descripción |
|---|---|---|
| `type` | string (URI) | Identificador estable de la categoría de error. **Es el campo que el cliente debe usar para ramificar lógica**, no `title` ni `detail`. |
| `title` | string | Título legible, fijo por `type`. |
| `status` | number | Código HTTP, repetido en el cuerpo. |
| `detail` | string | Descripción legible para mostrar al usuario. Genérica a propósito (ver §4). |
| `instance` | string | Path de la petición que falló. |
| `timestamp` | string (ISO-8601) | Momento en que se generó el error. |
| `errors` | array | **Solo en `validation-error`.** Lista de errores por campo (ver §4). |

### Catálogo de `type`

| `type` | HTTP | Cuándo |
|---|---|---|
| `urn:problem-type:validation-error` | 400 | El cuerpo no cumple las reglas de formato. Incluye `errors[]`. |
| `urn:problem-type:duplicate-resource` | 409 | Los datos entran en conflicto con un usuario existente (local o ya registrado en el proveedor de identidad). |
| `urn:problem-type:data-integrity` | 409 | Violación de una restricción de integridad en base de datos. |
| `urn:problem-type:resource-not-found` | 404 | El recurso solicitado no existe. (Sin uso en los endpoints actuales.) |
| `urn:problem-type:internal-error` | 500 | Error inesperado, incluido un fallo del proveedor de identidad que no sea "ya existe". `detail` siempre genérico; el detalle real queda en logs del servidor. |

---

## 3. Endpoints

### 3.1 `POST /api/v1/registro` — Registrar un usuario

Registra un usuario nuevo. **El propio servicio crea la cuenta en el proveedor de identidad
(Firebase Auth) antes de persistir el perfil de dominio** — el cliente no llama a Firebase
por su cuenta ni le manda un UID: solo manda `username`, `email` y `password`.

#### Petición

| | |
|---|---|
| Método | `POST` |
| Path | `/api/v1/registro` |
| Headers | `Content-Type: application/json` |
| Autenticación | Ninguna |

Cuerpo:

```json
{
  "username": "mateo",
  "email": "mateo@example.com",
  "password": "Passw0rd!23"
}
```

| Campo | Tipo | Obligatorio | Reglas |
|---|---|---|---|
| `username` | string | sí | 3–50 caracteres. Solo `A–Z a–z 0–9 . _ -`. Único (sin distinguir mayúsculas). |
| `email` | string | sí | Formato de email válido. Máx. 255 caracteres. Único (sin distinguir mayúsculas). Se normaliza a minúsculas antes de guardar. |
| `password` | string | sí | 8–20 caracteres. Al menos una mayúscula, una minúscula, un número y un carácter especial (cualquiera que no sea letra, número o espacio). Ningún carácter repetido 4 o más veces seguidas (`aaaa` invalido, `aaa` válido). Se reenvía a Firebase y **no se persiste** en este servicio. |

Se ignora cualquier campo extra del cuerpo (p. ej. un `uid` o `proveedor` de una integración
vieja: ninguno de los dos es un campo de la petición — el servidor los determina él mismo).

#### Respuesta `201 Created`

`Content-Type: application/json`

```json
{
  "id": 1,
  "username": "mateo",
  "email": "mateo@example.com",
  "proveedor": "password",
  "activo": true,
  "createdAt": "2026-09-09T03:13:36.766818Z"
}
```

| Campo | Tipo | Descripción |
|---|---|---|
| `id` | number | Identificador asignado por el servidor. |
| `username` | string | Tal cual se envió (recortando espacios). |
| `email` | string | Normalizado a minúsculas. |
| `proveedor` | string | Proveedor de identidad usado en el alta (hoy siempre `"password"`, es el único que crea este servicio). |
| `activo` | boolean | Siempre `true` en un alta nueva. |
| `createdAt` | string (ISO-8601) | Instante de creación en UTC. |

> El UID de Firebase **no** se devuelve en la respuesta — el cliente no lo necesita, nunca lo
> mandó él. No se devuelve cabecera `Location` en esta versión.

#### Respuesta `400 Bad Request` — validación

`type` = `urn:problem-type:validation-error`. Añade `errors[]` con un objeto por cada campo
que falló:

```json
{
  "type": "urn:problem-type:validation-error",
  "title": "Datos invalidos",
  "status": 400,
  "detail": "El cuerpo de la peticion no supero la validacion",
  "instance": "/api/v1/registro",
  "timestamp": "2026-09-09T03:10:00.123456Z",
  "errors": [
    { "field": "username", "message": "el tamaño debe estar entre 3 y 50" },
    { "field": "email", "message": "debe ser una dirección de correo electrónico con formato correcto" },
    { "field": "password", "message": "debe tener mayuscula, minuscula, numero y caracter especial, y ningun caracter repetido 4 o mas veces seguidas" }
  ]
}
```

- `errors[].field` es el nombre del campo del cuerpo (`username`, `email`, `password`) — úsalo
  para marcar el input correspondiente en el formulario.
- `errors[].message` es **orientativo** (texto por defecto de Bean Validation, puede variar
  según el idioma del servidor). El frontend debería mostrar sus propios textos a partir de
  `field` y las reglas de esta tabla, no confiar en `message` palabra por palabra.

#### Respuesta `409 Conflict` — el usuario ya existe

`type` = `urn:problem-type:duplicate-resource`. Se produce si `username` o `email` ya existen
localmente, **o** si Firebase ya tiene una cuenta con ese email y también existe la fila
correspondiente en la base de datos de este servicio (si Firebase la tiene pero este servicio
no, el alta se completa igualmente — ver la nota de reconciliación más abajo).

```json
{
  "type": "urn:problem-type:duplicate-resource",
  "title": "Recurso duplicado",
  "status": 409,
  "detail": "No se pudo completar el registro con los datos proporcionados",
  "instance": "/api/v1/registro",
  "timestamp": "2026-09-09T03:10:00.123456Z"
}
```

> **Por seguridad, el `409` es deliberadamente genérico.** No indica si colisionó el
> `username`, el `email`, o si el usuario ya existía en Firebase, ni devuelve el valor
> enviado, para no permitir enumerar cuentas. El frontend debe mostrar un mensaje del tipo
> *"No se pudo completar el registro. Revisa los datos e inténtalo de nuevo."* y **no**
> intentar deducir qué campo falló.

#### Respuesta `500 Internal Server Error`

`type` = `urn:problem-type:internal-error`, `detail` genérico. Incluye cualquier fallo al
hablar con el proveedor de identidad que no sea "el email ya existe" (red, cuota agotada,
credenciales del propio servicio, etc.). Reintentable con backoff.

#### Ejemplo `curl`

```bash
curl -i -X POST http://localhost:8081/api/v1/registro \
  -H 'Content-Type: application/json' \
  -d '{"username":"mateo","email":"mateo@example.com","password":"Passw0rd!23"}'
```

---

## 4. Notas de integración para el frontend

1. **Ramifica por `type`, no por `status` ni por textos.** `title`/`detail` pueden cambiar de
   redacción sin previo aviso; `type` y `status` son estables.
2. **El `409` no dice qué campo colisiona.** Es intencional. Mensaje genérico en la UI.
3. **No hace falta llamar a Firebase desde el cliente para este flujo.** El backend crea la
   cuenta por ti; solo mandas `username` + `email` + `password`. Si tu app usa el SDK de
   Firebase para otras cosas (login, sesión), esa integración es independiente de este alta.
4. **La contraseña nunca vuelve en ninguna respuesta.** No la guardes ni la muestres tras el
   alta; este servicio tampoco la persiste, solo la reenvía a Firebase.
5. **El `email` se normaliza a minúsculas** en el servidor; si tu UI lo muestra tras el alta,
   usa el valor devuelto en la respuesta, no el que tecleó el usuario.
6. **Valida la contraseña en el cliente con las mismas reglas de §3.1** para dar feedback
   inmediato; la validación del servidor es la autoritativa y devuelve `400` con `errors[]`.
7. **Unicidad de `username`/`email` no se puede pre-comprobar** (no hay endpoint para ello,
   también por el tema de enumeración). Se descubre al recibir el `409` del `POST`.

---

## 5. Modelos (TypeScript)

```ts
// Petición
export interface RegistroRequest {
  username: string; // 3–50, /^[A-Za-z0-9._-]+$/
  email: string;    // email válido, <= 255
  password: string; // 8–20; mayuscula + minuscula + numero + especial; sin 4+ repetidos
}

// Respuesta 201
export interface RegistroResponse {
  id: number;
  username: string;
  email: string;
  proveedor: string; // hoy siempre "password"
  activo: boolean;
  createdAt: string; // ISO-8601 UTC
}

// Error RFC 9457 (cualquier 4xx/5xx)
export interface ProblemDetail {
  type: string;      // "urn:problem-type:*"
  title: string;
  status: number;
  detail: string;
  instance: string;
  timestamp: string; // ISO-8601 UTC
  errors?: FieldError[]; // solo en validation-error
}

export interface FieldError {
  field: string;   // "username" | "email" | "password"
  message: string; // orientativo
}
```

---

## 6. Cómo funciona por dentro (para quien depure un 409/500 raro)

`RegistroService` orquesta la creación en dos sitios (Firebase primero, base de datos
después) y compensa si algo falla a medias:

1. Comprueba `username`/`email` localmente antes de tocar Firebase.
2. Crea el usuario en Firebase con `email` + `password`.
3. Si Firebase dice que el email **ya existe**, reconcilia: busca ese usuario en Firebase y
   comprueba si la base de datos local ya tiene su fila.
   - Si no la tiene (p. ej. un alta anterior que falló justo después de crear en Firebase),
     la crea ahora — el cliente recibe `201`, no un error.
   - Si ya la tiene, es un conflicto real → `409` genérico.
4. Si Firebase creó el usuario pero el guardado en la base de datos falla, se **revierte**
   (borra) ese usuario en Firebase para no dejarlo huérfano, y el cliente recibe `409`
   genérico igualmente.

Toda esta orquestación vive detrás de la interfaz `ProveedorIdentidad`
(`com.arquetipo.demo.registro.identidad`); el contrato HTTP de este documento no cambia si el
día de mañana se sustituye Firebase por otro proveedor.

---

## 7. Otros recursos del servicio

| Recurso | Path | Uso |
|---|---|---|
| Swagger UI | `/swagger-ui.html` | Exploración interactiva |
| OpenAPI JSON | `/v3/api-docs` | Generación de clientes / tipos |
| Health check | `/actuator/health` | Monitorización / readiness |

---

## 8. Control de versiones de este documento

| Fecha | Cambio |
|---|---|
| 2026-09-12 | El servicio pasa a crear la cuenta en Firebase Auth él mismo: la petición vuelve a `{ username, email, password }` (ya no `uid`/`proveedor`, que ahora decide el servidor). Se documenta la reconciliación y la compensación ante fallos parciales. |
| 2026-09-12 (anterior) | `POST /api/v1/registro` había cambiado de contraseña propia a `uid` + `proveedor` enviados por el cliente (superado por el cambio de arriba). |
| 2026-09-09 | Versión inicial: `POST /api/v1/registro`. |
