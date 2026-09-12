# Pruebas de seguridad — OWASP Top 10 (2021)

Tests automáticos que ejercen los controles de seguridad del servicio, uno por categoría del
OWASP Top 10 que es **verificable desde código**. Todos siguen el patrón **AAA**
(*Arrange – Act – Assert*), con los bloques marcados con comentarios.

Ubicación: `src/test/java/com/arquetipo/demo/security/`
Ejecución: `./gradlew test` (van incluidos en la suite normal).

## Cobertura

| OWASP | Estado | Clase de test | Qué comprueba |
|---|---|---|---|
| **A01 – Broken Access Control** | ⚠️ Gap conocido | *(ver nota)* | El servicio delega la autenticación en Firebase Auth pero **todavía no verifica el ID token**: `uid`/`proveedor` se confían tal cual del body de `POST /api/v1/registro`. Se cubre indirectamente: A05 verifica que Actuator no expone endpoints administrativos. Ver nota. |
| **A02 – Cryptographic Failures** | ✅ (alcance reducido) | `A02CryptographicFailuresTest` | El servicio ya **no gestiona contraseñas** (las guarda Firebase); los tests son una guarda de regresión: la entidad `Usuario` no tiene ningún campo de credenciales y un `password` enviado en el body se ignora (no se persiste ni se devuelve). |
| **A03 – Injection** | ✅ | `A03InjectionTest` | Payloads SQLi / scripting en `username`, `email` y `uid` se rechazan en validación (400) o nunca provocan un 5xx; las consultas del repositorio están parametrizadas (un valor con sintaxis SQL se trata como literal); la tabla sigue operativa tras los intentos. |
| **A04 – Insecure Design** | ✅ | `A04AccountEnumerationTest` | Resistencia a **enumeración de cuentas**: un conflicto de `username`, de `email` o de `uid` devuelven una respuesta byte-idéntica (salvo `timestamp`); la respuesta no incluye el campo que colisionó ni el valor enviado. |
| **A05 – Security Misconfiguration** | ✅ | `A05SecurityMisconfigurationTest` | Un error no controlado devuelve 500 **sin** mensaje interno ni stack trace (`trace`/`exception` ausentes, `detail` genérico); los endpoints de Actuator sensibles (`env`, `beans`, `configprops`, `heapdump`, `threaddump`, `mappings`, `loggers`, `scheduledtasks`) responden 404; `health` no revela componentes; los errores se sirven como `application/problem+json`. **CORS**: preflight y petición real desde un origen permitido llevan `Access-Control-Allow-Origin`; desde un origen fuera de la lista → 403 sin cabeceras `Access-Control-*`; sin `Access-Control-Allow-Credentials` por defecto. |
| **A06 – Vulnerable & Outdated Components** | ⚠️ No desde tests | — | Se cubre con análisis de dependencias (p. ej. `gradle dependencyCheckAnalyze` / Dependabot / `gradle --refresh-dependencies` + escáner), no con tests unitarios. |
| **A07 – Identification & Authentication Failures** | ✅ (alcance reducido) | `A07AuthenticationFailuresTest` | Ya no hay contraseña que validar aquí (la valida Firebase). Lo que este servicio garantiza: `uid` no vacío ni desproporcionado; `proveedor` (opcional, por defecto `password`) restringido a los providerId reales de Firebase Auth cuando se envía; formato de `username` — todo rechazado con 400 **antes de llegar a la capa de servicio** si no cumple. Ver nota del gap de verificación de token. |
| **A08 – Software & Data Integrity Failures** | ⚠️ No desde tests | — | Aplica a integridad del pipeline CI/CD y de artefactos (firmas, checksums de dependencias, `gradle --write-verification-metadata`). No hay lógica en la app que testear. |
| **A09 – Security Logging & Monitoring Failures** | ✅ | `A09SecurityLoggingTest` | Un intento de registro rechazado deja rastro en el log (nivel `WARN`, con el campo y valor concretos), mientras el mensaje al cliente sigue siendo genérico. |
| **A10 – Server-Side Request Forgery (SSRF)** | ➖ N/A | — | El servicio no realiza peticiones HTTP salientes a partir de datos del usuario. |

## Notas

- **A01/A07 — gap de seguridad deliberado, pendiente:** `POST /api/v1/registro` no verifica el
  ID token de Firebase; confía en `uid` y `proveedor` del body. Antes de exponer el
  servicio fuera de desarrollo hace falta un filtro que valide `Authorization: Bearer <idToken>`
  (Firebase Admin SDK, o un Resource Server con el JWK de Firebase) y derive esos dos valores
  del token verificado. Cuando se añada, aquí van los tests de: token ausente/expirado/inválido
  → 401, token de otro proyecto de Firebase → 401, y que `uid` del token siempre
  coincide con el de la fila creada (no se puede registrar a nombre de otro UID).
- **A02/A07 — alcance reducido a propósito:** la gestión de contraseñas (hash, política de
  fortaleza, reset, MFA) es responsabilidad de Firebase Auth, no de este servicio. No tiene
  sentido testear aquí una política de contraseñas que el código ya no implementa.
- Los tests de integración usan H2 en memoria (perfil de test), no requieren MySQL.
