package com.arquetipo.demo.registro.web.dto;

import java.time.Instant;

/**
 * DTO de salida tras registrar un usuario. No expone el UID de Firebase ni ningun dato de
 * autenticacion mas alla del proveedor usado.
 *
 * <p>{@code RegistroGrpcMapper} lo traduce a {@code RegistrarUsuarioResponse} (gRPC), el unico
 * protocolo que expone hoy el servicio.
 */
public record RegistroResponse(

		Long id,

		String username,

		String email,

		String proveedor,

		boolean activo,

		Instant createdAt
) {
}
