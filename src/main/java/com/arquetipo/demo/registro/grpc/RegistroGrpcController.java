package com.arquetipo.demo.registro.grpc;

import com.arquetipo.demo.common.exception.DuplicateResourceException;
import com.arquetipo.demo.registro.service.RegistroService;
import com.arquetipo.demo.registro.web.RegistroController;
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
 * Espejo por gRPC de {@link RegistroController}: mismo contrato ({@code username}/{@code
 * email}/{@code password} de entrada, mismo DTO de salida) y mismo flujo, delegando en el
 * mismo {@link RegistroService} — no se duplica logica de negocio, solo cambia el protocolo de
 * transporte. El REST sigue existiendo tal cual, sin tocarse.
 *
 * <p>Al no pasar por Spring MVC, la validacion de Bean Validation ({@code @Valid} en el
 * controller REST) se aplica aqui a mano con el mismo {@link Validator} sobre el mismo
 * {@link RegistroRequest}, así que las reglas (incluida la politica de contrasena) son
 * exactamente las mismas en los dos protocolos.
 *
 * <p>Mapeo de errores (mismo principio de "mensaje generico al cliente, detalle real solo en
 * el log" que {@code GlobalExceptionHandler}):
 * <ul>
 *   <li>Violacion de Bean Validation -&gt; {@code INVALID_ARGUMENT} (equivalente al 400 REST).</li>
 *   <li>{@link DuplicateResourceException} (username/email duplicado, o el usuario ya existente
 *       en el proveedor de identidad) -&gt; {@code ALREADY_EXISTS} con el mismo mensaje generico
 *       que ya lleva la excepcion (equivalente al 409 REST); el detalle real ya quedo en el log
 *       dentro de {@code RegistroService}.</li>
 *   <li>Cualquier otra excepcion -&gt; {@code INTERNAL} con un mensaje generico (equivalente al
 *       500 REST); la excepcion real se registra aqui, nunca se envia al cliente.</li>
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
		RegistroRequest request = mapper.aRegistroRequest(grpcRequest);

		Set<ConstraintViolation<RegistroRequest>> violaciones = validator.validate(request);
		if (!violaciones.isEmpty()) {
			responseObserver.onError(errorDeValidacion(violaciones));
			return;
		}

		try {
			RegistroResponse resultado = service.registrar(request);
			responseObserver.onNext(mapper.aGrpcResponse(resultado));
			responseObserver.onCompleted();
		} catch (DuplicateResourceException ex) {
			responseObserver.onError(Status.ALREADY_EXISTS.withDescription(ex.getMessage()).asRuntimeException());
		} catch (Exception ex) {
			log.error("Excepcion no controlada en el endpoint gRPC de registro", ex);
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
