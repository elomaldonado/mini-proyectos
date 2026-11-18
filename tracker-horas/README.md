# Tracker de horas · 進度

App Android para llevar el control de cuántas horas le dedico cada día a **mi negocio** y a **estudiar mandarín (中文)**, cuánto **dinero** gano y si hice **ejercicio**.
Antes lo llevaba en Excel; ahora todo aparece en la pantalla de bloqueo, encima de mi fondo de pantalla, cada vez que prendo el celular.

## Qué hace

- **Cronómetro** por categoría (Trabajo / 中文): ▶ empezar, ⏸ parar. Si pasa la medianoche, divide el tiempo entre los dos días.
- **Sumar a mano** bloques de +15 / +30 / −15 minutos.
- **Píldora en la Now Bar (One UI 8 / Android 16):** debajo de la hora, siempre visible. Muestra el cronómetro de lo que estoy haciendo o "Hoy: Trabajo 2h · 中文 0h". Es un *Live Update* (`requestPromotedOngoing` + `shortCriticalText`) mantenido por un servicio en primer plano que vuelve a arrancar al prender el teléfono.
- **Pantalla de bloqueo:** una tarjeta fija muestra el dinero de hoy y del mes y los tres mapas de calor; arriba, el cronómetro con lo que estoy haciendo (Trabajo o 中文). Botones ▶/⏸ para el cronómetro y **+ Dinero** para anotar un monto sin abrir la app. En Android 16 (One UI 8) el cronómetro pide mostrarse como *Live Update* (Now Bar).
- **Ejercicio:** al final del día marco *8k pasos*, *20 min calistenia* y *correr / saltar soga*. Niveles: 8k · 8k + calistenia · 8k + correr/soga · todo.
- **Dinero ganado** en soles (S/): se suma durante el día; muestra hoy, el mes y los últimos 30 días.
- **Mapas de calor** de los últimos 6 meses, estilo GitHub. Trabajo en azules (1h · 2–3h · 3–5h · 6h+), 中文 en verdes (1h · 2h · 3h · 3h+) y ejercicio en rojos suaves. Se puede tocar cualquier día para editarlo.
- **Nota del día** ("Pingtung", "examen", "drained"…), marcada con un puntito en el mapa.
- **Estadísticas:** horas de los últimos 7 y 30 días y racha de días seguidos.
- **Exportar / importar CSV** (también se puede importar con "Compartir → Progreso" desde WhatsApp) (`fecha,trabajo_horas,mandarin_horas,dinero_soles,ejercicio,nota`), compatible con Excel.

Todo se guarda en el teléfono. Sin cuentas, sin internet, sin dependencias externas.

## Instalar

Descarga la última versión desde el celular:
**[tracker-horas.apk](https://github.com/elomaldonado/mini-proyectos/releases/latest/download/tracker-horas.apk)**

Al abrirlo, Android pide permiso para "instalar apps desconocidas" desde el navegador. Después hay que aceptar las notificaciones de la app para que aparezca en la pantalla de bloqueo.

## Cómo está hecho

- Kotlin, sin librerías externas: solo el SDK de Android y la tipografía [DM Sans](https://fonts.google.com/specimen/DM+Sans) (licencia OFL) (UI construida en código, `SharedPreferences` + JSON para guardar los datos).
- Cada push a `main` dispara un workflow de **GitHub Actions** que compila el APK y lo publica en *Releases*.

```
app/src/main/java/com/elo/trackerhoras/
├── MainActivity.kt   # pantalla principal
├── Store.kt          # datos y cronómetro
├── Notifier.kt       # tarjeta de la pantalla de bloqueo
├── HeatmapRenderer.kt# dibujo de los mapas (app y pantalla de bloqueo)
├── ActionReceiver.kt # botones (▶/⏸, + Dinero), medianoche y reinicio
├── HeatmapView.kt    # mapa de calor de 6 meses
└── Csv.kt            # exportar / importar
```
