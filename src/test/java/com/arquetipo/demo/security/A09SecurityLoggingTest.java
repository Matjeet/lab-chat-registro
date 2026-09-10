package com.arquetipo.demo.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.arquetipo.demo.common.exception.DuplicateResourceException;
import com.arquetipo.demo.registro.domain.Usuario;
import com.arquetipo.demo.registro.mapper.UsuarioMapper;
import com.arquetipo.demo.registro.repository.UsuarioRepository;
import com.arquetipo.demo.registro.service.RegistroService;
import com.arquetipo.demo.registro.web.dto.RegistroRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * OWASP A09:2021 - Security Logging and Monitoring Failures.
 *
 * <p>Un intento de registro rechazado deja rastro en el log del servidor (nivel WARN, con
 * el detalle de que campo colisiono), mientras que el mensaje que recibe el cliente sigue
 * siendo generico.
 */
@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class A09SecurityLoggingTest {

	@Mock
	private UsuarioRepository repository;

	private RegistroService service;

	@BeforeEach
	void setUp() {
		service = new RegistroService(repository, new UsuarioMapper(), new BCryptPasswordEncoder());
	}

	private static RegistroRequest request() {
		return new RegistroRequest("mateo", "mateo@example.com", "passwordValida");
	}

	@Test
	void registroRechazadoPorUsernameDuplicado_seRegistraEnLogPeroNoEnLaRespuesta(CapturedOutput output) {
		// Arrange
		when(repository.existsByUsernameIgnoreCase("mateo")).thenReturn(true);

		// Act
		Throwable lanzada = org.assertj.core.api.Assertions.catchThrowable(() -> service.registrar(request()));

		// Assert: el log tiene el detalle
		assertThat(output.getOut())
				.contains("WARN")
				.contains("Registro rechazado")
				.contains("username")
				.contains("mateo");
		// ...pero lo que veria el cliente es generico
		assertThat(lanzada)
				.isInstanceOf(DuplicateResourceException.class)
				.hasMessage("No se pudo completar el registro con los datos proporcionados");
		assertThat(lanzada.getMessage()).doesNotContain("mateo");
	}

	@Test
	void carreraDetectadaEnElInsert_seRegistraEnLogConLaCausa(CapturedOutput output) {
		// Arrange
		when(repository.existsByUsernameIgnoreCase("mateo")).thenReturn(false);
		when(repository.existsByEmailIgnoreCase("mateo@example.com")).thenReturn(false);
		when(repository.saveAndFlush(any(Usuario.class)))
				.thenThrow(new DataIntegrityViolationException("Duplicate entry for key 'uk_usuarios_email'"));

		// Act
		assertThatThrownBy(() -> service.registrar(request()))
				.isInstanceOf(DuplicateResourceException.class)
				.hasMessage("No se pudo completar el registro con los datos proporcionados");

		// Assert
		assertThat(output.getOut())
				.contains("WARN")
				.contains("restriccion de unicidad");
	}
}
