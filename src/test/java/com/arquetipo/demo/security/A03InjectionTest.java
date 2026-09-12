package com.arquetipo.demo.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.arquetipo.demo.registro.domain.ProveedorAuth;
import com.arquetipo.demo.registro.identidad.ProveedorIdentidad;
import com.arquetipo.demo.registro.identidad.UsuarioExterno;
import com.arquetipo.demo.registro.repository.ProveedorAuthRepository;
import com.arquetipo.demo.registro.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * OWASP A03:2021 - Injection (SQLi / payloads de scripting).
 *
 * <p>Las cargas maliciosas se rechazan en validacion (nunca provocan un 500 ni se ejecutan)
 * y las consultas del repositorio estan parametrizadas (JPA), asi que un valor con sintaxis
 * SQL se trata como dato literal.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class A03InjectionTest {

	private static final String PASSWORD_VALIDA = "Passw0rd!23";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UsuarioRepository repository;

	@Autowired
	private ProveedorAuthRepository proveedorAuthRepository;

	@MockitoBean
	private ProveedorIdentidad proveedorIdentidad;

	@BeforeEach
	void seedProveedorYStubs() {
		if (proveedorAuthRepository.findByNombreIgnoreCase("password").isEmpty()) {
			ProveedorAuth proveedor = new ProveedorAuth();
			proveedor.setNombre("password");
			proveedorAuthRepository.saveAndFlush(proveedor);
		}
		when(proveedorIdentidad.nombreProveedor()).thenReturn("password");
		// Un UID unico y deterministico por email, para no chocar entre altas del mismo test.
		when(proveedorIdentidad.crearUsuario(anyString(), anyString()))
				.thenAnswer(inv -> new UsuarioExterno("fb-" + inv.getArgument(0)));
	}

	@ParameterizedTest
	@ValueSource(strings = {
			"admin' OR '1'='1",
			"'; DROP TABLE usuarios; --",
			"mateo\" OR \"\"=\"",
			"<script>alert(1)</script>",
			"robert'); DROP TABLE usuarios;--",
			"' UNION SELECT firebase_uid FROM usuarios --"
	})
	void registro_payloadDeInyeccionEnUsername_seRechazaSinError(String payload) throws Exception {
		// Arrange
		String body = """
				{"username":%s,"email":"inj@example.com","password":"%s"}
				""".formatted(toJson(payload), PASSWORD_VALIDA);

		// Act
		int statusCode = mockMvc.perform(post("/api/v1/registro")
						.contentType(MediaType.APPLICATION_JSON).content(body))
				.andReturn().getResponse().getStatus();

		// Assert: se rechaza como cliente (400), nunca 5xx
		assertThat(statusCode).isEqualTo(400);
	}

	@ParameterizedTest
	@ValueSource(strings = {
			"a' OR 1=1 --@example.com",
			"\"<script>\"@example.com",
			"'; DROP TABLE usuarios; --@x.com"
	})
	void registro_payloadDeInyeccionEnEmail_nuncaProvocaErrorDeServidor(String payload) throws Exception {
		// Arrange
		String body = """
				{"username":"injmail","email":%s,"password":"%s"}
				""".formatted(toJson(payload), PASSWORD_VALIDA);

		// Act
		int statusCode = mockMvc.perform(post("/api/v1/registro")
						.contentType(MediaType.APPLICATION_JSON).content(body))
				.andReturn().getResponse().getStatus();

		// Assert: la validacion lo rechaza o se guarda como literal, pero JAMAS es un 5xx
		assertThat(statusCode).isLessThan(500);
	}

	@Test
	void repositorio_consultaParametrizada_tratraElPayloadComoLiteral() {
		// Arrange
		String sqli = "' OR '1'='1' --";

		// Act + Assert: no lanza excepcion y devuelve false (no hay match literal)
		assertThatCode(() -> {
			assertThat(repository.existsByUsernameIgnoreCase(sqli)).isFalse();
			assertThat(repository.existsByEmailIgnoreCase(sqli)).isFalse();
			assertThat(repository.existsByFirebaseUid(sqli)).isFalse();
			assertThat(repository.findByUsernameIgnoreCase(sqli)).isEmpty();
		}).doesNotThrowAnyException();
	}

	@Test
	void trasIntentosDeInyeccion_laTablaSigueOperativa() throws Exception {
		// Arrange: lanzar un payload destructivo
		mockMvc.perform(post("/api/v1/registro").contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"username":"'; DROP TABLE usuarios; --","email":"x@x.com","password":"%s"}
						""".formatted(PASSWORD_VALIDA)));

		// Act: un alta legitima posterior
		mockMvc.perform(post("/api/v1/registro").contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"username":"legitimo","email":"legitimo@example.com","password":"%s"}
								""".formatted(PASSWORD_VALIDA)))
				.andExpect(status().isCreated());

		// Assert: la tabla existe y solo tiene el registro valido
		assertThat(repository.findByUsernameIgnoreCase("legitimo")).isPresent();
		assertThat(repository.count()).isEqualTo(1);
	}

	private static String toJson(String raw) {
		return "\"" + raw.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
	}
}
