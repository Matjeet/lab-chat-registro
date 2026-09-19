package com.arquetipo.demo.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.arquetipo.demo.registro.grpc.CapturingStreamObserver;
import com.arquetipo.demo.registro.grpc.RegistrarUsuarioRequest;
import com.arquetipo.demo.registro.grpc.RegistrarUsuarioResponse;
import com.arquetipo.demo.registro.grpc.RegistroGrpcController;
import com.arquetipo.demo.registro.grpc.RegistroGrpcMapper;
import com.arquetipo.demo.registro.service.RegistroService;
import com.arquetipo.demo.registro.web.dto.RegistroResponse;
import io.grpc.Status;
import jakarta.validation.Validation;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * OWASP A07:2021 - Identification and Authentication Failures.
 *
 * <p>La contrasena la sigue validando el proveedor de identidad al crear la cuenta, pero
 * este servicio impone su propia politica en el borde (para no reenviar al proveedor, ni una
 * sola vez, una contrasena que ya sabemos debil): 8-20 caracteres, mayuscula, minuscula,
 * numero, caracter especial y sin 4+ repeticiones seguidas del mismo caracter. Tambien se
 * valida el formato de `username`.
 *
 * <p>Sin contexto de Spring (mockea solo {@code RegistroService}), igual que hacia el
 * {@code @WebMvcTest} que este test usaba antes de que el registro pasara a servirse solo por
 * gRPC.
 */
@ExtendWith(MockitoExtension.class)
class A07AuthenticationFailuresTest {

	@Mock
	private RegistroService registroService;

	private RegistroGrpcController controller;

	@BeforeEach
	void construirController() {
		controller = new RegistroGrpcController(registroService, new RegistroGrpcMapper(),
				Validation.buildDefaultValidatorFactory().getValidator());
	}

	@ParameterizedTest
	@ValueSource(strings = {
			"Aa1!",                  // 4 caracteres: mas corta que el minimo (8)
			"Passw0rd!23456789ABCD", // 21 caracteres: mas larga que el maximo (20)
			"passw0rd!23",           // sin mayuscula
			"PASSW0RD!23",           // sin minuscula
			"Password!AB",           // sin numero
			"Passw0rd123",           // sin caracter especial
			"Paaaa0rd!23",           // "aaaa": 4 repeticiones seguidas del mismo caracter
	})
	void registro_contrasenaQueViolaLaPolitica_seRechaza(String contrasenaInvalida) {
		// Arrange
		CapturingStreamObserver<RegistrarUsuarioResponse> observer = new CapturingStreamObserver<>();

		// Act
		controller.registrar(RegistrarUsuarioRequest.newBuilder()
				.setUsername("usuario").setEmail("usuario@example.com").setPassword(contrasenaInvalida)
				.build(), observer);

		// Assert
		assertThat(observer.tieneError()).isTrue();
		assertThat(observer.errorDeEstado().getStatus().getCode()).isEqualTo(Status.Code.INVALID_ARGUMENT);
		assertThat(observer.errorDeEstado().getStatus().getDescription()).contains("password");
		verify(registroService, never()).registrar(any());
	}

	@ParameterizedTest
	@ValueSource(strings = {
			"Passw0r!",     // 8 caracteres: minimo exacto
			"Paaa0rd!23",   // "aaa": 3 repeticiones seguidas SI estan permitidas
			"Passw0rd!23Passw0rd!", // 20 caracteres: maximo exacto
	})
	void registro_contrasenaQueCumpleLaPolitica_pasaLaValidacion(String contrasenaValida) {
		// Arrange
		when(registroService.registrar(any()))
				.thenReturn(new RegistroResponse(1L, "usuario", "usuario@example.com", "password", true, Instant.now()));
		CapturingStreamObserver<RegistrarUsuarioResponse> observer = new CapturingStreamObserver<>();

		// Act
		controller.registrar(RegistrarUsuarioRequest.newBuilder()
				.setUsername("usuario").setEmail("usuario@example.com").setPassword(contrasenaValida)
				.build(), observer);

		// Assert
		assertThat(observer.tieneError()).isFalse();
		assertThat(observer.valor()).isNotNull();
	}

	@ParameterizedTest
	@ValueSource(strings = {"ab", "  ", "usuario con espacios", "e", "díéresis", "user@name", "../etc"})
	void registro_usernameConFormatoInvalido_seRechaza(String invalido) {
		// Arrange
		CapturingStreamObserver<RegistrarUsuarioResponse> observer = new CapturingStreamObserver<>();

		// Act
		controller.registrar(RegistrarUsuarioRequest.newBuilder()
				.setUsername(invalido).setEmail("usuario@example.com").setPassword("Passw0rd!23")
				.build(), observer);

		// Assert
		assertThat(observer.tieneError()).isTrue();
		assertThat(observer.errorDeEstado().getStatus().getCode()).isEqualTo(Status.Code.INVALID_ARGUMENT);
		verify(registroService, never()).registrar(any());
	}
}
