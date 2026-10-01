-- Avatar del usuario: un enlace http(s) comun, o una etiqueta <Blobatar .../> (avatar animado
-- generado por la libreria del mismo nombre) que el frontend renderiza tal cual, ya validada y
-- normalizada por la aplicacion (ver RegistroRequest/RegistroGrpcMapper). Nula para los
-- usuarios que no eligieron avatar, incluidos todos los registrados antes de esta migracion.
ALTER TABLE usuarios
    ADD COLUMN avatar VARCHAR(500) NULL AFTER email;
