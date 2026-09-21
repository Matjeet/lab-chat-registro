package com.arquetipo.demo.common.exception;

/**
 * Se lanza al buscar un usuario por una clave (p. ej. el UID de Firebase) que no corresponde
 * a ningun registro existente. El controller gRPC que la capture la traduce a {@code NOT_FOUND}.
 */
public class UsuarioNoEncontradoException extends RuntimeException {

	public UsuarioNoEncontradoException(String message) {
		super(message);
	}
}
