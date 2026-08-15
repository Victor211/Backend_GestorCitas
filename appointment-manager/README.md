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

## Configuración

El backend se configura íntegramente por variables de entorno — el mismo jar funciona en desarrollo local, en un servidor Linux o en cualquier otro entorno, cambiando únicamente esas variables (nunca recompilando). No se usan archivos `.properties`; toda la configuración vive en YAML (`application.yml` + un `application-{perfil}.yml` por entorno).

Perfil activo vía `SPRING_PROFILES_ACTIVE` (por defecto `dev` si no se define):

| Perfil | Uso | Particularidades |
|---|---|---|
| `dev` | Desarrollo local | `show-sql: true` |
| `prod` | Producción | `show-sql: false` |
| `test` | Suite de tests | No se usa en la práctica: los tests con `@SpringBootTest` reemplazan el datasource por H2 embebido vía `@AutoConfigureTestDatabase`, independientemente del perfil activo. Ver [docs/development.md](docs/development.md). |

Copiar [`.env.example`](.env.example) a `.env` y completar los valores para desarrollo local. El detalle completo de cada variable (con ejemplos y cuáles son secretas) está en **[docs/environment.md](docs/environment.md)**.

Guías paso a paso:

- **[docs/development.md](docs/development.md)** — cómo correr el backend y los tests localmente, cómo probar Swagger.
- **[docs/deployment.md](docs/deployment.md)** — despliegue conceptual en servidor Linux + Apache (reverse proxy) + systemd + HTTPS.

## Desarrollo local

Desde la carpeta `appointment-manager`, con las variables de entorno cargadas (ver [docs/development.md](docs/development.md)):

```bash
mvn spring-boot:run
```

La aplicación se levanta en `SERVER_ADDRESS:SERVER_PORT` (por defecto `0.0.0.0:8080`).

Swagger UI: `http://localhost:8080/swagger-ui/index.html`
API Docs (OpenAPI crudo): `http://localhost:8080/v3/api-docs`

## Build

```bash
mvn clean package
```

Resultado: `target/appointment-manager.jar`.

## Ejecución

```bash
java -jar target/appointment-manager.jar
```

Requiere las mismas variables de entorno que `mvn spring-boot:run`. Es el mismo artefacto que se despliega en producción (ver [docs/deployment.md](docs/deployment.md)); el comportamiento cambia solo según las variables de entorno presentes.

## Variables de entorno

Resumen rápido — ver **[docs/environment.md](docs/environment.md)** para la tabla completa con ejemplos y cuáles son secretas.

| Variable | Descripción |
|---|---|
| `SPRING_PROFILES_ACTIVE` | Perfil activo: `dev`, `prod` o `test`. Por defecto `dev`. |
| `SERVER_ADDRESS` / `SERVER_PORT` | Interfaz y puerto del servidor embebido. En desarrollo `SERVER_ADDRESS=0.0.0.0`; en producción, detrás de Apache, `SERVER_ADDRESS=127.0.0.1`. Por defecto `0.0.0.0` / `8080`. |
| `HIBERNATE_DDL_AUTO` | Estrategia de schema de Hibernate. Por defecto `update` (sin Flyway ni Liquibase). |
| `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD` | Conexión a PostgreSQL. Sin valores por defecto: si falta alguna, la aplicación no arranca. |
| `JWT_SECRET` | Clave usada para firmar los tokens JWT (HMAC). No debe commitearse un valor real al repositorio; se sugiere un string aleatorio largo (32+ caracteres) para desarrollo local. |
| `JWT_EXPIRATION` | Duración del access token, **expresada en milisegundos** (única unidad usada en todo el proyecto). Ejemplo: `3600000` = 1 hora. |

`JWT_SECRET` y `JWT_EXPIRATION` no tienen valor por defecto: si faltan, la aplicación no arranca.

## CORS

El backend expone su configuración de CORS mediante una única variable de entorno:

| Variable | Descripción |
|---|---|
| `CORS_ALLOWED_ORIGINS` | Lista de orígenes permitidos, separados por coma. Por defecto (si no se define) es `http://localhost:5173`. |

Ejemplo para desarrollo local (frontend en Vite, puertos 5173 y 5175):

```
CORS_ALLOWED_ORIGINS=http://localhost:5173,http://localhost:5175
```

La configuración se aplica globalmente a `/**` (no se usa `@CrossOrigin` en los controllers) y permite los métodos `GET, POST, PUT, PATCH, DELETE, OPTIONS` y los headers `Authorization, Content-Type, Accept, X-Requested-With, X-Hub-Signature-256`. `allowCredentials` está deshabilitado, por lo que no se envían cookies ni credenciales de sesión entre orígenes: la autenticación sigue siendo vía JWT en el header `Authorization`.

**En producción**, `CORS_ALLOWED_ORIGINS` debe configurarse con el dominio real del frontend (por ejemplo `https://app.mi-dominio.com`). Nunca debe usarse `*` como origen permitido.

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

### Motor conversacional (WhatsApp + IA)

El módulo `ai` (`ConversationServiceImpl`) orquesta la conversación completa. OpenAI solo interpreta
intención, extrae entidades (`SERVICE_NAME`, `EMPLOYEE_NAME`, `START_AT`, `APPOINTMENT_ID`) y redacta
texto libre para casos de charla general; el backend es la única fuente de verdad para identidad del
cliente, disponibilidad y confirmación.

**Identificación del cliente por `sender_phone`.** El teléfono que entrega el webhook de Meta
(`message.getFrom()`) se trata siempre como el dato confiable del cliente — nunca se le pregunta por
teléfono. Si no existe un `Customer` activo con ese teléfono en el `Business`, se le pide únicamente el
nombre (`CustomerIdentityResolver`) y se crea con `phone = sender_phone`. Si el nombre llega en un solo
token (ej. "María"), se acepta sin pedir apellido: `Customer.lastName` es una columna opcional, no hay
ninguna regla de negocio real que lo exija.

**Estado conversacional (`ConversationState`).** El proyecto no usa Redis ni ningún otro caché; la
memoria de la conversación se persiste en una tabla JPA más (`conversation_states`, una fila por
`business_id + customer_phone`, creada automáticamente por `ddl-auto: update` como el resto del
esquema). Guarda qué datos de una reserva ya se recopilaron (`pendingServiceId`, `pendingEmployeeId`,
`pendingDate`/`pendingStartAt`), si ya se saludó una vez (`greeted`) y si hay una propuesta esperando
confirmación (`stage = AWAITING_CONFIRMATION`). Una propuesta pendiente que nadie confirma ni rechaza
expira a los 20 minutos de inactividad (`ConversationStateStore.PENDING_CONFIRMATION_TTL`), verificado
de forma perezosa al leer el estado — no hay ningún job/scheduler adicional.

**Interpretación de fecha/hora (`BusinessDateTimeResolver`).** Toda referencia relativa ("hoy",
"mañana", "el próximo lunes") y toda desambiguación AM/PM se resuelve usando `Business.timezone`
(`ZoneId.of(business.getTimezone())`), nunca la zona del servidor ni UTC hardcodeado. Cuando el cliente
da una hora ambigua de 12 horas sin aclarar ("a las 4"), se intenta resolver en este orden: (1)
marcadores textuales ("de la tarde", "por la mañana", etc.); (2) si la fecha es hoy y la lectura AM ya
pasó pero la PM todavía no, se prefiere PM; (3) si sigue sin poder resolverse, el backend pregunta
explícitamente ("¿Te referís a las 04:00 o a las 16:00?") en vez de asumir. Si el cliente da la fecha y
la hora en mensajes separados ("hoy" → luego "a las 16"), la fecha ya resuelta se recuerda en
`ConversationState.pendingDate`/`pendingStartAt` y solo se pregunta el dato faltante.

**Confirmación obligatoria antes de crear.** Ningún `Appointment` se crea en el mismo turno en que se
completan los datos: al juntarse servicio + horario + profesional, el backend pasa la conversación a
`AWAITING_CONFIRMATION` y pregunta explícitamente. La clasificación de la respuesta ("sí"/"no"/"otra
hora"/etc.) es 100% determinística (`ConfirmationClassifier`, normaliza acentos/mayúsculas y compara
contra listas fijas de palabras) — la IA nunca decide si el cliente confirmó. Solo se vuelve a llamar a
la IA en ese estado cuando el mensaje no matchea ninguna palabra clave, para extraer un posible cambio
de dato (ej. "mejor a las 17"), y ahí se vuelve a presentar la confirmación con el dato actualizado.

**Revalidación al confirmar.** Entre la propuesta y el "sí" puede pasar tiempo real, así que la
confirmación positiva vuelve a invocar `AppointmentService.create(...)` (las mismas validaciones reales:
relación empleado-servicio, horario laboral, superposición, que no esté en el pasado) en vez de asumir
que el horario propuesto sigue libre. Si ya no está disponible, se responde brevemente y se vuelve a
`COLLECTING` sin crear nada.

**Idempotencia de WhatsApp.** La deduplicación por `external_message_id` en
`WhatsAppWebhookServiceImpl`/`whatsapp_inbound_events` ya existía y no se modificó: un evento de webhook
duplicado nunca vuelve a llegar a `ConversationService`, así que una confirmación duplicada no puede
producir dos citas por esa vía.

**Formato de moneda y estilo.** Todos los importes que ve el cliente (listado de servicios, contexto que
recibe la IA) se formatean con `GuaraniAmountFormatter` (`Gs. 65.000`, punto como separador de miles, sin
decimales) — la IA nunca ve ni escribe montos en dólares. El *system prompt* (`SystemPromptBuilder`)
separa reglas de presentación (brevedad, un saludo por conversación agregado por el backend, nunca
mencionar UTC/IDs/JSON) de reglas de negocio (disponibilidad, confirmación), que viven exclusivamente en
código Java y nunca dependen de que el modelo las respete.

### Advertencia

No incluyas `WHATSAPP_ACCESS_TOKEN`, `WHATSAPP_APP_SECRET` ni ningún otro secreto real en el repositorio, en el README, ni en commits. Configuralos únicamente como variables de entorno en tu entorno de ejecución.

## Dashboard API

`GET /api/dashboard` devuelve, en una única llamada, las métricas y próximas citas que necesita el Dashboard del frontend. Requiere JWT; el `Business` siempre se obtiene del token autenticado (nunca por query, path ni body), por lo que un usuario nunca puede ver datos de otro negocio.

```json
{
  "success": true,
  "message": "Operación exitosa",
  "data": {
    "todayAppointments": 3,
    "activeCustomers": 12,
    "activeEmployees": 4,
    "activeServices": 6,
    "upcomingAppointments": [
      {
        "id": 10,
        "customerId": 2,
        "customerName": "Cristian Benitez",
        "employeeId": 1,
        "employeeName": "Juan Gómez",
        "serviceId": 1,
        "serviceName": "Corte Premium",
        "startAt": "2026-08-05T13:00:00Z",
        "endAt": "2026-08-05T14:00:00Z",
        "status": "CONFIRMED"
      }
    ]
  }
}
```

**Métricas incluidas**: cantidad de citas de hoy, clientes activos, empleados activos, servicios activos y el listado de próximas citas.

**Zona horaria**: "hoy" se calcula con la zona horaria del `Business` (`business.timezone`), nunca con la zona del servidor ni asumiendo UTC. Si `business.timezone` no es un `ZoneId` válido, el endpoint responde con un error de negocio controlado en lugar de devolver métricas potencialmente incorrectas.

**Citas de hoy**: cuenta únicamente las citas del negocio con `startAt` dentro del día local actual y estado `PENDING` o `CONFIRMED` (excluye `CANCELLED`, `COMPLETED` y `NO_SHOW`).

**Próximas citas**: citas con `startAt` igual o posterior al momento actual y estado `PENDING` o `CONFIRMED`, ordenadas por `startAt` ascendente y limitadas a 5 resultados (no se pagina, es solo una vista resumida para el Dashboard).

Todas las fechas (`startAt`, `endAt`) se expresan como `Instant` en UTC, igual que en el resto de la API.

## Business Settings API

Endpoints para que la pantalla Settings del frontend consulte y actualice la configuración general del `Business` autenticado. Requieren JWT; el `Business` se obtiene exclusivamente a partir del usuario autenticado (nunca por `businessId` recibido en path, query o body), por lo que un usuario nunca puede ver ni modificar datos de otro negocio.

`GET /api/settings/business`

```json
{
  "success": true,
  "message": "Operación exitosa",
  "data": {
    "id": 1,
    "name": "Peluquería Elegance",
    "phone": "+595981123456",
    "email": "contacto@peluqueriaelegance.com",
    "address": "Asunción, Paraguay",
    "timezone": "America/Asuncion",
    "whatsappConfigured": false
  }
}
```

`PUT /api/settings/business`

```json
{
  "name": "Peluquería Elegance",
  "phone": "+595981123456",
  "email": "contacto@peluqueriaelegance.com",
  "address": "Asunción, Paraguay",
  "timezone": "America/Asuncion"
}
```

**Campos editables**: `name`, `phone`, `email`, `address`, `timezone`. `id`, `active`, `createdAt`, `updatedAt` y los identificadores de WhatsApp (`whatsappBusinessAccountId`, `whatsappPhoneNumberId`) no se pueden modificar desde este endpoint. `phone`, `email` y `address` son opcionales: enviarlos vacíos o solo con espacios los convierte a `null` (se centraliza en `StringNormalizer`, la misma utilidad ya usada por Employees).

**Validación de `timezone`**: es obligatorio y se valida con `ZoneId.of(timezone)` (sin lista hardcodeada de zonas). Si no es un `ZoneId` reconocido por `java.time`, responde `400` con el mensaje `"La zona horaria indicada no es válida"`. Al actualizarse, el resto del sistema (Dashboard, AI, interpretación de fechas de Appointments) usa naturalmente la nueva zona a través del `Business` persistido — las citas ya existentes no se recalculan ni se modifican (siguen almacenadas como `Instant` UTC).

**`whatsappConfigured`**: booleano calculado, `true` únicamente cuando el `Business` tiene un `whatsappPhoneNumberId` configurado (el identificador mínimo que usa el backend para enrutar webhooks y enviar mensajes). Este endpoint nunca expone `whatsappBusinessAccountId` ni `whatsappPhoneNumberId` como valores editables, y jamás devuelve secretos: `WHATSAPP_ACCESS_TOKEN`, `WHATSAPP_APP_SECRET`, `WHATSAPP_VERIFY_TOKEN` ni `OPENAI_API_KEY` no se gestionan ni se exponen desde esta API — siguen siendo exclusivamente variables de entorno del backend.

**Aislamiento multi-tenant**: el flujo es siempre JWT → usuario autenticado → `businessId` → `Business`; el request de actualización no tiene ningún campo `businessId`, por lo que es estructuralmente imposible modificar el negocio de otro usuario.
