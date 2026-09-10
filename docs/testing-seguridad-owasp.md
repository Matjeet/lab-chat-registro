# Pruebas de seguridad — OWASP Top 10 (2021)

Tests automáticos que ejercen los controles de seguridad del servicio, uno por categoría del
OWASP Top 10 que es **verificable desde código**. Todos siguen el patrón **AAA**
(*Arrange – Act – Assert*), con los bloques marcados con comentarios.

Ubicación: `src/test/java/com/arquetipo/demo/security/`
Ejecución: `./gradlew test` (van incluidos en la suite normal).

## Cobertura

| OWASP | Estado | Clase de test | Qué comprueba |
|---|---|---|---|
| **A01 – Broken Access Control** | Parcial | *(ver nota)* | El servicio no tiene autenticación todavía; el único endpoint es un alta pública. Se cubre indirectamente: A05 verifica que Actuator no expone endpoints administrativos. |
| **A02 – Cryptographic Failures** | ✅ | `A02CryptographicFailuresTest` | La contraseña se guarda como hash **BCrypt** (`$2…`, 60 chars), nunca en claro; ni la contraseña ni el hash aparecen en ninguna respuesta HTTP. |
| **A03 – Injection** | ✅ | `A03InjectionTest` | Payloads SQLi / scripting en `username` y `email` se rechazan en validación (400) y **nunca** producen un 5xx; las consultas del repositorio están parametrizadas (un valor con sintaxis SQL se trata como literal); la tabla sigue operativa tras los intentos. |
| **A04 – Insecure Design** | ✅ | `A04AccountEnumerationTest` | Resistencia a **enumeración de cuentas**: un conflicto de `username` y uno de `email` devuelven una respuesta byte-idéntica (salvo `timestamp`); la respuesta no incluye el campo que colisionó ni el valor enviado. |
| **A05 – Security Misconfiguration** | ✅ | `A05SecurityMisconfigurationTest` | Un error no controlado devuelve 500 **sin** mensaje interno ni stack trace (`trace`/`exception` ausentes, `detail` genérico); los endpoints de Actuator sensibles (`env`, `beans`, `configprops`, `heapdump`, `threaddump`, `mappings`, `loggers`, `scheduledtasks`) responden 404; `health` no revela componentes; los errores se sirven como `application/problem+json`. |
| **A06 – Vulnerable & Outdated Components** | ⚠️ No desde tests | — | Se cubre con análisis de dependencias (p. ej. `gradle dependencyCheckAnalyze` / Dependabot / `gradle --refresh-dependencies` + escáner), no con tests unitarios. |
| **A07 – Identification & Authentication Failures** | ✅ | `A07AuthenticationFailuresTest` | Política de contraseña (8–100 caracteres) y de formato de `username` (`[A-Za-z0-9._-]`, 3–50) aplicada en el borde: lo que no cumple se rechaza con 400 y **no llega a la capa de servicio**. |
| **A08 – Software & Data Integrity Failures** | ⚠️ No desde tests | — | Aplica a integridad del pipeline CI/CD y de artefactos (firmas, checksums de dependencias, `gradle --write-verification-metadata`). No hay lógica en la app que testear. |
| **A09 – Security Logging & Monitoring Failures** | ✅ | `A09SecurityLoggingTest` | Un intento de registro rechazado deja rastro en el log (nivel `WARN`, con el campo y valor concretos), mientras el mensaje al cliente sigue siendo genérico. |
| **A10 – Server-Side Request Forgery (SSRF)** | ➖ N/A | — | El servicio no realiza peticiones HTTP salientes a partir de datos del usuario. |

## Notas

- **A01**: cuando se añada autenticación/autorización, aquí irán tests de que un usuario no
  puede acceder a recursos de otro, que los endpoints mutantes exigen credenciales, etc.
- **A07 – límite conocido**: hoy solo se valida la **longitud** de la contraseña, no su
  robustez (no hay lista de contraseñas comunes ni comprobación de entropía). Si se añade esa
  regla, el test `registro_contrasenaEnElMinimoExacto_pasaLaValidacion` habrá que ajustarlo.
- Los tests de integración usan H2 en memoria (perfil de test), no requieren MySQL.
