package com.arquetipo.demo.registro.web.dto;

/**
 * Datos minimos de un usuario ya registrado: lo que necesita otro servicio para resolver la
 * identidad de quien ya tiene una sesion de Firebase, a partir de su UID
 * ({@code RegistroGrpcService/BuscarUsuarioPorUid}). No expone el UID (el cliente ya lo
 * tiene, es el dato de entrada) ni ningun otro campo del perfil mas alla de username/email/avatar.
 */
public record UsuarioBasico(String username, String email, String avatar) {
}
