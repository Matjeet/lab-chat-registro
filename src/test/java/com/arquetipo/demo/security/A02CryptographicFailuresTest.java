package com.arquetipo.demo.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.arquetipo.demo.registro.domain.ProveedorAuth;
import com.arquetipo.demo.registro.domain.Usuario;
import com.arquetipo.demo.registro.identidad.ProveedorIdentidad;
import com.arquetipo.demo.registro.identidad.UsuarioExterno;
import com.arquetipo.demo.registro.repository.ProveedorAuthRepository;
import com.arquetipo.demo.registro.repository.UsuarioRepository;
import java.lang.reflect.Field;
import java.util.Arrays;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * OWASP A02:2021 - Cryptographic Failures.
 *
 * <p>La contrasena la gestiona el proveedor de identidad (Firebase Auth): este servicio solo
 * la reenvia y nunca la persiste, nunca la devuelve y nunca la registra en el log.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
@Transactional
class A02CryptographicFailuresTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UsuarioRepository usuarioRepository;

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
	void registro_laContrasenaSeReenviaAlProveedorPeroNuncaSePersisteNiSeDevuelve() throws Exception {
		// Arrange
		String contrasena = "Passw0rd!23";
		when(proveedorIdentidad.crearUsuario(eq("cripto1@example.com"), eq(contrasena)))
				.thenReturn(new UsuarioExterno("fb-cripto1"));
		String body = """
				{"username":"cripto1","email":"cripto1@example.com","password":"%s"}
				""".formatted(contrasena);

		// Act
		String respuesta = mockMvc.perform(post("/api/v1/registro")
						.contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.password").doesNotExist())
				.andExpect(jsonPath("$.passwordHash").doesNotExist())
				.andReturn().getResponse().getContentAsString();

		// Assert: el proveedor SI recibio la contrasena en claro (es lo que hace falta)...
		org.mockito.Mockito.verify(proveedorIdentidad).crearUsuario("cripto1@example.com", contrasena);
		// ...pero ni la respuesta ni la entidad persistida la conservan en ningun lado
		assertThat(respuesta).doesNotContain(contrasena);
		Usuario guardado = usuarioRepository.findByUsernameIgnoreCase("cripto1").orElseThrow();
		assertThat(guardado.getFirebaseUid()).isEqualTo("fb-cripto1");
	}

	@Test
	void registro_laContrasenaNuncaApareceEnElLogDelServidor(CapturedOutput output) throws Exception {
		// Arrange: un alta real, para que el segundo intento colisione con username de verdad
		// (fuerza la ruta WARN de conflicto, que siempre se registra en el log)
		when(proveedorIdentidad.crearUsuario(eq("cripto2@example.com"), any()))
				.thenReturn(new UsuarioExterno("fb-cripto2"));
		mockMvc.perform(post("/api/v1/registro").contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"username":"yaexiste","email":"cripto2@example.com","password":"Passw0rd!23"}
						"""));

		String contrasenaDistintiva = "N0DebeAparecerEnLogs!1";

		// Act: reintento con el mismo username -> conflicto local, ni siquiera llama al proveedor
		mockMvc.perform(post("/api/v1/registro").contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"username":"yaexiste","email":"otro-cripto2@example.com","password":"%s"}
						""".formatted(contrasenaDistintiva)));

		// Assert
		assertThat(output.getOut()).doesNotContain(contrasenaDistintiva);
		assertThat(output.getErr()).doesNotContain(contrasenaDistintiva);
	}
}
