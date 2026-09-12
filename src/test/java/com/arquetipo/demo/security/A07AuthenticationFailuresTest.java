package com.arquetipo.demo.security;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.arquetipo.demo.registro.service.RegistroService;
import com.arquetipo.demo.registro.web.RegistroController;
import com.arquetipo.demo.registro.web.dto.RegistroResponse;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * OWASP A07:2021 - Identification and Authentication Failures.
 *
 * <p>La autenticacion en si la hace Firebase Auth; lo que este servicio debe garantizar es
 * que no acepta una identidad mal formada: un {@code uid} vacio o desproporcionado, un
 * {@code proveedor} que no sea uno de los soportados, o un {@code username} con formato
 * invalido, se rechazan con 400 antes de llegar a la capa de servicio.
 */
@WebMvcTest(RegistroController.class)
class A07AuthenticationFailuresTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private RegistroService registroService;

	@ParameterizedTest
	@ValueSource(strings = {"", " "})
	void registro_uidVacio_seRechaza(String vacio) throws Exception {
		// Arrange
		String body = """
				{"username":"usuario","email":"usuario@example.com","uid":%s,"proveedor":"password"}
				""".formatted(json(vacio));

		// Act + Assert
		mockMvc.perform(post("/api/v1/registro").contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors[*].field", org.hamcrest.Matchers.hasItem("uid")));
		verify(registroService, never()).registrar(any());
	}

	@Test
	void registro_uidMasLargoQueElMaximo_seRechaza() throws Exception {
		// Arrange
		String uidLargo = "a".repeat(129);
		String body = """
				{"username":"usuario","email":"usuario@example.com","uid":"%s","proveedor":"password"}
				""".formatted(uidLargo);

		// Act + Assert
		mockMvc.perform(post("/api/v1/registro").contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isBadRequest());
		verify(registroService, never()).registrar(any());
	}

	@ParameterizedTest
	@ValueSource(strings = {"", "linkedin.com", "PASSWORD", "password ", "'; DROP TABLE proveedores_auth; --"})
	void registro_proveedorEnviadoPeroNoSoportado_seRechaza(String proveedorInvalido) throws Exception {
		// Arrange
		String body = """
				{"username":"usuario","email":"usuario@example.com","uid":"fb-usuario","proveedor":%s}
				""".formatted(json(proveedorInvalido));

		// Act + Assert
		mockMvc.perform(post("/api/v1/registro").contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isBadRequest());
		verify(registroService, never()).registrar(any());
	}

	@ParameterizedTest
	@ValueSource(strings = {"password", "google.com", "facebook.com", "apple.com",
			"github.com", "twitter.com", "phone", "anonymous"})
	void registro_cualquierProveedorSoportado_pasaLaValidacion(String proveedorValido) throws Exception {
		// Arrange
		when(registroService.registrar(any()))
				.thenReturn(new RegistroResponse(1L, "usuario", "usuario@example.com", proveedorValido, true, Instant.now()));
		String body = """
				{"username":"usuario","email":"usuario@example.com","uid":"fb-usuario","proveedor":%s}
				""".formatted(json(proveedorValido));

		// Act + Assert
		mockMvc.perform(post("/api/v1/registro").contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isCreated());
	}

	@Test
	void registro_sinProveedorEnElCuerpo_pasaLaValidacion() throws Exception {
		// Arrange: el frontend actual no manda "proveedor"; el servicio asumira "password"
		when(registroService.registrar(any()))
				.thenReturn(new RegistroResponse(1L, "usuario", "usuario@example.com", "password", true, Instant.now()));

		// Act + Assert
		mockMvc.perform(post("/api/v1/registro").contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"username":"usuario","email":"usuario@example.com","uid":"fb-usuario"}
								"""))
				.andExpect(status().isCreated());
	}

	@ParameterizedTest
	@ValueSource(strings = {"ab", "  ", "usuario con espacios", "e", "díéresis", "user@name", "../etc"})
	void registro_usernameConFormatoInvalido_seRechaza(String invalido) throws Exception {
		// Arrange
		String body = """
				{"username":%s,"email":"usuario@example.com","uid":"fb-usuario","proveedor":"password"}
				""".formatted(json(invalido));

		// Act + Assert
		mockMvc.perform(post("/api/v1/registro").contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isBadRequest());
		verify(registroService, never()).registrar(any());
	}

	private static String json(String raw) {
		return "\"" + raw.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
	}
}
