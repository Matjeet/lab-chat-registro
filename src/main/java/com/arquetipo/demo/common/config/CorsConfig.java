package com.arquetipo.demo.common.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Habilita CORS para los endpoints {@code /api/**}.
 *
 * <p>Los origenes permitidos vienen de {@link CorsProperties} (variable de entorno
 * {@code CORS_ALLOWED_ORIGINS}). Si la lista queda vacia no se registra ningun mapping y el
 * navegador rechazara cualquier peticion cross-origin (comportamiento por defecto de Spring MVC).
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(CorsProperties.class)
public class CorsConfig implements WebMvcConfigurer {

	private final CorsProperties properties;

	public CorsConfig(CorsProperties properties) {
		this.properties = properties;
	}

	@Override
	public void addCorsMappings(CorsRegistry registry) {
		if (properties.allowedOrigins().isEmpty()) {
			log.warn("CORS deshabilitado: no hay origenes en CORS_ALLOWED_ORIGINS");
			return;
		}
		log.info("CORS habilitado para /api/** desde: {}", properties.allowedOrigins());
		registry.addMapping("/api/**")
				.allowedOrigins(properties.allowedOrigins().toArray(String[]::new))
				.allowedMethods(properties.allowedMethods().toArray(String[]::new))
				.allowedHeaders(properties.allowedHeaders().toArray(String[]::new))
				.allowCredentials(properties.allowCredentials())
				.maxAge(properties.maxAge().toSeconds());
	}
}
