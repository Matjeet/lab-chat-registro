package com.arquetipo.demo.registro.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.arquetipo.demo.registro.amqp.NotificadorAmqp;
import com.arquetipo.demo.registro.domain.ProveedorAuth;
import com.arquetipo.demo.registro.identidad.ProveedorIdentidad;
import com.arquetipo.demo.registro.identidad.UsuarioExterno;
import com.arquetipo.demo.registro.repository.ProveedorAuthRepository;
import com.arquetipo.demo.registro.repository.UsuarioRepository;
import com.arquetipo.demo.registro.web.dto.RegistroRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * El alta se anuncia en RabbitMQ solo cuando la transaccion de {@code registrar} hizo commit de
 * verdad, no antes. A diferencia de {@code RegistroServiceTest} (sin transaccion: publica de
 * inmediato), aqui corre el contexto completo con transacciones reales sobre H2 -- por eso NO es
 * {@code @Transactional}: un commit real es justo lo que se quiere observar, y por eso limpia
 * sus filas al terminar.
 */
@SpringBootTest
class RegistroPublicacionTrasCommitTest {

	private static final String PASSWORD_VALIDA = "Passw0rd!23";

	@Autowired
	private RegistroService service;

	@Autowired
	private UsuarioRepository repository;

	@Autowired
	private ProveedorAuthRepository proveedorAuthRepository;

	@Autowired
	private PlatformTransactionManager transactionManager;

	@MockitoBean
	private ProveedorIdentidad proveedorIdentidad;

	@MockitoBean
	private NotificadorAmqp notificador;

	@BeforeEach
	void seedProveedorYStubs() {
		if (proveedorAuthRepository.findByNombreIgnoreCase("password").isEmpty()) {
			ProveedorAuth proveedor = new ProveedorAuth();
			proveedor.setNombre("password");
			proveedorAuthRepository.saveAndFlush(proveedor);
		}
		when(proveedorIdentidad.nombreProveedor()).thenReturn("password");
		when(proveedorIdentidad.crearUsuario(anyString(), anyString()))
				.thenAnswer(inv -> new UsuarioExterno("fb-" + inv.getArgument(0)));
	}

	@AfterEach
	void limpiar() {
		repository.deleteAll();
		proveedorAuthRepository.deleteAll();
	}

	private static RegistroRequest request() {
		return new RegistroRequest("tras.commit", "tras.commit@example.com", PASSWORD_VALIDA,
				"<Blobatar name=\"tras.commit\" />");
	}

	@Test
	void registrar_publicaSoloDespuesDelCommit() {
		TransactionTemplate template = new TransactionTemplate(transactionManager);

		template.executeWithoutResult(status -> {
			service.registrar(request());

			// Dentro de la transaccion todavia no hay commit: aun no debe haberse publicado nada.
			verify(notificador, never()).notificarRegistro(any(), any());
		});

		// Ya con el commit hecho, se publica una sola vez, con username y avatar.
		verify(notificador, times(1)).notificarRegistro("tras.commit", "<Blobatar name=\"tras.commit\" />");
		assertThat(repository.existsByUsernameIgnoreCase("tras.commit")).isTrue();
	}

	@Test
	void registrar_dentroDeUnaTransaccionQueSeRevierte_noPublica() {
		TransactionTemplate template = new TransactionTemplate(transactionManager);

		template.executeWithoutResult(status -> {
			service.registrar(request());
			status.setRollbackOnly();
		});

		verify(notificador, never()).notificarRegistro(any(), any());
		assertThat(repository.existsByUsernameIgnoreCase("tras.commit")).isFalse();
	}

	@Test
	void registrar_conSuPropiaTransaccion_publicaUnaVezTrasElCommit() {
		service.registrar(request());

		verify(notificador, times(1)).notificarRegistro("tras.commit", "<Blobatar name=\"tras.commit\" />");
		assertThat(repository.existsByUsernameIgnoreCase("tras.commit")).isTrue();
	}
}
