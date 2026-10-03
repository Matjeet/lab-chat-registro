package com.arquetipo.demo.registro.amqp;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Publica en RabbitMQ (exchange declarado en {@code com.arquetipo.demo.common.config.RabbitMqConfig})
 * que un usuario acaba de registrarse, para que otros servicios puedan reaccionar sin que
 * chat-registro sepa quien lo lee. Un fallo al publicar (broker caido, etc.) se registra pero
 * nunca se propaga: el alta ya esta hecha en Firebase y en la base de datos, y el aviso es
 * best-effort, no la fuente de verdad (esa es MySQL).
 */
@Slf4j
@Component
public class NotificadorAmqp {

	private final RabbitTemplate rabbitTemplate;
	private final TopicExchange exchange;
	private final String routingKey;
	private final boolean habilitado;

	public NotificadorAmqp(RabbitTemplate rabbitTemplate, TopicExchange registroExchange,
			@Value("${app.amqp.registro-routing-key:registro.#}") String routingKey,
			@Value("${app.amqp.enabled:true}") boolean habilitado) {
		this.rabbitTemplate = rabbitTemplate;
		this.exchange = registroExchange;
		this.routingKey = routingKey;
		this.habilitado = habilitado;
	}

	public void notificarRegistro(String username, String avatar) {
		log.debug(">> notificarRegistro(username='{}')", username);
		if (!habilitado) {
			log.debug("<< notificarRegistro() -> omitido (app.amqp.enabled=false)");
			return;
		}
		try {
			rabbitTemplate.convertAndSend(exchange.getName(), routingKey, new UsuarioRegistradoAmqp(username, avatar));
			log.debug("<< notificarRegistro() -> OK");
		} catch (RuntimeException ex) {
			// RuntimeException y no solo AmqpException: se invoca tras el commit de la transaccion
			// del alta, y una excepcion ahi llegaria al cliente como fallo de un alta que ya esta hecha.
			log.error("No se pudo publicar el alta del usuario en RabbitMQ. username='{}'", username, ex);
		}
	}
}
