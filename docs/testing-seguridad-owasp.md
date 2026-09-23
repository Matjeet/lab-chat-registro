# Pruebas de seguridad — OWASP Top 10 (2021)

Tests automáticos que ejercen los controles de seguridad del servicio, uno por categoría del
OWASP Top 10 que es **verificable desde código**. Todos siguen el patrón **AAA**
(*Arrange – Act – Assert*), con los bloques marcados con comentarios.

Ubicación: `src/test/java/com/arquetipo/demo/security/`
Ejecución: `./gradlew test` (van incluidos en la suite normal).

## Cobertura

| OWASP | Estado | Clase de test | Qué comprueba |
|---|---|---|---|
| **A01 – Broken Access Control** | ✅ (para este flujo) | *(ver nota)* | El UID y el proveedor de la cuenta los determina el servidor (llama él mismo a Firebase Auth); el cliente no puede asignarse un UID ni un proveedor arbitrarios porque ninguno de los dos es un campo de la petición. `BuscarUsuarioPorUid` **no** implementa control de acceso propio a propósito — ver nota sobre el alcance. |
| **A02 – Cryptographic Failures** | ✅ | `A02CryptographicFailuresTest` | La contraseña en claro se reenvía al proveedor de identidad pero **nunca se persiste** (la entidad `Usuario` no tiene ningún campo de credenciales), **nunca se devuelve** en la respuesta, y **nunca aparece en el log del servidor**, ni siquiera cuando la petición se rechaza. |
| **A03 – Injection** | ✅ | `A03InjectionTest` | Payloads SQLi / scripting en `username` y `email` se rechazan en validación (`INVALID_ARGUMENT`) o nunca provocan un fallo interno (`INTERNAL`/`UNKNOWN`); las consultas del repositorio están parametrizadas (un valor con sintaxis SQL se trata como literal); la tabla sigue operativa tras los intentos. |
| **A04 – Insecure Design** | ✅ (para `Registrar`) | `A04AccountEnumerationTest` | Resistencia a **enumeración de cuentas** en el alta: un conflicto de `username`, de `email`, o un usuario que ya existe tanto en el proveedor de identidad como en la base local, devuelven un `Status` gRPC idéntico (mismo código `ALREADY_EXISTS`, misma `description`); la respuesta no incluye el campo que colisionó ni el valor enviado. `ExisteUsername` es una excepción deliberada a este principio — ver nota. |
| **A05 – Security Misconfiguration** | ✅ | `A05SecurityMisconfigurationTest` | Un error no controlado devuelve `INTERNAL` **sin** mensaje interno ni stack trace en la `description`; los endpoints de Actuator sensibles (`env`, `beans`, `configprops`, `heapdump`, `threaddump`, `mappings`, `loggers`, `scheduledtasks`) responden 404; `health` no revela componentes. La parte de CORS que llevaba esta clase se retiró junto con `CorsConfig`: no aplica a gRPC (no hay preflight/origen de navegador); si `chat-gateway` (el REST del sistema) tiene su propio CORS, se documenta ahí. |
| **A06 – Vulnerable & Outdated Components** | ⚠️ No desde tests | — | Se cubre con análisis de dependencias (p. ej. `gradle dependencyCheckAnalyze` / Dependabot / `gradle --refresh-dependencies` + escáner), no con tests unitarios. |
| **A07 – Identification & Authentication Failures** | ✅ | `A07AuthenticationFailuresTest` | Política de contraseña aplicada en el borde, **antes** de reenviarla al proveedor de identidad: 8–20 caracteres, mayúscula, minúscula, número, carácter especial, y ningún carácter repetido 4 o más veces seguidas. Una contraseña que la viola se rechaza con `INVALID_ARGUMENT` y **ni siquiera llega a la capa de servicio** (no se llama a Firebase con una contraseña que ya sabemos débil). También valida el formato de `username`. |
| **A08 – Software & Data Integrity Failures** | ⚠️ No desde tests | — | Aplica a integridad del pipeline CI/CD y de artefactos (firmas, checksums de dependencias, `gradle --write-verification-metadata`). No hay lógica en la app que testear. |
| **A09 – Security Logging & Monitoring Failures** | ✅ | `A09SecurityLoggingTest` | Un intento de registro rechazado —local, o porque el proveedor de identidad ya tenía el email, o por un fallo genérico del proveedor— deja rastro en el log (`WARN`/`ERROR`, con el motivo real), mientras el mensaje al cliente sigue siendo genérico. La contraseña no aparece en el log en ninguno de esos casos. |
| **A10 – Server-Side Request Forgery (SSRF)** | ➖ N/A | — | Las llamadas salientes del servicio son al SDK de administración de Firebase (`FirebaseAuth`), no a URLs derivadas de datos del usuario. |

## Notas

- **A01 — alcance de este veredicto, y por qué `BuscarUsuarioPorUid` no valida nada:** el UID y
  el proveedor no se pueden falsificar en el alta porque no son campos de `RegistroRequest` (el
  servidor los obtiene él mismo al llamar a Firebase). `BuscarUsuarioPorUid` es una consulta que
  sí necesita demostrar identidad (que quien pregunta por un uid sea su dueño) — pero esa
  decisión de arquitectura es explícita: **`chat-gateway` es el único punto del sistema que
  valida tokens de identidad** (integración propia con Firebase Admin SDK, ver
  `chat-gateway/docs/arquitectura-gateway.md`), para que microservicios futuros que necesiten
  autenticación no tengan que integrarse cada uno con Firebase (o con el proveedor que sea). Por
  eso `chat-registro` recibe aquí un `uid` ya autenticado y autorizado, sin ningún token que
  verificar — el test de control de acceso de este flujo (`AutenticacionExtractorTest` +
  `UsuarioControllerTest`) vive en `chat-gateway`, no aquí. Si un endpoint de `chat-registro`
  necesitara en el futuro su propio control de acceso (no delegado en el gateway), ahí sí
  correspondería un test A01 en esta suite.
- **A04 — por qué `ExisteUsername` no cuenta como enumeración insegura:** la resistencia a
  enumeración de `A04AccountEnumerationTest` es sobre el **alta** (`Registrar`): ahí sí importa
  que un atacante no distinga "username tomado" de "email tomado" de "ya existe en Firebase".
  `ExisteUsername` es un endpoint distinto, con un propósito distinto y documentado (validación
  en vivo de disponibilidad, ver `docs/contrato-grpc-registro.md` §2) — decir que un username
  está libre no expone nada sensible, así que no lleva el mismo tratamiento genérico. Si en el
  futuro se decide ocultar también esto (rate-limiting, por ejemplo), es una decisión nueva de
  producto, no un bug de esta suite.
- **A02/A07 — quién valida qué:** este servicio impone su propia política de contraseña en el
  borde (evita reenviar al proveedor una contraseña ya sabida débil), pero Firebase aplica la
  suya también al crear la cuenta; ambas capas son independientes y pueden divergir con el
  tiempo. La contraseña en si nunca se guarda aquí: la gestiona Firebase.
- **Compensación y reconciliación (A04/A09):** si Firebase crea el usuario pero el guardado
  local falla, el servicio borra ese usuario en Firebase (evita huérfanos) y responde el mismo
  conflicto genérico (`ALREADY_EXISTS`). Si Firebase dice que el email ya existe pero la base
  local no tiene fila para él, el servicio la crea (reconciliación) y responde con éxito, no un
  error — ver `RegistroService`/`docs/contrato-grpc-registro.md` §5 para el detalle completo.
- Los tests de integración usan H2 en memoria (perfil de test), no requieren MySQL ni
  credenciales reales de Firebase: `firebase.enabled=false` en el perfil de test evita que el
  contexto intente inicializar el SDK, y cada test aporta su propio `ProveedorIdentidad` de
  prueba con `@MockitoBean`.
