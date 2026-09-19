package com.arquetipo.demo.registro.grpc;

import com.arquetipo.demo.registro.web.dto.RegistroRequest;
import com.arquetipo.demo.registro.web.dto.RegistroResponse;
import com.arquetipo.demo.registro.web.dto.UsuarioBasico;
import org.springframework.stereotype.Component;

/**
 * Traduce entre los mensajes de {@code registro.proto} y los DTO del dominio
 * ({@link RegistroRequest} / {@link RegistroResponse}), para que la validacion y el flujo
 * ({@code RegistroService}) tengan una unica fuente de verdad.
 *
 * <p>Publica (no de paquete) para que los tests de seguridad
 * ({@code src/test/java/com/arquetipo/demo/security/}) puedan construir un
 * {@code RegistroGrpcController} sin levantar contexto de Spring, igual que hace
 * {@code RegistroGrpcControllerTest}.
 */
@Component
public class RegistroGrpcMapper {

	RegistroRequest aRegistroRequest(RegistrarUsuarioRequest grpcRequest) {
		return new RegistroRequest(grpcRequest.getUsername(), grpcRequest.getEmail(), grpcRequest.getPassword());
	}

	RegistrarUsuarioResponse aGrpcResponse(RegistroResponse response) {
		return RegistrarUsuarioResponse.newBuilder()
				.setId(response.id())
				.setUsername(response.username())
				.setEmail(response.email())
				.setProveedor(response.proveedor())
				.setActivo(response.activo())
				.setCreatedAt(response.createdAt().toString())
				.build();
	}

	BuscarUsuarioPorUidResponse aGrpcResponse(UsuarioBasico usuario) {
		return BuscarUsuarioPorUidResponse.newBuilder()
				.setUsername(usuario.username())
				.setEmail(usuario.email())
				.build();
	}
}
