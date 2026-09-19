package com.arquetipo.demo.registro.identidad;

/**
 * Fallo al hablar con el proveedor de identidad externo (red, credenciales del propio
 * servicio, error interno del proveedor, etc.). {@code RegistroGrpcController} la trata como
 * cualquier excepcion no controlada: {@code INTERNAL} con detalle generico para el cliente; el
 * detalle real de {@code getMessage()}/causa va a los logs de quien la capture.
 */
public class ProveedorIdentidadException extends RuntimeException {

	public ProveedorIdentidadException(String message) {
		super(message);
	}

	public ProveedorIdentidadException(String message, Throwable cause) {
		super(message, cause);
	}
}
