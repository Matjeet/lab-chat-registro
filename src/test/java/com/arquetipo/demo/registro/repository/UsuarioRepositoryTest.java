package com.arquetipo.demo.registro.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.arquetipo.demo.common.config.JpaAuditingConfig;
import com.arquetipo.demo.registro.domain.ProveedorAuth;
import com.arquetipo.demo.registro.domain.Usuario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

@DataJpaTest
@Import(JpaAuditingConfig.class)
class UsuarioRepositoryTest {

	@Autowired
	private UsuarioRepository repository;

	@Autowired
	private ProveedorAuthRepository proveedorAuthRepository;

	private ProveedorAuth proveedorPassword;

	@BeforeEach
	void seedProveedor() {
		ProveedorAuth proveedor = new ProveedorAuth();
		proveedor.setNombre("password");
		proveedorPassword = proveedorAuthRepository.saveAndFlush(proveedor);
	}

	private Usuario nuevo() {
		Usuario u = new Usuario();
		u.setUsername("mateo");
		u.setEmail("mateo@example.com");
		u.setFirebaseUid("firebase-uid-mateo");
		u.setProveedor(proveedorPassword);
		return u;
	}

	@Test
	void guardaYRellenaAuditoria() {
		Usuario guardado = repository.saveAndFlush(nuevo());

		assertThat(guardado.getId()).isNotNull();
		assertThat(guardado.getCreatedAt()).isNotNull();
		assertThat(guardado.getUpdatedAt()).isNotNull();
	}

	@Test
	void existsPorUsernameYEmail_ignoraMayusculas() {
		repository.saveAndFlush(nuevo());

		assertThat(repository.existsByUsernameIgnoreCase("MATEO")).isTrue();
		assertThat(repository.existsByEmailIgnoreCase("Mateo@Example.com")).isTrue();
		assertThat(repository.existsByUsernameIgnoreCase("otro")).isFalse();
	}

	@Test
	void existsPorFirebaseUid_esSensibleAMayusculas() {
		repository.saveAndFlush(nuevo());

		assertThat(repository.existsByFirebaseUid("firebase-uid-mateo")).isTrue();
		assertThat(repository.existsByFirebaseUid("FIREBASE-UID-MATEO")).isFalse();
	}
}
