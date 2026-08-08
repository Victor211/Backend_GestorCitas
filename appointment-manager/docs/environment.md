# Variables de entorno

Referencia completa de todas las variables de entorno que consume el backend. Todas se cargan a través de `application.yml` (compartido) y de los perfiles `application-{dev,prod,test}.yml`; ver [`../.env.example`](../.env.example) para un archivo de ejemplo listo para copiar, y [`development.md`](development.md) / [`deployment.md`](deployment.md) para cómo usarlas en cada entorno.

Convención: **"¿Secreta?" = Sí** significa que el valor nunca debe commitearse, aparecer en logs, en el README, ni en `.env.example` con un valor real — solo como placeholder (`change-me`).

## Aplicación / perfil

| Variable | Descripción | Ejemplo | ¿Secreta? |
|---|---|---|---|
| `SPRING_PROFILES_ACTIVE` | Perfil de Spring activo. Determina qué `application-*.yml` se aplica sobre `application.yml`. Valores válidos: `dev`, `prod`, `test`. | `dev` | No |
| `SERVER_ADDRESS` | Interfaz de red en la que escucha el servidor embebido. En desarrollo, `0.0.0.0` (accesible desde cualquier interfaz local). En producción detrás de un reverse proxy (Apache), `127.0.0.1` para que solo el proxy local pueda alcanzarlo. | `0.0.0.0` (dev) / `127.0.0.1` (prod) | No |
| `SERVER_PORT` | Puerto HTTP del servidor embebido (Tomcat). | `8080` | No |
| `HIBERNATE_DDL_AUTO` | Estrategia de esquema de Hibernate: `update`, `validate`, `none`, `create`, `create-drop`. El proyecto no usa Flyway ni Liquibase; `update` es el valor por defecto y el usado históricamente en dev/prod. | `update` | No |

## Base de datos (PostgreSQL)

| Variable | Descripción | Ejemplo | ¿Secreta? |
|---|---|---|---|
| `DB_HOST` | Host del servidor PostgreSQL. | `localhost` (dev) / `db.interno.midominio.com` (prod) | No |
| `DB_PORT` | Puerto de PostgreSQL. | `5432` | No |
| `DB_NAME` | Nombre de la base de datos. | `appointment_manager` | No |
| `DB_USER` | Usuario de conexión a la base de datos. | `postgres` | No (pero tratar con cuidado) |
| `DB_PASSWORD` | Contraseña del usuario de base de datos. | — | **Sí** |

Ninguna de las cinco tiene valor por defecto en `application-{dev,prod,test}.yml`: si falta alguna, la aplicación no arranca (falla rápido, en vez de conectarse silenciosamente a un destino incorrecto).

## JWT

| Variable | Descripción | Ejemplo | ¿Secreta? |
|---|---|---|---|
| `JWT_SECRET` | Clave usada para firmar los tokens JWT (HMAC). Se recomienda un string aleatorio de 32+ caracteres. | — | **Sí** |
| `JWT_EXPIRATION` | Duración del access token, **en milisegundos** (única unidad usada en todo el proyecto). | `86400000` (24 h) | No |

## CORS

| Variable | Descripción | Ejemplo | ¿Secreta? |
|---|---|---|---|
| `CORS_ALLOWED_ORIGINS` | Lista de orígenes permitidos, separados por coma. Por defecto (si no se define) es `http://localhost:5173`. En producción debe apuntar al dominio real del frontend; nunca usar `*`. | `http://localhost:5173` (dev) / `https://app.midominio.com` (prod) | No |

## OpenAI (motor conversacional)

| Variable | Descripción | Ejemplo | ¿Secreta? |
|---|---|---|---|
| `OPENAI_API_KEY` | API key de OpenAI usada por `OpenAiProvider`. | — | **Sí** |
| `OPENAI_MODEL` | Modelo a usar. Tiene valor por defecto. | `gpt-4o-mini` | No |
| `OPENAI_BASE_URL` | Host base de la API de OpenAI. Tiene valor por defecto; solo se sobreescribe para usar un proxy/gateway propio. | `https://api.openai.com/v1` | No |

## WhatsApp Cloud API (Meta)

| Variable | Descripción | Ejemplo | ¿Secreta? |
|---|---|---|---|
| `WHATSAPP_ACCESS_TOKEN` | Access token de la app de Meta con permiso para enviar mensajes. | — | **Sí** |
| `WHATSAPP_VERIFY_TOKEN` | String elegido por el equipo, usado únicamente para que Meta verifique el webhook. | — | **Sí** |
| `WHATSAPP_APP_SECRET` | App secret de Meta, usado para validar la firma `X-Hub-Signature-256` de cada webhook entrante. | — | **Sí** |
| `WHATSAPP_GRAPH_API_VERSION` | Versión de la Graph API a usar. Sin valor por defecto (nunca hardcodeada en el código). | `v23.0` | No |
| `WHATSAPP_BASE_URL` | Host base de la Graph API. Sin valor por defecto. | `https://graph.facebook.com` | No |
| `WHATSAPP_CONNECT_TIMEOUT_MS` | Opcional. Timeout de conexión del cliente HTTP hacia Meta, en milisegundos. | `5000` | No |
| `WHATSAPP_READ_TIMEOUT_MS` | Opcional. Timeout de lectura del cliente HTTP hacia Meta, en milisegundos. | `15000` | No |

Ninguna variable de WhatsApp ni de OpenAI se expone jamás en respuestas HTTP (ver `GET /api/settings/business`, que solo devuelve un booleano `whatsappConfigured`, nunca los identificadores ni los secretos).
