package com.arquetipo.demo.registro.web;

import com.arquetipo.demo.registro.web.dto.RegistroRequest;
import com.arquetipo.demo.registro.web.dto.RegistroResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;

/**
 * Contrato OpenAPI del recurso de registro. Aisla las anotaciones de documentacion
 * (springdoc / swagger) para que {@link RegistroController} solo contenga el enrutado y
 * la delegacion al servicio.
 *
 * <p>Lo implementa el controlador: springdoc lee las anotaciones heredadas de esta interfaz.
 */
@Tag(name = "Registro", description = "Alta de usuarios del servicio")
public interface RegistroApi {

	@Operation(
			summary = "Registrar un usuario",
			description = """
					Completa el alta de un usuario que ya se autentico con Firebase Auth. El
					`username` y el `email` deben ser unicos (no se distinguen mayusculas de
					minusculas) y el `email` se normaliza a minusculas antes de guardarlo. El
					`uid` identifica la cuenta de Firebase; el `proveedor` es opcional (por
					defecto `password`). El servicio no gestiona contrasenas.
					""")
	@ApiResponses({
			@ApiResponse(
					responseCode = "201",
					description = "Usuario registrado",
					content = @Content(
							mediaType = MediaType.APPLICATION_JSON_VALUE,
							schema = @Schema(implementation = RegistroResponse.class),
							examples = @ExampleObject(value = """
									{
									  "id": 1,
									  "username": "mateo",
									  "email": "mateo@example.com",
									  "proveedor": "password",
									  "activo": true,
									  "createdAt": "2026-09-08T20:53:47.441193Z"
									}
									"""))),
			@ApiResponse(
					responseCode = "400",
					description = "El cuerpo no supero la validacion (formato de los campos)",
					content = @Content(
							mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
							schema = @Schema(implementation = ProblemDetail.class),
							examples = @ExampleObject(value = """
									{
									  "type": "urn:problem-type:validation-error",
									  "title": "Datos invalidos",
									  "status": 400,
									  "detail": "El cuerpo de la peticion no supero la validacion",
									  "instance": "/api/v1/registro",
									  "errors": [
									    { "field": "email", "message": "debe ser una direccion de correo electronico con formato correcto" }
									  ]
									}
									"""))),
			@ApiResponse(
					responseCode = "409",
					description = """
							Los datos entran en conflicto con una cuenta existente. Por seguridad la
							respuesta es siempre la misma, sin indicar que campo colisiono.
							""",
					content = @Content(
							mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
							schema = @Schema(implementation = ProblemDetail.class),
							examples = @ExampleObject(value = """
									{
									  "type": "urn:problem-type:duplicate-resource",
									  "title": "Recurso duplicado",
									  "status": 409,
									  "detail": "No se pudo completar el registro con los datos proporcionados",
									  "instance": "/api/v1/registro"
									}
									""")))
	})
	RegistroResponse registrar(RegistroRequest request);
}
