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
 * verdad de las reglas de {@code username}/{@code email}/{@code password}/{@code avatar} — no
 * hay una capa OpenAPI/Swagger aparte que las repita.
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
		String password,

		/**
		 * Opcional (null si el usuario no eligio avatar; ver
		 * {@code RegistroGrpcMapper#normalizarAvatar}, que ya deja aqui null en vez de cadena
		 * vacia). Solo dos formatos, nada mas -- ni otra etiqueta HTML/JSX, ni un esquema de URL
		 * distinto de http(s): un enlace comun, o una etiqueta {@code <Blobatar .../>} (avatar
		 * animado de la libreria del mismo nombre) que el frontend renderiza tal cual. Restringir
		 * la forma exacta de la etiqueta (nombre fijo, sin '<'/'>' en los atributos) evita que este
		 * campo se use para colar otro tag (p. ej. {@code <script>}) si el frontend llega a
		 * insertarlo sin escapar.
		 *
		 * <p><b>Sin saltos de linea</b>, en ninguno de los dos formatos (a proposito: este valor
		 * se registra en logs de trazabilidad tal cual antes de que exista otra oportunidad de
		 * saneamiento -- ver {@code RegistroGrpcController#registrar}; permitir '\n'/'\r' aqui
		 * habilitaria forjar lineas de log falsas, CWE-117). Si el emisor genera la etiqueta
		 * formateada en varias lineas (indentada, por legibilidad), debe colapsarla a una sola
		 * antes de enviarla.
		 */
		@Size(max = 500)
		@Pattern(
				regexp = "^(https?://[^\\s\"'<>]+|<Blobatar(\\s[^<>\\r\\n]*)?/>)$",
				message = "debe ser un enlace http(s) o una etiqueta <Blobatar ... /> en una sola linea, "
						+ "sin saltos de linea")
		String avatar
) {

	/**
	 * Nunca incluir la contrasena en logs, ni siquiera por accidente via un log de este record.
	 * El avatar si se incluye: no es un dato sensible (username/email ya se registran en logs
	 * por precedente del proyecto, y el avatar esta pensado para mostrarse publicamente).
	 */
	@Override
	public String toString() {
		return "RegistroRequest[username=%s, email=%s, avatar=%s, password=***]"
				.formatted(username, email, avatar);
	}
}
