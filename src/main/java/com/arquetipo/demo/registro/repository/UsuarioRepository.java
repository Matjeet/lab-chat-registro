package com.arquetipo.demo.registro.repository;

import com.arquetipo.demo.registro.domain.Usuario;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repositorio de {@link Usuario}. Solo las consultas propias del dominio de registro.
 */
public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

	boolean existsByUsernameIgnoreCase(String username);

	boolean existsByEmailIgnoreCase(String email);

	boolean existsByFirebaseUid(String firebaseUid);

	Optional<Usuario> findByUsernameIgnoreCase(String username);
}
