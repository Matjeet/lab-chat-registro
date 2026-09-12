package com.arquetipo.demo.registro.service;

import com.arquetipo.demo.common.exception.DuplicateResourceException;
import com.arquetipo.demo.registro.domain.ProveedorAuth;
import com.arquetipo.demo.registro.domain.Usuario;
import com.arquetipo.demo.registro.mapper.UsuarioMapper;
import com.arquetipo.demo.registro.repository.ProveedorAuthRepository;
import com.arquetipo.demo.registro.repository.UsuarioRepository;
import com.arquetipo.demo.registro.web.dto.RegistroRequest;
import com.arquetipo.demo.registro.web.dto.RegistroResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reglas del alta de usuarios: valida unicidad y persiste.
 *
 * <p>La autenticacion la gestiona <b>Firebase Auth</b>: este servicio no guarda ninguna
 * contrasena, solo el UID que Firebase asigna ({@code Usuario.firebaseUid}, viene en
 * {@code request.uid()}) y el {@link ProveedorAuth} usado. Si el cliente no manda
 * {@code proveedor} se asume {@code "password"} (unico proveedor que usa hoy el frontend).
 *
 * <p><b>Nota de seguridad pendiente:</b> hoy {@code uid} y {@code proveedor} se toman tal cual
 * del cuerpo de la peticion — este metodo NO verifica el ID token de Firebase. Antes de
 * exponer el endpoint fuera de desarrollo hay que anadir un filtro que valide el token
 * (Firebase Admin SDK, o un Resource Server con el JWK de Firebase) y derive estos dos valores
 * del token verificado, nunca del body. Ver {@code docs/testing-seguridad-owasp.md} (A07).
 *
 * <p>Seguridad ya aplicada: ante un conflicto (username, email o uid ya registrados) el
 * cliente recibe siempre el mismo mensaje generico; el detalle va solo al log {@code WARN},
 * para no facilitar la enumeracion de cuentas.
 */
@Slf4j
@Service
@Transactional
public class RegistroService {

	/** Proveedor asumido cuando el cliente no manda uno explicito. */
	private static final String PROVEEDOR_POR_DEFECTO = "password";

	/** Mensaje unico que ve el cliente ante cualquier conflicto de unicidad. */
	private static final String CONFLICTO_GENERICO =
			"No se pudo completar el registro con los datos proporcionados";

	private final UsuarioRepository repository;
	private final ProveedorAuthRepository proveedorRepository;
	private final UsuarioMapper mapper;

	public RegistroService(UsuarioRepository repository, ProveedorAuthRepository proveedorRepository,
			UsuarioMapper mapper) {
		this.repository = repository;
		this.proveedorRepository = proveedorRepository;
		this.mapper = mapper;
	}

	public RegistroResponse registrar(RegistroRequest request) {
		String username = request.username().trim();
		String email = request.email().trim().toLowerCase();
		String uid = request.uid().trim();
		String nombreProveedor = request.proveedor() == null || request.proveedor().isBlank()
				? PROVEEDOR_POR_DEFECTO
				: request.proveedor().trim();

		if (repository.existsByUsernameIgnoreCase(username)) {
			log.warn("Registro rechazado: el username ya esta registrado. username='{}'", username);
			throw new DuplicateResourceException(CONFLICTO_GENERICO);
		}
		if (repository.existsByEmailIgnoreCase(email)) {
			log.warn("Registro rechazado: el email ya esta registrado. email='{}'", email);
			throw new DuplicateResourceException(CONFLICTO_GENERICO);
		}
		if (repository.existsByFirebaseUid(uid)) {
			log.warn("Registro rechazado: el uid ya esta registrado. uid='{}'", uid);
			throw new DuplicateResourceException(CONFLICTO_GENERICO);
		}

		// El @Pattern de RegistroRequest ya restringe `proveedor` a los valores sembrados por
		// la migracion V2; si aun asi no aparece en la tabla es un desalineamiento del propio
		// servidor (bug de despliegue), no un dato invalido del cliente -> error interno (500).
		ProveedorAuth proveedor = proveedorRepository.findByNombreIgnoreCase(nombreProveedor)
				.orElseThrow(() -> new IllegalStateException(
						"Proveedor de autenticacion no encontrado en proveedores_auth: " + nombreProveedor));

		Usuario usuario = new Usuario();
		usuario.setUsername(username);
		usuario.setEmail(email);
		usuario.setFirebaseUid(uid);
		usuario.setProveedor(proveedor);
		usuario.setActivo(true);

		try {
			Usuario guardado = repository.saveAndFlush(usuario);
			log.debug("Usuario registrado id={} username='{}'", guardado.getId(), guardado.getUsername());
			return mapper.toResponse(guardado);
		} catch (DataIntegrityViolationException ex) {
			// Carrera entre la comprobacion previa y el insert: el detalle va al log, no al cliente.
			log.warn("Registro rechazado por restriccion de unicidad en el insert. "
					+ "username='{}' email='{}' uid='{}'", username, email, uid, ex);
			throw new DuplicateResourceException(CONFLICTO_GENERICO);
		}
	}
}
