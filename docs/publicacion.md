# Publicación

Cómo se genera el APK (o el AAB) de producción de Ollin Actividades, firmado y listo para instalar o subir a una tienda.

## Por qué hace falta firmar

Android no instala un APK sin firma, y la firma es lo que ata una actualización a la app que ya está instalada: si mañana publicas una versión firmada con otra llave, el sistema la trata como una app distinta y se niega a actualizar. **No hay forma de recuperar un almacén de claves perdido**; la única salida sería publicar con otro `applicationId` y pedirle a cada persona que reinstale desde cero, perdiendo su bitácora.

Guarda el `.jks` y sus contraseñas en un gestor de contraseñas o en una copia fuera de este equipo. No entran al repositorio: `.gitignore` bloquea `*.jks`, `*.keystore` y `keystore.properties` precisamente porque con ellos cualquiera puede publicar una actualización falsa de Ollin Actividades que Android instalaría sin protestar.

## 1. Crear el almacén de claves

Una sola vez, en la raíz del proyecto. `keytool` viene con el JDK:

```bash
"$HOME/.jdks/jbr-21.0.11/bin/keytool" -genkeypair -v -keystore ollin-actividades-release.jks -alias ollin-actividades -keyalg RSA -keysize 4096 -validity 10000
```

Pide una contraseña y algunos datos de identidad. Los 10 000 días (unos 27 años) son la recomendación de Google: una llave vencida deja de servir para publicar actualizaciones.

**El nombre lleva la app completa, no solo `ollin`.** Ollin Actividades y Ollin Finanzas son dos aplicaciones distintas, cada una con su `applicationId`, su ficha en la tienda y su propio almacén de claves. Un archivo llamado `ollin-release.jks` o un alias `ollin` a secas no dicen a cuál de las dos pertenecen, y confundirlos al firmar no se nota hasta que la tienda rechaza la subida por venir de otra llave. **Nunca firmes las dos apps con el mismo almacén:** una llave comprometida se llevaría por delante las dos a la vez, y no hay forma de rotarla sin obligar a reinstalar.

## 2. Declarar las credenciales

Copia la plantilla y rellénala:

```bash
cp keystore.properties.example keystore.properties
```

```properties
storeFile=ollin-actividades-release.jks
storePassword=...
keyAlias=ollin-actividades
keyPassword=...
```

`storeFile` es relativo a la raíz del proyecto. El archivo no se versiona.

En un servidor de integración, en vez del archivo se pueden exportar las variables `OLLIN_ACTIVIDADES_STORE_FILE`, `OLLIN_ACTIVIDADES_STORE_PASSWORD`, `OLLIN_ACTIVIDADES_KEY_ALIAS` y `OLLIN_ACTIVIDADES_KEY_PASSWORD`; el build las usa cuando no encuentra `keystore.properties`. Van con el nombre completo por lo mismo que el almacén: si Ollin Actividades y Ollin Finanzas comparten servidor, unas variables `OLLIN_*` a secas se pisarían y cada app acabaría firmada con la llave de la otra.

## 3. Generar el artefacto

```bash
JAVA_HOME="$HOME/.jdks/jbr-21.0.11" ./gradlew assembleRelease
```

Queda en `app/build/outputs/apk/release/app-release.apk`.

Para Google Play, el formato es el App Bundle:

```bash
JAVA_HOME="$HOME/.jdks/jbr-21.0.11" ./gradlew bundleRelease
```

Queda en `app/build/outputs/bundle/release/app-release.aab`.

## 4. Comprobar la firma

```bash
"$ANDROID_HOME/build-tools/36.0.0/apksigner" verify --print-certs app/build/outputs/apk/release/app-release.apk
```

Para saber con qué llave se va a firmar antes de compilar nada, sin escribir ninguna contraseña:

```bash
JAVA_HOME="$HOME/.jdks/jbr-21.0.11" ./gradlew :app:signingReport
```

La variante `release` tiene que enseñar `ollin-actividades-release.jks` y el alias `ollin-actividades`. Si sale el almacén de Ollin Finanzas, o ninguno, es que `keystore.properties` apunta a otro lado.

Si `keystore.properties` no existe, la compilación **no falla**: el artefacto sale sin firmar (`app-release-unsigned.apk`). Es a propósito, para que alguien que solo quiere comprobar que R8 no rompió nada no necesite el almacén de publicación. Pero un APK sin firmar no se instala ni se sube a ninguna tienda, así que conviene mirar la salida de este comando antes de dar por buena una entrega.

## Qué lleva la compilación de release

- **R8 con `isMinifyEnabled` e `isShrinkResources`.** Las reglas de [`proguard-rules.pro`](../app/proguard-rules.pro) conservan las entidades de Room —para que los nombres de columna no se ofusquen— y las clases de SQLCipher, a las que se llama desde JNI.
- **Solo recursos en español** (`localeFilters`), porque la app es monolingüe.
- **Firma v2 y v3, sin v1.** `minSdk` es 26 y ya entiende v2; firmar también el zip antiguo solo agrega una firma que nadie verifica.

## Versionado

`versionCode` y `versionName` están en [`app/build.gradle.kts`](../app/build.gradle.kts). El `versionCode` es un entero que **tiene que subir en cada publicación**: Play rechaza un bundle cuyo código ya se usó. El `versionName` es el que ve la persona en `Ajustes → Acerca de Ollin`.

La compilación de depuración lleva `applicationId` con sufijo `.debug` y `versionName` con `-debug`, así que se puede tener instalada junto a la de producción sin que una pise a la otra.

## Antes de publicar

- Pasar las dos suites: `./gradlew testDebugUnitTest` y, con un teléfono o un emulador conectado, `./gradlew connectedDebugAndroidTest`. Ver [desarrollo](desarrollo.md#pruebas).
- Subir `versionCode` y `versionName`.
- Instalar el APK de release en un teléfono y abrirlo: R8 puede romper cosas que la compilación de depuración no enseña.
- Comprobar que la base abre. Es lo que más riesgo corre con la ofuscación, por SQLCipher y Room.
