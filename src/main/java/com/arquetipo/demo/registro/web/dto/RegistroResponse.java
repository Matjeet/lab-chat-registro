package com.arquetipo.demo.registro.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * DTO de salida tras registrar un usuario. No expone el UID de Firebase ni ningun dato de
 * autenticacion mas alla del proveedor usado.
 */
@Schema(name = "RegistroResponse", description = "Usuario registrado")
public record RegistroResponse(

		@Schema(description = "Identificador generado", example = "1")
		Long id,

		@Schema(description = "Nombre de usuario", example = "mateo")
		String username,

		@Schema(description = "Correo electronico (en minusculas)", example = "mateo@example.com")
		String email,

		@Schema(description = "Proveedor de Firebase Auth usado en el alta", example = "password")
		String proveedor,

		@Schema(description = "Si la cuenta esta activa", example = "true")
		boolean activo,

		@Schema(description = "Instante de creacion (UTC)", example = "2026-09-08T20:53:47.441193Z")
		Instant createdAt
) {
}
