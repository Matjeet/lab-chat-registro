package com.arquetipo.demo.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.arquetipo.demo.registro.domain.ProveedorAuth;
import com.arquetipo.demo.registro.domain.Usuario;
import com.arquetipo.demo.registro.repository.ProveedorAuthRepository;
import com.arquetipo.demo.registro.repository.UsuarioRepository;
import java.lang.reflect.Field;
import java.util.Arrays;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * OWASP A02:2021 - Cryptographic Failures.
 *
 * <p>Este servicio ya NO gestiona contrasenas: la autenticacion la hace Firebase Auth y aqui
 * solo se guarda el {@code firebaseUid} (un identificador, no un secreto) y el proveedor
 * usado. No hay material criptografico propio que verificar; estos tests son una guarda de
 * regresion para que nadie reintroduzca almacenamiento de contrasenas en este servicio.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class A02CryptographicFailuresTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UsuarioRepository usuarioRepository;

	@Autowired
	private ProveedorAuthRepository proveedorAuthRepository;

	@BeforeEach
	void seedProveedor() {
		if (proveedorAuthRepository.findByNombreIgnoreCase("password").isEmpty()) {
			ProveedorAuth proveedor = new ProveedorAuth();
			proveedor.setNombre("password");
			proveedorAuthRepository.saveAndFlush(proveedor);
		}
	}

	@Test
	void entidadUsuario_noTieneNingunCampoDeCredenciales() {
		// Arrange + Act: inspecciona los campos declarados de la entidad
		boolean tieneCampoDeCredenciales = Arrays.stream(Usuario.class.getDeclaredFields())
				.map(Field::getName)
				.map(String::toLowerCase)
				.anyMatch(nombre -> nombre.contains("password")
						|| nombre.contains("credential")
						|| nombre.contains("secret"));

		// Assert: guarda de regresion — la autenticacion es responsabilidad de Firebase
		assertThat(tieneCampoDeCredenciales).isFalse();
	}

	@Test
	void registro_siEnvianUnCampoPassword_seIgnoraYNoSePersisteNiSeDevuelve() throws Exception {
		// Arrange: el cliente manda un campo "password" que el contrato ya no define
		String secreto = "esto-no-deberia-guardarse-en-ningun-lado";
		String body = """
				{"username":"cripto1","email":"cripto1@example.com","uid":"fb-cripto1",
				 "proveedor":"password","password":"%s"}
				""".formatted(secreto);

		// Act
		String respuesta = mockMvc.perform(post("/api/v1/registro")
						.contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.password").doesNotExist())
				.andExpect(jsonPath("$.passwordHash").doesNotExist())
				.andReturn().getResponse().getContentAsString();

		// Assert: ni la respuesta ni la entidad persistida conservan ese valor
		assertThat(respuesta).doesNotContain(secreto);
		Usuario guardado = usuarioRepository.findByUsernameIgnoreCase("cripto1").orElseThrow();
		assertThat(guardado.getFirebaseUid()).isEqualTo("fb-cripto1");
	}
}
