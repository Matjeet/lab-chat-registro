package com.arquetipo.demo.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.arquetipo.demo.registro.domain.ProveedorAuth;
import com.arquetipo.demo.registro.grpc.CapturingStreamObserver;
import com.arquetipo.demo.registro.grpc.RegistrarUsuarioRequest;
import com.arquetipo.demo.registro.grpc.RegistrarUsuarioResponse;
import com.arquetipo.demo.registro.grpc.RegistroGrpcController;
import com.arquetipo.demo.registro.identidad.ProveedorIdentidad;
import com.arquetipo.demo.registro.identidad.UsuarioExterno;
import com.arquetipo.demo.registro.repository.ProveedorAuthRepository;
import com.arquetipo.demo.registro.repository.UsuarioRepository;
import io.grpc.Status;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

/**
 * OWASP A03:2021 - Injection (SQLi / payloads de scripting).
 *
 * <p>Las cargas maliciosas se rechazan en validacion (nunca provocan un fallo interno ni se
 * ejecutan) y las consultas del repositorio estan parametrizadas (JPA), asi que un valor con
 * sintaxis SQL se trata como dato literal.
 */
@SpringBootTest
@Transactional
class A03InjectionTest {

	private static final String PASSWORD_VALIDA = "Passw0rd!23";

	@Autowired
	private RegistroGrpcController controller;

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
	void registro_payloadDeInyeccionEnUsername_seRechazaSinError(String payload) {
		// Arrange
		CapturingStreamObserver<RegistrarUsuarioResponse> observer = new CapturingStreamObserver<>();

		// Act
		controller.registrar(RegistrarUsuarioRequest.newBuilder()
				.setUsername(payload).setEmail("inj@example.com").setPassword(PASSWORD_VALIDA)
				.build(), observer);

		// Assert: la validacion lo rechaza (INVALID_ARGUMENT), nunca un fallo interno
		assertThat(observer.tieneError()).isTrue();
		assertThat(observer.errorDeEstado().getStatus().getCode()).isEqualTo(Status.Code.INVALID_ARGUMENT);
	}

	@ParameterizedTest
	@ValueSource(strings = {
			"a' OR 1=1 --@example.com",
			"\"<script>\"@example.com",
			"'; DROP TABLE usuarios; --@x.com"
	})
	void registro_payloadDeInyeccionEnEmail_nuncaProvocaErrorDeServidor(String payload) {
		// Arrange
		CapturingStreamObserver<RegistrarUsuarioResponse> observer = new CapturingStreamObserver<>();

		// Act
		controller.registrar(RegistrarUsuarioRequest.newBuilder()
				.setUsername("injmail").setEmail(payload).setPassword(PASSWORD_VALIDA)
				.build(), observer);

		// Assert: la validacion lo rechaza o se guarda como literal, pero JAMAS un fallo interno
		if (observer.tieneError()) {
			Status.Code codigo = observer.errorDeEstado().getStatus().getCode();
			assertThat(codigo).isNotIn(Status.Code.INTERNAL, Status.Code.UNKNOWN);
		}
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
	void trasIntentosDeInyeccion_laTablaSigueOperativa() {
		// Arrange: lanzar un payload destructivo
		controller.registrar(RegistrarUsuarioRequest.newBuilder()
				.setUsername("'; DROP TABLE usuarios; --").setEmail("x@x.com").setPassword(PASSWORD_VALIDA)
				.build(), new CapturingStreamObserver<>());

		// Act: un alta legitima posterior
		CapturingStreamObserver<RegistrarUsuarioResponse> observer = new CapturingStreamObserver<>();
		controller.registrar(RegistrarUsuarioRequest.newBuilder()
				.setUsername("legitimo").setEmail("legitimo@example.com").setPassword(PASSWORD_VALIDA)
				.build(), observer);

		// Assert: la tabla existe y solo tiene el registro valido
		assertThat(observer.tieneError()).isFalse();
		assertThat(repository.findByUsernameIgnoreCase("legitimo")).isPresent();
		assertThat(repository.count()).isEqualTo(1);
	}
}
