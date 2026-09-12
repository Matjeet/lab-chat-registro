package com.arquetipo.demo.registro.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.arquetipo.demo.common.exception.DuplicateResourceException;
import com.arquetipo.demo.registro.domain.ProveedorAuth;
import com.arquetipo.demo.registro.domain.Usuario;
import com.arquetipo.demo.registro.mapper.UsuarioMapper;
import com.arquetipo.demo.registro.repository.ProveedorAuthRepository;
import com.arquetipo.demo.registro.repository.UsuarioRepository;
import com.arquetipo.demo.registro.web.dto.RegistroRequest;
import com.arquetipo.demo.registro.web.dto.RegistroResponse;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RegistroServiceTest {

	private static final String MENSAJE_GENERICO =
			"No se pudo completar el registro con los datos proporcionados";

	@Mock
	private UsuarioRepository repository;

	@Mock
	private ProveedorAuthRepository proveedorRepository;

	private RegistroService service;

	@BeforeEach
	void setUp() {
		service = new RegistroService(repository, proveedorRepository, new UsuarioMapper());
	}

	private static RegistroRequest request() {
		return new RegistroRequest("mateo", "Mateo@Example.com", "firebase-uid-1", "password");
	}

	private static ProveedorAuth proveedor(String nombre) {
		ProveedorAuth proveedor = new ProveedorAuth();
		proveedor.setId(1L);
		proveedor.setNombre(nombre);
		return proveedor;
	}

	@Test
	void registrar_normalizaEmailYResuelveElProveedor() {
		when(repository.existsByUsernameIgnoreCase("mateo")).thenReturn(false);
		when(repository.existsByEmailIgnoreCase("mateo@example.com")).thenReturn(false);
		when(repository.existsByFirebaseUid("firebase-uid-1")).thenReturn(false);
		when(proveedorRepository.findByNombreIgnoreCase("password")).thenReturn(Optional.of(proveedor("password")));
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
	void registrar_noPersisteNingunaContrasena() {
		when(repository.existsByUsernameIgnoreCase("mateo")).thenReturn(false);
		when(repository.existsByEmailIgnoreCase("mateo@example.com")).thenReturn(false);
		when(repository.existsByFirebaseUid("firebase-uid-1")).thenReturn(false);
		when(proveedorRepository.findByNombreIgnoreCase("password")).thenReturn(Optional.of(proveedor("password")));
		when(repository.saveAndFlush(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));

		service.registrar(request());

		org.mockito.ArgumentCaptor<Usuario> captor = org.mockito.ArgumentCaptor.forClass(Usuario.class);
		org.mockito.Mockito.verify(repository).saveAndFlush(captor.capture());
		Usuario persistido = captor.getValue();
		assertThat(persistido.getFirebaseUid()).isEqualTo("firebase-uid-1");
		assertThat(persistido.getProveedor().getNombre()).isEqualTo("password");
	}

	@Test
	void registrar_usernameDuplicado_lanzaConflictoGenerico() {
		when(repository.existsByUsernameIgnoreCase("mateo")).thenReturn(true);

		assertThatThrownBy(() -> service.registrar(request()))
				.isInstanceOf(DuplicateResourceException.class)
				.hasMessage(MENSAJE_GENERICO);
	}

	@Test
	void registrar_emailDuplicado_lanzaMismoConflictoGenerico() {
		when(repository.existsByUsernameIgnoreCase("mateo")).thenReturn(false);
		when(repository.existsByEmailIgnoreCase("mateo@example.com")).thenReturn(true);

		assertThatThrownBy(() -> service.registrar(request()))
				.isInstanceOf(DuplicateResourceException.class)
				.hasMessage(MENSAJE_GENERICO);
	}

	@Test
	void registrar_uidDuplicado_lanzaMismoConflictoGenerico() {
		when(repository.existsByUsernameIgnoreCase("mateo")).thenReturn(false);
		when(repository.existsByEmailIgnoreCase("mateo@example.com")).thenReturn(false);
		when(repository.existsByFirebaseUid("firebase-uid-1")).thenReturn(true);

		assertThatThrownBy(() -> service.registrar(request()))
				.isInstanceOf(DuplicateResourceException.class)
				.hasMessage(MENSAJE_GENERICO);
	}

	@Test
	void registrar_carreraEnInsert_lanzaMismoConflictoGenerico() {
		when(repository.existsByUsernameIgnoreCase("mateo")).thenReturn(false);
		when(repository.existsByEmailIgnoreCase("mateo@example.com")).thenReturn(false);
		when(repository.existsByFirebaseUid("firebase-uid-1")).thenReturn(false);
		when(proveedorRepository.findByNombreIgnoreCase("password")).thenReturn(Optional.of(proveedor("password")));
		when(repository.saveAndFlush(any(Usuario.class)))
				.thenThrow(new org.springframework.dao.DataIntegrityViolationException("uk_usuarios_email"));

		assertThatThrownBy(() -> service.registrar(request()))
				.isInstanceOf(DuplicateResourceException.class)
				.hasMessage(MENSAJE_GENERICO);
	}

	@Test
	void registrar_sinProveedorEnLaPeticion_asumePassword() {
		RegistroRequest sinProveedor = new RegistroRequest("mateo", "mateo@example.com", "firebase-uid-1", null);
		when(repository.existsByUsernameIgnoreCase("mateo")).thenReturn(false);
		when(repository.existsByEmailIgnoreCase("mateo@example.com")).thenReturn(false);
		when(repository.existsByFirebaseUid("firebase-uid-1")).thenReturn(false);
		when(proveedorRepository.findByNombreIgnoreCase("password")).thenReturn(Optional.of(proveedor("password")));
		when(repository.saveAndFlush(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));

		RegistroResponse response = service.registrar(sinProveedor);

		assertThat(response.proveedor()).isEqualTo("password");
	}

	@Test
	void registrar_proveedorNoSembradoEnBD_esErrorInternoNoConflicto() {
		when(repository.existsByUsernameIgnoreCase("mateo")).thenReturn(false);
		when(repository.existsByEmailIgnoreCase("mateo@example.com")).thenReturn(false);
		when(repository.existsByFirebaseUid("firebase-uid-1")).thenReturn(false);
		when(proveedorRepository.findByNombreIgnoreCase("password")).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.registrar(request()))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("password");
	}
}
