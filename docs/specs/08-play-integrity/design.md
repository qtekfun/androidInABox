# Spec 08: Play Integrity. Diseño

Estado: Borrador
Fecha: 2026-10-29
Requisitos: [requirements.md](requirements.md). Rutas relativas a `engine/`.

## 1. Cómo funciona el veredicto (resumen, con fuentes)

Play Integrity (API de Google, ver la documentación de Android Developers sobre Play
Integrity) devuelve un token que la app envía a su servidor; el servidor lo descifra con
Google y obtiene `deviceIntegrity` (`MEETS_BASIC_INTEGRITY`, `MEETS_DEVICE_INTEGRITY`,
`MEETS_STRONG_INTEGRITY`), `appIntegrity` y `accountDetails`. El veredicto lo calcula
Google en servidor con señales del cliente (GMS/Play Store, DroidGuard, atestación de
clave en hardware). Un entorno virtual solo controla las señales de software, no la
atestación de hardware (sin verificar el detalle de cada nivel en 2026).

Niveles esperados, hipótesis a validar:

| Nivel | Qué exige (hipótesis) | Expectativa en el box |
|---|---|---|
| BASIC | Dispositivo plausible, sin manipulación evidente | Posible si hay GMS y propiedades coherentes |
| DEVICE | Dispositivo certificado, bootloader bloqueado | Improbable: Google valida contra el hardware real |
| STRONG | Atestación respaldada por hardware con parche reciente | No alcanzable; fuera de alcance |

## 2. Qué toca el motor (solo medición salvo mejoras)

| Elemento | Ruta | Relevancia |
|---|---|---|
| Propiedades falsas | `Bcore/src/main/cpp/Utils/VirtualSpoof.cpp` | Todo proceso que cargue `libblackbox.so` ve un Pixel 6 con Android 12 (build `SP1A.210812.015`, parche enero 2022) mientras el SDK real es 36 |
| `Build` en Java | clases del motor que fijan `Build.*` (revisar `BActivityThread`/`HookManager`; sin verificar el lugar exacto) | Incoherencia con el nativo |
| UID | `utils/UIDSpoofingHelper.java` | Todas las apps invitadas comparten UID con el host |
| GMS virtual | spec 03 | Sin él no hay API |
| DroidGuard | módulo dinámico de GMS | Su token es una señal del veredicto |

## 3. Matriz de experimentos (R08.6)

Se mide en el mismo dispositivo y versión de GMS, cambiando una variable por vez:

| ID | Variable | A | B |
|---|---|---|---|
| E1 | Fuera del box (OPPO, GMS real) | referencia | — |
| E2 | Dentro, propiedades actuales | Pixel 6 / A12 | — |
| E3 | Dentro, propiedades coherentes con el dispositivo real (flag de prueba) | modelo real | — |
| E4 | Dentro, sin DroidGuard operativo (si falla) | token ausente | token presente |
| E5 | Dentro, Play Store virtual presente o ausente | presente | ausente |

Para el móvil de ROM china no hay E1 (sin GMS real); se compara E2/E3 con E5.

La mejora opcional de E3 (R08.7) sería, solo si aporta, una opción por proceso GMS
para no falsear propiedades que no hacen falta (alcance: `VirtualSpoof.cpp`). Hay que
evaluar el efecto en apps que dependen del Pixel 6 falso (sin verificar el motivo
original del spoof).

## 4. Flujo de medición

1. Instalar la app comprobadora (elegida por el propietario) fuera y dentro del box.
2. Ejecutar al menos 3 veces por celda para ver variabilidad; registrar el más frecuente.
3. Guardar `adb logcat -d` filtrado, sin datos personales ni cuentas.
4. Rellenar `resultados.md` (plantilla en la tarea T08.1): columnas de R08.3.

Comandos de apoyo:

```bash
adb logcat -d | grep -iE "IntegrityService|StandardIntegrity|ExpressIntegrity|DroidGuard|PlayCore"
```

## 5. Riesgos

- Cada token pedido consume cuota/estado en la cuenta de prueba: usar cuenta
  secundaria (spec 03, R03.23).
- Resultados no deterministas por la red y el servidor (sin verificar).
- Cambios de Google en los niveles; la medición caduca. Se fecha cada fila.
- Riesgo de cuenta: pedir veredictos repetidos desde un entorno virtual puede levantar
  señales de fraude (sin verificar).
- Ética/legal: medir un veredicto no es evadirlo; no se aplicará nada de R08.9.

## 6. Lista de comprobación antes de una mejora (R08.9)

- No altera ni sustituye claves, certificados o cadenas de atestación.
- No intercepta ni modifica la respuesta del servidor de Google.
- No emula Keystore/TEE.
- Solo cambia coherencia de datos que ya son del box (propiedades visibles al
  proceso invitado).
- Se mide antes y después y se documenta el resultado, incluso si no mejora.

## 7. Alternativas

1. No medir: descartada, el paso h es del plan de pruebas.
2. Parchear hasta obtener `DEVICE`: descartada (R08.9, incompatible con el alcance).
3. Medir con una app propia que pida el token: más trabajo y requiere servidor; se
   usa la app del propietario.

## 8. Plan de pruebas

| Req. | Prueba |
|---|---|
| R08.1-R08.4 | Plan de pruebas h, tabla de resultados |
| R08.5 | grep de logcat de arriba |
| R08.6 | Matriz E1-E5 |
| R08.8 | Pasos b, c, d tras cambiar propiedades |
