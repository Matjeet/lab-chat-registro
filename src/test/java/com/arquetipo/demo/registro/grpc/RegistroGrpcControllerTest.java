package com.arquetipo.demo.registro.grpc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.arquetipo.demo.common.exception.DuplicateResourceException;
import com.arquetipo.demo.common.exception.UsuarioNoEncontradoException;
import com.arquetipo.demo.registro.service.RegistroService;
import com.arquetipo.demo.registro.web.dto.RegistroResponse;
import com.arquetipo.demo.registro.web.dto.UsuarioBasico;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.StatusRuntimeException;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Prueba los tres rpc de {@link RegistroGrpcController} sobre un servidor in-process (sin red
 * real): {@code Registrar} (exito, validacion invalida, usuario duplicado),
 * {@code BuscarUsuarioPorUid} (encontrado, no encontrado, uid vacio) y
 * {@code ExisteUsername} (existe, no existe, username vacio).
 */
class RegistroGrpcControllerTest {

	private RegistroService registroService;
	private Server server;
	private ManagedChannel channel;
	private RegistroGrpcServiceGrpc.RegistroGrpcServiceBlockingStub stub;

	@BeforeEach
	void iniciarServidorInProcess() throws Exception {
		String nombreServidor = "registro-grpc-test-" + System.nanoTime();
		registroService = mock(RegistroService.class);
		Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
		RegistroGrpcController controller =
				new RegistroGrpcController(registroService, new RegistroGrpcMapper(), validator);

		server = InProcessServerBuilder.forName(nombreServidor)
				.directExecutor()
				.addService(controller)
				.build()
				.start();
		channel = InProcessChannelBuilder.forName(nombreServidor).directExecutor().build();
		stub = RegistroGrpcServiceGrpc.newBlockingStub(channel);
	}

	@AfterEach
	void detenerServidor() throws Exception {
		channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
		server.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
	}

	@Test
	void registrar_datosValidos_devuelveElUsuarioSinDatosDeAutenticacion() {
		when(registroService.registrar(any()))
				.thenReturn(new RegistroResponse(1L, "mateo", "mateo@example.com", "password", true, Instant.now()));

		RegistrarUsuarioResponse respuesta = stub.registrar(RegistrarUsuarioRequest.newBuilder()
				.setUsername("mateo")
				.setEmail("mateo@example.com")
				.setPassword("Passw0rd!23")
				.build());

		assertThat(respuesta.getId()).isEqualTo(1L);
		assertThat(respuesta.getUsername()).isEqualTo("mateo");
		assertThat(respuesta.getProveedor()).isEqualTo("password");
		assertThat(respuesta.getActivo()).isTrue();
	}

	@Test
	void registrar_cuerpoInvalido_devuelveInvalidArgument() {
		StatusRuntimeException excepcion = catchStatusRuntimeException(() -> stub.registrar(
				RegistrarUsuarioRequest.newBuilder()
						.setUsername("m")
						.setEmail("no-es-email")
						.setPassword("corta")
						.build()));

		assertThat(excepcion.getStatus().getCode()).isEqualTo(io.grpc.Status.Code.INVALID_ARGUMENT);
	}

	@Test
	void registrar_usuarioDuplicado_devuelveAlreadyExistsConMensajeGenerico() {
		when(registroService.registrar(any()))
				.thenThrow(new DuplicateResourceException(
						"No se pudo completar el registro con los datos proporcionados"));

		StatusRuntimeException excepcion = catchStatusRuntimeException(() -> stub.registrar(
				RegistrarUsuarioRequest.newBuilder()
						.setUsername("mateo")
						.setEmail("mateo@example.com")
						.setPassword("Passw0rd!23")
						.build()));

		assertThat(excepcion.getStatus().getCode()).isEqualTo(io.grpc.Status.Code.ALREADY_EXISTS);
		assertThat(excepcion.getStatus().getDescription())
				.isEqualTo("No se pudo completar el registro con los datos proporcionados")
				.doesNotContain("mateo");
	}

	@Test
	void buscarUsuarioPorUid_usuarioExiste_devuelveUsernameYEmail() {
		when(registroService.buscarPorFirebaseUid("uid-existente"))
				.thenReturn(new UsuarioBasico("mateo", "mateo@example.com"));

		BuscarUsuarioPorUidResponse respuesta = stub.buscarUsuarioPorUid(
				BuscarUsuarioPorUidRequest.newBuilder().setUid("uid-existente").build());

		assertThat(respuesta.getUsername()).isEqualTo("mateo");
		assertThat(respuesta.getEmail()).isEqualTo("mateo@example.com");
	}

	@Test
	void buscarUsuarioPorUid_sinUsuarioConEseUid_devuelveNotFound() {
		when(registroService.buscarPorFirebaseUid("uid-inexistente"))
				.thenThrow(new UsuarioNoEncontradoException("Usuario no encontrado"));

		StatusRuntimeException excepcion = catchStatusRuntimeException(() -> stub.buscarUsuarioPorUid(
				BuscarUsuarioPorUidRequest.newBuilder().setUid("uid-inexistente").build()));

		assertThat(excepcion.getStatus().getCode()).isEqualTo(io.grpc.Status.Code.NOT_FOUND);
	}

	@Test
	void buscarUsuarioPorUid_uidVacio_devuelveInvalidArgumentSinLlamarAlServicio() {
		StatusRuntimeException excepcion = catchStatusRuntimeException(() -> stub.buscarUsuarioPorUid(
				BuscarUsuarioPorUidRequest.newBuilder().setUid("").build()));

		assertThat(excepcion.getStatus().getCode()).isEqualTo(io.grpc.Status.Code.INVALID_ARGUMENT);
		org.mockito.Mockito.verify(registroService, org.mockito.Mockito.never()).buscarPorFirebaseUid(any());
	}

	@Test
	void existeUsername_usernameRegistrado_devuelveTrue() {
		when(registroService.existeUsername("mateo")).thenReturn(true);

		ExisteUsernameResponse respuesta = stub.existeUsername(
				ExisteUsernameRequest.newBuilder().setUsername("mateo").build());

		assertThat(respuesta.getExiste()).isTrue();
	}

	@Test
	void existeUsername_usernameLibre_devuelveFalse() {
		when(registroService.existeUsername("libre")).thenReturn(false);

		ExisteUsernameResponse respuesta = stub.existeUsername(
				ExisteUsernameRequest.newBuilder().setUsername("libre").build());

		assertThat(respuesta.getExiste()).isFalse();
	}

	@Test
	void existeUsername_usernameVacio_devuelveInvalidArgumentSinLlamarAlServicio() {
		StatusRuntimeException excepcion = catchStatusRuntimeException(() -> stub.existeUsername(
				ExisteUsernameRequest.newBuilder().setUsername("").build()));

		assertThat(excepcion.getStatus().getCode()).isEqualTo(io.grpc.Status.Code.INVALID_ARGUMENT);
		org.mockito.Mockito.verify(registroService, org.mockito.Mockito.never()).existeUsername(any());
	}

	private static StatusRuntimeException catchStatusRuntimeException(Runnable llamada) {
		try {
			llamada.run();
		} catch (StatusRuntimeException ex) {
			return ex;
		}
		throw new AssertionError("Se esperaba un StatusRuntimeException y no se lanzo ninguno");
	}
}
