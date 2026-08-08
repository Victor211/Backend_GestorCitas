# Despliegue en producción

Guía **conceptual** para desplegar el backend en un servidor Linux detrás de Apache como reverse proxy. No incluye scripts de instalación ni asume una distribución específica — las herramientas de gestión de paquetes (`apt`, `dnf`, etc.) y las rutas exactas varían entre distribuciones.

El backend es un jar Spring Boot autocontenido (Tomcat embebido); no requiere un servidor de aplicaciones externo. Apache actúa únicamente como reverse proxy TLS-terminating delante de él.

## 1. Servidor Linux

Requisitos en el servidor:

- **Java 21** (JRE es suficiente para ejecutar; no hace falta el JDK completo si el build se hace en otro lado, p. ej. en CI).
- **PostgreSQL** accesible (local al servidor o en un host de base de datos separado).
- El jar generado por `mvn clean package` (ver [development.md](development.md)): `target/appointment-manager.jar`.

## 2. Variables de entorno

En producción, `SERVER_ADDRESS=127.0.0.1` (el backend solo escucha en loopback; únicamente Apache, corriendo en la misma máquina, puede alcanzarlo directamente — nunca se expone el puerto de Tomcat a la red pública). `SPRING_PROFILES_ACTIVE=prod` activa `application-prod.yml` (`show-sql: false`, mismo datasource dirigido por `DB_*`).

Ver el detalle completo de cada variable en [environment.md](environment.md); `.env.example` en la raíz del proyecto sirve de plantilla.

Cómo inyectar esas variables depende de cómo se ejecute el proceso (ver sección systemd más abajo): normalmente vía un archivo de entorno (`EnvironmentFile=` en systemd) con permisos restringidos (legible solo por el usuario que corre el servicio), nunca commiteado al repositorio.

## 3. systemd

El patrón habitual es correr el jar como un servicio systemd, para que:

- arranque automáticamente al bootear el servidor;
- se reinicie solo si el proceso muere;
- centralice sus logs en `journalctl`.

Conceptualmente, un unit file de systemd para este backend necesita:

- Un `ExecStart` que invoque `java -jar /ruta/al/appointment-manager.jar`.
- Un `EnvironmentFile=` apuntando al archivo con las variables de producción (con permisos `600`, dueño el usuario de servicio).
- Un usuario dedicado sin privilegios (no correr como `root`).
- `Restart=on-failure` para resiliencia ante caídas del proceso.
- `WorkingDirectory` apuntando a donde vive el jar.

Este documento no incluye el unit file completo ni comandos de instalación: la ubicación del jar, el nombre del usuario de servicio y las rutas del archivo de entorno son decisiones específicas de cada servidor.

## 4. Apache como reverse proxy

Apache (con `mod_proxy` y `mod_proxy_http` habilitados) recibe el tráfico público en los puertos 80/443 y lo reenvía al backend, que escucha únicamente en `127.0.0.1:${SERVER_PORT}`.

Conceptualmente, la configuración del `VirtualHost` necesita:

- `ProxyPass` / `ProxyPassReverse` hacia `http://127.0.0.1:8080/` (o el `SERVER_PORT` configurado).
- Reenvío de headers relevantes (`X-Forwarded-For`, `X-Forwarded-Proto`) para que el backend, si en algún momento necesita saber el origen real de la request, no vea solo la IP de Apache.
- El header `X-Hub-Signature-256` (usado por el webhook de WhatsApp para validar la firma de Meta) debe pasar sin modificarse.

Este documento no incluye la configuración de Apache lista para copiar/pegar: los nombres de `VirtualHost`, rutas de certificados y módulos habilitados dependen de la distribución y de cómo esté administrado el servidor.

## 5. HTTPS

Meta exige HTTPS para el webhook de WhatsApp (`WHATSAPP_*`, ver [environment.md](environment.md)), y es indispensable para cualquier tráfico de producción con JWT en el header `Authorization`. HTTPS se termina en Apache (no en el backend Spring Boot, que sigue sirviendo HTTP plano internamente en `127.0.0.1`). La renovación de certificados (por ejemplo vía Let's Encrypt/`certbot`) es responsabilidad de la configuración de Apache del servidor, fuera del alcance de este backend.

## 6. CORS en producción

`CORS_ALLOWED_ORIGINS` debe apuntar al dominio real del frontend en producción (por ejemplo `https://app.midominio.com`), nunca a `http://localhost:5173` ni a `*`. Ver [environment.md](environment.md).

## 7. Resumen del flujo

```
Internet (HTTPS) → Apache (VirtualHost, TLS) → 127.0.0.1:${SERVER_PORT} (Tomcat embebido, systemd) → PostgreSQL (DB_*)
```

El mismo jar (`target/appointment-manager.jar`) que corre en desarrollo local corre en producción — el comportamiento cambia únicamente vía las variables de entorno documentadas en [environment.md](environment.md), nunca recompilando ni modificando código.
