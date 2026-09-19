package com.arquetipo.demo.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.arquetipo.demo.registro.grpc.CapturingStreamObserver;
import com.arquetipo.demo.registro.grpc.RegistrarUsuarioRequest;
import com.arquetipo.demo.registro.grpc.RegistrarUsuarioResponse;
import com.arquetipo.demo.registro.grpc.RegistroGrpcController;
import com.arquetipo.demo.registro.service.RegistroService;
import io.grpc.Status;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * OWASP A05:2021 - Security Misconfiguration.
 *
 * <p>Los errores no filtran detalles internos (mensaje de excepcion, stack trace) y los
 * endpoints de Actuator sensibles no estan expuestos.
 *
 * <p>La parte de CORS que llevaba esta clase se retiro junto con {@code CorsConfig}: era
 * infraestructura exclusiva del REST de este servicio, que ya no existe (gRPC no tiene el
 * concepto de preflight/origen de navegador) — chat-gateway es ahora el unico punto de
 * entrada REST del sistema y su propio CORS, si lo tiene, se documenta ahi.
 */
@SpringBootTest
@AutoConfigureMockMvc
class A05SecurityMisconfigurationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private RegistroGrpcController controller;

	@MockitoBean
	private RegistroService registroService;

	@Test
	void errorNoControlado_noFiltraMensajeInternoNiStackTrace() {
		// Arrange
		String detalleInterno = "host=db-prod-01.internal token=SECRET-abc123";
		when(registroService.registrar(any())).thenThrow(new IllegalStateException(detalleInterno));
		CapturingStreamObserver<RegistrarUsuarioResponse> observer = new CapturingStreamObserver<>();

		// Act
		controller.registrar(RegistrarUsuarioRequest.newBuilder()
				.setUsername("quiebra").setEmail("quiebra@example.com").setPassword("Passw0rd!23")
				.build(), observer);

		// Assert
		assertThat(observer.tieneError()).isTrue();
		Status status = observer.errorDeEstado().getStatus();
		assertThat(status.getCode()).isEqualTo(Status.Code.INTERNAL);
		assertThat(status.getDescription()).isEqualTo("Ocurrio un error inesperado. Contacte con soporte.");
		assertThat(status.getDescription())
				.doesNotContain(detalleInterno)
				.doesNotContain("db-prod-01")
				.doesNotContain("SECRET-abc123")
				.doesNotContain("IllegalStateException")
				.doesNotContainIgnoringCase("java.lang");
	}

	@ParameterizedTest
	@ValueSource(strings = {
			"/actuator/env",
			"/actuator/beans",
			"/actuator/configprops",
			"/actuator/mappings",
			"/actuator/threaddump",
			"/actuator/heapdump",
			"/actuator/loggers",
			"/actuator/scheduledtasks"
	})
	void actuator_endpointsSensiblesNoExpuestos(String path) throws Exception {
		// Act + Assert
		mockMvc.perform(get(path)).andExpect(status().isNotFound());
	}

	@Test
	void actuatorHealth_estaDisponiblePeroSinDetallesDeComponentes() throws Exception {
		// Act + Assert
		mockMvc.perform(get("/actuator/health"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("UP"))
				.andExpect(jsonPath("$.components").doesNotExist());
	}

	@Test
	void rutaDesconocida_devuelve404SinFiltrarInterioridades() throws Exception {
		// Act
		String cuerpo = mockMvc.perform(get("/ruta/que/no/existe"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.trace").doesNotExist())
				.andReturn().getResponse().getContentAsString();

		// Assert
		assertThat(cuerpo).doesNotContainIgnoringCase("exception");
	}
}
