# ADR-0001: Motor de virtualización basado en un fork de NewBlackbox

- Estado: aceptada (decisión del propietario, previa a la fase 0)
- Fecha: 2026-10-29

## Contexto

El proyecto necesita virtualización a nivel de app, sin root, en Android 13 a 17, con
GMS o microG dentro del espacio virtual y con las notificaciones de las apps invitadas
publicadas en el sistema real.

Alternativas evaluadas por el propietario:

| Opción | Licencia | Estado | Motivo de descarte |
|---|---|---|---|
| VirtualApp (asLody) | GPLv3 (versión abierta) | Abierta congelada en 2017; la actual es de pago | Sin soporte para Android moderno en la versión abierta |
| Twoyi | Apache 2.0 | Archivado en 2023 | Solo llega a Android 12; es un contenedor de ROM completa |
| NewBlackbox (ALEX5402) | Apache 2.0 | Activo (último commit julio de 2026) | Elegido |

## Decisión

Partir de un fork de NewBlackbox (https://github.com/ALEX5402/NewBlackbox). La fase 0
evalúa el upstream **sin modificar**, en el commit `89b5983` (rama por defecto a
29-10-2026), para decidir si se sigue, se pivota o se para.

## Consecuencias

- Heredamos la deuda técnica del linaje VirtualApp → BlackBox: proxies de binder por
  reflexión sobre APIs ocultas y hooks nativos dependientes de la versión de ART.
- La licencia Apache 2.0 permite un fork abierto. Hay que conservar los avisos
  (`LICENSE`, créditos de Dobby, xDL y FreeReflection).
- La app no incluirá GMS. El usuario instala GMS o microG por su cuenta.
