package com.arquetipo.demo.common.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Exchange de RabbitMQ donde chat-registro publica cada alta de usuario nueva (ver
 * {@code com.arquetipo.demo.registro.amqp.NotificadorAmqp}) y su {@link RabbitTemplate}. Este
 * servicio es quien declara el exchange: topic y durable, para que otros servicios se enlacen
 * con su propia cola (p. ej. {@code registro.#}) sin que este servicio sepa quien los lee.
 *
 * <p>El {@link ConnectionFactory} lo autoconfigura {@code spring-boot-starter-amqp} a partir de
 * {@code spring.rabbitmq.*} (ver {@code application.yml} / variables {@code RABBITMQ_HOST},
 * {@code RABBITMQ_PORT}, {@code RABBITMQ_USERNAME}, {@code RABBITMQ_PASSWORD}) y conecta de
 * forma perezosa -- el arranque de la app no depende de que RabbitMQ ya este levantado.
 */
@Slf4j
@Configuration
public class RabbitMqConfig {

	@Value("${app.amqp.registro-exchange:chat.conversacion}")
	private String nombreExchange;

	@Bean
	public TopicExchange registroExchange() {
		return new TopicExchange(nombreExchange, true, false);
	}

	@Bean
	public MessageConverter jsonMessageConverter() {
		// Jackson 3, el mismo que autoconfigura Spring Boot 4 para el resto del framework (el
		// Jackson2JsonMessageConverter de Jackson 2.x no tiene su databind en este classpath).
		return new JacksonJsonMessageConverter();
	}

	@Bean
	public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter messageConverter) {
		RabbitTemplate template = new RabbitTemplate(connectionFactory);
		template.setMessageConverter(messageConverter);
		return template;
	}

	/**
	 * Declara el exchange al arrancar (mejor esfuerzo) para que exista aunque todavia no se haya
	 * registrado nadie -- un consumidor que se enlaza antes del primer alta no lo encontraria. Si
	 * RabbitMQ no esta disponible en este momento no se tumba el arranque: {@code RabbitAdmin}
	 * (autoconfigurado) lo declara de todas formas en la primera conexion que se logre.
	 */
	@Bean
	public ApplicationRunner declararExchangeAlArrancar(AmqpAdmin amqpAdmin, TopicExchange registroExchange,
			@Value("${app.amqp.enabled:true}") boolean habilitado) {
		return args -> {
			if (!habilitado) {
				log.debug("RabbitMQ desactivado (app.amqp.enabled=false): no se declara el exchange al arrancar");
				return;
			}
			try {
				amqpAdmin.declareExchange(registroExchange);
				log.debug("Exchange '{}' declarado al arrancar", registroExchange.getName());
			} catch (AmqpException ex) {
				log.warn("No se pudo declarar el exchange '{}' al arrancar (RabbitMQ no disponible); "
						+ "se declarara en la primera conexion que se logre", registroExchange.getName(), ex);
			}
		};
	}
}
