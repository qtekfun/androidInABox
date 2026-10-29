# 09-ui: Rediseño de la interfaz del host — diseño

Estado: Borrador

## Decisiones

- **Compose + Material 3** (BOM 2024.06.00, extensión del compilador de Compose 1.5.13 compatible con Kotlin 1.9.23). Sin migrar Kotlin.
- **Una sola actividad** (`MainComposeActivity`) con `NavHost` (navigation-compose). Las actividades antiguas siguen declaradas durante la transición pero el lanzador abre la nueva.
- **Reutilización**: `AppsRepository`, `GmsRepository` y las API de `BlackBoxCore` se llaman desde un único `ViewModel` (`HostViewModel`) que expone `StateFlow`. Las operaciones del motor se ejecutan en `Dispatchers.IO`.
- **Color dinámico** con `dynamicLightColorScheme/dynamicDarkColorScheme` en Android 12+; paleta propia (teal) en versiones anteriores.
- **Iconos**: `material-icons-extended` (se recorta con R8 en release).
- **Datos de perfil**: el nombre se guarda en el mismo `SharedPreferences` `UserRemark` (`Remark<id>`) para compatibilidad.

## Navegación

```
Onboarding (solo 1.ª vez)
Perfiles ──► Apps del perfil ──► Ajustes de la app
   │               └──► Añadir app (sistema | archivos)
   └──► Ajustes ──► GMS | Batería y autoarranque | Acerca de
```

## Componentes

| Pieza | Responsabilidad |
|---|---|
| `ui/theme/` | Color, tipografía, formas |
| `ui/HostViewModel` | Estado y acciones sobre repositorios |
| `ui/screens/*` | Pantallas Compose |
| `ui/Navigation` | Rutas |
| `res/values{,-es}/strings.xml` | Cadenas en inglés (por defecto) y español |

## Riesgos

- Los repositorios usan `LiveData` y callbacks; se adaptan con `observeForever` acotado o llamadas directas.
- Instalar GMS desde archivos depende de la spec 03; hasta entonces se usa `installGms` del motor y el orden se aplica al instalar ficheros.
- Iconos de apps como `Drawable` → `ImageBitmap` con caché para no saturar memoria.

## Verificación

Ver tabla de `requirements.md`; además, `:app:assembleDebug` en cada tarea.
