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
- Motor conversacional propio (OpenAI vía RestClient, aislado detrás de `AiProvider`)
- Integración con WhatsApp Cloud API de Meta (vía RestClient, aislado detrás de `WhatsAppClient`)

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

Todos los endpoints protegidos (todos salvo `/api/auth/register`, `/api/auth/login`, los webhooks de WhatsApp y Swagger) requieren el header:

```
Authorization: Bearer <accessToken>
```

### Usarlo en Swagger UI

1. Abrir `/swagger-ui/index.html`.
2. Hacer clic en el botón **Authorize** (ícono de candado, arriba a la derecha).
3. Pegar únicamente el token (sin el prefijo `Bearer `) en el campo `bearerAuth` y confirmar.
4. A partir de ahí, todas las pruebas "Try it out" contra endpoints protegidos incluyen el header automáticamente.

## Integración con WhatsApp (Cloud API de Meta)

### Variables de entorno

| Variable | Descripción |
|---|---|
| `WHATSAPP_ACCESS_TOKEN` | Access token de la app de Meta con permiso para enviar mensajes. Sin valor por defecto. |
| `WHATSAPP_VERIFY_TOKEN` | String elegido por vos, usado únicamente para que Meta verifique el webhook (paso "Verify and Save" en la consola de Meta). Sin valor por defecto. |
| `WHATSAPP_APP_SECRET` | App secret de la app de Meta, usado para validar la firma `X-Hub-Signature-256` de cada webhook entrante. Sin valor por defecto. |
| `WHATSAPP_GRAPH_API_VERSION` | Versión de la Graph API a usar, por ejemplo `v21.0`. Sin valor por defecto (nunca hardcodeada en el código). |
| `WHATSAPP_BASE_URL` | Host base de la Graph API. Ejemplo conceptual: `https://graph.facebook.com`. Sin valor por defecto. |
| `WHATSAPP_CONNECT_TIMEOUT_MS` / `WHATSAPP_READ_TIMEOUT_MS` | Opcionales. Timeouts del cliente HTTP hacia Meta, en milisegundos. Por defecto `5000` / `15000`. |

**Ninguna de estas variables tiene un valor real en este repositorio.** No subas tokens ni secrets reales a ningún archivo versionado.

### Cómo configurar el webhook en Meta

1. En la consola de Meta for Developers, dentro de tu app de WhatsApp, ir a **WhatsApp → Configuration → Webhook**.
2. **Callback URL**: la URL pública donde corre este backend, seguida de `/api/webhooks/whatsapp`. Ejemplo conceptual:
   ```
   https://tu-dominio-publico.com/api/webhooks/whatsapp
   ```
   (En desarrollo local, se necesita un túnel público — por ejemplo ngrok — hacia el puerto `8080`.)
3. **Verify token**: el mismo valor configurado en `WHATSAPP_VERIFY_TOKEN`.
4. Al guardar, Meta hace `GET /api/webhooks/whatsapp?hub.mode=subscribe&hub.verify_token=...&hub.challenge=...`; el backend responde con `hub.challenge` en texto plano si el token coincide.
5. **Suscribirse al campo `messages`** dentro de "Webhook fields", para que Meta empiece a enviar los mensajes entrantes vía `POST /api/webhooks/whatsapp`.

### Cómo configurar `whatsappPhoneNumberId` para un Business

Por el momento no existe una pantalla/endpoint dedicado para esto (fuera de alcance del MVP). El campo `whatsappPhoneNumberId` (y opcionalmente `whatsappBusinessAccountId`) se completa directamente sobre la fila del `Business` correspondiente en la base de datos, con el `phone_number_id` que aparece en **WhatsApp → API Setup** dentro de la consola de Meta. Ese es el identificador que Meta envía en cada webhook y que el backend usa para determinar a qué negocio pertenece el mensaje — nunca se recibe un `businessId` directamente desde WhatsApp.

### Cómo probar con el número de prueba de Meta

1. En **WhatsApp → API Setup**, Meta provee un número de prueba y hasta 5 números de destino verificados.
2. Copiar el `phone_number_id` del número de prueba y guardarlo como `whatsappPhoneNumberId` del `Business` que quieras probar (ver punto anterior).
3. Desde uno de los números de destino verificados, enviar un mensaje de texto al número de prueba.
4. Meta entrega el evento a `POST /api/webhooks/whatsapp`; el backend lo valida, lo pasa al motor conversacional (`ConversationService`) y responde usando el mismo número de prueba mediante `MetaWhatsAppClient`.

### Advertencia

No incluyas `WHATSAPP_ACCESS_TOKEN`, `WHATSAPP_APP_SECRET` ni ningún otro secreto real en el repositorio, en el README, ni en commits. Configuralos únicamente como variables de entorno en tu entorno de ejecución.
