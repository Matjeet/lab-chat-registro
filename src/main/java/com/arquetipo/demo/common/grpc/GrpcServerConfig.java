package com.arquetipo.demo.common.grpc;

import io.grpc.BindableService;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registra el servidor gRPC embebido. {@code grpc.server.enabled=false} evita por completo su
 * creacion (los tests lo desactivan para no competir por el puerto entre clases), igual que
 * {@code firebase.enabled} con {@code FirebaseAppConfig}.
 */
@Configuration
@EnableConfigurationProperties(GrpcServerProperties.class)
@ConditionalOnProperty(prefix = "grpc.server", name = "enabled", havingValue = "true", matchIfMissing = true)
public class GrpcServerConfig {

	@Bean
	public GrpcServerLifecycle grpcServerLifecycle(GrpcServerProperties properties, List<BindableService> servicios) {
		return new GrpcServerLifecycle(properties, servicios);
	}
}
