package com.arquetipo.demo.registro.identidad;

/**
 * Usuario creado o encontrado en el proveedor de identidad externo. Por ahora solo hace
 * falta su identificador; si un proveedor futuro aporta mas datos utiles (email verificado,
 * telefono, etc.) se anaden aqui sin tocar {@link ProveedorIdentidad}.
 */
public record UsuarioExterno(String uid) {
}
