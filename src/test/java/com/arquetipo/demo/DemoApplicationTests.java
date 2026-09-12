package com.arquetipo.demo;

import com.arquetipo.demo.registro.identidad.ProveedorIdentidad;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
class DemoApplicationTests {

	// Con firebase.enabled=false (perfil de test) no hay ningun bean real de
	// ProveedorIdentidad; RegistroService necesita uno para poder construirse.
	@MockitoBean
	private ProveedorIdentidad proveedorIdentidad;

	@Test
	void contextLoads() {
	}

}
