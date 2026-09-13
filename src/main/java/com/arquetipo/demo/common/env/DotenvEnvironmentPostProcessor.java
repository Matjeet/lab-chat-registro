package com.arquetipo.demo.common.env;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;

/**
 * Carga {@code .env} (si existe en el directorio de trabajo) como fuente de propiedades de
 * Spring, con la prioridad mas baja: cualquier variable de entorno real, propiedad de sistema
 * o argumento de linea de comandos la pisa.
 *
 * <p>A diferencia de cargarlo solo en la tarea {@code bootRun} de Gradle (un {@code doFirst}
 * que fija variables de entorno del proceso hijo), esto corre dentro de la propia aplicacion
 * durante el arranque de Spring Boot: funciona igual con {@code ./gradlew bootRun}, desde el
 * IDE (el "Run" de IntelliJ lanza la clase principal directamente, sin pasar por las tareas
 * de Gradle) o como jar (@code java -jar}).
 *
 * <p>Se usa {@link SystemEnvironmentPropertySource} (no un {@code MapPropertySource} a secas)
 * para que las claves en mayusculas con guion bajo del {@code .env} (p. ej.
 * {@code FIREBASE_CREDENTIALS_PATH}) resuelvan los mismos placeholders relajados
 * ({@code ${firebase.credentials-path}}) que resolveria una variable de entorno real.
 *
 * <p>Formato igual que un {@code .env} de shell: {@code CLAVE=valor} por linea, {@code #} para
 * comentarios, comillas simples o dobles opcionales alrededor del valor.
 */
public class DotenvEnvironmentPostProcessor implements EnvironmentPostProcessor {

	private static final String DOTENV_FILENAME = ".env";
	private static final String PROPERTY_SOURCE_NAME = "dotenv";

	@Override
	public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
		Path dotenv = Path.of(DOTENV_FILENAME);
		if (!Files.isRegularFile(dotenv)) {
			return;
		}
		Map<String, Object> propiedades = parse(dotenv);
		if (!propiedades.isEmpty()) {
			environment.getPropertySources()
					.addLast(new SystemEnvironmentPropertySource(PROPERTY_SOURCE_NAME, propiedades));
		}
	}

	private static Map<String, Object> parse(Path dotenv) {
		Map<String, Object> propiedades = new LinkedHashMap<>();
		try {
			List<String> lineas = Files.readAllLines(dotenv);
			for (String rawLinea : lineas) {
				String linea = rawLinea.trim();
				if (linea.isEmpty() || linea.startsWith("#")) {
					continue;
				}
				int separador = linea.indexOf('=');
				if (separador <= 0) {
					continue;
				}
				String clave = linea.substring(0, separador).trim();
				String valor = quitarComillas(linea.substring(separador + 1).trim());
				propiedades.put(clave, valor);
			}
		} catch (IOException ex) {
			throw new IllegalStateException("No se pudo leer " + dotenv.toAbsolutePath(), ex);
		}
		return propiedades;
	}

	private static String quitarComillas(String valor) {
		if (valor.length() >= 2) {
			char primero = valor.charAt(0);
			char ultimo = valor.charAt(valor.length() - 1);
			if ((primero == '"' && ultimo == '"') || (primero == '\'' && ultimo == '\'')) {
				return valor.substring(1, valor.length() - 1);
			}
		}
		return valor;
	}
}
