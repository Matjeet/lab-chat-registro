# chat-registro — microservicio de registro de usuarios

Servicio backend en **Spring Boot 4 / Java 25** con arquitectura MVC por capas.
Expone el **flujo de registro** (`POST /api/v1/registro`) que persiste usuarios en la
base de datos MySQL centralizada del sistema.

## Stack

| Área | Elección |
|------|----------|
| Framework | Spring Boot 4.1.1 (`spring-boot-starter-webmvc`) |
| Lenguaje | Java 25 (toolchain de Gradle) |
| Build | Gradle (wrapper incluido) |
| Persistencia | Spring Data JPA + Hibernate; MySQL (runtime), H2 en memoria (tests) |
| Migraciones | Flyway (`spring-boot-starter-flyway` + `flyway-mysql`) |
| Contraseñas | BCrypt (`spring-security-crypto`) |
| Validación | Bean Validation (`spring-boot-starter-validation`) |
| Errores | RFC 9457 *Problem Details* vía `@RestControllerAdvice` |
| Docs API | springdoc-openapi + Swagger UI |
| Observabilidad | Spring Boot Actuator |
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
de `src/main/resources/db/migration` (`V1__crear_tabla_usuarios.sql`, …) y registrando en
`flyway_schema_history` las ya ejecutadas. Hibernate solo **valida**
(`spring.jpa.hibernate.ddl-auto=validate`): comprueba que las tablas cuadran con las
entidades y no modifica nada.

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
| `CORS_ALLOWED_ORIGINS` | `http://localhost:3000` | Orígenes permitidos para `/api/**`. Lista separada por comas, cada uno `esquema://host:puerto` sin barra final. Vacío = ninguna petición cross-origin aceptada. |
| `CORS_ALLOW_CREDENTIALS` | `false` | Permitir cookies/credenciales cross-origin (incompatible con origen `*`). |

Para desarrollo local hay un fichero **`.env`** (plantilla en [`.env.example`](.env.example),
no se versiona):

- `./gradlew bootRun` lo carga automáticamente (lógica en `build.gradle`); una variable ya
  presente en el entorno real tiene prioridad sobre la del `.env`.
- Docker: `docker run --env-file .env chat-registro`.
- Primera vez: `cp .env.example .env` y ajusta lo que necesites.

Los tests usan H2 en memoria (`src/test/resources/application.yml`); **no** leen `.env` ni
necesitan MySQL.

## Flujo de registro

`POST /api/v1/registro`

```json
{ "username": "mateo", "email": "mateo@example.com", "password": "secretpass" }
```

- `username`: 3–50 caracteres, `[a-zA-Z0-9._-]`, único (sin distinguir mayúsculas).
- `email`: formato válido, ≤255, único (se normaliza a minúsculas).
- `password`: 8–100 caracteres; se guarda **solo el hash BCrypt**, nunca en claro.

Respuestas: `201` con el usuario creado (sin hash) · `409` si los datos entran en conflicto
con una cuenta existente · `400` con lista `errors` si la validación falla.

Por seguridad, el `409` devuelve **siempre el mismo mensaje genérico** (`"No se pudo
completar el registro con los datos proporcionados"`), sin revelar qué campo colisionó ni
el valor enviado. El detalle (username/email concretos) queda solo en el log del servidor
(`WARN`), para no facilitar la enumeración de cuentas.

El contrato completo (esquemas, ejemplos y códigos de respuesta) está documentado con
anotaciones OpenAPI en la interfaz `RegistroApi` (que implementa el controlador) y en los
DTO, y se explora desde Swagger UI.

## Documentación de la API

- **Contratos para clientes** → [`docs/contratos-api.md`](docs/contratos-api.md) (request/response,
  errores, notas de integración para frontend, modelos TypeScript).

Con la aplicación levantada (`./gradlew bootRun`):

- **Swagger UI** → <http://localhost:8080/swagger-ui.html>
- **OpenAPI JSON** → <http://localhost:8080/v3/api-docs>

## Estructura

```
com.arquetipo.demo
├── DemoApplication.java
├── common/                              infraestructura transversal
│   ├── config/JpaAuditingConfig.java        auditoría (createdAt/updatedAt)
│   ├── config/CorsProperties.java           binding de `app.cors.*` (CORS_ALLOWED_ORIGINS)
│   ├── config/CorsConfig.java               habilita CORS para /api/**
│   ├── exception/
│   │   ├── ResourceNotFoundException        → 404
│   │   └── DuplicateResourceException       → 409
│   └── web/GlobalExceptionHandler.java      excepciones → Problem Details (RFC 9457)
└── registro/                            flujo de alta de usuarios
    ├── config/PasswordEncoderConfig.java    bean PasswordEncoder (BCrypt)
    ├── domain/Usuario.java                  entidad JPA (tabla `usuarios`)
    ├── repository/UsuarioRepository.java    existsBy… / findBy… ignorando mayúsculas
    ├── mapper/UsuarioMapper.java            entidad → RegistroResponse
    ├── service/RegistroService.java         unicidad + hash + persistencia
    └── web/
        ├── RegistroController.java          POST /api/v1/registro (enrutado + delegación)
        ├── RegistroApi.java                 contrato OpenAPI (anotaciones springdoc)
        └── dto/RegistroRequest.java · RegistroResponse.java

src/main/resources/db
├── bootstrap.sql                       esquema + usuario (se ejecuta como root, 1 vez)
└── migration/
    └── V1__crear_tabla_usuarios.sql     migración Flyway
```

Flujo de una petición: `Controller` → `Service` (transacciones + reglas) → `Repository` (JPA)
→ `Entity`. El `Mapper` traduce entre `Entity` y los DTO; el cliente nunca ve la entidad.

## Arrancar

```bash
./gradlew bootRun
```

> Gradle necesita un JDK 17+ para ejecutarse y la toolchain compila con Java 25. Si tu
> `JAVA_HOME` apunta a un JDK antiguo, ajústalo o descomenta `org.gradle.java.home` en
> `gradle.properties`.
>
> Antes del primer arranque hay que provisionar el esquema (ver *Base de datos*).

| Recurso | URL |
|---------|-----|
| Registro | `POST` http://localhost:8080/api/v1/registro |
| Swagger UI | http://localhost:8080/swagger-ui.html |
| OpenAPI JSON | http://localhost:8080/v3/api-docs |
| Actuator health | http://localhost:8080/actuator/health |

Tests: `./gradlew test` · Empaquetar: `./gradlew bootJar` · Docker: `docker build -t chat-registro .`

## Tests

Patrón **AAA** (*Arrange – Act – Assert*) en toda la suite. Van todos en `./gradlew test`.

- Unitarios y de slice: `RegistroServiceTest`, `RegistroControllerTest` (`@WebMvcTest`),
  `UsuarioRepositoryTest` (`@DataJpaTest`, H2).
- Integración: `@SpringBootTest` sobre H2 en memoria (no requiere MySQL).
- **Seguridad (OWASP Top 10)**: `src/test/java/com/arquetipo/demo/security/`, una clase por
  categoría verificable desde código — ver [`docs/testing-seguridad-owasp.md`](docs/testing-seguridad-owasp.md).

## Contrato de errores

Todas las respuestas de error siguen RFC 9457:

```json
{
  "type": "urn:problem-type:validation-error",
  "title": "Datos invalidos",
  "status": 400,
  "detail": "El cuerpo de la peticion no supero la validacion",
  "instance": "/api/v1/registro",
  "timestamp": "2026-01-01T10:00:00Z",
  "errors": [{ "field": "email", "message": "debe ser una dirección de correo electrónico con formato correcto" }]
}
```

| Excepción | HTTP |
|-----------|------|
| `ResourceNotFoundException` | 404 |
| `DuplicateResourceException` | 409 |
| Bean Validation (`@Valid`) | 400 con lista `errors` |
| `DataIntegrityViolationException` | 409 |
| cualquier otra | 500 (mensaje genérico, traza solo en logs) |
