# Contrato gRPC — Registro de usuarios (`chat-registro`)

Referencia para que **otro servicio** consuma por gRPC el alta y la consulta de usuarios de
**chat-registro**, sin necesidad de leer el código de este repositorio.

Este es el **único protocolo** que expone chat-registro para el registro. El REST del
sistema lo sirve `chat-gateway` (`POST /api/v1/registro` en su propio contrato), que reenvía
aquí por gRPC — si estás integrando el flujo de registro desde el frontend o desde fuera del
sistema, probablemente quieras la documentación de `chat-gateway`, no esta; este documento es
para quien hable **directamente** con chat-registro (el propio `chat-gateway`, u otro
servicio interno).

La fuente de verdad ejecutable es el propio `.proto`:
[`src/main/proto/registro.proto`](../src/main/proto/registro.proto).

---

## 1. Cómo conectarse

| Aspecto | Valor |
|---|---|
| Protocolo | gRPC (HTTP/2), **texto plano, sin TLS** (`-plaintext` en `grpcurl`, canal `usePlaintext()` en el cliente) |
| Host:puerto (local) | `localhost:9090` — puerto TCP propio del servidor gRPC (independiente del `server.port` HTTP, que hoy solo usa Actuator) |
| Variable de entorno del servidor | `GRPC_SERVER_PORT` (por defecto `9090`); `GRPC_SERVER_ENABLED=false` apaga el servidor por completo |
| Paquete proto | `com.arquetipo.demo.registro.grpc` |
| Servicio | `RegistroGrpcService` |
| Métodos (rpc) | `Registrar(RegistrarUsuarioRequest) returns (RegistrarUsuarioResponse)` · `BuscarUsuarioPorUid(BuscarUsuarioPorUidRequest) returns (BuscarUsuarioPorUidResponse)` · `ExisteUsername(ExisteUsernameRequest) returns (ExisteUsernameResponse)` — los tres unarios, sin streaming |
| Reflexión de servicio | Habilitada (`io.grpc:grpc-services`) — un cliente puede descubrir el contrato sin tener el `.proto`, ver §7 |
| Autenticación | Ninguna en ninguno de los tres rpc. **Este servicio no valida tokens de identidad**: `chat-gateway` es el único punto del sistema con integración con Firebase para verificar tokens (`Authorization: Bearer <idToken>`) — a `chat-registro` solo le llega, ya autenticado y autorizado, el dato que necesita (p. ej. el `uid` en `BuscarUsuarioPorUid`). `ExisteUsername` ni siquiera necesita eso: es una consulta pública de disponibilidad, ver §2. Pensado para tráfico interno exclusivamente; nunca se expone directamente a internet. |

> El puerto real por entorno lo define infraestructura; en producción probablemente vaya
> detrás de una red interna o un proxy con TLS. Pregunta al equipo de infraestructura la
> dirección de tu entorno si no es `localhost:9090`.

---

## 2. El `.proto`

Cópialo tal cual a tu proyecto cliente (o genera el stub apuntando a este archivo si tu build
lo permite referenciarlo directamente):

```proto
syntax = "proto3";

package com.arquetipo.demo.registro.grpc;

option java_multiple_files = true;
option java_package = "com.arquetipo.demo.registro.grpc";
option java_outer_classname = "RegistroProto";

service RegistroGrpcService {
  rpc Registrar (RegistrarUsuarioRequest) returns (RegistrarUsuarioResponse);
  rpc BuscarUsuarioPorUid (BuscarUsuarioPorUidRequest) returns (BuscarUsuarioPorUidResponse);
  rpc ExisteUsername (ExisteUsernameRequest) returns (ExisteUsernameResponse);
}

message RegistrarUsuarioRequest {
  string username = 1;
  string email = 2;
  string password = 3;
}

message RegistrarUsuarioResponse {
  int64 id = 1;
  string username = 2;
  string email = 3;
  string proveedor = 4;
  bool activo = 5;
  string created_at = 6;
}

message BuscarUsuarioPorUidRequest {
  string uid = 1;
}

message BuscarUsuarioPorUidResponse {
  string username = 1;
  string email = 2;
}

message ExisteUsernameRequest {
  string username = 1;
}

message ExisteUsernameResponse {
  bool existe = 1;
}
```

### `RegistrarUsuarioRequest`

| Campo | Tipo proto | Obligatorio | Reglas |
|---|---|---|---|
| `username` | `string` | sí | 3–50 caracteres. Solo `A–Z a–z 0–9 . _ -`. Único (sin distinguir mayúsculas). |
| `email` | `string` | sí | Formato de email válido. Máx. 255 caracteres. Único (sin distinguir mayúsculas). Se normaliza a minúsculas antes de guardar. |
| `password` | `string` | sí | 8–20 caracteres. Al menos una mayúscula, una minúscula, un número y un carácter especial (cualquiera que no sea letra, número o espacio). Ningún carácter repetido 4 o más veces seguidas (`aaaa` inválido, `aaa` válido). Se reenvía al proveedor de identidad y **no se persiste** en este servicio. |

> proto3 no distingue "campo ausente" de "cadena vacía": un `username`/`email`/`password` no
> asignado en el mensaje llega como `""` al servidor y falla la validación de longitud mínima
> igual que si lo hubieras mandado vacío a propósito — no hace falta ningún wrapper `optional`.

### `RegistrarUsuarioResponse`

| Campo | Tipo proto | Descripción |
|---|---|---|
| `id` | `int64` | Identificador asignado por el servidor. |
| `username` | `string` | Tal cual se envió (recortando espacios). |
| `email` | `string` | Normalizado a minúsculas. |
| `proveedor` | `string` | Proveedor de identidad usado en el alta (hoy siempre `"password"`). |
| `activo` | `bool` | Siempre `true` en un alta nueva. |
| `created_at` | `string` | Instante de creación en UTC, **ISO-8601** (ej. `"2026-09-08T20:53:47.441193Z"`). Se manda como `string`, no como `google.protobuf.Timestamp`, para no forzar esa dependencia en el cliente. |

> El UID del proveedor de identidad **no** se devuelve — el cliente no lo necesita.

### `BuscarUsuarioPorUidRequest` / `BuscarUsuarioPorUidResponse`

Resuelve a qué cuenta corresponde una sesión ya autenticada en Firebase, a partir de su UID —
pensado para `chat-gateway`, que ya autenticó al cliente (verificó su `idToken` con su propia
integración con Firebase Admin SDK) y comprobó que autoriza a consultar justo ese `uid` **antes
de llamar aquí**. `chat-registro` no repite esa verificación: confía en el `uid` que recibe.

| Campo | Mensaje | Tipo proto | Obligatorio | Descripción |
|---|---|---|---|---|
| `uid` | `BuscarUsuarioPorUidRequest` | `string` | sí | UID que Firebase Authentication le asignó al usuario al crear la cuenta. Identificador opaco, no un secreto. Vacío → `INVALID_ARGUMENT`. |
| `username` | `BuscarUsuarioPorUidResponse` | `string` | — | El `username` con el que se registró. |
| `email` | `BuscarUsuarioPorUidResponse` | `string` | — | El `email` (normalizado a minúsculas) con el que se registró. |

> No expone nada más del perfil (ni `id`, ni `proveedor`, ni `createdAt`) — si en el futuro
> hace falta más, se amplía este mensaje, no se reutiliza `RegistrarUsuarioResponse`.

### `ExisteUsernameRequest` / `ExisteUsernameResponse`

Dice si un `username` ya está en uso (sin distinguir mayúsculas) — pensado para validación en
vivo mientras se escribe (p. ej. un formulario de registro, como ya hace
`chat-frontend/src/utils/validacionRegistro.js` del lado del cliente). A diferencia de
`BuscarUsuarioPorUid`, esta consulta es **intencionalmente pública**: no expone ningún dato
del perfil, solo un booleano de disponibilidad, así que no necesita venir de una sesión
autenticada.

| Campo | Mensaje | Tipo proto | Obligatorio | Descripción |
|---|---|---|---|---|
| `username` | `ExisteUsernameRequest` | `string` | sí | El username a comprobar. Vacío → `INVALID_ARGUMENT`. Se recorta con `trim()` antes de consultar. |
| `existe` | `ExisteUsernameResponse` | `bool` | — | `true` si ya hay un usuario con ese `username` (sin distinguir mayúsculas). |

> No hay caso "no encontrado": la respuesta es siempre un booleano, nunca un error por username
> libre.

---

## 3. Ejemplo de llamada

### `grpcurl` (sin generar ningún stub, usa la reflexión del servidor)

```bash
grpcurl -plaintext -d '{
  "username": "mateo",
  "email": "mateo@example.com",
  "password": "Passw0rd!23"
}' localhost:9090 com.arquetipo.demo.registro.grpc.RegistroGrpcService/Registrar
```

Respuesta:

```json
{
  "id": "1",
  "username": "mateo",
  "email": "mateo@example.com",
  "proveedor": "password",
  "activo": true,
  "createdAt": "2026-09-08T20:53:47.441193Z"
}
```

### Cliente Java (stub bloqueante, generado a partir del `.proto`)

```java
ManagedChannel channel = ManagedChannelBuilder.forAddress("localhost", 9090)
        .usePlaintext()
        .build();
RegistroGrpcServiceGrpc.RegistroGrpcServiceBlockingStub stub =
        RegistroGrpcServiceGrpc.newBlockingStub(channel);

try {
    RegistrarUsuarioResponse respuesta = stub.registrar(RegistrarUsuarioRequest.newBuilder()
            .setUsername("mateo")
            .setEmail("mateo@example.com")
            .setPassword("Passw0rd!23")
            .build());
} catch (StatusRuntimeException ex) {
    // ver §4 — ex.getStatus().getCode() / ex.getStatus().getDescription()
}
```

### `BuscarUsuarioPorUid`

```bash
grpcurl -plaintext -d '{"uid": "0lSUQS1RdYauzu3ifx6izoyzkvt2"}' \
  localhost:9090 com.arquetipo.demo.registro.grpc.RegistroGrpcService/BuscarUsuarioPorUid
```

Respuesta:

```json
{
  "username": "mateo",
  "email": "mateo@example.com"
}
```

```java
BuscarUsuarioPorUidResponse respuesta = stub.buscarUsuarioPorUid(
        BuscarUsuarioPorUidRequest.newBuilder().setUid(uid).build());
```

### `ExisteUsername`

```bash
grpcurl -plaintext -d '{"username": "mateo"}' \
  localhost:9090 com.arquetipo.demo.registro.grpc.RegistroGrpcService/ExisteUsername
```

Respuesta:

```json
{
  "existe": true
}
```

```java
ExisteUsernameResponse respuesta = stub.existeUsername(
        ExisteUsernameRequest.newBuilder().setUsername(username).build());
```

---

## 4. Errores

gRPC no tiene *Problem Details*: los errores llegan como `StatusRuntimeException` con un
`Status.Code` y una `description` de texto libre.

### `Registrar`

**Mensaje genérico al cliente, detalle real solo en el log del servidor** — la ausencia de
un código HTTP no significa menos disciplina aquí, es la misma politica de seguridad
expresada con códigos gRPC.

| Situación | Código gRPC | `description` |
|---|---|---|
| El cuerpo no supera Bean Validation | `INVALID_ARGUMENT` | `"El cuerpo de la peticion no supero la validacion -> <campo>: <mensaje>; ..."` (un `campo: mensaje` por cada violación) |
| `username`/`email` duplicado, o el usuario ya existe en el proveedor de identidad | `ALREADY_EXISTS` | Mensaje genérico fijo: `"No se pudo completar el registro con los datos proporcionados"` — **nunca** indica qué campo colisionó |
| Cualquier otro fallo (proveedor de identidad, base de datos, bug interno) | `INTERNAL` | Mensaje genérico fijo: `"Ocurrio un error inesperado. Contacte con soporte."` |

Notas:

- **No ramifiques por `description` en `INVALID_ARGUMENT`.** El texto de cada violación es el
  mensaje por defecto de Bean Validation: orientativo, puede cambiar de redacción. Si
  necesitas marcar campos en una UI, valida tú mismo en el cliente con las reglas de la tabla
  de §2 antes de llamar.
- **El `ALREADY_EXISTS` es deliberadamente genérico**, por la misma razón que el `409` REST:
  evitar que alguien enumere cuentas probando emails/usernames. No reintentes asumiendo que es
  transitorio.

### `BuscarUsuarioPorUid`

Aquí **no** aplica el mensaje genérico: no es un alta con riesgo de enumeración de cuentas
por username/email — es una consulta puntual por un UID opaco (28 caracteres, no
correlativo) que quien pregunta ya posee de antemano. El `NOT_FOUND` puede describir la
situación tal cual.

| Situación | Código gRPC | `description` |
|---|---|---|
| `uid` vacío | `INVALID_ARGUMENT` | `"uid es obligatorio"` |
| Ningún usuario con ese `uid` | `NOT_FOUND` | `"Usuario no encontrado"` |
| Cualquier otro fallo (base de datos, bug interno) | `INTERNAL` | Mensaje genérico fijo: `"Ocurrio un error inesperado. Contacte con soporte."` |

> Este rpc no tiene ningún código de autenticación (`UNAUTHENTICATED`/`PERMISSION_DENIED`):
> esa responsabilidad es enteramente de `chat-gateway`, que valida el `idToken` con su propia
> integración con Firebase **antes** de llamar aquí — ver §6.

### `ExisteUsername`

| Situación | Código gRPC | `description` |
|---|---|---|
| `username` vacío | `INVALID_ARGUMENT` | `"username es obligatorio"` |
| Cualquier otro fallo (base de datos, bug interno) | `INTERNAL` | Mensaje genérico fijo: `"Ocurrio un error inesperado. Contacte con soporte."` |

No tiene `NOT_FOUND`: username libre y username no-vacío-pero-inválido-por-formato son casos
distintos que este rpc no diferencia — solo comprueba existencia, no valida el formato del
`username` (eso lo sigue haciendo `Registrar` con Bean Validation al crear la cuenta de
verdad).

Si accedes a cualquiera de los rpc a través de `chat-gateway` (REST), es su propia
documentación la que dice cómo traduce los códigos de arriba a HTTP, y cómo maneja además la
autenticación que este servicio no ve (para `BuscarUsuarioPorUid`) — ver §6.

**Un fallo de conexión** (servidor caído, puerto equivocado) llega como `UNAVAILABLE` en
cualquiera de los tres rpc — no está en las tablas porque no lo genera este servicio, es
infraestructura de gRPC.

---

## 5. Cómo funciona por dentro

`RegistroGrpcController` (`com.arquetipo.demo.registro.grpc`) no reimplementa ninguna regla:

- `Registrar` traduce el mensaje proto a `RegistroRequest` y delega en `RegistroService`, que
  es quien orquesta todo (alta en el proveedor de identidad, reconciliación si ya existía,
  compensación si el guardado local falla) — ver `README.md` §*Orquestación y compensación*
  para el detalle completo.
- `BuscarUsuarioPorUid` delega en `RegistroService.buscarPorFirebaseUid(uid)`, que hace una
  única consulta de solo lectura (`UsuarioRepository.findByFirebaseUid`) — no hay
  orquestación, compensación, ni verificación de identidad que explicar aquí: eso lo resuelve
  `chat-gateway` antes de llamar a este rpc (ver §6).
- `ExisteUsername` delega en `RegistroService.existeUsername(username)`, que reutiliza la
  misma consulta (`UsuarioRepository.existsByUsernameIgnoreCase`) que ya usa `Registrar` para
  su comprobación local de unicidad — no hay lógica nueva, solo se expone por su cuenta.

---

## 6. Contrato REST expuesto por `chat-gateway`

`chat-registro` no expone REST — `chat-gateway` es quien implementa esto como fachada REST de
`BuscarUsuarioPorUid`, siguiendo el mismo criterio que ya usa para `POST /api/v1/registro`
(Problem Details RFC 9457, ver la documentación propia de `chat-gateway`). **`ExisteUsername`
todavía no tiene fachada REST** — si `chat-gateway`/`chat-frontend` la necesitan, es un
contrato nuevo a definir, no está cubierto por lo de abajo.

| | |
|---|---|
| Método | `GET` |
| Path | `/api/v1/usuarios/{uid}` |
| Path param | `uid` — el UID de Firebase del usuario a resolver |
| Autenticación | El gateway exige `Authorization: Bearer <idToken>` y lo **verifica él mismo** con su propia integración con Firebase Admin SDK (`common.auth` en `chat-gateway`) — comprobando además que el uid que decodifica coincide con el `uid` pedido. A `BuscarUsuarioPorUid` en este servicio solo llega el `uid` ya autenticado: **nunca el token**. Sin la cabecera, o con un token inválido/de otro uid, el gateway rechaza (`401`/`403`) sin siquiera llamar por gRPC. |

Respuesta `200 OK`:

```json
{
  "username": "mateo",
  "email": "mateo@example.com"
}
```

| Código HTTP | Cuándo | Resuelto por |
|---|---|---|
| `200` | Usuario encontrado | `chat-registro` (`OK`) |
| `400` | `uid` vacío | `chat-registro` (`INVALID_ARGUMENT`) |
| `401` | Cabecera `Authorization` ausente/mal formada, o `idToken` inválido/expirado | **El gateway, sin llamar por gRPC** |
| `403` | El `idToken` es válido pero pertenece a un uid distinto al pedido | **El gateway, sin llamar por gRPC** |
| `404` | Ningún usuario con ese `uid` | `chat-registro` (`NOT_FOUND`) |
| `500` | Fallo inesperado de `chat-registro` | `chat-registro` (`INTERNAL`) |
| `503` | `chat-registro` no responde | El gateway (`UNAVAILABLE` u otro error de canal gRPC) |

Ejemplo:

```bash
curl -H "Authorization: Bearer <idToken>" http://localhost:8080/api/v1/usuarios/0lSUQS1RdYauzu3ifx6izoyzkvt2
```

Contrato completo (formato de error Problem Details, modelos TypeScript) en
`chat-gateway/docs/contratos-api.md` §4.2. Detalle de cómo el gateway valida el token en
`chat-gateway/docs/arquitectura-gateway.md`.

---

## 7. Generar el stub del cliente

Si tu proyecto usa Gradle con el plugin `com.google.protobuf` (igual que este repo), copia
`registro.proto` a tu `src/main/proto/` y añade las dependencias `io.grpc:grpc-stub` +
`io.grpc:grpc-protobuf` — el plugin genera `RegistroGrpcServiceGrpc`,
`RegistrarUsuarioRequest`/`RegistrarUsuarioResponse` automáticamente. Para otros lenguajes
(Go, Python, Node...), el mismo `.proto` es válido tal cual con el `protoc`/plugin de cada uno.

Si no quieres mantener una copia del `.proto`, el servidor tiene la **reflexión gRPC**
habilitada (`grpc.reflection.v1alpha`/`v1`): herramientas como `grpcurl` o
[Postman](https://learning.postman.com/docs/sending-requests/grpc/grpc-request-interface/)
pueden listar servicios y construir la petición sin el archivo, apuntando solo a
`localhost:9090` (o el host:puerto de tu entorno).

---

## 8. Control de versiones de este documento

| Fecha | Cambio |
|---|---|
| 2026-09-22 | Se añade `ExisteUsername` (booleano de disponibilidad de un `username`, sin autenticación — consulta pública, a diferencia de `BuscarUsuarioPorUid`). |
| 2026-09-20 | Se revierte el cambio del 2026-09-19 (2): `BuscarUsuarioPorUid` vuelve a no llevar `id_token` ni verificar nada — decisión de arquitectura explícita: **`chat-gateway` es el único punto del sistema que valida tokens de identidad** (con su propia integración con Firebase Admin SDK), para que futuros microservicios que necesiten autenticación no tengan que integrarse cada uno con Firebase. `chat-registro` conserva Firebase únicamente para crear/eliminar/buscar cuentas en el alta. Se quitan `TokenIdentidadInvalidoException`/`AccesoNoAutorizadoException` y el test `A01BrokenAccessControlTest` (esa propiedad de seguridad ahora se prueba en `chat-gateway`). |
| 2026-09-19 (2) | *(revertido el 2026-09-20)* `BuscarUsuarioPorUid` pasó a exigir y verificar `id_token` él mismo. |
| 2026-09-19 (1) | Se añade `BuscarUsuarioPorUid` (username/email a partir del uid de Firebase) y la propuesta de contrato REST §6 para que `chat-gateway` lo exponga. |
| 2026-09-18 | Se retira el REST de este servicio (`RegistroController`/`RegistroApi`, CORS, Swagger): gRPC pasa a ser el único protocolo. `chat-gateway` es ahora el único punto de entrada REST del sistema y reenvía aquí. Se actualizan las referencias a `contratos-api.md` (eliminado). |
| 2026-09-13 | Versión inicial: contrato gRPC de `RegistroGrpcService/Registrar`, espejo de `POST /api/v1/registro`. |
