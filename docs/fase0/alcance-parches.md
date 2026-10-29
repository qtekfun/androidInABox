# Fase 0: alcance de los parches bloqueantes

Rutas relativas a `Bcore/src/main/java/top/niunaijun/blackbox/`. Líneas aproximadas, verificadas en el árbol actual.

## 1. Split APKs

**Puntos de parche**
- `core/system/pm/BPackageManagerService.installPackageAsUserLocked` (~648-720): hoy copia un único `apkFile` (URI -> `cache/UUID.apk`), llama a `getPackageArchiveInfo` y `parserApk(file)` (~731, `PackageParserCompat.createParser/parsePackage/collectCertificates`).
- `core/system/pm/installer/CopyExecutor.exec`: solo `NativeUtils.copyNativeLib(baseCodePath, appLibDir)` y copia de `baseCodePath` a `BEnvironment.getBaseApkDir`. `CreatePackageExecutor` crea dir y lib dir.
- `core/system/pm/PackageManagerCompat.generateApplicationInfo` (~280-312): fija `sourceDir/publicSourceDir = p.baseCodePath`; no rellena `splitSourceDirs/splitPublicSourceDirs/splitNames`.
- `fake/frameworks/BPackageManager.java` 681 y 830: `splitNames = {}` solo en objetos *fallback*. Importante: el camino real (BPackageManagerService) ni siquiera los rellena, así que lo de 681/830 es secundario.
- `app/BActivityThread` ~360-380: `createPackageContext(applicationInfo)` + `BRLoadedApk._set_mApplicationInfo`. Ahí hay que garantizar que el LoadedApk se construya con el ApplicationInfo que ya lleva splits (y reconstruir classloader/resources, ver abajo). `getClassLoader` en ~637 usa `PathClassLoader(sourceDir)`: solo base, parchear a incluir splits (solo si ese camino se usa).
- API/UI: `BlackBoxCore.installPackageAsUser(File/Uri)` y las entradas de `BPackageManager`/AIDL: añadir `installPackageSplits(String[] paths|dir)`; para `.apks/.xapk/.apkm` descomprimir a un dir.

**Diseño mínimo**
1. Aceptar directorio o lista de APKs (base + `split_config.*` + `split_*`). Parsear el base con `parsePackage(file)` y los splits con `parseBaseApk`/`PackageParser.parsePackage(dir)` (el hidden `PackageParser` acepta un dir "cluster" y llena `baseCodePath`, `splitCodePaths`, `splitNames`).
2. Copiar todos a `BEnvironment.getAppDir(pkg)/` (`base.apk`, `split_*.apk`) y guardar en `BPackage`/`BPackageSettings` (serializable: añadir campos `splitCodePaths`, `splitNames`).
3. Extraer libs nativas del base y de los `config.<abi>` (ver pitfalls) a `appLibDir`.
4. En `generateApplicationInfo`: `ai.splitSourceDirs = ai.splitPublicSourceDirs = splitCodePaths`, `ai.splitNames`, y en `generatePackageInfo` `pi.splitNames`, `pi.splitRevisionCodes`.
5. Asegurar que el LoadedApk del guest se crea con ese ApplicationInfo (el `ClassLoader` y `Resources` se construyen a partir de `mApplicationInfo.splitSourceDirs`; si se hace `_set_mApplicationInfo` tarde, hay que recrear `mClassLoader`/`mResources`, o crear `createPackageContext` ya con el AI correcto).
6. Validación ABI: `AbiUtils.isSupport(apkFile)` debe mirar también los config splits.

**Pitfalls**
- `splitNames` y `splitSourceDirs` deben ser arrays de igual longitud y mismo orden; dependencias `isFeatureSplit`/`requiredSplit` (`split_*` con `android:isSplitRequired`) afectan al orden del classloader.
- Config splits (`split_config.arm64_v8a.apk`, `.xxhdpi`, `.es`): las libs viven en `lib/<abi>/` de ese split; `NativeUtils.copyNativeLib` hoy solo abre un zip. También hay que elegir ABI primaria coherente (`primaryCpuAbi`) y `nativeLibraryDir`.
- Resources: sin los config splits de densidad/idioma falla `Resources$NotFoundException` o se ven assets vacíos; `LoadedApk.mSplitResDirs` (en Android 12+ `getSplitResDirs`) debe contener todos.
- `IOCore` redirige `/data/data/<pkg>/lib` (IOCore:115); hay que asegurar que `nativeLibraryDir` apunte al dir rellenado.
- Firma: todos los splits deben compartir firma; `collectCertificates` sobre el base basta, pero verificar.
- Reinstalación/actualización: `CreatePackageExecutor` borra todo el dir; ok, pero no dejar splits huérfanos.
- Android 16: `PackageParser` oculto es restringido; revisar que las reflexiones (`BRPackageParser*`) sigan funcionando en el dispositivo.

**Esfuerzo:** 6-8 días-dev. **Confianza:** media (el parseo y copia son directos; classloader/recursos en LoadedApk en A16 y libs desde config splits son lo incierto).

## 2. PendingIntent broadcast/service

**Puntos de parche**
- `fake/service/IActivityManagerProxy.GetIntentSender.hook` (~495-525): el `switch(type)` solo trata `INTENT_SENDER_ACTIVITY`. Añadir `INTENT_SENDER_BROADCAST` (1) y `INTENT_SENDER_SERVICE` (4) (+ `FOREGROUND_SERVICE` 5, `ACTIVITY_RESULT` no).
- Helpers a crear: `ProxyPendingRecord.saveStub` (proxy/record) ya sirve para serializar target + userId.
- Receptores en el host: `proxy/ProxyBroadcastReceiver` (ya existe, usa `ProxyBroadcastRecord`) y `proxy/ProxyService` (P%d). Hace falta un receptor/servicio de *pending* nuevo, p.ej. `ProxyPendingReceiver` y `ProxyPendingService`, declarados en el manifest del host (y en `ProxyManifest`).
- `getIntentSenderWithSourceToken/WithFeature` heredan de `GetIntentSender` (560-566): índice del array de intents (`getIntentsIndex`, fallback fijo 6/5) debe validarse en A14-16 (con feature id el índice cambia; ya se busca por `instanceof Intent[]`, bien).

**Diseño mínimo**
1. En broadcast: `shadow = new Intent().setComponent(host/ProxyPendingReceiver)`, `saveStub(shadow, intent, userId)`; el `intent` original lleva el componente/acción del guest. Para service: igual hacia `ProxyPendingService` (o un receiver que luego llame `BActivityManager.startService`).
2. En el receptor del host: `ProxyPendingRecord.create(getIntent())`; **combinar el fill-in**: el sistema entrega al shadow el intent ya fusionado con el fill-in (extras de RemoteInput `android.remoteinput.resultsData`, ClipData, `ClipDescription`), por lo que hay que copiar extras/ClipData/action/data del intent recibido (menos los `_B_|_P_*`) sobre `mTarget` (`mTarget.fillIn(received, 0)` o `putExtras`), y reenviar con `BActivityManager.scheduleBroadcastReceiver(...)` / `startService(...)` al userId guardado.
3. Flags: PendingIntent sin FLAG_MUTABLE no recibe fill-in; RemoteInput exige MUTABLE (A12+). El guest ya pasa MUTABLE; no tocar. Pero el *shadow* debe conservar `flags`. Si el guest usa `FLAG_IMMUTABLE`, no reescribir extras.

**Pitfalls**
- Colisión de PendingIntent: el sistema identifica por `Intent.filterEquals` (acción/data/type/component/categories, sin extras) + requestCode. Todos los shadows tendrían el mismo componente y filtro -> se fusionan/actualizan entre apps/usuarios. Solución: poner en el shadow un `data = Uri("bbpi://<userId>/<pkg>/<requestCode>/<hash(target.filterHashCode)>")` o `setAction` único; es el pitfall principal.
- Para activity ya existente pasa lo mismo (revisar el caso actual).
- `FLAG_UPDATE_CURRENT`/`NO_CREATE` (`PendingIntent.getBroadcast(..., NO_CREATE)` usado para "ya existe una alarma") deben seguir funcionando: con data único, sí.
- Servicios en A12+: lanzar un FGS desde background de un PendingIntent de servicio está restringido; mejor usar receptor y luego `BActivityManager.startService`.
- ClipData con `content://` URIs: los permisos de URI granted al shadow no pasan al guest; usar `FLAG_GRANT_*` ya incluidos en el intent recibido.
- Binder: Intent con extras Parcelable de clases del guest dentro de `_B_|_P_target_` se desmarshala en el host y falla (ClassNotFound) en el classloader del host. Hay que serializar el target a `Bundle`/bytes (`marshall`) y desmarshalar dentro del proceso guest, o usar `ParceledListSlice`-like con el classloader del guest. Esto ya afecta a `ProxyPendingRecord` actual.
- `getPackageForIntentSender`/`getUidForIntentSender` ya hookeados; mantener el registro (`BActivityManager.getIntentSender`).
- Las notificaciones reescriben también `contentIntent`, `actions`, `RemoteInput` en el proceso guest o en `BNotificationManagerService`; confirmar que todo pasa por `getIntentSender` en el proceso guest.

**Esfuerzo:** 4-5 días. **Confianza:** media-alta.

## 3. Presentación de notificaciones

**Puntos de parche** (`core/system/notification/BNotificationManagerService.java`)
- `enqueueNotificationWithTag` (170-195): ignora `tag`; `mRealNotificationManager.notify(notificationId, notification)` -> el ID es hash(pkg+userId+id) (`getNotificationId` ~300). Usar `notify(tag', id', ...)` con `tag' = "bb:<userId>:<pkg>:<tag>"` o seguir con hash pero incluyendo `tag` en el hash, y registrar `(tag,id)` para `cancelNotificationWithTag` (197-208) y `mIds` (que es Set<Integer>: cambiar a un record tag+id).
- `getBlackChannelId` (~275): `channelId + CHANNEL_BLACK + userId`; añadir paquete: `pkg + sep + userId + sep + channelId`. `getRealChannelId` (split) debe revertir (hoy `split(CHANNEL_BLACK)[0]`; con IDs que contengan el separador se rompe: usar un formato parseable y mapa explícito). Los mismos cambios en grupos y en `handleNotificationChannel`/`createNotificationChannel` (103-135). Ojo: los ids de canal ya creados cambiarán -> los usuarios pierden ajustes (migración opcional).
- Icono pequeño: `Notification.mSmallIcon` es `Icon.createWithResource(guestPkg, resId)` o `smallIcon` int. Resolver en el proceso guest o aquí: cargar el drawable con el `Resources` del guest (`BPackageManager.getResourcesForApplication`/`createPackageContext` con el APK virtual), convertir a `Bitmap` -> `Icon.createWithBitmap` (usar versión monocroma/alpha, A16 puede mostrar mal bitmaps a color: mejor usar alpha-only o tintar), y asignar a `notification.setSmallIcon(icon)` (hidden `mSmallIcon`). También `largeIcon` si es resource, y los iconos de `actions`.
- Nombre de app: `Notification.Builder.EXTRA_SUBSTITUTE_APP_NAME` ("android.substName") en `notification.extras` con label del guest + `[clone N]`; requiere permiso `SUBSTITUTE_NOTIFICATION_APP_NAME` (el host no lo tiene normalmente, no es otorgable a apps normales). Alternativa: `setSubText(label)` o `setContentInfo`; `setGroup` con summary por guest. Perfil/usuario: añadir sufijo en `subText`.
- Hook en el cliente (si existe `INotificationManagerProxy`) para hacer el trabajo de iconos en el proceso guest, donde sí hay Resources.

**Pitfalls:** tamaño binder con bitmaps grandes (escalar a 96px); ocultar color accent; icono ya "host" cuando `Icon.TYPE_RESOURCE` con `pkg` guest: el SystemUI intenta cargar de ese paquete (falla). `getNotificationChannels` devuelve canales con id real (resetNotificationChannel) -> mantener bidireccional. `deletePackageNotification` (~255) usa mismo mapeo.

**Esfuerzo:** 3-4 días. **Confianza:** media-alta (substName limitado por permiso).

## 4. GMS / microG y spoofing de firma

**Puntos de parche**
- Providers: `fake/service/IActivityManagerProxy.GetContentProvider` (~147-160): la rama `contains("com.google.android.gms") || com.android.vending || gsf` hace `method.invoke(who,args)` al sistema real. Para microG: condicionar con un flag `BlackBoxCore.isMicroGMode()` y si el paquete está instalado virtualmente (`resolveContentProvider(auth, ...)` != null) dejar pasar al `else` (provider virtual) en vez de la rama real. Idem `ContentProviderDelegate.update`. Revisar también `getContentProviderExternal`, `ContentResolver.acquireProvider` y `IContentProvider` de `settings`/`media`.
- Servicios/bind: `bindService` a `com.google.android.gms` hacia el real (revisar `IActivityManagerProxy.BindService`, `startService`) y `IPackageManager.resolveService/queryIntentServices` para que resuelvan al GMS virtual.
- Núcleo: `core/GmsCore.java` (solo clona GMS del host): añadir instalación de microG (`com.google.android.gms`, `com.android.vending` fake, `com.google.android.gsf`) como apps virtuales.
- Spoofing de firma: `core/system/pm/PackageManagerCompat.generatePackageInfo(BPackage,...)` (~56-215): ahí se construye el PackageInfo del guest. Hoy `GET_SIGNATURES` -> `base.signatures` del PM real si existe, si no `p.mSignatures`; `GET_SIGNING_CERTIFICATES` (~205) -> `base.signingInfo` o signingDetails sintético. Parchear: si `p.packageName` es `com.google.android.gms` (o `com.android.vending`, `gsf`) y el paquete virtual está marcado microG (p.ej. metadata `fake-signature` de microG o simplemente lista blanca), reemplazar por la firma de Google (cert fijo, hex 308204a8... de GMS) tanto en `pi.signatures` como en `pi.signingInfo` (construir `SigningDetails` con `signatures` y `pastSigningCertificates`).
- Entrada de flags: `fake/service/IPackageManagerProxy.GetPackageInfo` (129-): usa `MethodParameterUtils.toInt(args[1])`, que ya convierte Long (A13+ `getPackageInfo(String,long,int)`); GET_SIGNING_CERTIFICATES (0x08000000) cabe en int pero `PackageManager.PackageInfoFlags` de 33+ llegan como long en AIDL: ok. Sin embargo `getPackageInfo` para `com.android.vending` devuelve `createFakeGooglePlayServicesPackageInfo()` (línea 137): también debe llevar firma spoof. Cubrir `getPackageArchiveInfo`/`getInstalledPackages`/`checkSignatures`/`compareSignatures`/`getPackageUid` (checkSignatures SIGNATURE_MATCH entre gms y apps Google), y `queryIntentActivities` paths que construyen PackageInfo.
- `BPackageManagerService.getPackageInfo` (266-290) y `getInstalledPackages` (~375) llegan a `generatePackageInfo`: un único punto, bien.

**Pitfalls**
- Spoof solo cuando el *llamante* verifique (apps que comparan firma de GMS; aplicar siempre para GMS virtual sin importar llamante es lo que hace microG).
- `SigningInfo.getApkContentsSigners` vs `getSigningCertificateHistory`: rellenar ambos; `hasMultipleSigners=false`. Instancia vía `BRSigningInfo._new(signingDetails)` (ya usado); en A16 comprobar que el constructor oculto aún exista.
- Apps con `PackageManager.GET_SIGNATURES` deprecado siguen existiendo (GMS client libs viejas).
- Play Integrity/SafetyNet no funcionará con microG; Play Store real no pasa con firma falsa (requiere verificación de servidor).
- microG necesita permisos privilegiados (FAKE_PACKAGE_SIGNATURE ya no existe sin ROM): aquí se resuelve por spoof en el PM virtual, no por permiso.
- Mezcla GMS real del host + virtual: si ambos están, `getContentProvider` decide por authority; riesgo de datos divididos. Decidir modo exclusivo.
- Cuentas (AccountManager) y `com.google` auth: microG usa `AccountManager` virtual (`AccountManagerCompat` existe).

**Esfuerzo:** 8-12 días (providers+servicios+firma ~4-5; el resto integración, cuentas, push FCM/registro de dispositivo). **Confianza:** baja-media (la firma es fácil, que microG funcione extremo a extremo es incierto).

## Resumen
| Bloqueador | Días | Confianza |
|---|---|---|
| 1 Split APKs | 6-8 | media |
| 2 PendingIntent broadcast/service | 4-5 | media-alta |
| 3 Presentación de notificaciones | 3-4 | media-alta |
| 4 GMS/microG + spoof | 8-12 | baja-media |
