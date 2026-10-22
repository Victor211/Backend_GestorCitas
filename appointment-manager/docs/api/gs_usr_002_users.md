# GS-USR-002 — API de usuarios y roles

Todas las rutas requieren `Authorization: Bearer <JWT>` de un **OWNER**. El negocio siempre se
obtiene del JWT; ningún endpoint acepta `businessId`.

Respuestas con el envoltorio habitual: `{ "success", "message", "data", "timestamp" }`.

## POST /api/users — crear ADMIN o STAFF (201)

```json
{
  "firstName": "Luis",
  "lastName": "Gómez",
  "email": "luis@negocio.com",
  "password": "ContraseñaSegura123!",
  "role": "ADMIN"
}
```

Respuesta (`data`): `{ "id": 7, "firstName": "Luis", "lastName": "Gómez", "email": "luis@negocio.com", "role": "ADMIN", "active": true }`

Errores: 400 (validación, rol `OWNER` o desconocido, contraseña de 8–72 bytes), 401 (sin JWT),
403 (no OWNER), 409 (email ya registrado).

## GET /api/users?page=0&size=20 — listar usuarios del negocio

`data` es una página (`content`, `totalElements`, …) con los mismos campos que arriba. Orden fijo
(nombre, apellido, id); `sort` se ignora; `size` máximo 100.

## PATCH /api/users/{id}/status — activar/desactivar

```json
{ "active": false }
```

200 con el usuario actualizado. 404 si el id no existe **o pertenece a otro negocio**. 409 si
intenta desactivar su propia cuenta o al último OWNER activo.

## Login de los usuarios creados

`POST /api/auth/login` con su email y contraseña (sin cambios). Un usuario inactivo recibe 401 y su
JWT previo deja de funcionar de inmediato (el filtro JWT consulta `active` en cada request).

## Matriz de permisos

| Módulo / ruta                                                                 | OWNER | ADMIN | STAFF |
|-------------------------------------------------------------------------------|:-----:|:-----:|:-----:|
| `/api/auth/register`, `/api/auth/login`, `/api/webhooks/whatsapp`, swagger    | público | público | público |
| `/api/auth/me`                                                                | ✔ | ✔ | ✔ |
| `/api/users/**`                                                               | ✔ | ✘ | ✘ |
| `PUT /api/settings/business`                                                  | ✔ | ✘ | ✘ |
| `GET /api/settings/business`                                                  | ✔ | ✔ | ✘ |
| `/api/appointments`, `/customers`, `/employees`, `/schedules`, `/services`, `/dashboard`, `/conversations`, `/ai` | ✔ | ✔ | ✘ |
| Cualquier otra ruta autenticada no listada                                    | ✔ | ✘ | ✘ |

STAFF no tiene permisos operativos hasta que se defina qué puede hacer (decisión pendiente). Para
habilitarle algo, agregar la ruta/método en `SecurityConfig` con `hasAnyRole("OWNER","ADMIN","STAFF")`
antes de la regla `OPERATIONAL_ENDPOINTS`.
