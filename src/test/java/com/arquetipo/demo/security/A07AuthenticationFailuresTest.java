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
 * <p>Politica de contrasenas (longitud minima/maxima) y de formato de identificador
 * aplicada en el borde: una peticion que no la cumple se rechaza con 400 y ni siquiera
 * llega a la capa de servicio.
 */
@WebMvcTest(RegistroController.class)
class A07AuthenticationFailuresTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private RegistroService registroService;

	@ParameterizedTest
	@ValueSource(strings = {"", " ", "1234567", "corta12"})
	void registro_contrasenaMasCortaQueElMinimo_seRechaza(String debil) throws Exception {
		// Arrange
		String body = """
				{"username":"usuario","email":"usuario@example.com","password":%s}
				""".formatted(json(debil));

		// Act + Assert
		mockMvc.perform(post("/api/v1/registro").contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors[*].field", org.hamcrest.Matchers.hasItem("password")));
		verify(registroService, never()).registrar(any());
	}

	@Test
	void registro_contrasenaMasLargaQueElMaximo_seRechaza() throws Exception {
		// Arrange
		String passwordLarga = "a".repeat(101);
		String body = """
				{"username":"usuario","email":"usuario@example.com","password":"%s"}
				""".formatted(passwordLarga);

		// Act + Assert
		mockMvc.perform(post("/api/v1/registro").contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isBadRequest());
		verify(registroService, never()).registrar(any());
	}

	@Test
	void registro_contrasenaEnElMinimoExacto_pasaLaValidacion() throws Exception {
		// Arrange
		when(registroService.registrar(any()))
				.thenReturn(new RegistroResponse(1L, "usuario", "usuario@example.com", true, Instant.now()));

		// Act + Assert: 8 caracteres es el minimo permitido
		mockMvc.perform(post("/api/v1/registro").contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"username":"usuario","email":"usuario@example.com","password":"12345678"}
								"""))
				.andExpect(status().isCreated());
	}

	@ParameterizedTest
	@ValueSource(strings = {"ab", "  ", "usuario con espacios", "e", "díéresis", "user@name", "../etc"})
	void registro_usernameConFormatoInvalido_seRechaza(String invalido) throws Exception {
		// Arrange
		String body = """
				{"username":%s,"email":"usuario@example.com","password":"passwordValida"}
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
