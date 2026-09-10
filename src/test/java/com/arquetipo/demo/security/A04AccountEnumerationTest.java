package com.arquetipo.demo.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * OWASP A04:2021 - Insecure Design (resistencia a la enumeracion de cuentas).
 *
 * <p>Un conflicto de username y uno de email deben producir una respuesta indistinguible,
 * sin revelar que campo colisiono ni devolver el valor enviado.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class A04AccountEnumerationTest {

	@Autowired
	private MockMvc mockMvc;

	@BeforeEach
	void altaPrevia() throws Exception {
		mockMvc.perform(post("/api/v1/registro").contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"username":"existente","email":"existente@example.com","password":"passwordValida"}
								"""))
				.andExpect(status().isCreated());
	}

	@Test
	void conflictoDeUsernameYDeEmail_producenLaMismaRespuesta() throws Exception {
		// Arrange
		String colisionUsername = """
				{"username":"existente","email":"otro@example.com","password":"passwordValida"}
				""";
		String colisionEmail = """
				{"username":"otro","email":"existente@example.com","password":"passwordValida"}
				""";

		// Act
		String cuerpoPorUsername = ejecutarConflicto(colisionUsername);
		String cuerpoPorEmail = ejecutarConflicto(colisionEmail);

		// Assert: cuerpos identicos salvo el timestamp
		assertThat(sinTimestamp(cuerpoPorUsername)).isEqualTo(sinTimestamp(cuerpoPorEmail));
	}

	@Test
	void respuestaDeConflicto_noRevelaCampoNiValorEnviado() throws Exception {
		// Arrange
		String colisionUsername = """
				{"username":"existente","email":"secreto-tecleado@example.com","password":"passwordValida"}
				""";

		// Act
		String cuerpo = ejecutarConflicto(colisionUsername);

		// Assert
		assertThat(cuerpo)
				.doesNotContain("existente")
				.doesNotContain("secreto-tecleado")
				.doesNotContain("username")
				.doesNotContain("email");
		assertThat(cuerpo).contains("No se pudo completar el registro con los datos proporcionados");
	}

	private String ejecutarConflicto(String body) throws Exception {
		return mockMvc.perform(post("/api/v1/registro").contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isConflict())
				.andReturn().getResponse().getContentAsString();
	}

	private static String sinTimestamp(String json) {
		return json.replaceAll("\"timestamp\"\\s*:\\s*\"[^\"]*\"", "\"timestamp\":\"<>\"");
	}
}
