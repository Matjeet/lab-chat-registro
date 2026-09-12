package com.arquetipo.demo.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.arquetipo.demo.registro.service.RegistroService;
import com.arquetipo.demo.registro.web.dto.RegistroResponse;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * OWASP A05:2021 - Security Misconfiguration.
 *
 * <p>Los errores no filtran detalles internos (mensaje de excepcion, stack trace), los
 * endpoints de Actuator sensibles no estan expuestos y CORS solo acepta los origenes de la
 * lista permitida.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties =
		"app.cors.allowed-origins=https://frontend.permitido.example,http://localhost:3000")
class A05SecurityMisconfigurationTest {

	private static final String DETALLE_INTERNO = "host=db-prod-01.internal token=SECRET-abc123";
	private static final String ORIGEN_PERMITIDO = "https://frontend.permitido.example";
	private static final String ORIGEN_NO_PERMITIDO = "https://sitio-malicioso.example";

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private RegistroService registroService;

	@Test
	void errorNoControlado_noFiltraMensajeInternoNiStackTrace() throws Exception {
		// Arrange
		when(registroService.registrar(any()))
				.thenThrow(new IllegalStateException(DETALLE_INTERNO));

		// Act
		String cuerpo = mockMvc.perform(post("/api/v1/registro")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"username":"quiebra","email":"quiebra@example.com","password":"passwordValida"}
								"""))
				.andExpect(status().isInternalServerError())
				.andExpect(jsonPath("$.trace").doesNotExist())
				.andExpect(jsonPath("$.exception").doesNotExist())
				.andExpect(jsonPath("$.detail").value("Ocurrio un error inesperado. Contacte con soporte."))
				.andReturn().getResponse().getContentAsString();

		// Assert
		assertThat(cuerpo)
				.doesNotContain(DETALLE_INTERNO)
				.doesNotContain("db-prod-01")
				.doesNotContain("SECRET-abc123")
				.doesNotContain("IllegalStateException")
				.doesNotContainIgnoringCase("java.lang");
	}

	@Test
	void errores_seSirvenComoProblemJson() throws Exception {
		// Arrange
		String cuerpoInvalido = """
				{"username":"x","email":"no-email","password":"1"}
				""";

		// Act + Assert
		mockMvc.perform(post("/api/v1/registro").contentType(MediaType.APPLICATION_JSON).content(cuerpoInvalido))
				.andExpect(status().isBadRequest())
				.andExpect(result -> assertThat(result.getResponse().getContentType())
						.startsWith("application/problem+json"));
	}

	@ParameterizedTest
	@ValueSource(strings = {
			"/actuator/env",
			"/actuator/beans",
			"/actuator/configprops",
			"/actuator/mappings",
			"/actuator/threaddump",
			"/actuator/heapdump",
			"/actuator/loggers",
			"/actuator/scheduledtasks"
	})
	void actuator_endpointsSensiblesNoExpuestos(String path) throws Exception {
		// Act + Assert
		mockMvc.perform(get(path)).andExpect(status().isNotFound());
	}

	@Test
	void actuatorHealth_estaDisponiblePeroSinDetallesDeComponentes() throws Exception {
		// Act + Assert
		mockMvc.perform(get("/actuator/health"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("UP"))
				.andExpect(jsonPath("$.components").doesNotExist());
	}

	@Test
	void rutaDesconocida_devuelve404SinFiltrarInterioridades() throws Exception {
		// Act
		String cuerpo = mockMvc.perform(get("/ruta/que/no/existe"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.trace").doesNotExist())
				.andReturn().getResponse().getContentAsString();

		// Assert
		assertThat(cuerpo).doesNotContainIgnoringCase("exception");
	}

	// --- CORS -------------------------------------------------------------------

	@Test
	void cors_preflightDesdeOrigenPermitido_devuelveLasCabecerasCors() throws Exception {
		// Act
		var respuesta = mockMvc.perform(options("/api/v1/registro")
				.header(HttpHeaders.ORIGIN, ORIGEN_PERMITIDO)
				.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"));

		// Assert
		respuesta.andExpect(status().isOk())
				.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ORIGEN_PERMITIDO))
				.andExpect(header().stringValues(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS,
						org.hamcrest.Matchers.hasItem(org.hamcrest.Matchers.containsString("POST"))));
	}

	@Test
	void cors_preflightDesdeOrigenNoPermitido_seRechazaSinCabecerasCors() throws Exception {
		// Act
		var respuesta = mockMvc.perform(options("/api/v1/registro")
				.header(HttpHeaders.ORIGIN, ORIGEN_NO_PERMITIDO)
				.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"));

		// Assert
		respuesta.andExpect(status().isForbidden())
				.andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
	}

	@Test
	void cors_peticionRealDesdeOrigenPermitido_llevaCabeceraAllowOrigin() throws Exception {
		// Arrange
		when(registroService.registrar(any()))
				.thenReturn(new RegistroResponse(1L, "corsok", "corsok@example.com", true, Instant.now()));

		// Act + Assert
		mockMvc.perform(post("/api/v1/registro")
						.header(HttpHeaders.ORIGIN, ORIGEN_PERMITIDO)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"username":"corsok","email":"corsok@example.com","password":"passwordValida"}
								"""))
				.andExpect(status().isCreated())
				.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ORIGEN_PERMITIDO));
	}

	@Test
	void cors_peticionRealDesdeOrigenNoPermitido_seRechazaSinCabeceraAllowOrigin() throws Exception {
		// Act
		var respuesta = mockMvc.perform(post("/api/v1/registro")
				.header(HttpHeaders.ORIGIN, ORIGEN_NO_PERMITIDO)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"username":"corsbad","email":"corsbad@example.com","password":"passwordValida"}
						"""));

		// Assert
		respuesta.andExpect(status().isForbidden())
				.andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
	}

	@Test
	void cors_porDefecto_noSePermitenCredenciales() throws Exception {
		// Act
		var respuesta = mockMvc.perform(options("/api/v1/registro")
				.header(HttpHeaders.ORIGIN, ORIGEN_PERMITIDO)
				.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"));

		// Assert
		respuesta.andExpect(status().isOk())
				.andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS));
	}
}
