# Handoff: usar el Galaxy Z Flip 5 desde la pantalla exterior

App Android para usar un **Galaxy Z Flip 5 cerrado** desde su pantalla exterior (Flex Window):
abre la pantalla de inicio de One UI y cualquier app en la pantalla exterior, con barra de
navegación, gestos, recientes y fondo de pantalla.

> **Uso principal:** se creó para dar una **segunda vida** a un Z Flip 5 cuya pantalla interior
> había quedado inservible, y para poder **recuperar datos importantes** del teléfono sin tener
> que repararla.

## Funciones

| Pieza | Qué hace |
|---|---|
| **Lanzador** (`LauncherActivity`) | Cuadrícula con todas las apps y buscador. Abre cada app en la pantalla donde está el lanzador; si la app estaba abierta en la pantalla interior, la trae a la exterior. Botones **One UI**, **Fondo** y **A interior / A exterior**. |
| **Servicio de accesibilidad** (`CoverRedirectService`) | Con el móvil cerrado: (1) si una app se abre en la pantalla interior (por ejemplo desde el inicio de One UI) la mueve a la exterior; (2) dibuja la barra de navegación y los gestos en la exterior. Arranca solo con el sistema. |
| **Recientes** | El botón de las tres rayas abre la vista **"Aplicaciones recientes" de One UI** en la pantalla exterior. Si no estuviera disponible, usa una lista propia de apps usadas. |
| **Fondo de pantalla** (`WallpaperActivity`) | Pone una imagen como fondo de la pantalla exterior (se ve detrás de One UI). Desde el botón **Fondo** del lanzador, con **Compartir → Fondo de la pantalla exterior** desde la Galería, o copiando el fondo de la pantalla interior. |
| **Widget** (`CoverWidgetProvider`) | Widget para la Flex Window con dos botones, **One UI** y **Apps**, por si se quiere entrar sin usar ADB. Se añade desde los ajustes de widgets de la pantalla exterior (pide abrir el móvil). |

### Barra de navegación (pantalla exterior)

`⋮` arrastrar · `|||` Recientes · `▢` Inicio (One UI) · `<` Atrás · `⠿` Todas las apps

- Mismo orden y estilo que One UI: iconos vectoriales de trazo fino, oscuros con halo claro y sin fondo.
- Se coloca abajo a la izquierda, fuera del módulo de cámaras: la app lee el recorte de pantalla
  que declara el sistema. Se puede arrastrar por `⋮` y recuerda la posición.
- Gestos: deslizar desde el borde izquierdo o derecho hacia dentro = **Atrás**; deslizar hacia
  arriba desde el borde inferior = **Inicio**.
- Solo aparece con el móvil cerrado y una app en primer plano. Se oculta sobre el inicio y la
  pantalla de bloqueo de la exterior de Samsung, para no permitir saltarse el bloqueo.

## Instalación

Requiere ADB (depuración USB activada).

```sh
adb install -r app-release.apk

# Activar el servicio de accesibilidad (conserva los que ya estén activos)
SVC=com.handoff.cover/com.handoff.cover.CoverRedirectService
CUR=$(adb shell settings get secure enabled_accessibility_services)
[ "$CUR" = "null" ] && CUR="" || CUR="$CUR:"
adb shell settings put secure enabled_accessibility_services "$CUR$SVC"
adb shell settings put secure accessibility_enabled 1

# Opcional: permitir "Usar el mismo fondo que la pantalla interior"
adb shell appops set com.handoff.cover MANAGE_EXTERNAL_STORAGE allow

# Abrir el lanzador en la pantalla exterior (con el móvil cerrado)
adb shell am start --display 1 -n com.handoff.cover/.LauncherActivity
```

Android no deja activar desde Ajustes el servicio de accesibilidad de una app instalada fuera de
la tienda ("ajuste restringido"), por eso se activa con ADB. Si se borran los datos de la app, hay
que volver a activarlo.

## Compilar

Con Android Studio (abrir la carpeta) o desde terminal:

```sh
export ANDROID_HOME=/ruta/al/Android/Sdk      # o crear local.properties con sdk.dir=...
./gradlew assembleRelease
# APK: app/build/outputs/apk/release/app-release.apk (firmada con la clave debug)
```

Requisitos: JDK 17 o superior, Android Gradle Plugin 9.4.1, Gradle 9.8.2, SDK `android-37`,
`minSdk` 30. Sin librerías externas: solo Java y la API de Android.

## Limitaciones

- **No es un espejo de la pantalla interior.** Con el móvil abierto, la pantalla exterior está
  apagada. El modo de doble pantalla (el que usa la cámara) se cancela en cuanto la app que lo pide
  deja de estar en primer plano. Con el móvil cerrado, la interior está apagada. Por eso las apps
  se ejecutan directamente en la pantalla exterior.
- **Tamaño de los iconos de One UI:** en la pantalla exterior One UI usa un tamaño fijo; no cambia
  con la cuadrícula, con "Tamaño de aplicaciones" ni con la densidad de pantalla.
- Las apps se ven en 748×720; algunas no están pensadas para ese tamaño.
- Probado en Galaxy Z Flip 5 (SM-F731B) con Android 16 / One UI 8.5. Ids de pantalla: interior = 0,
  exterior = 1. Los flags de fondo de Samsung (4 = principal, 16 = exterior) y los componentes de
  One UI usados pueden cambiar en otras versiones.
