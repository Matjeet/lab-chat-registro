package com.arquetipo.demo.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.arquetipo.demo.registro.domain.ProveedorAuth;
import com.arquetipo.demo.registro.grpc.CapturingStreamObserver;
import com.arquetipo.demo.registro.grpc.RegistrarUsuarioRequest;
import com.arquetipo.demo.registro.grpc.RegistrarUsuarioResponse;
import com.arquetipo.demo.registro.grpc.RegistroGrpcController;
import com.arquetipo.demo.registro.identidad.ProveedorIdentidad;
import com.arquetipo.demo.registro.identidad.UsuarioExterno;
import com.arquetipo.demo.registro.identidad.UsuarioYaRegistradoException;
import com.arquetipo.demo.registro.repository.ProveedorAuthRepository;
import io.grpc.Status;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

/**
 * OWASP A04:2021 - Insecure Design (resistencia a la enumeracion de cuentas).
 *
 * <p>Un conflicto de username, de email, o un usuario ya existente tanto en el proveedor de
 * identidad como en la base local, deben producir una respuesta indistinguible, sin revelar
 * que colisiono ni el valor enviado.
 */
@SpringBootTest
@Transactional
class A04AccountEnumerationTest {

	private static final String PASSWORD_VALIDA = "Passw0rd!23";

	@Autowired
	private RegistroGrpcController controller;

	@Autowired
	private ProveedorAuthRepository proveedorAuthRepository;

	@MockitoBean
	private ProveedorIdentidad proveedorIdentidad;

	@BeforeEach
	void seedProveedorYAltaPrevia() {
		if (proveedorAuthRepository.findByNombreIgnoreCase("password").isEmpty()) {
			ProveedorAuth proveedor = new ProveedorAuth();
			proveedor.setNombre("password");
			proveedorAuthRepository.saveAndFlush(proveedor);
		}
		when(proveedorIdentidad.nombreProveedor()).thenReturn("password");
		when(proveedorIdentidad.crearUsuario(anyString(), anyString()))
				.thenAnswer(inv -> new UsuarioExterno("fb-" + inv.getArgument(0)));

		CapturingStreamObserver<RegistrarUsuarioResponse> observer = new CapturingStreamObserver<>();
		controller.registrar(RegistrarUsuarioRequest.newBuilder()
				.setUsername("existente").setEmail("existente@example.com").setPassword(PASSWORD_VALIDA)
				.build(), observer);
		assertThat(observer.tieneError()).isFalse();
	}

	@Test
	void conflictoDeUsernameDeEmailYDeProveedorExterno_producenLaMismaRespuesta() {
		// Arrange: colisiones por username y por email (locales)
		RegistrarUsuarioRequest colisionUsername = RegistrarUsuarioRequest.newBuilder()
				.setUsername("existente").setEmail("otro@example.com").setPassword(PASSWORD_VALIDA).build();
		RegistrarUsuarioRequest colisionEmail = RegistrarUsuarioRequest.newBuilder()
				.setUsername("otro1").setEmail("existente@example.com").setPassword(PASSWORD_VALIDA).build();

		// El proveedor externo tambien dice "ya existe" para un tercer email, y esa cuenta
		// coincide con la que ya tenemos en la base local (reconciliacion -> conflicto real)
		when(proveedorIdentidad.crearUsuario(eq("otroproveedor@example.com"), anyString()))
				.thenThrow(new UsuarioYaRegistradoException("ya existe en el proveedor", null));
		when(proveedorIdentidad.buscarPorEmail("otroproveedor@example.com"))
				.thenReturn(Optional.of(new UsuarioExterno("fb-existente@example.com")));
		RegistrarUsuarioRequest colisionEnProveedor = RegistrarUsuarioRequest.newBuilder()
				.setUsername("otro2").setEmail("otroproveedor@example.com").setPassword(PASSWORD_VALIDA).build();

		// Act
		Status statusPorUsername = ejecutarConflicto(colisionUsername);
		Status statusPorEmail = ejecutarConflicto(colisionEmail);
		Status statusPorProveedor = ejecutarConflicto(colisionEnProveedor);

		// Assert: los tres son indistinguibles (mismo codigo, misma descripcion)
		assertThat(statusPorUsername.getCode()).isEqualTo(Status.Code.ALREADY_EXISTS);
		assertThat(statusPorUsername.getDescription()).isEqualTo(statusPorEmail.getDescription());
		assertThat(statusPorEmail.getDescription()).isEqualTo(statusPorProveedor.getDescription());
	}

	@Test
	void respuestaDeConflicto_noRevelaCampoNiValorEnviado() {
		// Arrange
		RegistrarUsuarioRequest colisionUsername = RegistrarUsuarioRequest.newBuilder()
				.setUsername("existente").setEmail("secreto-tecleado@example.com").setPassword(PASSWORD_VALIDA)
				.build();

		// Act
		String descripcion = ejecutarConflicto(colisionUsername).getDescription();

		// Assert
		assertThat(descripcion)
				.doesNotContain("existente")
				.doesNotContain("secreto-tecleado")
				.doesNotContain("username")
				.doesNotContain("email")
				.doesNotContain("password")
				.isEqualTo("No se pudo completar el registro con los datos proporcionados");
	}

	private Status ejecutarConflicto(RegistrarUsuarioRequest request) {
		CapturingStreamObserver<RegistrarUsuarioResponse> observer = new CapturingStreamObserver<>();
		controller.registrar(request, observer);
		assertThat(observer.tieneError()).isTrue();
		return observer.errorDeEstado().getStatus();
	}
}
