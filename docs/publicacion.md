# Publicación

Cómo se genera el APK (o el AAB) de producción de Ollin Actividades, firmado y listo para instalar o subir a una tienda.

## Por qué hace falta firmar

Android no instala un APK sin firma, y la firma es lo que ata una actualización a la app que ya está instalada: si mañana publicas una versión firmada con otra llave, el sistema la trata como una app distinta y se niega a actualizar. **No hay forma de recuperar un almacén de claves perdido**; la única salida sería publicar con otro `applicationId` y pedirle a cada persona que reinstale desde cero, perdiendo su bitácora.

Guarda el `.jks` y sus contraseñas en un gestor de contraseñas o en una copia fuera de este equipo. No entran al repositorio: `.gitignore` bloquea `*.jks`, `*.keystore` y `keystore.properties` precisamente porque con ellos cualquiera puede publicar una actualización falsa de Ollin que Android instalaría sin protestar.

## 1. Crear el almacén de claves

Una sola vez, en la raíz del proyecto. `keytool` viene con el JDK:

```bash
"$HOME/.jdks/jbr-21.0.11/bin/keytool" -genkeypair -v -keystore ollin-release.jks -alias ollin -keyalg RSA -keysize 4096 -validity 10000
```

Pide una contraseña y algunos datos de identidad. Los 10 000 días (unos 27 años) son la recomendación de Google: una llave vencida deja de servir para publicar actualizaciones.

## 2. Declarar las credenciales

Copia la plantilla y rellénala:

```bash
cp keystore.properties.example keystore.properties
```

```properties
storeFile=ollin-release.jks
storePassword=...
keyAlias=ollin
keyPassword=...
```

`storeFile` es relativo a la raíz del proyecto. El archivo no se versiona.

En un servidor de integración, en vez del archivo se pueden exportar las variables `OLLIN_STORE_FILE`, `OLLIN_STORE_PASSWORD`, `OLLIN_KEY_ALIAS` y `OLLIN_KEY_PASSWORD`; el build las usa cuando no encuentra `keystore.properties`.

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

Si `keystore.properties` no existe, la compilación **no falla**: el artefacto sale sin firmar (`app-release-unsigned.apk`). Es a propósito, para que alguien que solo quiere comprobar que R8 no rompió nada no necesite el almacén de publicación. Pero un APK sin firmar no se instala ni se sube a ninguna tienda, así que conviene mirar la salida de este comando antes de dar por buena una entrega.

## Qué lleva la compilación de release

- **R8 con `isMinifyEnabled` e `isShrinkResources`.** Las reglas de [`proguard-rules.pro`](../app/proguard-rules.pro) conservan las entidades de Room —para que los nombres de columna no se ofusquen— y las clases de SQLCipher, a las que se llama desde JNI.
- **Solo recursos en español** (`localeFilters`), porque la app es monolingüe.
- **Firma v2 y v3, sin v1.** `minSdk` es 26 y ya entiende v2; firmar también el zip antiguo solo agrega una firma que nadie verifica.

## Versionado

`versionCode` y `versionName` están en [`app/build.gradle.kts`](../app/build.gradle.kts). El `versionCode` es un entero que **tiene que subir en cada publicación**: Play rechaza un bundle cuyo código ya se usó. El `versionName` es el que ve la persona en `Ajustes → Acerca de Ollin`.

La compilación de depuración lleva `applicationId` con sufijo `.debug` y `versionName` con `-debug`, así que se puede tener instalada junto a la de producción sin que una pise a la otra.

## Antes de publicar

- Pasar la suite completa: `./gradlew testDebugUnitTest`.
- Subir `versionCode` y `versionName`.
- Instalar el APK de release en un teléfono y abrirlo: R8 puede romper cosas que la compilación de depuración no enseña.
- Comprobar que la base abre. Es lo que más riesgo corre con la ofuscación, por SQLCipher y Room.
