# Desarrollo local

Guía para levantar y probar el backend en una máquina de desarrollo.

## Requisitos

- **Java 21** (JDK).
- **Maven 3.9+** (o el wrapper `mvnw`, si se agrega más adelante).
- **PostgreSQL** corriendo localmente (o accesible por red) con una base de datos creada de antemano. El backend no crea la base de datos por vos, solo el esquema dentro de ella (vía Hibernate, ver `HIBERNATE_DDL_AUTO` en [environment.md](environment.md)).

## Variables de entorno

1. Copiar `.env.example` (en la raíz de `appointment-manager/`) a `.env` (o `.env.local`) y completar los valores.
2. Cargar esas variables en el entorno antes de ejecutar el backend. Cómo hacerlo depende de tu shell/IDE:
   - **IntelliJ IDEA**: en la configuración de ejecución (Run Configuration) de `AppointmentManagerApplication`, cargar el archivo `.env` con el plugin "EnvFile" o pegar las variables manualmente en "Environment variables".
   - **Terminal (bash/zsh)**: `export $(grep -v '^#' .env | xargs)` antes de `mvn spring-boot:run` (cuidado si algún valor contiene espacios o `=` adicionales).
   - **PowerShell**: `Get-Content .env | ForEach-Object { if ($_ -match '^\s*([^#][^=]*)=(.*)$') { [Environment]::SetEnvironmentVariable($matches[1], $matches[2]) } }`.

Ver [environment.md](environment.md) para el detalle de cada variable.

Para desarrollo local, `SPRING_PROFILES_ACTIVE=dev` y `SERVER_ADDRESS=0.0.0.0` (ver [environment.md](environment.md)).

## Cómo ejecutar

Desde la carpeta `appointment-manager`:

```bash
mvn spring-boot:run
```

La aplicación se levanta en `http://localhost:8080` (o el `SERVER_PORT` configurado).

## Cómo probar Swagger

1. Abrir `http://localhost:8080/swagger-ui/index.html`.
2. Registrar un usuario con `POST /api/auth/register` (o hacer login con `POST /api/auth/login` si ya existe uno) usando "Try it out".
3. Copiar el `accessToken` de la respuesta.
4. Hacer clic en el botón **Authorize** (ícono de candado, arriba a la derecha).
5. Pegar únicamente el token (sin el prefijo `Bearer `) en el campo `bearerAuth` y confirmar.
6. A partir de ahí, todas las pruebas "Try it out" contra endpoints protegidos incluyen el header `Authorization` automáticamente.

El documento OpenAPI crudo está disponible en `http://localhost:8080/v3/api-docs`.

## Cómo ejecutar los tests

```bash
mvn clean test
```

Los tests de integración (`*ControllerIntegrationTest`, `*ServiceImplTest` con `@SpringBootTest`) usan una base de datos embebida (H2, vía `@AutoConfigureTestDatabase(Replace.ANY)`) con el esquema recreado en cada corrida (`ddl-auto=create-drop`) — no requieren PostgreSQL corriendo ni las variables `DB_*` configuradas.

## Build

```bash
mvn clean package
```

Genera `target/appointment-manager.jar`.

## Ejecución del jar empaquetado

```bash
java -jar target/appointment-manager.jar
```

Requiere las mismas variables de entorno que `mvn spring-boot:run` (ver [environment.md](environment.md)). El mismo jar sirve para desarrollo local, servidor Linux o cualquier otro entorno — el comportamiento cambia únicamente según las variables de entorno presentes, nunca según el artefacto compilado.
