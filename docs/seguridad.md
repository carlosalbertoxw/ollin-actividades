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
- **Girar el teléfono no es salir.** La actividad se detiene y se recrea, pero `isChangingConfigurations` la distingue de una salida de verdad; sin eso, como una salida sin avisar no tiene gracia, cada giro volvería a pedir la llave.
- Se mide con el **reloj monótono** (`elapsedRealtime`): cambiar la hora del teléfono no debe poder alargar la gracia.
- Con candado configurado la ventana lleva `FLAG_SECURE`: ni capturas de pantalla ni miniatura en la vista de apps recientes. Mientras no se sabe, se asume que sí.

Con candado configurado, los **recordatorios** también salen discretos: dicen que hay un hábito pendiente o una tarea por empezar, sin nombre ni detalle. Marcar la ventana con `FLAG_SECURE` y a la vez anunciar «Terapia, te toca hoy» a quien mire el teléfono encima de la mesa sería incoherente. `VISIBILITY_PRIVATE` sola no basta: Android solo oculta el contenido en la pantalla de bloqueo si la persona eligió «ocultar contenido sensible», que no es lo que viene de fábrica. Ver [Recordatorios](recordatorios.md#privacidad).

Las transiciones de bloqueo se escriben de golpe en DataStore. Si el modo y el PIN se guardaran por separado podría quedar un "modo PIN" sin PIN, y eso deja la app cerrada sin llave.

**Un modo que no se puede leer no abre la app.** Para el resto de las preferencias, lo ilegible vuelve al valor de fábrica, y para el candado el de fábrica es no tenerlo: un enum renombrado, una clave que cambió de tipo o un archivo dañado abrían la bitácora sin pedir nada. Ahora se distingue *no haber puesto candado* de *no poder leer cuál se puso*, y en el segundo caso se deduce de lo que sí se lee: si hay huella de PIN, el PIN; si no, la credencial del teléfono. Solo se abre si el teléfono no tiene ningún bloqueo, porque entonces no hay con qué cerrar. Ver `AjustesRepositorio.leeModoBloqueo`.

### El PIN propio

[`ClavePin`](../app/src/main/java/com/carlosalbertoxw/ollin/actividades/data/seguridad/ClavePin.kt) nunca guarda el PIN: guarda **PBKDF2-HMAC-SHA256, 120 000 iteraciones, 256 bits**, con sal aleatoria de 16 bytes distinta por teléfono.

Un PIN de cuatro dígitos tiene diez mil combinaciones; sin un derivado lento bastaría un segundo para probarlas todas contra el archivo de preferencias. La derivación pesa cientos de milisegundos a propósito y corre fuera del hilo principal.

#### La huella va sellada con el Keystore

Diez mil combinaciones siguen siendo pocas: con el archivo de preferencias en la mano —un teléfono con root, un respaldo por adb—, ni PBKDF2 aguanta más que unos minutos. Por eso el resultado se **sella** además con un HMAC-SHA256 cuya llave vive en el Keystore ([`LlaveDelPin`](../app/src/main/java/com/carlosalbertoxw/ollin/actividades/data/seguridad/LlaveDelPin.kt)) y no se puede extraer: la huella ya no se puede calcular fuera del teléfono, y dentro cada intento pasa por la app y su freno.

- Las huellas selladas llevan el prefijo `ks1:`. Las de la 1.2.1 y anteriores son PBKDF2 a secas: **siguen abriendo**, y en cuanto su dueño acierta se guardan selladas con la misma sal, sin pedirle nada. Si guardarla falla, se deja como estaba y se reintenta en el siguiente acierto.
- La llave del sello no exige autenticación: es justo lo que se usa para autenticarse. Si alguna vez se pierde, la huella sellada deja de coincidir y el PIN no abre, igual que la base: las dos viven y mueren con el Keystore de la app.

#### Freno a los intentos

PBKDF2 encarece cada intento, pero no lo suficiente: a un par de décimas por derivación, quien tenga el teléfono en la mano y sepa automatizar pulsaciones agota las diez mil combinaciones en menos de una hora. Por eso hay una espera creciente.

- Se perdonan **3 fallos seguidos**. A partir del cuarto, la espera escala 5 s → 15 → 30 → 60 → 120 → 300 y se queda ahí.
- El contador vive en **DataStore**, y lo único que lo borra es acertar. Matar la app no sirve para saltarse la espera: al arrancar, [`ControlBloqueo`](../app/src/main/java/com/carlosalbertoxw/ollin/actividades/data/seguridad/ControlBloqueo.kt) vuelve a cobrar entera la que tocan los fallos guardados, así que cerrar la app desde Recientes tras cada intento cobra la espera entera cada vez en vez de regalar uno.
- No se guarda ningún instante, solo la cuenta. La espera en curso vive en el reloj monótono (`elapsedRealtime`), que no se persiste: no hay reloj que engañar cambiando la hora ni reiniciando el teléfono, que es lo que pasaría al persistir un "bloqueado hasta".
- **Todo PIN pasa por `ControlBloqueo.intentaPin`**: la pantalla de bloqueo y el diálogo de Ajustes que pide el PIN actual antes de cambiarlo o quitarlo. El freno vive ahí y no en cada pantalla, porque la que se olvidara de él sería el atajo para adivinarlo; las pantallas solo leen la cuenta atrás. Los intentos van de uno en uno (un `Mutex`): dos toques seguidos no pueden leer los dos que no hay espera antes de que el primero apunte su fallo. El fallo se escribe en disco antes de contestar.

Poner un PIN nuevo estrena contador: quien acaba de demostrar que es el dueño no hereda la espera del anterior.

La comparación es en tiempo constante (`MessageDigest.isEqual`): un `==` normal corta en el primer byte distinto, y ese tiempo de más revela cuánto del PIN se acertó.

#### Qué protege el candado y qué no

El candado —PIN o credencial del teléfono— decide quién **ve** la bitácora en la pantalla, no quién puede leer la base. La base se abre antes de llegar al candado con una llave del Keystore que no exige autenticación (ver [cifrado de la base](#cifrado-de-la-base)), y el PIN no participa en ella.

Protege, por lo tanto, frente a quien tiene el teléfono desbloqueado en la mano: es el caso para el que existe. No protege frente a un teléfono con root o con la depuración USB abierta a un equipo de confianza: desde ahí se puede usar la llave del Keystore —no copiarla— y leer la base, y probar los diez mil PIN de cuatro dígitos contra la huella guardada es cuestión de minutos, por lento que sea PBKDF2. Un PIN más largo lo encarece; ninguno lo vuelve imposible.

### La credencial del sistema

[`CredencialDelSistema`](../app/src/main/java/com/carlosalbertoxw/ollin/actividades/ui/seguridad/CredencialDelSistema.kt) pide huella, patrón o PIN del teléfono. Desde Android 11 usa `BiometricPrompt` con `BIOMETRIC_STRONG or DEVICE_CREDENTIAL`; antes, el diálogo unificado no admite credencial del dispositivo, así que abre la pantalla de desbloqueo del sistema.

**El éxito no se cree por el callback.** El diálogo avisa con `onAuthenticationSucceeded`, y en un teléfono con root alguien puede invocar esa función a mano —con Frida, por ejemplo— y abrir el candado sin poner el dedo. Por eso el diálogo recibe un cifrador de [`LlaveDeDesbloqueo`](../app/src/main/java/com/carlosalbertoxw/ollin/actividades/data/seguridad/LlaveDeDesbloqueo.kt): una llave del Keystore que solo se puede usar justo después de una autenticación real (`setUserAuthenticationParameters(0, …)`), y quien lo comprueba es el hardware. Al volver, la app intenta cifrar con ese cifrador; si no puede, nadie se autenticó, diga lo que diga el callback.

Eso obliga a pedir huella de **clase fuerte**: las débiles no pueden habilitar una llave del Keystore. Quien solo tenga una débil —algunos desbloqueos con la cara— entra con el patrón o el PIN del teléfono.

Si una huella nueva o un cambio del bloqueo del teléfono invalida la llave, se crea otra y basta con autenticarse de nuevo. Y si en algún teléfono el Keystore no deja preparar la llave, se cae a la pantalla de desbloqueo del sistema en vez de dejar a nadie fuera de su bitácora.

La llave no protege la base, que va con la suya y sin exigir autenticación: solo vuelve inútil el atajo de saltarse el diálogo.

Se usa en dos lugares: para entrar, y en Ajustes para confirmar antes de quitar el candado.

## Respaldos

El respaldo automático y el traspaso a un teléfono nuevo **excluyen** la base, sus diarios, la envoltura de la llave y las preferencias (`backup_rules.xml`, `data_extraction_rules.xml`).

La razón es física: una llave del Keystore no se puede restaurar ni transferir, así que la copia llegaría ilegible y el usuario creería tener un respaldo que no sirve.

**El respaldo real es la exportación a `.xlsx`**, que el usuario decide dónde guardar. Ver [Excel](excel.md). Ese archivo **no va cifrado** —tiene que abrirse en Excel, WPS o Sheets—, y *Acerca de* lo dice: lo que protege la base deja de protegerlo en cuanto se exporta, y conviene guardarlo donde se guardaría la bitácora en papel.

Como es el único, Ollin lo recuerda: un aviso semanal si no se ha exportado, y otro al encontrar una versión nueva —instalar un APK encima es cuando más importa tenerlo—. Los dos nacen encendidos y tienen su propio interruptor, aparte del de los hábitos. Ver [recordatorios](recordatorios.md#el-recordatorio-de-respaldar).

El aviso semanal sale también **arriba de Hoy** cada vez que se abre la app, mientras toque respaldar: la notificación se pierde entre las demás, y una vez descartada no vuelve hasta la semana siguiente. Ver [recordatorios](recordatorios.md#también-en-hoy).

## La comprobación de actualizaciones

Ollin se instala fuera de la tienda, así que nadie avisa de una corrección: sin esto, quien instaló el APK en marzo se queda con el de marzo para siempre. Una vez al día la app pide un archivo estático al [sitio](sitio.md) y compara.

**Qué sale del teléfono:** una petición `GET`. Sin identificador, sin la versión instalada —la comparación ocurre aquí dentro, con el JSON ya descargado— y evidentemente sin nada de la bitácora. Lo único que el otro extremo puede deducir es que alguien, desde una dirección IP, pidió ese archivo: lo mismo que abrir la dirección en el navegador. Lo sirve GitHub Pages.

**Qué no hace:** descargar ni instalar nada. Cuando hay versión nueva, *Acerca de* enseña un botón que abre el sitio en el navegador. Una app que se actualiza sola necesita el permiso de instalar paquetes, y con él se convierte en un canal de entrega: quien comprometa el servidor de actualizaciones entrega código arbitrario a todos los teléfonos que lo consultan.

Tres cierres, porque el enlace acaba abriéndose en el navegador de alguien y viene de fuera:

- **Solo una descarga de las releases de este repositorio.** Que fuera `https` no bastaba: el JSON llega por el dominio propio, y quien se quedara con él podría anunciar a todas las instalaciones el enlace que quisiera. El enlace tiene que empezar por `https://github.com/carlosalbertoxw/ollin-actividades/releases/download/`, que no cambia de dueño con el dominio; si no, el archivo se descarta entero. Las notas se recortan a 300 caracteres. Ver [actualizaciones](actualizaciones.md#solo-una-descarga-de-las-releases).
- **Las redirecciones se siguen a mano** (`instanceFollowRedirects = false`), un solo salto y solo si el destino también es `https` y es el propio sitio (`carlosalbertoxw.com` o `carlosalbertoxw.github.io`). A mano y no automáticas justamente para poder exigirlo: una que se quedara en `http` dejaría la respuesta viajando en claro. Y se sigue una porque la dirección va compilada dentro de cada APK —mudar el sitio no puede apagar el aviso en todas las instalaciones a la vez—.
- **`usesCleartextTraffic="false"`** en el manifiesto, que lo prohíbe a nivel de plataforma por si lo anterior fallara.

La respuesta tiene un tope de 64 KB. El archivo real ronda los 400 bytes; el tope está porque es lo único que entra a la app desde la red, y sin límite un servidor que nunca cierra la respuesta agota la memoria del teléfono.

Se apaga en `Ajustes → Actualizaciones`. Nace encendido, al revés que los recordatorios: un aviso de hábito lo puede dar la propia memoria, enterarse de que se corrigió un fallo que te afecta, no. Ver [actualizaciones](actualizaciones.md).

## Manejo de errores

Los mensajes que ve el usuario ocultan los internos a propósito: el texto crudo de una excepción habla de rutas, clases y consultas, no le sirve de nada y de paso enseña cómo está hecha la app. El fallo real va a logcat, sin datos del usuario.

### El informe del último fallo

Sin analítica, una app que se cierra al abrirse solo se descubre cuando alguien lo cuenta, y entonces ya no hay con qué diagnosticar. [`RegistroDeFallos`](../app/src/main/java/com/carlosalbertoxw/ollin/actividades/data/diagnostico/RegistroDeFallos.kt) guarda el último fallo que cerró la app —o que impidió arrancarla— en `ultimo-fallo.txt`, en el almacenamiento privado: la versión de la app, la de Android, la fecha y la traza, con un tope de 16 KB. Ni modelo del teléfono ni identificadores.

*Acerca de* lo enseña cuando existe, para leerlo, copiarlo o borrarlo. **No sale del teléfono**: copiarlo y mandárselo a alguien lo decide la persona, después de haberlo leído. Está fuera del respaldo del sistema, igual que la base.
