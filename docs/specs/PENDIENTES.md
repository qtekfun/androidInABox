# Decisiones pendientes del propietario

Recopiladas al escribir las specs (2026-10-29). Cada una indica a qué spec bloquea.
Para responder basta con el número y la respuesta. Las marcadas ★ bloquean el
arranque de M1, M2 o M3.

## Alcance y forma de trabajar

1. ★ **Spike de GMS real (03):** 10 días con corte anticipado al día 5. Si sale
   "pivotar", el paso a microG se decide con un ADR. ¿De acuerdo?
2. **Android Auto (07):** el spike empieza por el simulador de unidad principal (DHU)
   vía `adb forward`. Si no proyecta, ¿aceptas cerrar Android Auto como "abandonar"
   (unos 2 días de coste) y documentar alternativas?
3. **Puente de notificaciones del sistema real hacia el box (06):** sirve para que la
   app del reloj, dentro del box, vea también las notificaciones de fuera. Sería
   opcional, desactivado por defecto y con lista blanca. ¿Lo incluimos?
4. **Avisos legales (03):** usar GMS en un entorno no certificado puede ir contra las
   condiciones de Google. ¿Mostramos el aviso y recomendamos una cuenta secundaria
   (R03.23)?

## Dispositivos

5. ★ **Móviles con ROM china:** marca, modelo y ROM de los que tienen Android 16 y 17.
   Hace falta para las pruebas y para confirmar las pantallas de autoarranque y
   batería de cada fabricante (04).
6. **Reloj Wear OS (06):** ¿qué modelo llegará? Si es de Xiaomi, OPPO u OnePlus, su app
   propia quizá funcione sin el box (sin verificar).
7. **Android Auto (07):** ¿hay coche o unidad principal? Si no, ¿vale un segundo
   Android como unidad de prueba?
8. **GMS de Android 17 (03):** ¿has extraído ya el `GmsCore` del APEX de MindTheGapps
   17, o lo hago yo como tarea?

## Comportamiento

9. **Nombre del perfil (02):** ¿"Perfil N" fijo o un nombre editable ("Trabajo",
   "Personal")?
10. **Título de la notificación (02, R02.6):** si el reloj no muestra el subtexto,
    ¿activamos la opción de poner el nombre de la app en el título?
11. **JniHook roto (05):** si falla la autocomprobación de la redirección de IO,
    ¿solo un aviso visible, o bloquear el arranque de apps invitadas?
12. **Suplantación de propiedades (05/08):** hoy todo proceso ve un Pixel 6 con
    Android 12. Propuesta: desactivada por defecto, también para las apps de Google, y
    en 08 probar un perfil coherente con el dispositivo real. ¿De acuerdo?
13. **Clones con splits (01):** las apps clonadas del sistema usarían los APK del
    sistema sin copiarlos. Si actualizas o desinstalas la app fuera, el clon cambia o
    se rompe. La alternativa es copiarlos, con más espacio. ¿Cuál prefieres?
14. **`.obb` de los `.xapk` (01):** ¿fuera de la v1?

## Técnicas (puedo decidirlas yo si no tienes preferencia)

15. **Cambios de AIDL en el motor (02/04):** para el keep-alive y la entrega de
    PendingIntents. Solo si el spike T02.2 lo exige.
16. **Medición de push (04):** `tools/pushprobe` necesitaría un proyecto Firebase propio,
    con su configuración fuera del repo. ¿Lo tienes, o medimos solo con WhatsApp y
    Gmail?
17. **App comprobadora de Play Integrity (08):** ¿cuál usamos? La eliges tú.
18. **Orden de 05:** las tareas de spoof y limpieza (T05.10–T05.14) no bloquean M1.
    Propuesta: hacerlas después de 01.

## Interfaz (spec 09)

19. **Estilo y tecnología del rediseño:** propuesta por defecto:
    - Jetpack Compose con Material 3 y color dinámico (Material You).
    - Modo claro y oscuro.
    - Pantallas: perfiles → apps de cada perfil → ajustes de la app (GMS, spoof,
      notificaciones), más un asistente de primera configuración (permisos,
      batería, autoarranque) y la gestión de GMS.

    ¿Tienes referencias visuales (GBox, GSpace, Shelter, Island…) o algo que quieras
    evitar? ¿Cambiamos también el nombre visible "BlackBox" por "androidInABox" u
    otro?
