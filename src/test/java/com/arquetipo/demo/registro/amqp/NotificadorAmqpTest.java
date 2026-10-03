package com.arquetipo.demo.registro.amqp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

class NotificadorAmqpTest {

	private static final String EXCHANGE = "chat.conversacion";
	private static final String ROUTING_KEY = "registro.#";

	private RabbitTemplate rabbitTemplate;
	private NotificadorAmqp notificador;

	@BeforeEach
	void setUp() {
		rabbitTemplate = mock(RabbitTemplate.class);
		notificador = new NotificadorAmqp(rabbitTemplate, new TopicExchange(EXCHANGE, true, false), ROUTING_KEY, true);
	}

	@Test
	void notificarRegistro_publicaUsernameYAvatarEnElExchangeConLaRoutingKey() {
		notificador.notificarRegistro("mateo", "https://cdn.example.com/avatares/mateo.png");

		ArgumentCaptor<UsuarioRegistradoAmqp> captor = ArgumentCaptor.forClass(UsuarioRegistradoAmqp.class);
		verify(rabbitTemplate).convertAndSend(eq(EXCHANGE), eq(ROUTING_KEY), captor.capture());
		assertThat(captor.getValue().username()).isEqualTo("mateo");
		assertThat(captor.getValue().avatar()).isEqualTo("https://cdn.example.com/avatares/mateo.png");
	}

	@Test
	void notificarRegistro_sinAvatar_publicaAvatarNulo() {
		notificador.notificarRegistro("mateo", null);

		ArgumentCaptor<UsuarioRegistradoAmqp> captor = ArgumentCaptor.forClass(UsuarioRegistradoAmqp.class);
		verify(rabbitTemplate).convertAndSend(eq(EXCHANGE), eq(ROUTING_KEY), captor.capture());
		assertThat(captor.getValue()).isEqualTo(new UsuarioRegistradoAmqp("mateo", null));
	}

	@Test
	void notificarRegistro_brokerCaido_noPropagaLaExcepcion() {
		doThrow(new AmqpConnectException(new java.net.ConnectException("Connection refused")))
				.when(rabbitTemplate).convertAndSend(any(String.class), any(String.class), any(Object.class));

		assertThatCode(() -> notificador.notificarRegistro("mateo", null)).doesNotThrowAnyException();
	}

	@Test
	void notificarRegistro_falloInesperado_noPropagaLaExcepcion() {
		doThrow(new IllegalStateException("conversion rota"))
				.when(rabbitTemplate).convertAndSend(any(String.class), any(String.class), any(Object.class));

		assertThatCode(() -> notificador.notificarRegistro("mateo", null)).doesNotThrowAnyException();
	}

	@Test
	void notificarRegistro_desactivado_noPublicaNada() {
		NotificadorAmqp desactivado =
				new NotificadorAmqp(rabbitTemplate, new TopicExchange(EXCHANGE, true, false), ROUTING_KEY, false);

		desactivado.notificarRegistro("mateo", null);

		verify(rabbitTemplate, never()).convertAndSend(any(String.class), any(String.class), any(Object.class));
	}
}
