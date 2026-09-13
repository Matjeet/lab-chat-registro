# Contrato gRPC — Registro de usuarios (`chat-registro`)

Referencia para que **otro servicio** consuma por gRPC el alta de usuarios de
**chat-registro**, sin necesidad de leer el código de este repositorio.

Es el **mismo contrato y el mismo flujo** que `POST /api/v1/registro` (ver
[`contratos-api.md`](contratos-api.md)) — mismas reglas de validación, mismas reglas de
negocio, mismo `RegistroService` por debajo. Solo cambia el protocolo de transporte. Si ya
integraste el REST, no hay nada nuevo que aprender salvo el mapeo de errores (§4).

La fuente de verdad ejecutable es el propio `.proto`:
[`src/main/proto/registro.proto`](../src/main/proto/registro.proto).

---

## 1. Cómo conectarse

| Aspecto | Valor |
|---|---|
| Protocolo | gRPC (HTTP/2), **texto plano, sin TLS** (`-plaintext` en `grpcurl`, canal `usePlaintext()` en el cliente) |
| Host:puerto (local) | `localhost:9090` — puerto propio e independiente del HTTP (`8080`) |
| Variable de entorno del servidor | `GRPC_SERVER_PORT` (por defecto `9090`); `GRPC_SERVER_ENABLED=false` apaga el servidor por completo |
| Paquete proto | `com.arquetipo.demo.registro.grpc` |
| Servicio | `RegistroGrpcService` |
| Método (rpc) | `Registrar(RegistrarUsuarioRequest) returns (RegistrarUsuarioResponse)` — unario, sin streaming |
| Reflexión de servicio | Habilitada (`io.grpc:grpc-services`) — un cliente puede descubrir el contrato sin tener el `.proto`, ver §6 |
| Autenticación | Ninguna, igual que el REST — es el propio alta |

> El puerto real por entorno lo define infraestructura (igual que el `8080` del REST); en
> producción probablemente vaya detrás de un proxy/gateway con TLS. Pregunta al equipo de
> infraestructura la dirección de tu entorno si no es `localhost:9090`.

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
```

### `RegistrarUsuarioRequest`

| Campo | Tipo proto | Obligatorio | Reglas (idénticas al REST, ver `contratos-api.md` §3.1) |
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
| `created_at` | `string` | Instante de creación en UTC, **ISO-8601** (mismo formato que el `createdAt` del REST, ej. `"2026-09-08T20:53:47.441193Z"`). Se manda como `string`, no como `google.protobuf.Timestamp`, para no forzar esa dependencia en el cliente. |

> El UID del proveedor de identidad **no** se devuelve — igual que en el REST, el cliente no lo
> necesita.

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

---

## 4. Errores

gRPC no tiene *Problem Details*: los errores llegan como `StatusRuntimeException` con un
`Status.Code` y una `description` de texto libre. Mismo principio de seguridad que el REST
(§2 de `contratos-api.md`): **mensaje genérico al cliente, detalle real solo en el log del
servidor** — nunca asumas que la ausencia de un `4xx`/`5xx` HTTP equivalente significa menos
información filtrada; aquí es la misma disciplina, solo que expresada con códigos gRPC.

| Situación | Código gRPC | `description` | Equivalente REST |
|---|---|---|---|
| El cuerpo no supera Bean Validation | `INVALID_ARGUMENT` | `"El cuerpo de la peticion no supero la validacion -> <campo>: <mensaje>; ..."` (un `campo: mensaje` por cada violación) | `400` + `errors[]` |
| `username`/`email` duplicado, o el usuario ya existe en el proveedor de identidad | `ALREADY_EXISTS` | Mensaje genérico fijo: `"No se pudo completar el registro con los datos proporcionados"` — **nunca** indica qué campo colisionó | `409` |
| Cualquier otro fallo (proveedor de identidad, base de datos, bug interno) | `INTERNAL` | Mensaje genérico fijo: `"Ocurrio un error inesperado. Contacte con soporte."` | `500` |

Notas:

- **No ramifiques por `description` en `INVALID_ARGUMENT`.** El texto de cada violación es el
  mensaje por defecto de Bean Validation (igual que `errors[].message` en el REST): orientativo,
  puede cambiar de redacción. Si necesitas marcar campos en una UI, valida tú mismo en el
  cliente con las reglas de la tabla de §2 antes de llamar, igual que recomienda
  `contratos-api.md` §4.6 para el REST.
- **El `ALREADY_EXISTS` es deliberadamente genérico**, por la misma razón que el `409` REST:
  evitar que alguien enumere cuentas probando emails/usernames. No reintentes asumiendo que es
  transitorio.
- **Un fallo de conexión** (servidor caído, puerto equivocado) llega como `UNAVAILABLE`, no
  está en la tabla porque no lo genera este servicio — es infraestructura de gRPC.

---

## 5. Cómo funciona por dentro

`RegistroGrpcController` (`com.arquetipo.demo.registro.grpc`) es un espejo de
`RegistroController`: no reimplementa ninguna regla, solo traduce el mensaje proto al mismo
`RegistroRequest` que usa el REST y delega en el mismo `RegistroService`. La orquestación
completa (alta en el proveedor de identidad, reconciliación si ya existía, compensación si el
guardado local falla) es exactamente la descrita en `contratos-api.md` §6 — no se repite aquí
porque es literalmente el mismo código ejecutándose para los dos protocolos.

---

## 6. Generar el stub del cliente

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

## 7. Control de versiones de este documento

| Fecha | Cambio |
|---|---|
| 2026-09-13 | Versión inicial: contrato gRPC de `RegistroGrpcService/Registrar`, espejo de `POST /api/v1/registro`. |
