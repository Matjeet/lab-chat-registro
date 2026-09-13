package com.arquetipo.demo.common.grpc;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Arranca el servidor gRPC real (Netty), no el in-process de
 * {@code RegistroGrpcControllerTest}. Existe porque un desajuste de versiones entre
 * {@code grpc-netty-shaded} y {@code grpc-core} (este ultimo forzado por el BOM de
 * firebase-admin a una version distinta) solo revienta al construir el {@code Server} real
 * ({@code NettyServerBuilder.build()}, ver el comentario de {@code grpcVersion} en
 * build.gradle) — un canal in-process nunca pasa por ahi y no lo habria detectado.
 */
class GrpcServerLifecycleTest {

	@Test
	void start_levantaElServidorNettyReal_yLuegoSeDetieneLimpio() {
		GrpcServerProperties properties = new GrpcServerProperties();
		properties.setPort(0); // puerto efimero: no colisiona si varias suites corren en paralelo
		GrpcServerLifecycle lifecycle = new GrpcServerLifecycle(properties, List.of());

		lifecycle.start();
		try {
			assertThat(lifecycle.isRunning()).isTrue();
		} finally {
			lifecycle.stop();
		}
		assertThat(lifecycle.isRunning()).isFalse();
	}
}
