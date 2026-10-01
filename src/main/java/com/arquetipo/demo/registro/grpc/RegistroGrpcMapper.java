package com.arquetipo.demo.registro.grpc;

import com.arquetipo.demo.registro.web.dto.RegistroRequest;
import com.arquetipo.demo.registro.web.dto.RegistroResponse;
import com.arquetipo.demo.registro.web.dto.UsuarioBasico;
import org.springframework.stereotype.Component;

/**
 * Traduce entre los mensajes de {@code registro.proto} y los DTO del dominio
 * ({@link RegistroRequest} / {@link RegistroResponse}), para que la validacion y el flujo
 * ({@code RegistroService}) tengan una unica fuente de verdad.
 *
 * <p>Publica (no de paquete) para que los tests de seguridad
 * ({@code src/test/java/com/arquetipo/demo/security/}) puedan construir un
 * {@code RegistroGrpcController} sin levantar contexto de Spring, igual que hace
 * {@code RegistroGrpcControllerTest}.
 */
@Component
public class RegistroGrpcMapper {

	RegistroRequest aRegistroRequest(RegistrarUsuarioRequest grpcRequest) {
		return new RegistroRequest(
				grpcRequest.getUsername(),
				grpcRequest.getEmail(),
				grpcRequest.getPassword(),
				normalizarAvatar(grpcRequest.getAvatar()));
	}

	RegistrarUsuarioResponse aGrpcResponse(RegistroResponse response) {
		return RegistrarUsuarioResponse.newBuilder()
				.setId(response.id())
				.setUsername(response.username())
				.setEmail(response.email())
				.setProveedor(response.proveedor())
				.setActivo(response.activo())
				.setCreatedAt(response.createdAt().toString())
				.setAvatar(response.avatar() == null ? "" : response.avatar())
				.build();
	}

	/**
	 * proto3 no distingue "avatar ausente" de cadena vacia: "" (o solo espacios) se trata como
	 * "sin avatar" (null), para que el {@code @Pattern} de {@link RegistroRequest#avatar()} no
	 * rechace la ausencia del campo como si fuera un formato invalido.
	 *
	 * <p>Si el valor no esta vacio pero llega envuelto en un unico par de comillas rectas
	 * (simples o dobles) que encierran toda la cadena -- p. ej. copiado tal cual desde un
	 * literal de string en vez del contenido en si --, se retira ese par exterior para dejar la
	 * etiqueta/URL lista para que el frontend la use, sin alterar nada mas del contenido.
	 */
	private String normalizarAvatar(String avatarCrudo) {
		String recortado = avatarCrudo.trim();
		if (recortado.isEmpty()) {
			return null;
		}
		if (recortado.length() >= 2) {
			char primero = recortado.charAt(0);
			char ultimo = recortado.charAt(recortado.length() - 1);
			boolean envueltoEnComillas = (primero == '"' && ultimo == '"') || (primero == '\'' && ultimo == '\'');
			if (envueltoEnComillas) {
				recortado = recortado.substring(1, recortado.length() - 1).trim();
			}
		}
		return recortado.isEmpty() ? null : recortado;
	}

	BuscarUsuarioPorUidResponse aGrpcResponse(UsuarioBasico usuario) {
		return BuscarUsuarioPorUidResponse.newBuilder()
				.setUsername(usuario.username())
				.setEmail(usuario.email())
				.setAvatar(usuario.avatar() == null ? "" : usuario.avatar())
				.build();
	}
}
