package com.arquetipo.demo.registro.grpc;

import com.arquetipo.demo.common.exception.DuplicateResourceException;
import com.arquetipo.demo.common.exception.UsuarioNoEncontradoException;
import com.arquetipo.demo.registro.service.RegistroService;
import com.arquetipo.demo.registro.web.dto.RegistroRequest;
import com.arquetipo.demo.registro.web.dto.RegistroResponse;
import com.arquetipo.demo.registro.web.dto.UsuarioBasico;
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
 * Punto de entrada del registro y consulta de usuarios: {@code registrar} recibe {@code
 * username}/{@code email}/{@code password} y {@code buscarUsuarioPorUid} resuelve
 * username/email a partir del UID de Firebase de una sesion ya autenticada. En los dos casos
 * delega en {@link RegistroService} (unica logica de negocio, sin duplicarla aqui). Es el
 * unico protocolo que expone este servicio — el REST del sistema lo sirve {@code
 * chat-gateway}, que reenvia aqui por gRPC (ver {@code docs/contrato-grpc-registro.md}).
 *
 * <p>Al no pasar por Spring MVC no hay {@code @Valid} automatico: la validacion de Bean
 * Validation se aplica aqui a mano con el mismo {@link Validator} sobre el mismo
 * {@link RegistroRequest} que usa {@code RegistroService}, asi que las reglas (incluida la
 * politica de contrasena) son la unica fuente de verdad, sin una capa REST/OpenAPI aparte que
 * las repita.
 *
 * <p>Mapeo de errores de {@code registrar} (mismo principio de "mensaje generico al cliente,
 * detalle real solo en el log" que se aplicaba antes en el REST):
 * <ul>
 *   <li>Violacion de Bean Validation -&gt; {@code INVALID_ARGUMENT}.</li>
 *   <li>{@link DuplicateResourceException} (username/email duplicado, o el usuario ya existente
 *       en el proveedor de identidad) -&gt; {@code ALREADY_EXISTS} con el mismo mensaje generico
 *       que ya lleva la excepcion; el detalle real ya quedo en el log dentro de
 *       {@code RegistroService}.</li>
 *   <li>Cualquier otra excepcion -&gt; {@code INTERNAL} con un mensaje generico; la excepcion
 *       real se registra aqui, nunca se envia al cliente.</li>
 * </ul>
 *
 * <p>{@code buscarUsuarioPorUid}: {@code uid} vacio -&gt; {@code INVALID_ARGUMENT}; sin
 * usuario con ese uid -&gt; {@code NOT_FOUND}; cualquier otra excepcion -&gt; {@code INTERNAL}
 * (mismo criterio de log que arriba). No valida ningun token: quien llama (siempre
 * {@code chat-gateway}) ya autentico y autorizo la peticion antes de invocar este rpc — ver
 * {@code docs/contrato-grpc-registro.md} §1. Aqui el mensaje de {@code NOT_FOUND} no necesita
 * ser generico: no es un alta con riesgo de enumeracion de cuentas por username/email, es una
 * consulta puntual por un UID opaco.
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

	@Override
	public void buscarUsuarioPorUid(BuscarUsuarioPorUidRequest grpcRequest,
			StreamObserver<BuscarUsuarioPorUidResponse> responseObserver) {
		log.debug(">> buscarUsuarioPorUid(uid='{}')", grpcRequest.getUid());

		if (grpcRequest.getUid().isBlank()) {
			log.debug("<< buscarUsuarioPorUid() -> INVALID_ARGUMENT (uid vacio)");
			responseObserver.onError(
					Status.INVALID_ARGUMENT.withDescription("uid es obligatorio").asRuntimeException());
			return;
		}

		try {
			UsuarioBasico usuario = service.buscarPorFirebaseUid(grpcRequest.getUid());
			responseObserver.onNext(mapper.aGrpcResponse(usuario));
			responseObserver.onCompleted();
			log.debug("<< buscarUsuarioPorUid() -> OK");
		} catch (UsuarioNoEncontradoException ex) {
			log.debug("<< buscarUsuarioPorUid() -> NOT_FOUND");
			responseObserver.onError(Status.NOT_FOUND.withDescription(ex.getMessage()).asRuntimeException());
		} catch (Exception ex) {
			log.error("Excepcion no controlada al buscar el usuario por uid", ex);
			log.debug("<< buscarUsuarioPorUid() -> INTERNAL");
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
