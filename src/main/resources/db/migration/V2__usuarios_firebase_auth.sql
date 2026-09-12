-- La autenticacion pasa a gestionarla Firebase Auth: este servicio deja de guardar
-- contrasenas y en su lugar guarda el UID que asigna Firebase y el proveedor usado.

-- Tabla auxiliar de proveedores de autenticacion soportados. Los valores son los
-- identificadores tal cual los expone el SDK de Firebase Auth (providerId).
CREATE TABLE proveedores_auth (
    id     BIGINT      NOT NULL AUTO_INCREMENT,
    nombre VARCHAR(50) NOT NULL,
    CONSTRAINT pk_proveedores_auth PRIMARY KEY (id),
    CONSTRAINT uk_proveedores_auth_nombre UNIQUE (nombre)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

INSERT INTO proveedores_auth (nombre) VALUES
    ('password'),
    ('google.com'),
    ('facebook.com'),
    ('apple.com'),
    ('github.com'),
    ('twitter.com'),
    ('phone'),
    ('anonymous');

-- Ya no hace falta contrasena: Firebase Auth es quien la gestiona.
ALTER TABLE usuarios
    DROP COLUMN password_hash;

ALTER TABLE usuarios
    ADD COLUMN firebase_uid VARCHAR(128) NOT NULL AFTER email,
    ADD COLUMN proveedor_id BIGINT NOT NULL AFTER firebase_uid;

ALTER TABLE usuarios
    ADD CONSTRAINT uk_usuarios_firebase_uid UNIQUE (firebase_uid),
    ADD CONSTRAINT fk_usuarios_proveedor FOREIGN KEY (proveedor_id) REFERENCES proveedores_auth (id);
