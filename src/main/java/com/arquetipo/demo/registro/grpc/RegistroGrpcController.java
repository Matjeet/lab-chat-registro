package com.arquetipo.demo.registro.grpc;

import com.arquetipo.demo.common.exception.DuplicateResourceException;
import com.arquetipo.demo.registro.service.RegistroService;
import com.arquetipo.demo.registro.web.dto.RegistroRequest;
import com.arquetipo.demo.registro.web.dto.RegistroResponse;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Punto de entrada del alta de usuarios: recibe {@code username}/{@code email}/{@code
 * password}, delega en {@link RegistroService} (unica logica de negocio, sin duplicarla aqui)
 * y devuelve el mismo DTO de siempre traducido a {@code RegistrarUsuarioResponse}. Es el unico
 * protocolo que expone este servicio para el registro — el REST del sistema lo sirve
 * {@code chat-gateway}, que reenvia aqui por gRPC (ver
 * {@code docs/contrato-grpc-registro.md}).
 *
 * <p>Al no pasar por Spring MVC no hay {@code @Valid} automatico: la validacion de Bean
 * Validation se aplica aqui a mano con el mismo {@link Validator} sobre el mismo
 * {@link RegistroRequest} que usa {@code RegistroService}, asi que las reglas (incluida la
 * politica de contrasena) son la unica fuente de verdad, sin una capa REST/OpenAPI aparte que
 * las repita.
 *
 * <p>Mapeo de errores (mismo principio de "mensaje generico al cliente, detalle real solo en
 * el log" que se aplicaba antes en el REST):
 * <ul>
 *   <li>Violacion de Bean Validation -&gt; {@code INVALID_ARGUMENT}.</li>
 *   <li>{@link DuplicateResourceException} (username/email duplicado, o el usuario ya existente
 *       en el proveedor de identidad) -&gt; {@code ALREADY_EXISTS} con el mismo mensaje generico
 *       que ya lleva la excepcion; el detalle real ya quedo en el log dentro de
 *       {@code RegistroService}.</li>
 *   <li>Cualquier otra excepcion -&gt; {@code INTERNAL} con un mensaje generico; la excepcion
 *       real se registra aqui, nunca se envia al cliente.</li>
 * </ul>
 */
@Slf4j
@Component
public class RegistroGrpcController extends RegistroGrpcServiceGrpc.RegistroGrpcServiceImplBase {

	private static final String DETALLE_VALIDACION = "El cuerpo de la peticion no supero la validacion";
	private static final String DETALLE_ERROR_INTERNO = "Ocurrio un error inesperado. Contacte con soporte.";

	private final RegistroService service;
	private final RegistroGrpcMapper mapper;
	private final Validator validator;

	public RegistroGrpcController(RegistroService service, RegistroGrpcMapper mapper, Validator validator) {
		this.service = service;
		this.mapper = mapper;
		this.validator = validator;
	}

	@Override
	public void registrar(RegistrarUsuarioRequest grpcRequest,
			StreamObserver<RegistrarUsuarioResponse> responseObserver) {
		log.debug(">> registrar(username='{}', email='{}')", grpcRequest.getUsername(), grpcRequest.getEmail());
		RegistroRequest request = mapper.aRegistroRequest(grpcRequest);

		Set<ConstraintViolation<RegistroRequest>> violaciones = validator.validate(request);
		if (!violaciones.isEmpty()) {
			log.debug("<< registrar() -> INVALID_ARGUMENT ({} violacion(es))", violaciones.size());
			responseObserver.onError(errorDeValidacion(violaciones));
			return;
		}

		try {
			RegistroResponse resultado = service.registrar(request);
			responseObserver.onNext(mapper.aGrpcResponse(resultado));
			responseObserver.onCompleted();
			log.debug("<< registrar() -> OK, id={}", resultado.id());
		} catch (DuplicateResourceException ex) {
			log.debug("<< registrar() -> ALREADY_EXISTS");
			responseObserver.onError(Status.ALREADY_EXISTS.withDescription(ex.getMessage()).asRuntimeException());
		} catch (Exception ex) {
			log.error("Excepcion no controlada en el endpoint gRPC de registro", ex);
			log.debug("<< registrar() -> INTERNAL");
			responseObserver.onError(Status.INTERNAL.withDescription(DETALLE_ERROR_INTERNO).asRuntimeException());
		}
	}

	private StatusRuntimeException errorDeValidacion(Set<ConstraintViolation<RegistroRequest>> violaciones) {
		String detalle = violaciones.stream()
				.map(v -> "%s: %s".formatted(v.getPropertyPath(), v.getMessage()))
				.collect(Collectors.joining("; "));
		return Status.INVALID_ARGUMENT
				.withDescription(DETALLE_VALIDACION + " -> " + detalle)
				.asRuntimeException();
	}
}
