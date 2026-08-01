# appointment-manager

Backend del proyecto Appointment Manager, construido con Spring Boot.

## Requisitos

- Java 21
- Maven 3.9+ (o usar el wrapper `mvnw` si se agrega más adelante)

## Stack

- Java 21
- Spring Boot 3.5.16
- Spring Web
- Spring Data JPA
- PostgreSQL Driver
- Spring Security + JWT (jjwt)
- Validation
- MapStruct
- Lombok
- SpringDoc OpenAPI (Swagger)
- Spring Boot DevTools

## Cómo ejecutar el proyecto

Desde la carpeta `appointment-manager`:

```bash
mvn spring-boot:run
```

O compilando el jar y ejecutándolo:

```bash
mvn clean package
java -jar target/appointment-manager-0.0.1-SNAPSHOT.jar
```

La aplicación se levanta en el puerto `8080`.

## Variables de entorno

Además de las variables de base de datos (`DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD`), el proyecto requiere:

| Variable | Descripción |
|---|---|
| `JWT_SECRET` | Clave usada para firmar los tokens JWT (HMAC). No debe commitearse un valor real al repositorio; se sugiere un string aleatorio largo (32+ caracteres) para desarrollo local. |
| `JWT_EXPIRATION` | Duración del access token, **expresada en milisegundos** (única unidad usada en todo el proyecto). Ejemplo: `3600000` = 1 hora. |

Ninguna de las dos tiene valor por defecto: si faltan, la aplicación no arranca.

## Autenticación

El backend usa autenticación **stateless con JWT** (Bearer token). El flujo es:

### 1. Registrar una cuenta

`POST /api/auth/register` — crea el `Business` y el `User` (rol `OWNER`) en una única operación, y devuelve un token de acceso.

```json
{
  "ownerFirstName": "Ana",
  "ownerLastName": "Gómez",
  "email": "ana@minegocio.com",
  "password": "unaContraseñaSegura123",
  "businessName": "Barbería Central",
  "businessPhone": "+595981000000",
  "businessTimezone": "America/Asuncion"
}
```

### 2. Iniciar sesión

`POST /api/auth/login`:

```json
{
  "email": "ana@minegocio.com",
  "password": "unaContraseñaSegura123"
}
```

Ambos endpoints devuelven un `ApiResponse<AuthResponse>` con esta forma:

```json
{
  "success": true,
  "message": "...",
  "data": {
    "accessToken": "<jwt>",
    "tokenType": "Bearer",
    "expiresIn": 3600000,
    "user": { "id": 1, "email": "ana@minegocio.com", "role": "OWNER", "businessId": 1, "businessName": "Barbería Central", "...": "..." }
  }
}
```

### 3. Usar el token en las siguientes peticiones

Todos los endpoints protegidos (todos salvo `/api/auth/register`, `/api/auth/login` y Swagger) requieren el header:

```
Authorization: Bearer <accessToken>
```

### Usarlo en Swagger UI

1. Abrir `/swagger-ui/index.html`.
2. Hacer clic en el botón **Authorize** (ícono de candado, arriba a la derecha).
3. Pegar únicamente el token (sin el prefijo `Bearer `) en el campo `bearerAuth` y confirmar.
4. A partir de ahí, todas las pruebas "Try it out" contra endpoints protegidos incluyen el header automáticamente.
