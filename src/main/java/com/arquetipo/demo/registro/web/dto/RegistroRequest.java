package com.arquetipo.demo.registro.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * DTO de entrada del alta de un usuario ya autenticado con Firebase Auth.
 *
 * <p>{@code uid} es el identificador que asigna Firebase (campo {@code user.uid} del SDK de
 * cliente). {@code proveedor} es opcional: si no se envia, el servicio asume {@code "password"}
 * (unico proveedor que usa hoy el frontend); se valida igual si se envia.
 *
 * <p><b>Nota de seguridad:</b> {@code uid} y {@code proveedor} se toman tal cual del cuerpo de
 * la peticion; este servicio todavia NO verifica el ID token de Firebase (ver
 * {@code RegistroService}). No usar en producción sin anadir esa verificacion.
 */
@Schema(name = "RegistroRequest", description = "Datos para completar el alta de un usuario ya autenticado con Firebase")
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
				description = "UID que Firebase Authentication asigno al usuario tras autenticarse "
						+ "(user.uid del SDK de cliente).",
				example = "aB3dEfGhIjKlMnOpQrStUvWxYz12",
				maxLength = 128)
		@NotBlank
		@Size(max = 128)
		String uid,

		@Schema(
				description = "Proveedor de Firebase Auth usado, tal cual su providerId. "
						+ "Opcional: si se omite se asume \"password\".",
				example = "password",
				requiredMode = Schema.RequiredMode.NOT_REQUIRED,
				allowableValues = {"password", "google.com", "facebook.com", "apple.com",
						"github.com", "twitter.com", "phone", "anonymous"})
		@Pattern(
				regexp = "^(password|google\\.com|facebook\\.com|apple\\.com|github\\.com|twitter\\.com|phone|anonymous)$",
				message = "proveedor no soportado")
		String proveedor
) {
}
