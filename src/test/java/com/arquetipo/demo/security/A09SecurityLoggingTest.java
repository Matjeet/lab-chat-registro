package com.arquetipo.demo.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.arquetipo.demo.common.exception.DuplicateResourceException;
import com.arquetipo.demo.registro.domain.ProveedorAuth;
import com.arquetipo.demo.registro.identidad.ProveedorIdentidad;
import com.arquetipo.demo.registro.identidad.ProveedorIdentidadException;
import com.arquetipo.demo.registro.identidad.UsuarioExterno;
import com.arquetipo.demo.registro.identidad.UsuarioYaRegistradoException;
import com.arquetipo.demo.registro.mapper.UsuarioMapper;
import com.arquetipo.demo.registro.repository.ProveedorAuthRepository;
import com.arquetipo.demo.registro.repository.UsuarioRepository;
import com.arquetipo.demo.registro.service.RegistroService;
import com.arquetipo.demo.registro.web.dto.RegistroRequest;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

/**
 * OWASP A09:2021 - Security Logging and Monitoring Failures.
 *
 * <p>Un intento de registro rechazado (localmente o por el proveedor de identidad) deja
 * rastro en el log del servidor, con el detalle real, mientras que el mensaje que recibe el
 * cliente sigue siendo generico. La contrasena en claro nunca aparece en ningun log, ni
 * siquiera cuando el proveedor de identidad falla.
 */
@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class A09SecurityLoggingTest {

	private static final String PASSWORD_VALIDA = "Passw0rd!23";

	@Mock
	private UsuarioRepository repository;

	@Mock
	private ProveedorAuthRepository proveedorRepository;

	@Mock
	private ProveedorIdentidad proveedorIdentidad;

	private RegistroService service;

	@BeforeEach
	void setUp() {
		service = new RegistroService(repository, proveedorRepository, new UsuarioMapper(), proveedorIdentidad);
	}

	private static RegistroRequest request() {
		return new RegistroRequest("mateo", "mateo@example.com", PASSWORD_VALIDA);
	}

	private static ProveedorAuth proveedorPassword() {
		ProveedorAuth proveedor = new ProveedorAuth();
		proveedor.setId(1L);
		proveedor.setNombre("password");
		return proveedor;
	}

	@Test
	void registroRechazadoPorUsernameDuplicado_seRegistraEnLogPeroNoEnLaRespuesta(CapturedOutput output) {
		// Arrange
		when(repository.existsByUsernameIgnoreCase("mateo")).thenReturn(true);

		// Act
		Throwable lanzada = org.assertj.core.api.Assertions.catchThrowable(() -> service.registrar(request()));

		// Assert: el log tiene el detalle...
		assertThat(output.getOut())
				.contains("WARN")
				.contains("Registro rechazado")
				.contains("username")
				.contains("mateo");
		// ...pero lo que veria el cliente es generico...
		assertThat(lanzada)
				.isInstanceOf(DuplicateResourceException.class)
				.hasMessage("No se pudo completar el registro con los datos proporcionados");
		assertThat(lanzada.getMessage()).doesNotContain("mateo");
		// ...y la contrasena jamas aparece en el log.
		assertThat(output.getOut()).doesNotContain(PASSWORD_VALIDA);
	}

	@Test
	void proveedorDiceQueYaExiste_seRegistraEnLogConElMotivoReal(CapturedOutput output) {
		// Arrange
		when(repository.existsByUsernameIgnoreCase("mateo")).thenReturn(false);
		when(repository.existsByEmailIgnoreCase("mateo@example.com")).thenReturn(false);
		when(proveedorIdentidad.nombreProveedor()).thenReturn("password");
		when(proveedorRepository.findByNombreIgnoreCase("password")).thenReturn(Optional.of(proveedorPassword()));
		when(proveedorIdentidad.crearUsuario(eq("mateo@example.com"), any()))
				.thenThrow(new UsuarioYaRegistradoException(
						"Firebase: el email ya esta registrado (EMAIL_ALREADY_EXISTS)", null));
		when(proveedorIdentidad.buscarPorEmail("mateo@example.com"))
				.thenReturn(Optional.of(new UsuarioExterno("uid-existente")));
		when(repository.existsByFirebaseUid("uid-existente")).thenReturn(true);

		// Act
		assertThatThrownBy(() -> service.registrar(request()))
				.isInstanceOf(DuplicateResourceException.class)
				.hasMessage("No se pudo completar el registro con los datos proporcionados");

		// Assert: el detalle real del proveedor queda en el log...
		assertThat(output.getOut())
				.contains("WARN")
				.contains("EMAIL_ALREADY_EXISTS");
		// ...pero nunca la contrasena
		assertThat(output.getOut()).doesNotContain(PASSWORD_VALIDA);
	}

	@Test
	void fallaGenericaDelProveedor_seRegistraEnLogComoError(CapturedOutput output) {
		// Arrange
		when(repository.existsByUsernameIgnoreCase("mateo")).thenReturn(false);
		when(repository.existsByEmailIgnoreCase("mateo@example.com")).thenReturn(false);
		when(proveedorIdentidad.nombreProveedor()).thenReturn("password");
		when(proveedorRepository.findByNombreIgnoreCase("password")).thenReturn(Optional.of(proveedorPassword()));
		when(proveedorIdentidad.crearUsuario(eq("mateo@example.com"), any()))
				.thenThrow(new ProveedorIdentidadException("timeout hablando con el proveedor"));

		// Act
		assertThatThrownBy(() -> service.registrar(request())).isInstanceOf(ProveedorIdentidadException.class);

		// Assert
		assertThat(output.getOut())
				.contains("ERROR")
				.contains("timeout hablando con el proveedor");
		assertThat(output.getOut()).doesNotContain(PASSWORD_VALIDA);
	}

	@Test
	void carreraDetectadaEnElInsert_seRegistraEnLogConLaCausa(CapturedOutput output) {
		// Arrange
		when(repository.existsByUsernameIgnoreCase("mateo")).thenReturn(false);
		when(repository.existsByEmailIgnoreCase("mateo@example.com")).thenReturn(false);
		when(proveedorIdentidad.crearUsuario(eq("mateo@example.com"), any()))
				.thenReturn(new UsuarioExterno("uid-nuevo"));
		when(proveedorRepository.findByNombreIgnoreCase("password")).thenReturn(Optional.of(proveedorPassword()));
		when(proveedorIdentidad.nombreProveedor()).thenReturn("password");
		when(repository.saveAndFlush(any()))
				.thenThrow(new org.springframework.dao.DataIntegrityViolationException(
						"Duplicate entry for key 'uk_usuarios_email'"));

		// Act
		assertThatThrownBy(() -> service.registrar(request()))
				.isInstanceOf(DuplicateResourceException.class)
				.hasMessage("No se pudo completar el registro con los datos proporcionados");

		// Assert
		assertThat(output.getOut())
				.contains("WARN")
				.contains("restriccion de unicidad");
		assertThat(output.getOut()).doesNotContain(PASSWORD_VALIDA);
	}
}
