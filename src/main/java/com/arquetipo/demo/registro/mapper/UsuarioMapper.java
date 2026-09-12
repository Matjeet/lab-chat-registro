package com.arquetipo.demo.registro.mapper;

import com.arquetipo.demo.registro.domain.Usuario;
import com.arquetipo.demo.registro.web.dto.RegistroResponse;
import org.springframework.stereotype.Component;

/**
 * Mapeo manual entre {@link Usuario} y sus DTO. La creacion de la entidad vive en el
 * servicio porque necesita resolver el {@code ProveedorAuth} antes de asignarlo.
 */
@Component
public class UsuarioMapper {

	public RegistroResponse toResponse(Usuario usuario) {
		return new RegistroResponse(
				usuario.getId(),
				usuario.getUsername(),
				usuario.getEmail(),
				usuario.getProveedor().getNombre(),
				usuario.isActivo(),
				usuario.getCreatedAt());
	}
}
