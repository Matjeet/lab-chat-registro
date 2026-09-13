package com.arquetipo.demo.common.grpc;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuracion del servidor gRPC embebido. Sigue el mismo patron que
 * {@code firebase.enabled} (ver FirebaseAppConfig): {@code enabled=false} evita por completo
 * el arranque del servidor (los tests lo desactivan para no pelearse por el puerto).
 */
@ConfigurationProperties(prefix = "grpc.server")
public class GrpcServerProperties {

	/** Si el servidor gRPC se levanta al iniciar la aplicacion. */
	private boolean enabled = true;

	/** Puerto TCP del servidor gRPC. Independiente del puerto HTTP (server.port). */
	private int port = 9090;

	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public int getPort() {
		return port;
	}

	public void setPort(int port) {
		this.port = port;
	}
}
