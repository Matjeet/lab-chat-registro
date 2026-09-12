package com.arquetipo.demo.registro.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * DTO de entrada del alta de un usuario.
 *
 * <p>El servicio crea primero el usuario en el proveedor de identidad (Firebase Auth) con
 * {@code email}/{@code password}; el UID y el proveedor los determina el servidor, nunca el
 * cliente (ver {@code RegistroService}). La contrasena solo se reenvia al proveedor: este
 * servicio no la persiste en ningun sitio.
 */
@Schema(name = "RegistroRequest", description = "Datos para registrar un usuario nuevo")
public record RegistroRequest(

		@Schema(
				description = "Nombre de usuario unico. Solo letras, numeros y los signos . _ -",
				example = "mateo",
				minLength = 3, maxLength = 50)
		@NotBlank
		@Size(min = 3, max = 50)
		@Pattern(regexp = "^[a-zA-Z0-9._-]+$",
				message = "solo admite letras, numeros y los signos . _ -")
		String username,

		@Schema(
				description = "Correo electronico unico. Se normaliza a minusculas.",
				example = "mateo@example.com",
				maxLength = 255)
		@NotBlank
		@Email
		@Size(max = 255)
		String email,

		@Schema(
				description = "Contrasena en claro: se reenvia al proveedor de identidad "
						+ "(Firebase Auth) y no se guarda en este servicio.",
				example = "Passw0rd!23",
				minLength = 8, maxLength = 20,
				format = "password")
		@NotBlank
		@Size(min = 8, max = 20)
		@Pattern(
				regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9\\s])(?!.*(.)\\1{3,}).+$",
				message = "debe tener mayuscula, minuscula, numero y caracter especial, "
						+ "y ningun caracter repetido 4 o mas veces seguidas")
		String password
) {

	/** Nunca incluir la contrasena en logs, ni siquiera por accidente via un log de este record. */
	@Override
	public String toString() {
		return "RegistroRequest[username=%s, email=%s, password=***]".formatted(username, email);
	}
}
