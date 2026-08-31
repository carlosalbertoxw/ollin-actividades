import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

/**
 * Credenciales de firma.
 *
 * Se leen de `keystore.properties` en la raiz del proyecto, que no se versiona;
 * si no existe, se caen a variables de entorno, que es lo que sirve en un
 * servidor de integracion. Ver `keystore.properties.example` y docs/publicacion.md.
 *
 * Nunca van escritas aqui: este archivo si viaja en el repositorio, y un
 * almacen de claves filtrado permite publicar actualizaciones falsas de Ollin
 * Actividades que Android instalaria sin protestar.
 *
 * Las variables llevan el nombre completo de la app, no un `OLLIN_` a secas:
 * Ollin Finanzas se publica aparte y con su propio almacen, y en un servidor
 * compartido unos nombres genericos harian que cada app tomara la llave de la
 * otra sin avisar.
 */
val credenciales = Properties().apply {
    val archivo = rootProject.file("keystore.properties")
    if (archivo.isFile) archivo.inputStream().use(::load)
}

fun credencial(clave: String, variable: String): String? =
    (credenciales.getProperty(clave) ?: System.getenv(variable))?.takeIf { it.isNotBlank() }

val almacenDeClaves = credencial("storeFile", "OLLIN_ACTIVIDADES_STORE_FILE")
    ?.let(rootProject::file)
    ?.takeIf { it.isFile }

/**
 * La version sale de CHANGELOG.md, no de este archivo.
 *
 * Un numero escrito a mano aqui se olvida: se publica la 1.2.0 con el build
 * todavia en 1.1.0, o al reves, y quien instala el APK ve una version que no
 * corresponde a las notas que leyo. Con el historial como unica fuente, subir
 * la version y explicar por que son el mismo gesto, y el flujo de publicacion
 * puede negarse a etiquetar algo que nadie documento.
 *
 * Se lee el primer encabezado `## [x.y.z]` del archivo. "Sin publicar" no
 * casa con el patron a proposito, asi que compilar mientras hay cambios sin
 * etiquetar sigue dando la ultima version publicada.
 */
val versionPublicada: Triple<Int, Int, Int> = run {
    val historial = rootProject.file("CHANGELOG.md")
    require(historial.isFile) { "Falta CHANGELOG.md: de ahi sale la version." }

    val encabezado = Regex("""^##\s+\[(\d+)\.(\d+)\.(\d+)]""", RegexOption.MULTILINE)
    val primero = encabezado.find(historial.readText())
        ?: error("CHANGELOG.md no tiene ningun encabezado `## [x.y.z]`.")

    val (mayor, menor, parche) = primero.destructured
    Triple(mayor.toInt(), menor.toInt(), parche.toInt())
}

val nombreDeVersion = versionPublicada.toList().joinToString(".")

/**
 * El entero que compara Android. Se deriva del semver con tres huecos de dos
 * cifras —1.2.3 es 10203— para que crezca solo y nunca haya que acordarse de
 * subirlo aparte. Da margen hasta 99 versiones menores y 99 parches, de sobra
 * para una app que publica a mano, y ordena igual que el semver: cualquier
 * version posterior produce un entero mayor.
 */
val codigoDeVersion = versionPublicada.let { (mayor, menor, parche) ->
    mayor * 10_000 + menor * 100 + parche
}

/**
 * De donde se entera la app de que hay una version nueva.
 *
 * Es el sitio de GitHub Pages y no la API de GitHub: la API limita las
 * peticiones anonimas por IP —una red compartida las agota entre todos— y
 * devuelve un objeto enorme del que solo se usan tres campos. Un JSON estatico
 * detras de un CDN no se cae, no se limita y se puede mirar con el navegador.
 */
val urlDeActualizaciones = providers
    .gradleProperty("ollin.urlActualizaciones")
    .orNull
    ?: "https://carlosalbertoxw.github.io/ollin-actividades/version.json"

android {
    namespace = "com.carlosalbertoxw.ollin.actividades"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.carlosalbertoxw.ollin.actividades"
        minSdk = 26
        targetSdk = 36
        versionCode = codigoDeVersion
        versionName = nombreDeVersion

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "URL_ACTUALIZACIONES", "\"$urlDeActualizaciones\"")
    }

    androidResources {
        // La app esta escrita en espanol; no se empaquetan los recursos de las
        // bibliotecas en los otros ochenta idiomas.
        localeFilters += listOf("es")
    }

    signingConfigs {
        create("release") {
            if (almacenDeClaves != null) {
                storeFile = almacenDeClaves
                storePassword = credencial("storePassword", "OLLIN_ACTIVIDADES_STORE_PASSWORD")
                keyAlias = credencial("keyAlias", "OLLIN_ACTIVIDADES_KEY_ALIAS")
                keyPassword = credencial("keyPassword", "OLLIN_ACTIVIDADES_KEY_PASSWORD")
            }
            // v1 no: minSdk 26 ya entiende v2, y firmar tambien el zip viejo
            // solo agrega una firma que nadie verifica.
            enableV1Signing = false
            enableV2Signing = true
            enableV3Signing = true
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            // Sin credenciales el artefacto sale sin firmar en vez de fallar la
            // compilacion: quien solo quiere comprobar que R8 no rompio nada no
            // tiene por que tener el almacen de claves de publicacion.
            signingConfig = signingConfigs.getByName("release").takeIf { almacenDeClaves != null }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    buildFeatures {
        compose = true
        // La pantalla de Acerca de ensena la version instalada y de ahi sale
        // tambien la direccion que se consulta para saber si hay una mas nueva.
        buildConfig = true
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }

    lint {
        // Un aviso que no rompe nada no se lee. Los que Lint marca como error
        // —fugas de contexto, APIs por encima del minSdk, permisos que faltan—
        // son cosas que en esta app se notarian en el telefono de alguien.
        abortOnError = true
        warningsAsErrors = false
        checkDependencies = true
        // La app es monolingue por decision explicita (localeFilters = "es"),
        // asi que las quejas por traducciones ausentes son ruido.
        disable += setOf("MissingTranslation", "ExtraTranslation")
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    // Los esquemas exportados viajan como assets de la suite instrumentada.
    // MigrationTestHelper los lee de ahi para comparar la base migrada contra
    // lo que Room espera; sin esta linea no encuentra ninguno y las pruebas de
    // migracion pasan sin comprobar nada.
    sourceSets.getByName("androidTest").assets.srcDir("$projectDir/schemas")
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.kotlinx.coroutines.android)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.biometric)
    // Debe ir explicito: sin el, biometric fija fragment en 1.2.5 y los
    // launchers de ActivityResult truenan al abrirse. Ver libs.versions.toml.
    implementation(libs.androidx.fragment)
    implementation(libs.sqlcipher.android)

    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core.ktx)

    // Las pruebas de interfaz corren sobre un telefono o un emulador. Se
    // intentaron en la JVM con Robolectric y no salio: su reloj virtual no
    // conversa con el cronometro de la pantalla de hoy ni con los dialogos.
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.test.core.ktx)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.room.runtime)
    // MigrationTestHelper: corre las migraciones de verdad contra los esquemas
    // exportados en app/schemas/. Ver MigracionesTest.
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
}
