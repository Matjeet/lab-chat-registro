package com.arquetipo.demo.common.config;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuracion de CORS.
 *
 * <p>La lista de origenes permitidos se inyecta por la variable de entorno
 * {@code CORS_ALLOWED_ORIGINS} (valores separados por comas). El resto de opciones tiene
 * valores por defecto razonables para una API REST consumida con tokens en la cabecera
 * {@code Authorization}.
 *
 * @param allowedOrigins   origenes exactos permitidos (esquema + host + puerto, sin barra final)
 * @param allowedMethods   metodos HTTP permitidos en peticiones cross-origin
 * @param allowedHeaders   cabeceras de peticion permitidas ({@code *} = reflejar las solicitadas)
 * @param allowCredentials si se permiten cookies / credenciales (incompatible con origen {@code *})
 * @param maxAge           cuanto puede cachear el navegador la respuesta preflight
 */
@ConfigurationProperties(prefix = "app.cors")
public record CorsProperties(
		List<String> allowedOrigins,
		List<String> allowedMethods,
		List<String> allowedHeaders,
		boolean allowCredentials,
		Duration maxAge
) {

	public CorsProperties {
		allowedOrigins = allowedOrigins == null ? List.of() : List.copyOf(allowedOrigins);
		allowedMethods = allowedMethods == null || allowedMethods.isEmpty()
				? List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
				: List.copyOf(allowedMethods);
		allowedHeaders = allowedHeaders == null || allowedHeaders.isEmpty()
				? List.of("*")
				: List.copyOf(allowedHeaders);
		maxAge = maxAge == null ? Duration.ofHours(1) : maxAge;
	}
}
