package com.arquetipo.demo.registro.repository;

import com.arquetipo.demo.registro.domain.ProveedorAuth;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repositorio de {@link ProveedorAuth}. Tabla de solo lectura para la aplicacion (los
 * valores los siembra la migracion Flyway), asi que solo hace falta la consulta de busqueda.
 */
public interface ProveedorAuthRepository extends JpaRepository<ProveedorAuth, Long> {

	Optional<ProveedorAuth> findByNombreIgnoreCase(String nombre);
}
