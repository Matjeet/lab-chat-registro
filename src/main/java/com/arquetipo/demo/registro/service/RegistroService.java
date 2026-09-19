package com.arquetipo.demo.registro.service;

import com.arquetipo.demo.common.exception.DuplicateResourceException;
import com.arquetipo.demo.registro.domain.ProveedorAuth;
import com.arquetipo.demo.registro.domain.Usuario;
import com.arquetipo.demo.registro.identidad.ProveedorIdentidad;
import com.arquetipo.demo.registro.identidad.ProveedorIdentidadException;
import com.arquetipo.demo.registro.identidad.UsuarioExterno;
import com.arquetipo.demo.registro.identidad.UsuarioYaRegistradoException;
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
 * Orquesta el alta de usuarios: crea la identidad en el proveedor externo (Firebase Auth,
 * via {@link ProveedorIdentidad}) y persiste el perfil de dominio.
 *
 * <p>Orden de las operaciones y compensaciones:
 * <ol>
 *   <li>Comprobaciones locales de unicidad (username, email) — evitan llamar al proveedor
 *       cuando ya sabemos que el alta no puede completarse.</li>
 *   <li>Se crea el usuario en el proveedor de identidad con email + contrasena. El UID y el
 *       proveedor los decide el servidor (nunca el cliente).</li>
 *   <li>Si el proveedor dice que el email ya existe, se reconcilia: se busca el usuario
 *       existente en el proveedor y, si la base local no tiene fila para el, se crea ahora
 *       (autocuracion de un alta previa que fallo a medias); si ya la tiene, es un conflicto
 *       genuino.</li>
 *   <li>Si la creacion en el proveedor tuvo exito pero el guardado en la base de datos falla,
 *       se revierte el usuario recien creado en el proveedor para no dejarlo huerfano. En la
 *       rama de reconciliacion NUNCA se borra: ese usuario ya existia antes de esta peticion.</li>
 * </ol>
 *
 * <p>Seguridad: ante cualquier conflicto (username, email, o el usuario ya existente en el
 * proveedor) el cliente recibe siempre el mismo mensaje generico; el detalle real (incluido
 * el motivo que reporta el proveedor) va solo al log del servidor, para no facilitar la
 * enumeracion de cuentas. La contrasena en claro nunca se registra en logs ni se persiste:
 * solo se reenvia al proveedor.
 */
@Slf4j
@Service
@Transactional
public class RegistroService {

	/** Mensaje unico que ve el cliente ante cualquier conflicto de unicidad. */
	private static final String CONFLICTO_GENERICO =
			"No se pudo completar el registro con los datos proporcionados";

	private final UsuarioRepository repository;
	private final ProveedorAuthRepository proveedorRepository;
	private final UsuarioMapper mapper;
	private final ProveedorIdentidad proveedorIdentidad;

	public RegistroService(UsuarioRepository repository, ProveedorAuthRepository proveedorRepository,
			UsuarioMapper mapper, ProveedorIdentidad proveedorIdentidad) {
		this.repository = repository;
		this.proveedorRepository = proveedorRepository;
		this.mapper = mapper;
		this.proveedorIdentidad = proveedorIdentidad;
	}

	public RegistroResponse registrar(RegistroRequest request) {
		String username = request.username().trim();
		String email = request.email().trim().toLowerCase();
		log.debug(">> registrar(username='{}', email='{}')", username, email);

		if (repository.existsByUsernameIgnoreCase(username)) {
			log.warn("Registro rechazado: el username ya esta registrado. username='{}'", username);
			throw new DuplicateResourceException(CONFLICTO_GENERICO);
		}
		if (repository.existsByEmailIgnoreCase(email)) {
			log.warn("Registro rechazado: el email ya esta registrado. email='{}'", email);
			throw new DuplicateResourceException(CONFLICTO_GENERICO);
		}

		ProveedorAuth proveedor = resolverProveedor();

		try {
			UsuarioExterno creado = proveedorIdentidad.crearUsuario(email, request.password());
			RegistroResponse respuesta = guardar(username, email, creado, proveedor, true);
			log.debug("<< registrar() -> OK, id={}", respuesta.id());
			return respuesta;
		} catch (UsuarioYaRegistradoException ex) {
			log.warn("El proveedor de identidad ya tenia un usuario con este email; "
					+ "se intenta reconciliar. email='{}' motivo='{}'", email, ex.getMessage());
			RegistroResponse respuesta = reconciliar(username, email, proveedor);
			log.debug("<< registrar() -> OK (reconciliado), id={}", respuesta.id());
			return respuesta;
		} catch (ProveedorIdentidadException ex) {
			log.error("Fallo al crear el usuario en el proveedor de identidad. email='{}'", email, ex);
			// Sin log de fin a proposito: la ausencia de "<< registrar()" marca el punto exacto
			// del fallo cuando se lee el log de arriba hacia abajo.
			throw ex;
		}
	}

	/**
	 * El proveedor ya tenia un usuario con este email: si nuestra base no tiene fila para el
	 * (un alta anterior que fallo a medias, por ejemplo), se crea ahora. Si ya la tiene, es un
	 * conflicto real.
	 */
	private RegistroResponse reconciliar(String username, String email, ProveedorAuth proveedor) {
		log.debug(">> reconciliar(email='{}')", email);
		UsuarioExterno existente = proveedorIdentidad.buscarPorEmail(email)
				.orElseThrow(() -> {
					log.warn("El proveedor dijo que el email ya existia pero no se encontro al buscarlo. "
							+ "email='{}'", email);
					return new DuplicateResourceException(CONFLICTO_GENERICO);
				});

		if (repository.existsByFirebaseUid(existente.uid())) {
			log.warn("Registro rechazado: el usuario ya existe en el proveedor y en la base local. "
					+ "email='{}'", email);
			throw new DuplicateResourceException(CONFLICTO_GENERICO);
		}

		log.warn("Reconciliando: se crea la fila local que faltaba para un usuario ya existente "
				+ "en el proveedor de identidad. email='{}' uid='{}'", email, existente.uid());
		// No se compensa (no borrar) si el guardado fallara: el usuario ya existia en el
		// proveedor antes de esta peticion, no lo creamos nosotros ahora.
		RegistroResponse respuesta = guardar(username, email, existente, proveedor, false);
		log.debug("<< reconciliar() -> OK, id={}", respuesta.id());
		return respuesta;
	}

	private RegistroResponse guardar(String username, String email, UsuarioExterno usuarioExterno,
			ProveedorAuth proveedor, boolean revertirEnProveedorSiFalla) {
		log.debug(">> guardar(username='{}', email='{}', uid='{}')", username, email, usuarioExterno.uid());
		Usuario usuario = new Usuario();
		usuario.setUsername(username);
		usuario.setEmail(email);
		usuario.setFirebaseUid(usuarioExterno.uid());
		usuario.setProveedor(proveedor);
		usuario.setActivo(true);

		try {
			Usuario guardado = repository.saveAndFlush(usuario);
			log.debug("Usuario registrado id={} username='{}'", guardado.getId(), guardado.getUsername());
			RegistroResponse respuesta = mapper.toResponse(guardado);
			log.debug("<< guardar() -> OK, id={}", respuesta.id());
			return respuesta;
		} catch (DataIntegrityViolationException ex) {
			// Carrera entre la comprobacion previa y el insert: el detalle va al log, no al cliente.
			log.warn("Registro rechazado por restriccion de unicidad en el insert. "
					+ "username='{}' email='{}' uid='{}'", username, email, usuarioExterno.uid(), ex);
			if (revertirEnProveedorSiFalla) {
				revertirEnProveedor(usuarioExterno.uid());
			}
			throw new DuplicateResourceException(CONFLICTO_GENERICO);
		}
	}

	private void revertirEnProveedor(String uidExterno) {
		log.debug(">> revertirEnProveedor(uid='{}')", uidExterno);
		try {
			proveedorIdentidad.eliminarUsuario(uidExterno);
			log.debug("<< revertirEnProveedor() -> OK");
		} catch (Exception ex) {
			log.error("No se pudo revertir el usuario del proveedor de identidad tras un fallo de "
					+ "guardado local; requiere limpieza manual. uid='{}'", uidExterno, ex);
		}
	}

	private ProveedorAuth resolverProveedor() {
		log.debug(">> resolverProveedor()");
		// El nombre lo decide el proveedor de identidad (nunca el cliente). El @Pattern de
		// RegistroRequest ya no existe para "proveedor" porque ya no es un campo de la
		// peticion; si aun asi no aparece en proveedores_auth es un desalineamiento del
		// propio servidor (bug de despliegue: falta sembrar la migracion), no un dato
		// invalido del cliente -> error interno (500).
		String nombreProveedor = proveedorIdentidad.nombreProveedor();
		ProveedorAuth proveedor = proveedorRepository.findByNombreIgnoreCase(nombreProveedor)
				.orElseThrow(() -> new IllegalStateException(
						"Proveedor de autenticacion no encontrado en proveedores_auth: " + nombreProveedor));
		log.debug("<< resolverProveedor() -> OK, nombre='{}'", nombreProveedor);
		return proveedor;
	}
}
