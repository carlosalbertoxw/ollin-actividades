# Seguridad y privacidad

**Tu bitácora no sale del teléfono**: no hay cuenta, no hay nube, no hay publicidad y no hay analítica. La única vez que Ollin usa la red es para preguntar si salió una versión nueva, y esa petición no lleva nada tuyo dentro; está detallada [más abajo](#la-comprobación-de-actualizaciones).

Los permisos que declara son cinco: `USE_BIOMETRIC` para el candado; `POST_NOTIFICATIONS`, `RECEIVE_BOOT_COMPLETED` y `SCHEDULE_EXACT_ALARM` para los [recordatorios](recordatorios.md); e `INTERNET` para lo anterior.

## Cifrado de la base

La base va cifrada con **AES-256 (SQLCipher)**. No hay camino sin cifrar: si SQLCipher no arranca, la app no abre. Es preferible a que una bitácora personal quede en claro sin avisar.

```
frase aleatoria de 32 bytes (hex)
        │  envuelta con AES/GCM
        ▼
llave maestra en AndroidKeyStore  ──►  no sale del dispositivo, ni con root
```

[`LlaveBase`](../app/src/main/java/com/carlosalbertoxw/ollin/actividades/data/seguridad/LlaveBase.kt) genera la frase una sola vez al azar y la guarda envuelta en `SharedPreferences` (`ollin_llave`). La app pide desenvolverla; nunca ve la llave maestra.

Dos detalles que no son evidentes:

- **La frase se representa en hexadecimal a propósito.** SQLCipher deriva su llave del texto que recibe, y con caracteres imprimibles el resultado es el mismo por cualquier camino por el que se le entregue la frase. Con bytes crudos, dos caminos distintos producirían llaves distintas y la base quedaría ilegible.
- **La llave del Keystore no exige desbloqueo del usuario** (`setUserAuthenticationRequired(false)`): la base se abre antes de que puedas autenticarte, y exigirlo dejaría la app sin arrancar.

La frase nueva se escribe con `commit()` y no `apply()`: si el proceso muriera antes de persistirla, la base quedaría cifrada con una frase que ya nadie conoce.

## Bloqueo de la app

Tres modos ([`ModoBloqueo`](../app/src/main/java/com/carlosalbertoxw/ollin/actividades/data/prefs/Ajustes.kt)):

| Modo | Con qué se abre |
|---|---|
| `NINGUNO` | Sin bloqueo |
| `SISTEMA` | Patrón, PIN, contraseña o huella del propio teléfono |
| `PIN` | Un PIN exclusivo de Ollin, de 4 a 12 dígitos |

[`ControlBloqueo`](../app/src/main/java/com/carlosalbertoxw/ollin/actividades/data/seguridad/ControlBloqueo.kt) vive en el `Contenedor` y no en un ViewModel, porque debe sobrevivir a que la actividad se recree: si el estado se perdiera al girar el teléfono, girarlo sería la forma de saltarse el candado.

Detalles del comportamiento:

- **Arranca bloqueada.** Todavía no se sabe si hay candado puesto, y equivocarse hacia el lado cerrado solo cuesta un parpadeo.
- **Se cierra en cuanto sale al fondo.** Pulsar Inicio y pasarle el teléfono a alguien es justo el caso que el candado existe para cubrir.
- **Un minuto de gracia, pero solo para un viaje de ida y vuelta al sistema.** Importar y exportar abren el selector de archivos, que manda Ollin al fondo; sin ese margen, elegir un `.xlsx` te expulsaría a medio camino. La pantalla que va a abrir el selector lo pide antes con `esperaVueltaDelSistema()`, y el permiso **se gasta al usarlo**: el siguiente viaje tiene que volver a pedirlo.
- Se mide con el **reloj monótono** (`elapsedRealtime`): cambiar la hora del teléfono no debe poder alargar la gracia.
- Con candado configurado la ventana lleva `FLAG_SECURE`: ni capturas de pantalla ni miniatura en la vista de apps recientes. Mientras no se sabe, se asume que sí.

Con candado configurado, los **recordatorios** también salen discretos: `VISIBILITY_PRIVATE`, así que en la pantalla de bloqueo se ve que hay un aviso de Ollin pero no de qué. Marcar la ventana con `FLAG_SECURE` y a la vez anunciar «Terapia, te toca hoy» a quien mire el teléfono encima de la mesa sería incoherente. Ver [Recordatorios](recordatorios.md).

Las transiciones de bloqueo se escriben de golpe en DataStore. Si el modo y el PIN se guardaran por separado podría quedar un "modo PIN" sin PIN, y eso deja la app cerrada sin llave.

### El PIN propio

[`ClavePin`](../app/src/main/java/com/carlosalbertoxw/ollin/actividades/data/seguridad/ClavePin.kt) nunca guarda el PIN: guarda **PBKDF2-HMAC-SHA256, 120 000 iteraciones, 256 bits**, con sal aleatoria de 16 bytes distinta por teléfono.

Un PIN de cuatro dígitos tiene diez mil combinaciones; sin un derivado lento bastaría un segundo para probarlas todas contra el archivo de preferencias. La derivación pesa cientos de milisegundos a propósito y corre fuera del hilo principal.

#### Freno a los intentos

PBKDF2 encarece cada intento, pero no lo suficiente: a un par de décimas por derivación, quien tenga el teléfono en la mano y sepa automatizar pulsaciones agota las diez mil combinaciones en menos de una hora. Por eso hay una espera creciente.

- Se perdonan **3 fallos seguidos**. A partir del cuarto, la espera escala 5 s → 15 → 30 → 60 → 120 → 300 y se queda ahí.
- El contador vive en **DataStore**, y lo único que lo borra es acertar. Matar la app no sirve para saltarse la espera: al volver, la espera empieza de nuevo con el mismo contador.
- No se guarda ningún instante, solo la cuenta. Así no hay reloj que engañar cambiando la hora ni reiniciando el teléfono, que es lo que pasaría al persistir un "bloqueado hasta".
- **Las dos puertas comparten el contador**: la pantalla de bloqueo y el diálogo de Ajustes que pide el PIN actual antes de cambiarlo o quitarlo. Frenar solo una equivaldría a no frenar ninguna.

Poner un PIN nuevo estrena contador: quien acaba de demostrar que es el dueño no hereda la espera del anterior.

La comparación es en tiempo constante (`MessageDigest.isEqual`): un `==` normal corta en el primer byte distinto, y ese tiempo de más revela cuánto del PIN se acertó.

### La credencial del sistema

[`CredencialDelSistema`](../app/src/main/java/com/carlosalbertoxw/ollin/actividades/ui/seguridad/CredencialDelSistema.kt) pide huella, patrón o PIN del teléfono. Desde Android 11 usa `BiometricPrompt` con `BIOMETRIC_WEAK or DEVICE_CREDENTIAL`; antes, el diálogo unificado no admite credencial del dispositivo, así que abre la pantalla de desbloqueo del sistema.

Se usa en dos lugares: para entrar, y en Ajustes para confirmar antes de quitar el candado.

## Respaldos

El respaldo automático y el traspaso a un teléfono nuevo **excluyen** la base, sus diarios, la envoltura de la llave y las preferencias (`backup_rules.xml`, `data_extraction_rules.xml`).

La razón es física: una llave del Keystore no se puede restaurar ni transferir, así que la copia llegaría ilegible y el usuario creería tener un respaldo que no sirve.

**El respaldo real es la exportación a `.xlsx`**, que el usuario decide dónde guardar. Ver [Excel](excel.md).

Como es el único, Ollin lo recuerda: un aviso semanal si no se ha exportado, y otro al encontrar una versión nueva —instalar un APK encima es cuando más importa tenerlo—. Los dos nacen encendidos y tienen su propio interruptor, aparte del de los hábitos. Ver [recordatorios](recordatorios.md#el-recordatorio-de-respaldar).

## La comprobación de actualizaciones

Ollin se instala fuera de la tienda, así que nadie avisa de una corrección: sin esto, quien instaló el APK en marzo se queda con el de marzo para siempre. Una vez al día la app pide un archivo estático al [sitio](sitio.md) y compara.

**Qué sale del teléfono:** una petición `GET`. Sin identificador, sin la versión instalada —la comparación ocurre aquí dentro, con el JSON ya descargado— y evidentemente sin nada de la bitácora. Lo único que el otro extremo puede deducir es que alguien, desde una dirección IP, pidió ese archivo: lo mismo que abrir la dirección en el navegador. Lo sirve GitHub Pages.

**Qué no hace:** descargar ni instalar nada. Cuando hay versión nueva, *Acerca de* enseña un botón que abre el sitio en el navegador. Una app que se actualiza sola necesita el permiso de instalar paquetes, y con él se convierte en un canal de entrega: quien comprometa el servidor de actualizaciones entrega código arbitrario a todos los teléfonos que lo consultan.

Tres cierres, porque el enlace acaba abriéndose en el navegador de alguien y viene de fuera:

- **Solo `https`.** Si la dirección del APK no lo es, se ignora; si no queda ninguna válida, el archivo se descarta entero.
- **Las redirecciones se siguen a mano** (`instanceFollowRedirects = false`), un solo salto y solo si el destino también es `https`. A mano y no automáticas justamente para poder exigirlo: una que se quedara en `http` dejaría la respuesta viajando en claro. Y se sigue una porque la dirección va compilada dentro de cada APK —mudar el sitio no puede apagar el aviso en todas las instalaciones a la vez—.
- **`usesCleartextTraffic="false"`** en el manifiesto, que lo prohíbe a nivel de plataforma por si lo anterior fallara.

La respuesta tiene un tope de 64 KB. El archivo real ronda los 400 bytes; el tope está porque es lo único que entra a la app desde la red, y sin límite un servidor que nunca cierra la respuesta agota la memoria del teléfono.

Se apaga en `Ajustes → Actualizaciones`. Nace encendido, al revés que los recordatorios: un aviso de hábito lo puede dar la propia memoria, enterarse de que se corrigió un fallo que te afecta, no. Ver [actualizaciones](actualizaciones.md).

## Manejo de errores

Los mensajes que ve el usuario ocultan los internos a propósito: el texto crudo de una excepción habla de rutas, clases y consultas, no le sirve de nada y de paso enseña cómo está hecha la app. El fallo real va a logcat, sin datos del usuario.
