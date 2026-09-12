package com.arquetipo.demo.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.arquetipo.demo.registro.domain.ProveedorAuth;
import com.arquetipo.demo.registro.identidad.ProveedorIdentidad;
import com.arquetipo.demo.registro.identidad.UsuarioExterno;
import com.arquetipo.demo.registro.identidad.UsuarioYaRegistradoException;
import com.arquetipo.demo.registro.repository.ProveedorAuthRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * OWASP A04:2021 - Insecure Design (resistencia a la enumeracion de cuentas).
 *
 * <p>Un conflicto de username, de email, o un usuario ya existente tanto en el proveedor de
 * identidad como en la base local, deben producir una respuesta indistinguible, sin revelar
 * que colisiono ni el valor enviado.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class A04AccountEnumerationTest {

	private static final String PASSWORD_VALIDA = "Passw0rd!23";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ProveedorAuthRepository proveedorAuthRepository;

	@MockitoBean
	private ProveedorIdentidad proveedorIdentidad;

	@BeforeEach
	void seedProveedorYAltaPrevia() throws Exception {
		if (proveedorAuthRepository.findByNombreIgnoreCase("password").isEmpty()) {
			ProveedorAuth proveedor = new ProveedorAuth();
			proveedor.setNombre("password");
			proveedorAuthRepository.saveAndFlush(proveedor);
		}
		when(proveedorIdentidad.nombreProveedor()).thenReturn("password");
		when(proveedorIdentidad.crearUsuario(anyString(), anyString()))
				.thenAnswer(inv -> new UsuarioExterno("fb-" + inv.getArgument(0)));

		mockMvc.perform(post("/api/v1/registro").contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"username":"existente","email":"existente@example.com","password":"%s"}
								""".formatted(PASSWORD_VALIDA)))
				.andExpect(status().isCreated());
	}

	@Test
	void conflictoDeUsernameDeEmailYDeProveedorExterno_producenLaMismaRespuesta() throws Exception {
		// Arrange: colisiones por username y por email (locales)
		String colisionUsername = """
				{"username":"existente","email":"otro@example.com","password":"%s"}
				""".formatted(PASSWORD_VALIDA);
		String colisionEmail = """
				{"username":"otro1","email":"existente@example.com","password":"%s"}
				""".formatted(PASSWORD_VALIDA);

		// El proveedor externo tambien dice "ya existe" para un tercer email, y esa cuenta
		// coincide con la que ya tenemos en la base local (reconciliacion -> conflicto real)
		when(proveedorIdentidad.crearUsuario(eq("otroproveedor@example.com"), anyString()))
				.thenThrow(new UsuarioYaRegistradoException("ya existe en el proveedor", null));
		when(proveedorIdentidad.buscarPorEmail("otroproveedor@example.com"))
				.thenReturn(Optional.of(new UsuarioExterno("fb-existente@example.com")));
		String colisionEnProveedor = """
				{"username":"otro2","email":"otroproveedor@example.com","password":"%s"}
				""".formatted(PASSWORD_VALIDA);

		// Act
		String cuerpoPorUsername = ejecutarConflicto(colisionUsername);
		String cuerpoPorEmail = ejecutarConflicto(colisionEmail);
		String cuerpoPorProveedor = ejecutarConflicto(colisionEnProveedor);

		// Assert: los tres cuerpos son identicos salvo el timestamp
		assertThat(sinTimestamp(cuerpoPorUsername)).isEqualTo(sinTimestamp(cuerpoPorEmail));
		assertThat(sinTimestamp(cuerpoPorEmail)).isEqualTo(sinTimestamp(cuerpoPorProveedor));
	}

	@Test
	void respuestaDeConflicto_noRevelaCampoNiValorEnviado() throws Exception {
		// Arrange
		String colisionUsername = """
				{"username":"existente","email":"secreto-tecleado@example.com","password":"%s"}
				""".formatted(PASSWORD_VALIDA);

		// Act
		String cuerpo = ejecutarConflicto(colisionUsername);

		// Assert
		assertThat(cuerpo)
				.doesNotContain("existente")
				.doesNotContain("secreto-tecleado")
				.doesNotContain("username")
				.doesNotContain("email")
				.doesNotContain("\"password\"");
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
