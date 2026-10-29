# Spike GMS real: cuaderno de evidencias

Registro cronológico de experimentos del spike (spec 03). Cada entrada: qué se
probó, con qué build y qué se vio. Los logs completos se quedan fuera del repo, porque
pueden contener datos personales.

## Experimento 1: GMS de fábrica (APK único) en el OPPO, 2026-10-29

- **Dispositivo:** OPPO CPH2841, Android 16, ROM EEA (con GMS en el sistema real).
- **Motor:** `aiab-main` con la spec 05 (`0970078`), build debug.
- **APKs de fábrica** extraídos del propio OPPO (`/product/priv-app`). Firmas
  verificadas contra el GMS del sistema, ver [fuentes-gms.md](../../investigacion/fuentes-gms.md).
- **Instalación** en el box, en orden: GSF, GmsCore (224 MB) y Phonesky (100 MB).
  Las tres se instalan correctamente.

### Resultados

1. **Play Store no arranca, y no es por los splits.**
   - Error: `ClassNotFoundException: com.android.vending.AssetBrowserActivity`.
   - En el manifiesto de Phonesky, `AssetBrowserActivity` es un `<activity-alias>`
     con `targetActivity="com.google.android.finsky.activities.MainActivity"`.
   - El motor instancia el alias en vez del destino: es un **bug del motor con los
     activity-alias** que afecta a cualquier app que los use como lanzador.
   - Se corrige en una rama aparte (`fix/activity-alias`).
2. **GMS (procesos `:p2` y `:p3`) sigue muriendo** con
   `FATAL:external/cronet/stable/base/android/jni_android.cc:220 Uncaught Java
   exception in native code` (SIGTRAP).
   - **La hipótesis "faltan los splits" queda descartada:** con el APK único de
     fábrica ocurre igual.
   - Pistas en el log del proceso GMS antes del crash:
     - `GmsModuleFndr: NullPointerException: ... android.app.Application.getClassLoader()
       on a null object reference`. El buscador de módulos (Dynamite/Chimera) recibe
       un `Application` nulo. **Hipótesis:** el motor no fija
       `ActivityThread.mInitialApplication` / `currentApplication()` en el proceso
       invitado cuando el arranque es por content provider o servicio.
     - `WorkManagerProxy: ... IllegalStateException: WorkManager is not initialized
       properly`. El proxy propio del motor (`fake/service/WorkManagerProxy.java`)
       interfiere con GMS.
     - `FlagStore` / `AsyncOperation`: `Empty (package not registered?) or malformed
       snapshot token`. Phenotype (configuración por flags) no registra el paquete;
       puede estar relacionado con el desvío de providers al GMS real.
     - `ChimeraCfgMgr: Failed to read module config ... app_chimera/current_config.fb:
       ENOENT`. Es normal en el primer arranque.
3. **El desvío de providers al GMS real sigue activo:** `Permission Denial: opening
   provider com.google.android.gms.common.appdoctor.AppDoctorProvider ... requires
   INTERNAL_BROADCAST`. Es la tarea de la spec 03 de resolver los providers de GMS
   contra el GMS virtual.

### Siguiente

- Aplicar el fix de activity-alias y repetir con Play Store.
- Investigar `Application` nulo en procesos arrancados por provider (`BActivityThread`)
  y el impacto de `WorkManagerProxy` sobre GMS.
- Desactivar el desvío de providers de GMS cuando GMS está instalado en el box
  (diseño de la spec 03).

## Experimento 2: con los arreglos de alias, contexto de aplicación e icono, 2026-10-29

- **Motor:** `aiab-main` con las specs 05, 01 y 02, más los fixes de activity-alias,
  `Application` del invitado (`mApplication`/`mAllApplications`, sin `WorkManagerProxy`)
  y la recursión de `areNotificationsEnabled`.
- **Prueba:** abrir Play Store en el box y observar 60 s.

### Resultados

| Señal | Experimento 1 | Experimento 2 |
|---|---|---|
| `Unable to instantiate ... AssetBrowserActivity` (alias) | sí | **no** |
| `GmsModuleFndr: NullPointerException ... Application.getClassLoader()` | sí | **no** |
| `WorkManager is not initialized properly` | sí | **no** |
| `jni_android.cc` (cronet) en procesos de **GMS** | sí | **no** |
| `ClassCastException: Cannot cast qoy to aqpi/ariq` en `com.android.vending:background` | sí | sí |
| `Permission Denial: opening provider ...AppDoctorProvider` | sí | sí |

### Conclusiones

- **El crash de cronet era un efecto secundario.** Lo provocaba una excepción Java no
  capturada en el mismo proceso. Ahora solo aparece en el proceso `:background` de
  Play Store, por el `ClassCastException`. Los procesos de GMS ya no mueren.
- **Causa del `ClassCastException`:** Play Store declara
  `android:appComponentFactory="...PhoneskyAppComponentFactory"`, y el motor lo
  ignoraba. `LoadedApk` crea la factoría a partir del `ApplicationInfo`; el motor
  sustituye el `ApplicationInfo` después, y la factoría queda la de por defecto.
  Afecta a cualquier app moderna con factoría propia. Se corrige en
  `fix/app-component-factory`.
- **Pendiente** (diseño de la spec 03): resolver los providers de GMS contra el GMS
  virtual en lugar del sistema real.
