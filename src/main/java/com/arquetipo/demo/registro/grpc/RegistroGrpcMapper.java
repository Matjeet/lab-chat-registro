package com.arquetipo.demo.registro.grpc;

import com.arquetipo.demo.registro.web.dto.RegistroRequest;
import com.arquetipo.demo.registro.web.dto.RegistroResponse;
import org.springframework.stereotype.Component;

/**
 * Traduce entre los mensajes de {@code registro.proto} y los DTO REST existentes
 * ({@link RegistroRequest} / {@link RegistroResponse}), para que ambos protocolos compartan
 * exactamente el mismo contrato de validacion y el mismo flujo ({@code RegistroService}).
 */
@Component
class RegistroGrpcMapper {

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
}
