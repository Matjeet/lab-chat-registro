package com.arquetipo.demo.registro.grpc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.arquetipo.demo.common.exception.DuplicateResourceException;
import com.arquetipo.demo.registro.service.RegistroService;
import com.arquetipo.demo.registro.web.dto.RegistroResponse;
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
 * Prueba el endpoint gRPC de registro sobre un servidor in-process (sin red real), mirando el
 * mismo contrato que {@code RegistroControllerTest} (el equivalente REST): mismo exito, mismo
 * 400/409 (aqui INVALID_ARGUMENT/ALREADY_EXISTS) con mensaje generico.
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

	private static StatusRuntimeException catchStatusRuntimeException(Runnable llamada) {
		try {
			llamada.run();
		} catch (StatusRuntimeException ex) {
			return ex;
		}
		throw new AssertionError("Se esperaba un StatusRuntimeException y no se lanzo ninguno");
	}
}
