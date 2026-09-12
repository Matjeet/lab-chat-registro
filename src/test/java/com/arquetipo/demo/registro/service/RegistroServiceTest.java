package com.arquetipo.demo.registro.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class RegistroServiceTest {

	private static final String MENSAJE_GENERICO =
			"No se pudo completar el registro con los datos proporcionados";
	private static final String PASSWORD_VALIDA = "Passw0rd!23";

	@Mock
	private UsuarioRepository repository;

	@Mock
	private ProveedorAuthRepository proveedorRepository;

	@Mock
	private ProveedorIdentidad proveedorIdentidad;

	private RegistroService service;

	@BeforeEach
	void setUp() {
		service = new RegistroService(repository, proveedorRepository, new UsuarioMapper(), proveedorIdentidad);
	}

	private static RegistroRequest request() {
		return new RegistroRequest("mateo", "Mateo@Example.com", PASSWORD_VALIDA);
	}

	private static ProveedorAuth proveedor(String nombre) {
		ProveedorAuth proveedor = new ProveedorAuth();
		proveedor.setId(1L);
		proveedor.setNombre(nombre);
		return proveedor;
	}

	private void stubProveedorFeliz() {
		when(proveedorIdentidad.nombreProveedor()).thenReturn("password");
		when(proveedorRepository.findByNombreIgnoreCase("password")).thenReturn(Optional.of(proveedor("password")));
	}

	@Test
	void registrar_creaPrimeroEnElProveedorYLuegoPersiste() {
		stubProveedorFeliz();
		when(repository.existsByUsernameIgnoreCase("mateo")).thenReturn(false);
		when(repository.existsByEmailIgnoreCase("mateo@example.com")).thenReturn(false);
		when(proveedorIdentidad.crearUsuario("mateo@example.com", PASSWORD_VALIDA))
				.thenReturn(new UsuarioExterno("uid-nuevo-1"));
		when(repository.saveAndFlush(any(Usuario.class))).thenAnswer(inv -> {
			Usuario u = inv.getArgument(0);
			u.setId(1L);
			return u;
		});

		RegistroResponse response = service.registrar(request());

		assertThat(response.id()).isEqualTo(1L);
		assertThat(response.email()).isEqualTo("mateo@example.com");
		assertThat(response.username()).isEqualTo("mateo");
		assertThat(response.proveedor()).isEqualTo("password");
	}

	@Test
	void registrar_persisteElUidQueDevuelveElProveedor_noLaContrasena() {
		stubProveedorFeliz();
		when(repository.existsByUsernameIgnoreCase("mateo")).thenReturn(false);
		when(repository.existsByEmailIgnoreCase("mateo@example.com")).thenReturn(false);
		when(proveedorIdentidad.crearUsuario("mateo@example.com", PASSWORD_VALIDA))
				.thenReturn(new UsuarioExterno("uid-nuevo-1"));
		when(repository.saveAndFlush(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));

		service.registrar(request());

		ArgumentCaptor<Usuario> captor = ArgumentCaptor.forClass(Usuario.class);
		verify(repository).saveAndFlush(captor.capture());
		Usuario persistido = captor.getValue();
		assertThat(persistido.getFirebaseUid()).isEqualTo("uid-nuevo-1");
		assertThat(persistido.getProveedor().getNombre()).isEqualTo("password");
	}

	@Test
	void registrar_usernameDuplicado_niSiquieraLlamaAlProveedor() {
		when(repository.existsByUsernameIgnoreCase("mateo")).thenReturn(true);

		assertThatThrownBy(() -> service.registrar(request()))
				.isInstanceOf(DuplicateResourceException.class)
				.hasMessage(MENSAJE_GENERICO);
		verify(proveedorIdentidad, never()).crearUsuario(any(), any());
	}

	@Test
	void registrar_emailDuplicadoLocalmente_niSiquieraLlamaAlProveedor() {
		when(repository.existsByUsernameIgnoreCase("mateo")).thenReturn(false);
		when(repository.existsByEmailIgnoreCase("mateo@example.com")).thenReturn(true);

		assertThatThrownBy(() -> service.registrar(request()))
				.isInstanceOf(DuplicateResourceException.class)
				.hasMessage(MENSAJE_GENERICO);
		verify(proveedorIdentidad, never()).crearUsuario(any(), any());
	}

	@Test
	void registrar_proveedorDiceQueYaExisteYNoHayFilaLocal_reconciliaYRegistraConExito() {
		stubProveedorFeliz();
		when(repository.existsByUsernameIgnoreCase("mateo")).thenReturn(false);
		when(repository.existsByEmailIgnoreCase("mateo@example.com")).thenReturn(false);
		when(proveedorIdentidad.crearUsuario(eq("mateo@example.com"), any()))
				.thenThrow(new UsuarioYaRegistradoException("ya existe", null));
		when(proveedorIdentidad.buscarPorEmail("mateo@example.com"))
				.thenReturn(Optional.of(new UsuarioExterno("uid-preexistente")));
		when(repository.existsByFirebaseUid("uid-preexistente")).thenReturn(false);
		when(repository.saveAndFlush(any(Usuario.class))).thenAnswer(inv -> {
			Usuario u = inv.getArgument(0);
			u.setId(2L);
			return u;
		});

		RegistroResponse response = service.registrar(request());

		assertThat(response.id()).isEqualTo(2L);
		ArgumentCaptor<Usuario> captor = ArgumentCaptor.forClass(Usuario.class);
		verify(repository).saveAndFlush(captor.capture());
		assertThat(captor.getValue().getFirebaseUid()).isEqualTo("uid-preexistente");
	}

	@Test
	void registrar_proveedorDiceQueYaExisteYYaHayFilaLocal_esConflictoGenerico() {
		stubProveedorFeliz();
		when(repository.existsByUsernameIgnoreCase("mateo")).thenReturn(false);
		when(repository.existsByEmailIgnoreCase("mateo@example.com")).thenReturn(false);
		when(proveedorIdentidad.crearUsuario(eq("mateo@example.com"), any()))
				.thenThrow(new UsuarioYaRegistradoException("ya existe", null));
		when(proveedorIdentidad.buscarPorEmail("mateo@example.com"))
				.thenReturn(Optional.of(new UsuarioExterno("uid-preexistente")));
		when(repository.existsByFirebaseUid("uid-preexistente")).thenReturn(true);

		assertThatThrownBy(() -> service.registrar(request()))
				.isInstanceOf(DuplicateResourceException.class)
				.hasMessage(MENSAJE_GENERICO);
		verify(repository, never()).saveAndFlush(any());
	}

	@Test
	void registrar_reconciliacionSinPoderBuscarEnElProveedor_esConflictoGenerico() {
		stubProveedorFeliz();
		when(repository.existsByUsernameIgnoreCase("mateo")).thenReturn(false);
		when(repository.existsByEmailIgnoreCase("mateo@example.com")).thenReturn(false);
		when(proveedorIdentidad.crearUsuario(eq("mateo@example.com"), any()))
				.thenThrow(new UsuarioYaRegistradoException("ya existe", null));
		when(proveedorIdentidad.buscarPorEmail("mateo@example.com")).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.registrar(request()))
				.isInstanceOf(DuplicateResourceException.class)
				.hasMessage(MENSAJE_GENERICO);
	}

	@Test
	void registrar_fallaGenericaDelProveedor_sePropagaSinConvertirseEnConflicto() {
		stubProveedorFeliz();
		when(repository.existsByUsernameIgnoreCase("mateo")).thenReturn(false);
		when(repository.existsByEmailIgnoreCase("mateo@example.com")).thenReturn(false);
		when(proveedorIdentidad.crearUsuario(eq("mateo@example.com"), any()))
				.thenThrow(new ProveedorIdentidadException("red caida"));

		assertThatThrownBy(() -> service.registrar(request()))
				.isInstanceOf(ProveedorIdentidadException.class)
				.isNotInstanceOf(DuplicateResourceException.class);
	}

	@Test
	void registrar_creacionExitosaPeroFallaElGuardado_revierteElUsuarioEnElProveedor() {
		stubProveedorFeliz();
		when(repository.existsByUsernameIgnoreCase("mateo")).thenReturn(false);
		when(repository.existsByEmailIgnoreCase("mateo@example.com")).thenReturn(false);
		when(proveedorIdentidad.crearUsuario("mateo@example.com", PASSWORD_VALIDA))
				.thenReturn(new UsuarioExterno("uid-nuevo-1"));
		when(repository.saveAndFlush(any(Usuario.class)))
				.thenThrow(new DataIntegrityViolationException("uk_usuarios_email"));

		assertThatThrownBy(() -> service.registrar(request()))
				.isInstanceOf(DuplicateResourceException.class)
				.hasMessage(MENSAJE_GENERICO);

		verify(proveedorIdentidad, times(1)).eliminarUsuario("uid-nuevo-1");
	}

	@Test
	void registrar_reconciliacionConFalloDeGuardado_noBorraElUsuarioPreexistente() {
		stubProveedorFeliz();
		when(repository.existsByUsernameIgnoreCase("mateo")).thenReturn(false);
		when(repository.existsByEmailIgnoreCase("mateo@example.com")).thenReturn(false);
		when(proveedorIdentidad.crearUsuario(eq("mateo@example.com"), any()))
				.thenThrow(new UsuarioYaRegistradoException("ya existe", null));
		when(proveedorIdentidad.buscarPorEmail("mateo@example.com"))
				.thenReturn(Optional.of(new UsuarioExterno("uid-preexistente")));
		when(repository.existsByFirebaseUid("uid-preexistente")).thenReturn(false);
		when(repository.saveAndFlush(any(Usuario.class)))
				.thenThrow(new DataIntegrityViolationException("uk_usuarios_email"));

		assertThatThrownBy(() -> service.registrar(request()))
				.isInstanceOf(DuplicateResourceException.class)
				.hasMessage(MENSAJE_GENERICO);

		verify(proveedorIdentidad, never()).eliminarUsuario(any());
	}

	@Test
	void registrar_revertirFallaEnElProveedor_noOcultaElConflictoOriginal() {
		stubProveedorFeliz();
		when(repository.existsByUsernameIgnoreCase("mateo")).thenReturn(false);
		when(repository.existsByEmailIgnoreCase("mateo@example.com")).thenReturn(false);
		when(proveedorIdentidad.crearUsuario("mateo@example.com", PASSWORD_VALIDA))
				.thenReturn(new UsuarioExterno("uid-nuevo-1"));
		when(repository.saveAndFlush(any(Usuario.class)))
				.thenThrow(new DataIntegrityViolationException("uk_usuarios_email"));
		org.mockito.Mockito.doThrow(new ProveedorIdentidadException("tambien fallo el borrado"))
				.when(proveedorIdentidad).eliminarUsuario("uid-nuevo-1");

		assertThatThrownBy(() -> service.registrar(request()))
				.isInstanceOf(DuplicateResourceException.class)
				.hasMessage(MENSAJE_GENERICO);
	}

	@Test
	void registrar_proveedorNoSembradoEnBD_esErrorInternoNoConflicto() {
		when(repository.existsByUsernameIgnoreCase("mateo")).thenReturn(false);
		when(repository.existsByEmailIgnoreCase("mateo@example.com")).thenReturn(false);
		when(proveedorIdentidad.nombreProveedor()).thenReturn("password");
		when(proveedorRepository.findByNombreIgnoreCase("password")).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.registrar(request()))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("password");
		verify(proveedorIdentidad, never()).crearUsuario(any(), any());
	}
}
