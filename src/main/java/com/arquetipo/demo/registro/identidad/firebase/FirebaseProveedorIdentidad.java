package com.arquetipo.demo.registro.identidad.firebase;

import com.arquetipo.demo.registro.identidad.ProveedorIdentidad;
import com.arquetipo.demo.registro.identidad.ProveedorIdentidadException;
import com.arquetipo.demo.registro.identidad.UsuarioExterno;
import com.arquetipo.demo.registro.identidad.UsuarioYaRegistradoException;
import com.google.firebase.auth.AuthErrorCode;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.UserRecord;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Implementacion de {@link ProveedorIdentidad} sobre Firebase Authentication (Admin SDK).
 *
 * <p>Este es el UNICO sitio del servicio que conoce las clases de Firebase; el resto del
 * codigo (incluido {@code RegistroService}) solo ve la abstraccion.
 */
@Component
@ConditionalOnProperty(prefix = "firebase", name = "enabled", havingValue = "true", matchIfMissing = true)
public class FirebaseProveedorIdentidad implements ProveedorIdentidad {

	/** providerId que usa Firebase Auth para las cuentas de email + contrasena. */
	private static final String NOMBRE_PROVEEDOR = "password";

	private final FirebaseAuth firebaseAuth;

	public FirebaseProveedorIdentidad(FirebaseAuth firebaseAuth) {
		this.firebaseAuth = firebaseAuth;
	}

	@Override
	public UsuarioExterno crearUsuario(String email, String password) {
		try {
			UserRecord.CreateRequest request = new UserRecord.CreateRequest()
					.setEmail(email)
					.setPassword(password)
					.setEmailVerified(false)
					.setDisabled(false);
			UserRecord creado = firebaseAuth.createUser(request);
			return new UsuarioExterno(creado.getUid());
		} catch (FirebaseAuthException ex) {
			if (ex.getAuthErrorCode() == AuthErrorCode.EMAIL_ALREADY_EXISTS) {
				throw new UsuarioYaRegistradoException(
						"Firebase: el email ya esta registrado (" + ex.getAuthErrorCode() + ")", ex);
			}
			throw new ProveedorIdentidadException(
					"Firebase rechazo la creacion del usuario: " + ex.getMessage(), ex);
		}
	}

	@Override
	public void eliminarUsuario(String uidExterno) {
		try {
			firebaseAuth.deleteUser(uidExterno);
		} catch (FirebaseAuthException ex) {
			throw new ProveedorIdentidadException(
					"Firebase rechazo la eliminacion del usuario uid=" + uidExterno, ex);
		}
	}

	@Override
	public Optional<UsuarioExterno> buscarPorEmail(String email) {
		try {
			UserRecord existente = firebaseAuth.getUserByEmail(email);
			return Optional.of(new UsuarioExterno(existente.getUid()));
		} catch (FirebaseAuthException ex) {
			if (ex.getAuthErrorCode() == AuthErrorCode.USER_NOT_FOUND) {
				return Optional.empty();
			}
			throw new ProveedorIdentidadException(
					"Firebase fallo al buscar el usuario por email: " + ex.getMessage(), ex);
		}
	}

	@Override
	public String nombreProveedor() {
		return NOMBRE_PROVEEDOR;
	}
}
