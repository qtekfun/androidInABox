# 09-ui: Rediseño de la interfaz del host — requisitos

Estado: Borrador

## Contexto

La interfaz actual (heredada de NewBlackbox: vistas XML, `ViewPager`, diálogos de
material-dialogs) se percibe como fea e incoherente. Se rediseña por completo con Jetpack
Compose y Material 3 sin cambiar el motor. Parte de la propuesta por defecto del punto 19
de `PENDIENTES.md`, a falta de referencias visuales del propietario.

Nombre visible de la app: **androidInABox** (en el lanzador, la barra superior y "Acerca de").

## Historias de usuario

- U1. Como usuario quiero ver mis perfiles (espacios) y entrar en uno para ver sus apps.
- U2. Como usuario quiero lanzar, desinstalar, borrar los datos o crear un acceso directo de cada app.
- U3. Como usuario quiero añadir una app clonándola de las instaladas (incluidas apps de sistema actualizadas) o instalándola desde un APK o bundle.
- U4. Como usuario quiero ajustar notificaciones y suplantación de propiedades por app.
- U5. Como usuario quiero instalar los servicios de Google y ver su estado.
- U6. Como usuario quiero ajustes claros (ocultar root, batería y autoarranque, licencias).
- U7. Como usuario nuevo quiero un asistente que explique y pida los permisos necesarios.

## Requisitos (EARS)

### Marco y estilo

- **R09.1** THE host SHALL construir su interfaz con Jetpack Compose y Material 3.
- **R09.2** WHERE el sistema es Android 12 o superior THE host SHALL usar color dinámico (Material You); en versiones anteriores SHALL usar una paleta propia coherente.
- **R09.3** THE host SHALL seguir el tema claro u oscuro del sistema, con ambas paletas completas.
- **R09.4** THE host SHALL mostrar el nombre "androidInABox" en el lanzador y en la interfaz.
- **R09.5** WHEN el usuario abre el lanzador THE host SHALL abrir la interfaz nueva (no la antigua).
- **R09.6** THE host SHALL usar espaciado, tipografía, formas e iconos coherentes (escala de 4 dp, tipografía de Material 3, iconos de Material).

### Perfiles y apps

- **R09.7** THE host SHALL mostrar como pantalla principal la lista de perfiles del box, con nombre (editable), número de apps y acceso a ajustes.
- **R09.8** WHEN el usuario pulsa "Nuevo perfil" THE host SHALL crear un perfil vacío y abrirlo.
- **R09.9** WHEN el usuario abre un perfil THE host SHALL mostrar sus apps en una cuadrícula con icono y nombre, o un estado vacío con la acción "Añadir app".
- **R09.10** WHEN el usuario pulsa una app THE host SHALL lanzarla en ese perfil; IF falla THEN THE host SHALL mostrar un mensaje de error.
- **R09.11** WHEN el usuario mantiene pulsada una app (o abre su menú) THE host SHALL ofrecer: ajustes de la app, detener, borrar datos, crear acceso directo y desinstalar.
- **R09.12** IF la acción es destructiva (borrar datos, desinstalar) THEN THE host SHALL pedir confirmación antes de ejecutarla.
- **R09.13** WHILE una operación larga (instalar, cargar) está en curso THE host SHALL mostrar un indicador de progreso y no bloquear el hilo principal.

### Añadir app

- **R09.14** WHEN el usuario elige "Añadir app" THE host SHALL ofrecer dos orígenes: apps instaladas en el sistema (con búsqueda) y archivos (APK, APKS, XAPK, APKM, o varios APK).
- **R09.15** THE host SHALL listar en las apps instaladas también las apps de sistema que hayan sido actualizadas, y marcar las ya presentes en el perfil.
- **R09.16** WHEN el usuario elige un origen THE host SHALL instalar con el motor en el perfil activo y mostrar el resultado (éxito o motivo del fallo).

### Ajustes por app

- **R09.17** THE host SHALL ofrecer por app un acceso a los ajustes de notificaciones del host (canal de la app del box) y un interruptor de "suplantar propiedades del dispositivo".
- **R09.18** WHERE la suplantación de propiedades aún no está implementada en el motor THE host SHALL mostrar el interruptor deshabilitado con la indicación "próximamente".

### Gestor de GMS

- **R09.19** THE host SHALL mostrar por perfil el estado de GMS (instalado o no) y permitir instalar o desinstalar.
- **R09.20** WHERE el usuario instala GMS desde archivos THE host SHALL pedir y aplicar el orden GSF, GMS Core, Phonesky, y indicar el paso en curso.
- **R09.21** IF el motor no admite GMS THEN THE host SHALL mostrar la función deshabilitada con su motivo.

### Ajustes

- **R09.22** THE host SHALL ofrecer en ajustes: ocultar root, servicio en segundo plano, VPN, desactivar FLAG_SECURE, prefijo en notificaciones y acceso al gestor de GMS, aplicando los cambios con la misma persistencia actual.
- **R09.23** THE host SHALL ofrecer una guía de batería y autoarranque con acceso directo a los ajustes del sistema.
- **R09.24** THE host SHALL ofrecer "Acerca de" con versión, enlace al repositorio y licencias, incluido el aviso de NewBlackbox bajo Apache-2.0.

### Primera ejecución

- **R09.25** WHEN el usuario abre la app por primera vez THE host SHALL mostrar un asistente de varios pasos que explique y solicite: notificaciones (Android 13+), exclusión de optimización de batería y acceso a almacenamiento.
- **R09.26** THE host SHALL permitir omitir cada paso y SHALL recordar que el asistente ya se completó.
- **R09.27** IF un permiso es denegado THEN THE host SHALL seguir siendo utilizable y poder reabrir el paso desde ajustes.

### Accesibilidad e idiomas

- **R09.28** THE host SHALL dar descripciones de contenido a iconos accionables y objetivos táctiles de al menos 48 dp.
- **R09.29** THE host SHALL ofrecer cadenas en español e inglés, usando el idioma del sistema.
- **R09.30** THE host SHALL mantener contraste suficiente (Material 3) y no depender solo del color para transmitir estado.

## Verificación

| Requisito | Cómo |
|---|---|
| R09.1-R09.6, R09.28-R09.30 | Compilación de CI y revisión visual en dispositivo (claro/oscuro, Android 11 y 12+, TalkBack) |
| R09.7-R09.16 | Pasos del plan de pruebas en dispositivo: crear perfil, instalar, lanzar, borrar, desinstalar |
| R09.17-R09.21 | Instalar GMS en un perfil de prueba; comprobar estado |
| R09.22-R09.24 | Alternar ajustes y comprobar persistencia tras reiniciar |
| R09.25-R09.27 | Desinstalar y reinstalar el host; recorrer el asistente |
