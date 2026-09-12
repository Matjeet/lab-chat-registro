package com.arquetipo.demo.registro.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * Usuario registrado en el servicio. La autenticacion la gestiona Firebase Auth: aqui solo
 * se guarda el UID que asigna Firebase ({@code firebaseUid}) y el {@link ProveedorAuth} usado,
 * nunca una contrasena.
 */
@Getter
@Setter
@Entity
@Table(name = "usuarios")
@EntityListeners(AuditingEntityListener.class)
public class Usuario {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Version
	private Long version;

	@Column(nullable = false, unique = true, length = 50)
	private String username;

	@Column(nullable = false, unique = true, length = 255)
	private String email;

	/** UID que Firebase Authentication asigna al usuario. Identificador opaco, no un secreto. */
	@Column(name = "firebase_uid", nullable = false, unique = true, length = 128)
	private String firebaseUid;

	/** Proveedor de Firebase Auth con el que se registro (p. ej. "password", "google.com"). */
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "proveedor_id", nullable = false)
	private ProveedorAuth proveedor;

	@Column(nullable = false)
	private boolean activo = true;

	@CreatedDate
	@Column(name = "created_at", updatable = false, nullable = false)
	private Instant createdAt;

	@LastModifiedDate
	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;
}
