package com.arquetipo.demo.registro.amqp;

/**
 * Mensaje publicado en RabbitMQ cuando un usuario termina de registrarse (ver
 * {@link NotificadorAmqp}). Por ahora solo lleva lo que necesitan los consumidores para
 * identificar y mostrar al usuario: {@code username} y {@code avatar} (enlace http(s) o etiqueta
 * {@code <Blobatar .../>} tal cual quedo persistido, {@code null} si no eligio uno). No incluye
 * email, uid del proveedor de identidad ni ningun dato de autenticacion.
 */
public record UsuarioRegistradoAmqp(String username, String avatar) {
}
