package com.arquetipo.demo.registro.grpc;

import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;

/**
 * Captura el resultado de una llamada sincrona a un rpc unario, para tests que llaman al
 * controller directamente (sin levantar un servidor/canal real): {@code RegistroGrpcController}
 * no usa hilos propios, asi que la respuesta o el error ya estan disponibles cuando
 * {@code registrar(...)} retorna.
 */
public final class CapturingStreamObserver<T> implements StreamObserver<T> {

	private T valor;
	private Throwable error;

	@Override
	public void onNext(T value) {
		this.valor = value;
	}

	@Override
	public void onError(Throwable t) {
		this.error = t;
	}

	@Override
	public void onCompleted() {
		// No hace falta nada: valor()/tieneError() ya reflejan el resultado.
	}

	public T valor() {
		return valor;
	}

	public boolean tieneError() {
		return error != null;
	}

	public StatusRuntimeException errorDeEstado() {
		return (StatusRuntimeException) error;
	}
}
