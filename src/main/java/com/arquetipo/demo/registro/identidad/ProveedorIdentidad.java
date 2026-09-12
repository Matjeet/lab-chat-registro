package com.arquetipo.demo.registro.identidad;

import java.util.Optional;

/**
 * Puerto hacia el proveedor de identidad externo que crea, elimina y busca usuarios (hoy
 * Firebase Auth; ver {@code identidad.firebase}).
 *
 * <p>{@link com.arquetipo.demo.registro.service.RegistroService} programa contra esta
 * abstraccion, nunca contra el SDK concreto de un proveedor: cambiar de proveedor de
 * identidad (o anadir uno nuevo) solo implica escribir una clase nueva que implemente esta
 * interfaz, sin tocar la logica de negocio del registro.
 */
public interface ProveedorIdentidad {

	/**
	 * Crea el usuario en el proveedor externo.
	 *
	 * @param email    correo del usuario, ya normalizado (minusculas)
	 * @param password contrasena en claro; el proveedor la gestiona, este servicio no la guarda
	 * @return el usuario creado en el proveedor
	 * @throws UsuarioYaRegistradoException si el proveedor ya tiene un usuario con ese email
	 * @throws ProveedorIdentidadException  ante cualquier otro fallo del proveedor
	 */
	UsuarioExterno crearUsuario(String email, String password);

	/**
	 * Revierte una alta ya creada en el proveedor. Se usa cuando la creacion en el proveedor
	 * tuvo exito pero el guardado posterior en la base de datos falla, para no dejar un
	 * usuario huerfano en el proveedor.
	 */
	void eliminarUsuario(String uidExterno);

	/**
	 * Busca un usuario ya existente en el proveedor por email. Se usa para reconciliar: si el
	 * proveedor rechaza la creacion porque el email ya existe, esto recupera su UID para
	 * comprobar si la base de datos local ya tiene (o no) la fila correspondiente.
	 */
	Optional<UsuarioExterno> buscarPorEmail(String email);

	/**
	 * Nombre del proveedor tal cual se guarda en la tabla auxiliar {@code proveedores_auth}
	 * (p. ej. {@code "password"}). Lo decide el proveedor, nunca el cliente de la API.
	 */
	String nombreProveedor();
}
