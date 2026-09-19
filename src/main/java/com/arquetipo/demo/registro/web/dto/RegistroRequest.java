package com.arquetipo.demo.registro.web.dto;

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
 *
 * <p>Es el mismo contrato para los dos protocolos que expone el servicio: lo recibe
 * directamente el gRPC ({@code RegistroGrpcController} lo construye desde
 * {@code RegistrarUsuarioRequest}) y su validacion (Bean Validation) es la unica fuente de
 * verdad de las reglas de {@code username}/{@code email}/{@code password} — no hay una capa
 * OpenAPI/Swagger aparte que las repita.
 */
public record RegistroRequest(

		@NotBlank
		@Size(min = 3, max = 50)
		@Pattern(regexp = "^[a-zA-Z0-9._-]+$",
				message = "solo admite letras, numeros y los signos . _ -")
		String username,

		@NotBlank
		@Email
		@Size(max = 255)
		String email,

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
