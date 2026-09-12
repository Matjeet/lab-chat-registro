package com.arquetipo.demo.registro.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.arquetipo.demo.registro.service.RegistroService;
import com.arquetipo.demo.registro.web.dto.RegistroResponse;
import com.arquetipo.demo.common.exception.DuplicateResourceException;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(RegistroController.class)
class RegistroControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private RegistroService registroService;

	@Test
	void registrar_datosValidos_devuelve201SinDatosDeAutenticacion() throws Exception {
		when(registroService.registrar(any()))
				.thenReturn(new RegistroResponse(1L, "mateo", "mateo@example.com", "password", true, Instant.now()));

		mockMvc.perform(post("/api/v1/registro")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"username":"mateo","email":"mateo@example.com","uid":"firebase-uid-1","proveedor":"password"}
								"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").value(1))
				.andExpect(jsonPath("$.username").value("mateo"))
				.andExpect(jsonPath("$.proveedor").value("password"))
				.andExpect(jsonPath("$.uid").doesNotExist())
				.andExpect(jsonPath("$.password").doesNotExist())
				.andExpect(jsonPath("$.passwordHash").doesNotExist());
	}

	@Test
	void registrar_cuerpoInvalido_devuelve400ConErrores() throws Exception {
		mockMvc.perform(post("/api/v1/registro")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"username":"m","email":"no-es-email","uid":"","proveedor":"no-existe"}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors").isArray());
	}

	@Test
	void registrar_usuarioDuplicado_devuelve409ConMensajeGenerico() throws Exception {
		when(registroService.registrar(any()))
				.thenThrow(new DuplicateResourceException(
						"No se pudo completar el registro con los datos proporcionados"));

		mockMvc.perform(post("/api/v1/registro")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"username":"mateo","email":"mateo@example.com","uid":"firebase-uid-1","proveedor":"password"}
								"""))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.title").value("Recurso duplicado"))
				.andExpect(jsonPath("$.detail").value("No se pudo completar el registro con los datos proporcionados"))
				.andExpect(jsonPath("$.detail", org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("mateo"))));
	}
}
