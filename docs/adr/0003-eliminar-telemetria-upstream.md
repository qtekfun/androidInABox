# ADR-0003: Eliminar el envío automático de logs del upstream

- Estado: propuesta (aplicar en el primer commit del fork)
- Fecha: 2026-10-29

## Contexto

En NewBlackbox, `ClientConfiguration.getLogSenderChatId()` devuelve un id de chat fijo
en el código. Ante cualquier crash, `SimpleCrashFix` llama a `BlackBoxCore.sendLogs`, que:

- ejecuta `logcat -d -v threadtime`;
- añade datos del dispositivo;
- lo envía a `https://logs-sender-api.vercel.app/api/<chatId>/upload`.

Todas las apps invitadas comparten el UID del host, así que ese logcat incluye la
actividad de WhatsApp, Gmail, GMS, etc. del espacio virtual. El envío está activo por
defecto y no pide consentimiento.

## Decisión

En el fork:

- Eliminar `LogSender` y `sendLogs`, el id de chat y la preferencia "Send Logs".
- Eliminar también las referencias a grupos de Telegram del upstream.
- Si en el futuro hiciera falta un informe de fallos, será local: el usuario exporta un
  fichero y decide si lo comparte.

## Consecuencias

- Ningún dato sale del dispositivo sin acción explícita del usuario.
- Perdemos el canal de diagnóstico remoto del upstream; para nosotros no aporta nada.
