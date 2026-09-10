package com.arquetipo.demo.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.arquetipo.demo.registro.domain.Usuario;
import com.arquetipo.demo.registro.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * OWASP A02:2021 - Cryptographic Failures.
 *
 * <p>Verifica que la contrasena se almacena cifrada (hash BCrypt) y que ni la contrasena ni
 * su hash salen nunca del servidor.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class A02CryptographicFailuresTest {

	private static final String PLAINTEXT = "S3cretoDelUsuario!";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UsuarioRepository repository;

	@Test
	void registro_almacenaLaContrasenaComoHashBcrypt_noEnClaro() throws Exception {
		// Arrange
		String body = """
				{"username":"cripto1","email":"cripto1@example.com","password":"%s"}
				""".formatted(PLAINTEXT);

		// Act
		mockMvc.perform(post("/api/v1/registro").contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isCreated());

		// Assert
		Usuario guardado = repository.findByUsernameIgnoreCase("cripto1").orElseThrow();
		assertThat(guardado.getPasswordHash())
				.isNotEqualTo(PLAINTEXT)
				.startsWith("$2");                       // prefijo de un hash BCrypt
		assertThat(guardado.getPasswordHash().length()).isBetween(59, 60);
		assertThat(new BCryptPasswordEncoder().matches(PLAINTEXT, guardado.getPasswordHash())).isTrue();
	}

	@Test
	void registro_laRespuestaNoExponeContrasenaNiHash() throws Exception {
		// Arrange
		String body = """
				{"username":"cripto2","email":"cripto2@example.com","password":"%s"}
				""".formatted(PLAINTEXT);

		// Act
		String respuesta = mockMvc.perform(post("/api/v1/registro")
						.contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.password").doesNotExist())
				.andExpect(jsonPath("$.passwordHash").doesNotExist())
				.andReturn().getResponse().getContentAsString();

		// Assert
		assertThat(respuesta)
				.doesNotContain(PLAINTEXT)
				.doesNotContain("$2a$")
				.doesNotContain("$2b$");
	}
}
