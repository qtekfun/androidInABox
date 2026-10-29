# Spec 08: notas de implementación (medición)

Estado: Borrador. Fecha: 2026-10-29. No sustituye a `resultados.md`.

## Qué hay en el motor (rama feat/05-08-spoof-integrity)

- El perfil falso de `VirtualSpoof.cpp` (Pixel 6 / Android 12) ya no se instala al cargar `libblackbox.so`.
  Está apagado por defecto: host, `:black` e invitadas ven los valores reales del dispositivo.
  Esa situación es la variante "propiedades coherentes" de E3 (R08.7), sin código extra.
- Se activa por app y por perfil con el interruptor de la pantalla de ajustes de la app
  ("Suplantar propiedades del dispositivo"), o por código con
  `BlackBoxCore.get().setPropertySpoofEnabled(paquete, usuario, true)`. Se aplica al reiniciar la app.
  Es el caso E2 (propiedades "actuales" del Pixel 6).
- Volcado bajo demanda del estado visible por el invitado (`IntegrityProbe`): `Build.*`, `SystemProperties`
  y estado del arranque verificado. No cambia nada.

## Pasos de medición (necesitan dispositivo y spec 03)

1. E3 (coherente): sin tocar el interruptor, instalar la app comprobadora en el box y pedir el veredicto.
2. E2 (Pixel 6): activar el interruptor de esa app, cerrarla y abrirla de nuevo; pedir el veredicto.
3. Para volcar lo que ve el invitado antes de abrirla:

```bash
adb shell setprop debug.aiab.dump 1
# abrir la app invitada
adb logcat -d -s AiabProbe
adb shell setprop debug.aiab.dump 0
```

4. Comparar el volcado con E1 (fuera del box) y registrar la fila en `resultados.md` con fecha.
5. Aviso: `Build.MODEL` y `Build.FINGERPRINT` ya vienen cargados desde el zigote, por lo que el interruptor
   probablemente solo afecte a lecturas nativas con `__system_property_get` (ver tabla de
   `05-mantenimiento-motor/design.md` §5; sigue pendiente de medir en dispositivo, T05.10).

## Pendiente

- Medir en dispositivo (T08.2 a T08.7); el código de T08.7 queda hecho.
- Análisis y recomendación (T08.8).
