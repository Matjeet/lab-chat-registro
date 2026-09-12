package com.arquetipo.demo.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.arquetipo.demo.registro.domain.ProveedorAuth;
import com.arquetipo.demo.registro.repository.ProveedorAuthRepository;
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
 * <p>Un conflicto de username, de email o de uid deben producir una respuesta
 * indistinguible, sin revelar que campo colisiono ni devolver el valor enviado.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class A04AccountEnumerationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ProveedorAuthRepository proveedorAuthRepository;

	@BeforeEach
	void seedProveedorYAltaPrevia() throws Exception {
		if (proveedorAuthRepository.findByNombreIgnoreCase("password").isEmpty()) {
			ProveedorAuth proveedor = new ProveedorAuth();
			proveedor.setNombre("password");
			proveedorAuthRepository.saveAndFlush(proveedor);
		}

		mockMvc.perform(post("/api/v1/registro").contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"username":"existente","email":"existente@example.com",
								 "uid":"fb-existente","proveedor":"password"}
								"""))
				.andExpect(status().isCreated());
	}

	@Test
	void conflictoDeUsernameDeEmailYDeUid_producenLaMismaRespuesta() throws Exception {
		// Arrange
		String colisionUsername = """
				{"username":"existente","email":"otro@example.com",
				 "uid":"fb-otro-1","proveedor":"password"}
				""";
		String colisionEmail = """
				{"username":"otro1","email":"existente@example.com",
				 "uid":"fb-otro-2","proveedor":"password"}
				""";
		String colisionFirebaseUid = """
				{"username":"otro2","email":"otro2@example.com",
				 "uid":"fb-existente","proveedor":"password"}
				""";

		// Act
		String cuerpoPorUsername = ejecutarConflicto(colisionUsername);
		String cuerpoPorEmail = ejecutarConflicto(colisionEmail);
		String cuerpoPorFirebaseUid = ejecutarConflicto(colisionFirebaseUid);

		// Assert: los tres cuerpos son identicos salvo el timestamp
		assertThat(sinTimestamp(cuerpoPorUsername)).isEqualTo(sinTimestamp(cuerpoPorEmail));
		assertThat(sinTimestamp(cuerpoPorEmail)).isEqualTo(sinTimestamp(cuerpoPorFirebaseUid));
	}

	@Test
	void respuestaDeConflicto_noRevelaCampoNiValorEnviado() throws Exception {
		// Arrange
		String colisionUsername = """
				{"username":"existente","email":"secreto-tecleado@example.com",
				 "uid":"fb-secreto","proveedor":"password"}
				""";

		// Act
		String cuerpo = ejecutarConflicto(colisionUsername);

		// Assert
		assertThat(cuerpo)
				.doesNotContain("existente")
				.doesNotContain("secreto-tecleado")
				.doesNotContain("fb-secreto")
				.doesNotContain("username")
				.doesNotContain("email")
				.doesNotContain("\"uid\"");
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
