# Seguridad y privacidad

Ollin no manda nada a ningún servidor: no hay cuenta, no hay nube y no hay publicidad. El único permiso que declara la app es `USE_BIOMETRIC`.

## Cifrado de la base

La base va cifrada con **AES-256 (SQLCipher)**. No hay camino sin cifrar: si SQLCipher no arranca, la app no abre. Es preferible a que una bitácora personal quede en claro sin avisar.

```
frase aleatoria de 32 bytes (hex)
        │  envuelta con AES/GCM
        ▼
llave maestra en AndroidKeyStore  ──►  no sale del dispositivo, ni con root
```

[`LlaveBase`](../app/src/main/java/mx/ollin/actividades/data/seguridad/LlaveBase.kt) genera la frase una sola vez al azar y la guarda envuelta en `SharedPreferences` (`ollin_llave`). La app pide desenvolverla; nunca ve la llave maestra.

Dos detalles que no son evidentes:

- **La frase se representa en hexadecimal a propósito.** SQLCipher deriva su llave del texto que recibe, y con caracteres imprimibles el resultado es el mismo por cualquier camino por el que se le entregue la frase. Con bytes crudos, dos caminos distintos producirían llaves distintas y la base quedaría ilegible.
- **La llave del Keystore no exige desbloqueo del usuario** (`setUserAuthenticationRequired(false)`): la base se abre antes de que puedas autenticarte, y exigirlo dejaría la app sin arrancar.

La frase nueva se escribe con `commit()` y no `apply()`: si el proceso muriera antes de persistirla, la base quedaría cifrada con una frase que ya nadie conoce.

## Bloqueo de la app

Tres modos ([`ModoBloqueo`](../app/src/main/java/mx/ollin/actividades/data/prefs/Ajustes.kt)):

| Modo | Con qué se abre |
|---|---|
| `NINGUNO` | Sin bloqueo |
| `SISTEMA` | Patrón, PIN, contraseña o huella del propio teléfono |
| `PIN` | Un PIN exclusivo de Ollin, de 4 a 12 dígitos |

[`ControlBloqueo`](../app/src/main/java/mx/ollin/actividades/data/seguridad/ControlBloqueo.kt) vive en el `Contenedor` y no en un ViewModel, porque debe sobrevivir a que la actividad se recree: si el estado se perdiera al girar el teléfono, girarlo sería la forma de saltarse el candado.

Detalles del comportamiento:

- **Arranca bloqueada.** Todavía no se sabe si hay candado puesto, y equivocarse hacia el lado cerrado solo cuesta un parpadeo.
- **Un minuto de gracia** al volver del fondo. Importar y exportar abren el selector de archivos del sistema, que manda Ollin al fondo; sin ese margen, elegir un `.xlsx` te expulsaría a medio camino.
- Se mide con el **reloj monótono** (`elapsedRealtime`): cambiar la hora del teléfono no debe poder alargar la gracia.
- Con candado configurado la ventana lleva `FLAG_SECURE`: ni capturas de pantalla ni miniatura en la vista de apps recientes. Mientras no se sabe, se asume que sí.

Las transiciones de bloqueo se escriben de golpe en DataStore. Si el modo y el PIN se guardaran por separado podría quedar un "modo PIN" sin PIN, y eso deja la app cerrada sin llave.

### El PIN propio

[`ClavePin`](../app/src/main/java/mx/ollin/actividades/data/seguridad/ClavePin.kt) nunca guarda el PIN: guarda **PBKDF2-HMAC-SHA256, 120 000 iteraciones, 256 bits**, con sal aleatoria de 16 bytes distinta por teléfono.

Un PIN de cuatro dígitos tiene diez mil combinaciones; sin un derivado lento bastaría un segundo para probarlas todas contra el archivo de preferencias. La derivación pesa cientos de milisegundos a propósito y corre fuera del hilo principal.

La comparación es en tiempo constante (`MessageDigest.isEqual`): un `==` normal corta en el primer byte distinto, y ese tiempo de más revela cuánto del PIN se acertó.

### La credencial del sistema

[`CredencialDelSistema`](../app/src/main/java/mx/ollin/actividades/ui/seguridad/CredencialDelSistema.kt) pide huella, patrón o PIN del teléfono. Desde Android 11 usa `BiometricPrompt` con `BIOMETRIC_WEAK or DEVICE_CREDENTIAL`; antes, el diálogo unificado no admite credencial del dispositivo, así que abre la pantalla de desbloqueo del sistema.

Se usa en dos lugares: para entrar, y en Ajustes para confirmar antes de quitar el candado.

## Respaldos

El respaldo automático y el traspaso a un teléfono nuevo **excluyen** la base, sus diarios, la envoltura de la llave y las preferencias (`backup_rules.xml`, `data_extraction_rules.xml`).

La razón es física: una llave del Keystore no se puede restaurar ni transferir, así que la copia llegaría ilegible y el usuario creería tener un respaldo que no sirve.

**El respaldo real es la exportación a `.xlsx`**, que el usuario decide dónde guardar. Ver [Excel](excel.md).

## Manejo de errores

Los mensajes que ve el usuario ocultan los internos a propósito: el texto crudo de una excepción habla de rutas, clases y consultas, no le sirve de nada y de paso enseña cómo está hecha la app. El fallo real va a logcat, sin datos del usuario.
