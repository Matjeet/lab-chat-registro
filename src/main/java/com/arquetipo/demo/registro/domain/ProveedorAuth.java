package com.arquetipo.demo.registro.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Proveedor de autenticacion soportado por Firebase Auth (p. ej. {@code password},
 * {@code google.com}). Tabla auxiliar de solo lectura para la aplicacion: los valores los
 * siembra la migracion {@code V2__usuarios_firebase_auth.sql}; no se crean desde el codigo.
 */
@Getter
@Setter
@Entity
@Table(name = "proveedores_auth")
public class ProveedorAuth {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, unique = true, length = 50)
	private String nombre;
}
