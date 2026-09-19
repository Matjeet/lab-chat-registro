# chat-registro — microservicio de registro de usuarios

Servicio backend en **Spring Boot 4 / Java 25** con arquitectura MVC por capas.
Expone el **flujo de registro** por **gRPC** (`RegistroGrpcService/Registrar`, único
protocolo del servicio) y persiste usuarios en la base de datos MySQL centralizada del
sistema. El REST del sistema lo sirve **`chat-gateway`**, que reenvía aquí por gRPC.

## Stack

| Área | Elección |
|------|----------|
| Framework | Spring Boot 4.1.1 |
| Lenguaje | Java 25 (toolchain de Gradle) |
| Build | Gradle (wrapper incluido) |
| Persistencia | Spring Data JPA + Hibernate; MySQL (runtime), H2 en memoria (tests) |
| Migraciones | Flyway (`spring-boot-starter-flyway` + `flyway-mysql`) |
| Identidad | Firebase Auth (Admin SDK), tras el puerto `registro.identidad.ProveedorIdentidad`. El servicio no persiste contraseñas. |
| Validación | Bean Validation (`spring-boot-starter-validation`), aplicada a mano en el controller gRPC |
| Transporte | gRPC (`registro/grpc/`, servidor embebido, ver *Protocolo gRPC*) |
| Observabilidad | Spring Boot Actuator (vía `spring-boot-starter-webmvc`, único uso que le queda al HTTP en este servicio) |
| Utilidades | Lombok, DevTools |

## Base de datos

Todos los microservicios comparten **una instancia MySQL** (localhost:3306). Cada servicio
tiene su **propio esquema** y su **propio usuario** con permisos solo sobre ese esquema.

| Servicio | Esquema | Usuario |
|----------|---------|---------|
| chat-registro | `chat_registro` | `chat_registro_svc` |

### Provisión (una sola vez, como `root`)

```bash
mysql -u root -p < src/main/resources/db/bootstrap.sql
```

Crea el esquema `chat_registro` y el usuario `chat_registro_svc` / `chat_registro_pw`.

### Esquema de tablas (Flyway)

Las tablas las crea **Flyway** al arrancar la aplicación, aplicando en orden las migraciones
de `src/main/resources/db/migration` y registrando en `flyway_schema_history` las ya
ejecutadas. Hibernate solo **valida** (`spring.jpa.hibernate.ddl-auto=validate`): comprueba
que las tablas cuadran con las entidades y no modifica nada.

| Migración | Qué hace |
|---|---|
| `V1__crear_tabla_usuarios.sql` | Tabla `usuarios` (username, email, password_hash — ya retirada, ver `V2`). |
| `V2__usuarios_firebase_auth.sql` | Tabla auxiliar `proveedores_auth` (sembrada con los `providerId` de Firebase Auth); en `usuarios` quita `password_hash` y añade `firebase_uid` (único) y `proveedor_id` (FK a `proveedores_auth`). |

Para un cambio de esquema se añade un fichero nuevo `V<n>__descripcion.sql` (nunca se edita
uno ya aplicado) y se ajusta la entidad JPA correspondiente.

### Credenciales y variables de entorno

`application.yml` trae valores por defecto para desarrollo local. En cualquier otro entorno
se sobreescriben por variables de entorno:

| Variable | Por defecto | Descripción |
|----------|-------------|-------------|
| `DB_URL` | `jdbc:mysql://localhost:3306/chat_registro?...` | JDBC URL del esquema del servicio |
| `DB_USERNAME` | `chat_registro_svc` | Usuario propio del servicio |
| `DB_PASSWORD` | `chat_registro_pw` | Contraseña de ese usuario |
| `JPA_DDL_AUTO` | `validate` | `validate` \| `none` \| `update` \| `create` \| `create-drop` |
| `FIREBASE_ENABLED` | `true` | `false` desactiva el SDK de Firebase por completo. **Sin un proveedor de identidad la app no arranca** (`RegistroService` lo necesita); solo tiene sentido en `false` si vas a sustituirlo por otro bean tú mismo. |
| `FIREBASE_CREDENTIALS_PATH` | *(vacío)* | Ruta al JSON de la cuenta de servicio de Firebase. Vacío = credenciales por defecto del entorno (ADC / `GOOGLE_APPLICATION_CREDENTIALS`). |
| `GRPC_SERVER_ENABLED` | `true` | `false` desactiva por completo el servidor gRPC (los tests lo hacen). |
| `GRPC_SERVER_PORT` | `9090` | Puerto TCP del servidor gRPC, independiente del HTTP (`server.port`, solo usado hoy por Actuator). |

Para desarrollo local hay un fichero **`.env`** (plantilla en [`.env.example`](.env.example),
no se versiona):

- Lo carga la propia aplicación al arrancar (`DotenvEnvironmentPostProcessor`, en
  `common/env/`) — funciona igual con `./gradlew bootRun`, desde el IDE o como jar
  (`java -jar`). Una variable ya presente en el entorno real tiene prioridad sobre la del
  `.env`.
- Docker: `docker run --env-file .env chat-registro`.
- Primera vez: `cp .env.example .env` y ajusta lo que necesites.

Los tests usan H2 en memoria (`src/test/resources/application.yml`); **no** leen `.env` ni
necesitan MySQL.

## Flujo de registro

Este servicio **llama a Firebase Auth**, no el cliente: recibe `username` + `email` +
`password` (por gRPC, ver *Protocolo gRPC*), crea primero el usuario en Firebase (Admin SDK)
y solo si eso funciona persiste el perfil de dominio. El UID y el proveedor los decide el
servidor; no son campos de la petición. La contraseña en claro se reenvía a Firebase y
**nunca se guarda** en este servicio.

- `username`: 3–50 caracteres, `[a-zA-Z0-9._-]`, único (sin distinguir mayúsculas).
- `email`: formato válido, ≤255, único (se normaliza a minúsculas).
- `password`: 8–20 caracteres; al menos una mayúscula, una minúscula, un número y un carácter
  especial (cualquiera que no sea letra, número o espacio); ningún carácter repetido 4 o más
  veces seguidas (`aaaa` no vale, `aaa` sí). Se valida aquí (para no reenviar al proveedor una
  contraseña que ya sabemos débil) y de nuevo la aplica Firebase al crear la cuenta.

Resultado: el usuario creado (`RegistrarUsuarioResponse`) · `ALREADY_EXISTS` si los datos
entran en conflicto con una cuenta existente (username, email, o el usuario ya existente en
Firebase) · `INVALID_ARGUMENT` si la validación falla. Detalle completo del contrato y del
mapeo de errores en [`docs/contrato-grpc-registro.md`](docs/contrato-grpc-registro.md).

Por seguridad, el conflicto devuelve **siempre el mismo mensaje genérico** (`"No se pudo
completar el registro con los datos proporcionados"`), sin revelar qué colisionó ni el valor
enviado. El detalle concreto (incluido lo que reporte Firebase) queda solo en el log del
servidor (`WARN`/`ERROR`), para no facilitar la enumeración de cuentas.

### Orquestación y compensación (`RegistroService`)

1. Comprobaciones locales de unicidad (username, email) — evita llamar a Firebase si ya
   sabemos que el alta no puede completarse.
2. Crea el usuario en Firebase con `email` + `password`.
3. Si Firebase dice que el email **ya existe**: se busca ese usuario en Firebase y se
   **reconcilia** — si nuestra base no tiene fila para él (un alta anterior que falló a
   medias, por ejemplo), se crea ahora y el alta termina en éxito; si ya la tiene, es un
   conflicto real (`ALREADY_EXISTS` genérico).
4. Si Firebase creó el usuario pero **el guardado en la base de datos falla**, se **revierte**
   (borra) el usuario recién creado en Firebase, para no dejarlo huérfano. En la rama de
   reconciliación nunca se borra: ese usuario ya existía en Firebase antes de esta petición.

### Abstracción del proveedor de identidad

Toda la integración con Firebase vive en `com.arquetipo.demo.registro.identidad`, detrás de
la interfaz `ProveedorIdentidad` (`crearUsuario` / `eliminarUsuario` / `buscarPorEmail` /
`nombreProveedor`). `RegistroService` solo conoce esa interfaz: cambiar de proveedor de
identidad (o añadir uno nuevo) es escribir una implementación nueva en un subpaquete
(`identidad/firebase/` hoy), sin tocar la lógica de negocio del registro.

## Documentación de la API

**Contrato gRPC (para otros servicios, incluido `chat-gateway`)** →
[`docs/contrato-grpc-registro.md`](docs/contrato-grpc-registro.md) (campos, mapeo de
errores, ejemplo `grpcurl`/Java, cómo generar el stub del cliente).

## Estructura

```
com.arquetipo.demo
├── DemoApplication.java
├── common/                              infraestructura transversal
│   ├── config/JpaAuditingConfig.java        auditoría (createdAt/updatedAt)
│   ├── env/DotenvEnvironmentPostProcessor.java  carga .env (Gradle, IDE o jar — ver META-INF/spring.factories)
│   ├── exception/DuplicateResourceException  → ALREADY_EXISTS en el controller gRPC
│   └── grpc/                                servidor gRPC embebido
│       ├── GrpcServerProperties.java            binding de `grpc.server.*` (puerto, enabled)
│       ├── GrpcServerConfig.java                registra el servidor si grpc.server.enabled=true
│       └── GrpcServerLifecycle.java             arranca/detiene el servidor con el ciclo de vida de Spring
└── registro/                            flujo de alta de usuarios
    ├── domain/Usuario.java                  entidad JPA (tabla `usuarios`)
    ├── domain/ProveedorAuth.java            entidad JPA de solo lectura (tabla `proveedores_auth`)
    ├── repository/UsuarioRepository.java    existsBy… / findBy… ignorando mayúsculas
    ├── repository/ProveedorAuthRepository.java  findByNombreIgnoreCase
    ├── mapper/UsuarioMapper.java            entidad → RegistroResponse
    ├── identidad/                           puerto hacia el proveedor de identidad externo
    │   ├── ProveedorIdentidad.java              interfaz: crearUsuario / eliminarUsuario / buscarPorEmail / nombreProveedor
    │   ├── UsuarioExterno.java                  record (uid)
    │   ├── ProveedorIdentidadException.java     fallo del proveedor → INTERNAL generico
    │   ├── UsuarioYaRegistradoException.java    dispara la reconciliacion en RegistroService
    │   └── firebase/
    │       ├── FirebaseAppConfig.java           inicializa el SDK (FirebaseApp/FirebaseAuth)
    │       └── FirebaseProveedorIdentidad.java  implementacion sobre Firebase Admin SDK
    ├── service/RegistroService.java         orquesta: unicidad local + proveedor + persistencia + compensacion
    ├── web/dto/RegistroRequest.java · RegistroResponse.java   contrato compartido (validado y usado por grpc/)
    └── grpc/                                unico protocolo expuesto (ver *Protocolo gRPC*)
        ├── RegistroGrpcController.java          rpc Registrar (valida + delega en RegistroService)
        └── RegistroGrpcMapper.java              traduce entre los DTO y los mensajes de registro.proto

src/main/proto/registro.proto             contrato gRPC (servicio + mensajes), genera los stubs en build/generated

src/main/resources/db
├── bootstrap.sql                            esquema + usuario (se ejecuta como root, 1 vez)
└── migration/
    ├── V1__crear_tabla_usuarios.sql          migración Flyway inicial
    └── V2__usuarios_firebase_auth.sql        quita password_hash; añade firebase_uid + proveedores_auth
```

Flujo de una petición: `RegistroGrpcController` (valida) → `RegistroService` (transacciones +
reglas) → `Repository` (JPA) → `Entity`. El `Mapper`/`RegistroGrpcMapper` traducen entre
`Entity`/DTO y el mensaje proto; el cliente nunca ve la entidad.

## Arrancar

```bash
./gradlew bootRun
```

> Gradle necesita un JDK 17+ para ejecutarse y la toolchain compila con Java 25. Si tu
> `JAVA_HOME` apunta a un JDK antiguo, ajústalo o descomenta `org.gradle.java.home` en
> `gradle.properties`.
>
> Antes del primer arranque hay que provisionar el esquema (ver *Base de datos*).
>
> **El servicio necesita un proveedor de identidad para arrancar.** Con `FIREBASE_ENABLED=true`
> hace falta una clave de cuenta de servicio real (`FIREBASE_CREDENTIALS_PATH`); si no,
> `bootRun` falla explicando qué falta (`FirebaseApp`/credenciales o el bean `ProveedorIdentidad`).
> Con `FIREBASE_ENABLED=false` el contexto tampoco arranca (`RegistroService` no tiene con qué
> construirse) — solo tiene sentido si aportas tú mismo un bean `ProveedorIdentidad` alternativo.

| Recurso | Dirección |
|---------|-----|
| Registro (gRPC) | `localhost:9090`, `RegistroGrpcService/Registrar` (ver *Protocolo gRPC*) |
| Actuator health | http://localhost:8081/actuator/health |

Tests: `./gradlew test` · Empaquetar: `./gradlew bootJar` · Docker: `docker build -t chat-registro .`

## Protocolo gRPC

`registro/grpc/RegistroGrpcController` es el **único protocolo** que expone este servicio
para el registro — el REST del sistema lo sirve `chat-gateway`, que reenvía aquí por gRPC. El
contrato vive en [`src/main/proto/registro.proto`](src/main/proto/registro.proto):

```proto
service RegistroGrpcService {
  rpc Registrar (RegistrarUsuarioRequest) returns (RegistrarUsuarioResponse);
}
```

- **Servidor**: embebido, se arranca/detiene con el ciclo de vida de Spring
  (`common/grpc/GrpcServerLifecycle`), en un puerto TCP propio e independiente del HTTP
  (`grpc.server.port`, por defecto `9090`; variable de entorno `GRPC_SERVER_PORT`).
  `GRPC_SERVER_ENABLED=false` lo desactiva por completo (los tests ya lo hacen).
- **Reflexión habilitada** (`io.grpc:grpc-services`): se puede probar sin tener el `.proto` a
  mano, por ejemplo con [`grpcurl`](https://github.com/fullstorydev/grpcurl):
  ```bash
  grpcurl -plaintext -d '{"username":"mateo","email":"mateo@example.com","password":"Passw0rd!23"}' \
    localhost:9090 com.arquetipo.demo.registro.grpc.RegistroGrpcService/Registrar
  ```
- **Validación**: como gRPC no pasa por Spring MVC, `RegistroGrpcController` valida a mano el
  `RegistroRequest` con el mismo `Validator` de Bean Validation que usa el resto del flujo —
  no hay una capa REST/OpenAPI aparte que repita las reglas.
- **Errores** (mismo principio de mensaje genérico al cliente / detalle real solo en el log):

  | Situación | Código gRPC |
  |---|---|
  | Validación fallida | `INVALID_ARGUMENT` |
  | Username/email duplicado, o ya existente en el proveedor | `ALREADY_EXISTS` (mensaje genérico) |
  | Error inesperado | `INTERNAL` (mensaje genérico) |

- Generación de stubs: plugin `com.google.protobuf` (`./gradlew generateProto`), se ejecuta
  automáticamente antes de compilar. **La cache de configuración de Gradle está desactivada**
  (`gradle.properties`) porque ese plugin todavía no la soporta.
- **Contrato para otro servicio consumidor** → [`docs/contrato-grpc-registro.md`](docs/contrato-grpc-registro.md)
  (campos, mapeo de errores, ejemplo `grpcurl`/Java, cómo generar el stub del cliente).

## Tests

Patrón **AAA** (*Arrange – Act – Assert*) en toda la suite. Van todos en `./gradlew test`.

- Unitarios: `RegistroServiceTest`, `UsuarioRepositoryTest` (`@DataJpaTest`, H2).
- De protocolo: `RegistroGrpcControllerTest` (servidor gRPC in-process, sin red real).
- Integración: `@SpringBootTest` sobre H2 en memoria (no requiere MySQL).
- **Seguridad (OWASP Top 10)**: `src/test/java/com/arquetipo/demo/security/`, una clase por
  categoría verificable desde código — la mayoría llama directamente a
  `RegistroGrpcController` (con o sin contexto de Spring según lo que necesite cada una) en
  vez de un canal de red — ver [`docs/testing-seguridad-owasp.md`](docs/testing-seguridad-owasp.md).

## Contrato de errores

Sin REST en este servicio no hay *Problem Details*: los errores de gRPC llegan como
`StatusRuntimeException` con un código y una `description` — ver la tabla de *Protocolo
gRPC* de más arriba y el detalle completo (incluida la política de "mensaje genérico al
cliente, detalle real en el log") en
[`docs/contrato-grpc-registro.md`](docs/contrato-grpc-registro.md) §4.

| Excepción de dominio | Código gRPC |
|-----------|------|
| `DuplicateResourceException` | `ALREADY_EXISTS` |
| Bean Validation | `INVALID_ARGUMENT` |
| `DataIntegrityViolationException` (traducida a `DuplicateResourceException` en `RegistroService`) | `ALREADY_EXISTS` |
| `ProveedorIdentidadException` (fallo de Firebase que no es "ya existe") | `INTERNAL` (mensaje genérico; el detalle real solo en logs) |
| cualquier otra | `INTERNAL` (mensaje genérico, traza solo en logs) |
