package com.arquetipo.demo.registro.identidad.firebase;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import java.io.FileInputStream;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Inicializa el SDK de administracion de Firebase.
 *
 * <p>Se puede desactivar por completo con {@code firebase.enabled=false} (los tests lo hacen,
 * ver {@code src/test/resources/application.yml}): en ese caso ni este bean ni
 * {@link FirebaseProveedorIdentidad} se crean, y quien necesite un {@code ProveedorIdentidad}
 * en un test debe aportar el suyo (mock).
 */
@Configuration
@ConditionalOnProperty(prefix = "firebase", name = "enabled", havingValue = "true", matchIfMissing = true)
public class FirebaseAppConfig {

	@Bean
	public FirebaseApp firebaseApp(@Value("${firebase.credentials-path:}") String credentialsPath) throws IOException {
		if (!FirebaseApp.getApps().isEmpty()) {
			return FirebaseApp.getInstance();
		}
		GoogleCredentials credenciales = credentialsPath == null || credentialsPath.isBlank()
				// Sin ruta explicita: credenciales por defecto del entorno (ADC), p. ej.
				// GOOGLE_APPLICATION_CREDENTIALS o la identidad del propio host en la nube.
				? GoogleCredentials.getApplicationDefault()
				: GoogleCredentials.fromStream(new FileInputStream(credentialsPath));
		FirebaseOptions opciones = FirebaseOptions.builder()
				.setCredentials(credenciales)
				.build();
		return FirebaseApp.initializeApp(opciones);
	}

	@Bean
	public FirebaseAuth firebaseAuth(FirebaseApp firebaseApp) {
		return FirebaseAuth.getInstance(firebaseApp);
	}
}
