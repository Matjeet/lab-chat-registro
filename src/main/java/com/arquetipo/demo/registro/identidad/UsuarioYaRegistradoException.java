package com.arquetipo.demo.registro.identidad;

/**
 * El proveedor de identidad ya tiene un usuario con el email solicitado. No implica por si
 * sola un 409 al cliente: {@code RegistroService} la usa para disparar la reconciliacion
 * (comprobar si esa cuenta ya tiene fila en la base local) antes de decidir la respuesta.
 */
public class UsuarioYaRegistradoException extends ProveedorIdentidadException {

	public UsuarioYaRegistradoException(String message, Throwable cause) {
		super(message, cause);
	}
}
