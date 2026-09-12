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
 * <p>La contrasena la sigue validando el proveedor de identidad al crear la cuenta, pero
 * este servicio impone su propia politica en el borde (para no reenviar al proveedor, ni una
 * sola vez, una contrasena que ya sabemos debil): 8-20 caracteres, mayuscula, minuscula,
 * numero, caracter especial y sin 4+ repeticiones seguidas del mismo caracter. Tambien se
 * valida el formato de `username`.
 */
@WebMvcTest(RegistroController.class)
class A07AuthenticationFailuresTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private RegistroService registroService;

	@ParameterizedTest
	@ValueSource(strings = {
			"Aa1!",                  // 4 caracteres: mas corta que el minimo (8)
			"Passw0rd!23456789ABCD", // 21 caracteres: mas larga que el maximo (20)
			"passw0rd!23",           // sin mayuscula
			"PASSW0RD!23",           // sin minuscula
			"Password!AB",           // sin numero
			"Passw0rd123",           // sin caracter especial
			"Paaaa0rd!23",           // "aaaa": 4 repeticiones seguidas del mismo caracter
	})
	void registro_contrasenaQueViolaLaPolitica_seRechaza(String contrasenaInvalida) throws Exception {
		// Arrange
		String body = """
				{"username":"usuario","email":"usuario@example.com","password":"%s"}
				""".formatted(contrasenaInvalida);

		// Act + Assert
		mockMvc.perform(post("/api/v1/registro").contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors[*].field", org.hamcrest.Matchers.hasItem("password")));
		verify(registroService, never()).registrar(any());
	}

	@ParameterizedTest
	@ValueSource(strings = {
			"Passw0r!",     // 8 caracteres: minimo exacto
			"Paaa0rd!23",   // "aaa": 3 repeticiones seguidas SI estan permitidas
			"Passw0rd!23Passw0rd!", // 20 caracteres: maximo exacto
	})
	void registro_contrasenaQueCumpleLaPolitica_pasaLaValidacion(String contrasenaValida) throws Exception {
		// Arrange
		when(registroService.registrar(any()))
				.thenReturn(new RegistroResponse(1L, "usuario", "usuario@example.com", "password", true, Instant.now()));
		String body = """
				{"username":"usuario","email":"usuario@example.com","password":"%s"}
				""".formatted(contrasenaValida);

		// Act + Assert
		mockMvc.perform(post("/api/v1/registro").contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isCreated());
	}

	@ParameterizedTest
	@ValueSource(strings = {"ab", "  ", "usuario con espacios", "e", "díéresis", "user@name", "../etc"})
	void registro_usernameConFormatoInvalido_seRechaza(String invalido) throws Exception {
		// Arrange
		String body = """
				{"username":%s,"email":"usuario@example.com","password":"Passw0rd!23"}
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
