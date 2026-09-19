package com.arquetipo.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Punto de entrada del microservicio chat-registro.
 *
 * <p>Infraestructura transversal (auditoria JPA, excepciones de dominio) en
 * {@code com.arquetipo.demo.common}; el flujo de registro en {@code com.arquetipo.demo.registro},
 * expuesto por gRPC ({@code registro/grpc/}) — es el unico protocolo de este servicio; el REST
 * del sistema lo expone {@code chat-gateway}, que reenvia aqui por gRPC.
 */
@SpringBootApplication
public class DemoApplication {

	public static void main(String[] args) {
		SpringApplication.run(DemoApplication.class, args);
	}

}
