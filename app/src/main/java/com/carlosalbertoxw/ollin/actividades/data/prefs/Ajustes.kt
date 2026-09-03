package com.carlosalbertoxw.ollin.actividades.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import com.carlosalbertoxw.ollin.actividades.data.excel.EsquemaExportacion
import com.carlosalbertoxw.ollin.actividades.data.excel.HojaExportable

private val Context.almacen by preferencesDataStore(name = "ollin_actividades_ajustes")

/** Con que se desbloquea Ollin al abrirla. */
enum class ModoBloqueo(val etiqueta: String) {
    NINGUNO("Sin bloqueo"),
    /** El patron, PIN, contrasena o huella del propio telefono. */
    SISTEMA("Del teléfono"),
    /** Un PIN exclusivo de Ollin, distinto al del telefono. */
    PIN("PIN propio")
}

/**
 * Preferencias de apariencia, metas, archivo y bloqueo. Las metas no son un
 * juicio: son la referencia contra la que la pantalla de hoy dibuja su barra, y
 * por eso se pueden mover.
 */
data class Ajustes(
    val temaOscuro: Boolean? = null,          // null = sigue al sistema
    val colorDinamico: Boolean = false,
    /** Minutos de trabajo que se consideran una jornada completa. */
    val metaTrabajoMinutos: Int = 300,
    /** Minutos de movimiento al dia. La OMS sugiere 150 a la semana. */
    val metaFisicoMinutos: Int = 30,
    /** Duracion que propone el boton rapido al registrar sin cronometro. */
    val duracionRapidaMinutos: Int = 25,
    val muestraCompletadasEnHoy: Boolean = true,
    /**
     * Interruptor maestro de los avisos de habitos y tareas.
     *
     * Nace encendido. Un recordatorio que hay que ir a activar a Ajustes lo
     * activa quien ya se acordaba solo, que es justo quien menos lo necesita;
     * la funcion se pagaba a si misma solo para los convencidos.
     *
     * Encendido no significa que suene sin permiso: desde Android 13 hace
     * falta POST_NOTIFICATIONS, y hasta que se conceda no llega nada. Ajustes
     * ensena el aviso de que falta, con el atajo para darlo.
     */
    val recordatorios: Boolean = true,
    /**
     * Si Ollin pregunta una vez al dia si hay una version mas nueva.
     *
     * Nace encendido, al reves que los recordatorios, porque no resuelven lo
     * mismo. Un aviso de habito lo puede dar la propia memoria; enterarse de
     * que se corrigio un fallo que te afecta, no, y una app que se instala
     * fuera de la tienda no tiene ningun otro canal para decirlo. Lo que sale
     * del telefono es un GET a un archivo estatico, sin nada dentro.
     */
    val buscarActualizaciones: Boolean = true,
    /** Milisegundos epoch de la ultima consulta hecha. Cero: nunca. */
    val ultimaComprobacion: Long = 0L,
    /** Ultima version que anuncio el sitio, aunque sea la que ya esta puesta. */
    val versionDisponible: String? = null,
    /** De donde se baja. Se abre en el navegador; Ollin nunca descarga sola. */
    val urlDeDescarga: String? = null,
    val notasDeVersion: String? = null,
    /**
     * Si Ollin recuerda hacer un respaldo en Excel cada semana.
     *
     * Nace encendido por lo mismo que el aviso de versiones: la base va cifrada
     * con una llave del Keystore que no se respalda ni viaja a otro telefono,
     * asi que el `.xlsx` no es *un* respaldo, es **el unico**. Quien lo apague
     * sabe lo que hace; quien nunca lo encienda se enteraria el dia que cambie
     * de telefono, que es tarde.
     */
    val avisaRespaldo: Boolean = true,
    /**
     * Desde cuando corre el plazo del proximo aviso de respaldo: el ultimo
     * respaldo hecho, o el primer arranque si todavia no hay ninguno.
     *
     * Uno solo y no dos porque las dos fechas responden la misma pregunta —"el
     * plazo empieza a contar aqui"— y llevarlas por separado obligaria a
     * decidir cual manda en cada lectura.
     */
    val respaldoDesde: Long = 0L,
    /**
     * Cuando se exporto por ultima vez de verdad. Cero: nunca.
     *
     * Separado de [respaldoDesde] porque responden preguntas distintas. El
     * ancla del plazo tambien la mueven el primer arranque y encender el
     * interruptor; esta solo la mueve un `.xlsx` escrito. Mezclarlas obligaria
     * al aviso a decir "hace 7 dias" a quien no ha respaldado nunca, que es
     * justo la frase que no hay que decir.
     */
    val ultimoRespaldo: Long = 0L,
    /** Cuando se aviso por ultima vez, para no repetirlo cada dia. */
    val ultimoAvisoRespaldo: Long = 0L,
    /** De que version ya se aviso, para no anunciarla dos veces. */
    val versionAvisada: String? = null,
    /** Interruptor maestro de las tarjetas de ayuda de cada pantalla. */
    val muestraTutoriales: Boolean = true,
    /** Claves de [com.carlosalbertoxw.ollin.actividades.ui.components.Tutorial] ya descartadas. */
    val tutorialesOcultos: Set<String> = emptySet(),
    val esquema: EsquemaExportacion = EsquemaExportacion.EXTENDIDO,
    val hojas: Set<HojaExportable> = HojaExportable.PREDETERMINADAS,
    /** Vacia la bitacora antes de importar. Si es falso, agrega. */
    val reemplazarAlImportar: Boolean = true,
    /** Da de alta categorias y habitos que el archivo mencione y no existan. */
    val creaFaltantesAlImportar: Boolean = true,
    val modoBloqueo: ModoBloqueo = ModoBloqueo.NINGUNO,
    /** Del PIN solo se guarda su huella derivada; el PIN en claro no se escribe nunca. */
    val pinHash: String? = null,
    val pinSal: String? = null,
    /**
     * Intentos fallidos seguidos contra el PIN. Se guarda en disco a proposito:
     * es lo que hace que matar la app no sirva para saltarse la espera.
     */
    val pinFallos: Int = 0
)

class AjustesRepositorio(private val contexto: Context) {

    private object Claves {
        val TEMA = stringPreferencesKey("tema")
        val DINAMICO = booleanPreferencesKey("color_dinamico")
        val META_TRABAJO = intPreferencesKey("meta_trabajo_minutos")
        val META_FISICO = intPreferencesKey("meta_fisico_minutos")
        val DURACION_RAPIDA = intPreferencesKey("duracion_rapida_minutos")
        val COMPLETADAS_HOY = booleanPreferencesKey("muestra_completadas_hoy")
        val TUTORIALES = booleanPreferencesKey("muestra_tutoriales")
        val TUTORIALES_OCULTOS = stringSetPreferencesKey("tutoriales_ocultos")
        val ESQUEMA = stringPreferencesKey("esquema")
        val HOJAS = stringSetPreferencesKey("hojas")
        val REEMPLAZAR = booleanPreferencesKey("reemplazar_al_importar")
        val CREA_FALTANTES = booleanPreferencesKey("crea_faltantes_al_importar")
        val BLOQUEO = stringPreferencesKey("modo_bloqueo")
        val PIN_HASH = stringPreferencesKey("pin_hash")
        val PIN_SAL = stringPreferencesKey("pin_sal")
        val PIN_FALLOS = intPreferencesKey("pin_fallos")
        val RECORDATORIOS = booleanPreferencesKey("recordatorios")
        val ACTUALIZACIONES = booleanPreferencesKey("buscar_actualizaciones")
        val ULTIMA_COMPROBACION = longPreferencesKey("ultima_comprobacion")
        val VERSION_DISPONIBLE = stringPreferencesKey("version_disponible")
        val URL_DESCARGA = stringPreferencesKey("url_descarga")
        val NOTAS_VERSION = stringPreferencesKey("notas_version")
        val AVISA_RESPALDO = booleanPreferencesKey("avisa_respaldo")
        val RESPALDO_DESDE = longPreferencesKey("respaldo_desde")
        val ULTIMO_RESPALDO = longPreferencesKey("ultimo_respaldo")
        val ULTIMO_AVISO_RESPALDO = longPreferencesKey("ultimo_aviso_respaldo")
        val VERSION_AVISADA = stringPreferencesKey("version_avisada")
    }

    val ajustes: Flow<Ajustes> = contexto.almacen.data.map(::interpreta)

    /**
     * Traduce lo que hay en disco.
     *
     * `internal` para poder probarla con unas preferencias escritas a mano,
     * incluidas las que dejo una version anterior. Ver [PreferenciasHeredadasTest].
     */
    internal fun interpreta(p: Preferences): Ajustes = conLoGuardado(p.asMap())

    private fun conLoGuardado(p: Map<Preferences.Key<*>, Any>): Ajustes = Ajustes(
        temaOscuro = when (p.lee(Claves.TEMA)) {
            "oscuro" -> true
            "claro" -> false
            else -> null
        },
        colorDinamico = p.lee(Claves.DINAMICO) ?: false,
        metaTrabajoMinutos = p.lee(Claves.META_TRABAJO) ?: 300,
        metaFisicoMinutos = p.lee(Claves.META_FISICO) ?: 30,
        duracionRapidaMinutos = p.lee(Claves.DURACION_RAPIDA) ?: 25,
        muestraCompletadasEnHoy = p.lee(Claves.COMPLETADAS_HOY) ?: true,
        muestraTutoriales = p.lee(Claves.TUTORIALES) ?: true,
        tutorialesOcultos = p.lee(Claves.TUTORIALES_OCULTOS) ?: emptySet(),
        esquema = p.lee(Claves.ESQUEMA)
            ?.let { runCatching { EsquemaExportacion.valueOf(it) }.getOrNull() }
            ?: EsquemaExportacion.EXTENDIDO,
        hojas = p.lee(Claves.HOJAS)
            ?.mapNotNull { runCatching { HojaExportable.valueOf(it) }.getOrNull() }
            ?.toSet()
            ?.takeIf { it.isNotEmpty() }
            ?: HojaExportable.PREDETERMINADAS,
        reemplazarAlImportar = p.lee(Claves.REEMPLAZAR) ?: true,
        creaFaltantesAlImportar = p.lee(Claves.CREA_FALTANTES) ?: true,
        modoBloqueo = p.lee(Claves.BLOQUEO)
            ?.let { runCatching { ModoBloqueo.valueOf(it) }.getOrNull() }
            ?: ModoBloqueo.NINGUNO,
        pinHash = p.lee(Claves.PIN_HASH),
        pinSal = p.lee(Claves.PIN_SAL),
        pinFallos = p.lee(Claves.PIN_FALLOS) ?: 0,
        recordatorios = p.lee(Claves.RECORDATORIOS) ?: true,
        buscarActualizaciones = p.lee(Claves.ACTUALIZACIONES) ?: true,
        ultimaComprobacion = p.lee(Claves.ULTIMA_COMPROBACION) ?: 0L,
        versionDisponible = p.lee(Claves.VERSION_DISPONIBLE),
        urlDeDescarga = p.lee(Claves.URL_DESCARGA),
        notasDeVersion = p.lee(Claves.NOTAS_VERSION),
        avisaRespaldo = p.lee(Claves.AVISA_RESPALDO) ?: true,
        respaldoDesde = p.lee(Claves.RESPALDO_DESDE) ?: 0L,
        ultimoRespaldo = p.lee(Claves.ULTIMO_RESPALDO) ?: 0L,
        ultimoAvisoRespaldo = p.lee(Claves.ULTIMO_AVISO_RESPALDO) ?: 0L,
        versionAvisada = p.lee(Claves.VERSION_AVISADA)
    )

    suspend fun guardaTema(oscuro: Boolean?) {
        contexto.almacen.edit {
            when (oscuro) {
                true -> it[Claves.TEMA] = "oscuro"
                false -> it[Claves.TEMA] = "claro"
                null -> it.remove(Claves.TEMA)
            }
        }
    }

    suspend fun guardaColorDinamico(valor: Boolean) {
        contexto.almacen.edit { it[Claves.DINAMICO] = valor }
    }

    suspend fun guardaMetaTrabajo(minutos: Int) {
        contexto.almacen.edit { it[Claves.META_TRABAJO] = minutos.coerceIn(0, 24 * 60) }
    }

    suspend fun guardaMetaFisico(minutos: Int) {
        contexto.almacen.edit { it[Claves.META_FISICO] = minutos.coerceIn(0, 24 * 60) }
    }

    suspend fun guardaDuracionRapida(minutos: Int) {
        contexto.almacen.edit { it[Claves.DURACION_RAPIDA] = minutos.coerceIn(1, 8 * 60) }
    }

    suspend fun guardaMuestraCompletadas(valor: Boolean) {
        contexto.almacen.edit { it[Claves.COMPLETADAS_HOY] = valor }
    }

    // ----------------------------------------------------------- tutoriales

    suspend fun guardaMuestraTutoriales(valor: Boolean) {
        contexto.almacen.edit { it[Claves.TUTORIALES] = valor }
    }

    /** Descarta una sola tarjeta. Las demas siguen apareciendo. */
    suspend fun ocultaTutorial(clave: String) {
        contexto.almacen.edit {
            it[Claves.TUTORIALES_OCULTOS] = (it[Claves.TUTORIALES_OCULTOS] ?: emptySet()) + clave
        }
    }

    /** Vuelve a poner todas, y de paso reactiva el interruptor maestro. */
    suspend fun reiniciaTutoriales() {
        contexto.almacen.edit {
            it.remove(Claves.TUTORIALES_OCULTOS)
            it[Claves.TUTORIALES] = true
        }
    }

    // ------------------------------------------------------------- archivo

    suspend fun guardaEsquema(esquema: EsquemaExportacion) {
        contexto.almacen.edit { it[Claves.ESQUEMA] = esquema.name }
    }

    suspend fun guardaHojas(hojas: Set<HojaExportable>) {
        contexto.almacen.edit {
            it[Claves.HOJAS] = HojaExportable.normaliza(hojas).map(HojaExportable::name).toSet()
        }
    }

    suspend fun guardaReemplazar(valor: Boolean) {
        contexto.almacen.edit { it[Claves.REEMPLAZAR] = valor }
    }

    suspend fun guardaCreaFaltantes(valor: Boolean) {
        contexto.almacen.edit { it[Claves.CREA_FALTANTES] = valor }
    }

    // ------------------------------------------------------------- bloqueo

    /**
     * Las tres transiciones de bloqueo se escriben de golpe. Si el modo y el PIN
     * se guardaran por separado podria quedar un "modo PIN" sin PIN, y eso deja
     * la app cerrada sin llave.
     */
    suspend fun activaBloqueoSistema() {
        contexto.almacen.edit {
            it[Claves.BLOQUEO] = ModoBloqueo.SISTEMA.name
            it.remove(Claves.PIN_HASH)
            it.remove(Claves.PIN_SAL)
            it.remove(Claves.PIN_FALLOS)
        }
    }

    suspend fun activaBloqueoPin(hash: String, sal: String) {
        contexto.almacen.edit {
            it[Claves.BLOQUEO] = ModoBloqueo.PIN.name
            it[Claves.PIN_HASH] = hash
            it[Claves.PIN_SAL] = sal
            // Un PIN nuevo estrena contador: la espera que dejo el anterior no
            // tiene por que heredarla quien acaba de demostrar que es el dueno.
            it.remove(Claves.PIN_FALLOS)
        }
    }

    suspend fun quitaBloqueo() {
        contexto.almacen.edit {
            it.remove(Claves.BLOQUEO)
            it.remove(Claves.PIN_HASH)
            it.remove(Claves.PIN_SAL)
            it.remove(Claves.PIN_FALLOS)
        }
    }

    suspend fun guardaRecordatorios(valor: Boolean) {
        contexto.almacen.edit { it[Claves.RECORDATORIOS] = valor }
    }

    // ------------------------------------------------------ actualizaciones

    /**
     * Mover el interruptor olvida lo que se supo la ultima vez, en los dos
     * sentidos y por razones distintas.
     *
     * Al **apagarlo**, porque si no quedaria en pantalla el aviso de una
     * version nueva que ya nadie va a volver a comprobar, sin forma de saber si
     * sigue siendo cierto.
     *
     * Al **encenderlo**, porque se borra tambien la marca de tiempo: quien
     * acaba de activarlo espera enterarse ahora y no cuando venza el dia que
     * corria desde la ultima consulta, que pudo ser hace meses.
     */
    suspend fun guardaBuscarActualizaciones(valor: Boolean) {
        contexto.almacen.edit {
            it[Claves.ACTUALIZACIONES] = valor
            it.remove(Claves.ULTIMA_COMPROBACION)
            it.remove(Claves.VERSION_DISPONIBLE)
            it.remove(Claves.URL_DESCARGA)
            it.remove(Claves.NOTAS_VERSION)
        }
    }

    /** Lo que dijo el sitio y cuando se le pregunto, de una sola escritura. */
    suspend fun guardaComprobacion(
        cuando: Long,
        version: String,
        url: String,
        notas: String?
    ) {
        contexto.almacen.edit {
            it[Claves.ULTIMA_COMPROBACION] = cuando
            it[Claves.VERSION_DISPONIBLE] = version
            it[Claves.URL_DESCARGA] = url
            if (notas.isNullOrBlank()) it.remove(Claves.NOTAS_VERSION)
            else it[Claves.NOTAS_VERSION] = notas
        }
    }

    /** Suma un fallo. El contador no tiene techo; la espera si. */
    suspend fun sumaFalloPin() {
        contexto.almacen.edit {
            it[Claves.PIN_FALLOS] = (it[Claves.PIN_FALLOS] ?: 0) + 1
        }
    }

    // ------------------------------------------------------------ respaldo

    suspend fun guardaAvisaRespaldo(valor: Boolean) {
        contexto.almacen.edit {
            it[Claves.AVISA_RESPALDO] = valor
            // Encenderlo estrena plazo: quien lo activa hoy no quiere un aviso
            // inmediato porque lleve meses sin exportar, quiere el de dentro de
            // una semana. Apagarlo tambien lo limpia, para que volver a
            // encenderlo no arrastre una cuenta vieja.
            it.remove(Claves.ULTIMO_AVISO_RESPALDO)
            it[Claves.RESPALDO_DESDE] = System.currentTimeMillis()
        }
    }

    /**
     * Un respaldo hecho reinicia el plazo y borra el aviso pendiente.
     *
     * Lo llama la exportacion cuando termina bien. No distingue a donde se
     * guardo el archivo: si el sistema acepto la escritura, hay un `.xlsx` en
     * algun sitio que la persona eligio, y eso es exactamente lo que el aviso
     * pedia.
     */
    suspend fun marcaRespaldo(cuando: Long = System.currentTimeMillis()) {
        contexto.almacen.edit {
            it[Claves.RESPALDO_DESDE] = cuando
            it[Claves.ULTIMO_RESPALDO] = cuando
            it.remove(Claves.ULTIMO_AVISO_RESPALDO)
        }
    }

    /** El ancla del plazo la primera vez, sin fingir que hubo un respaldo. */
    suspend fun estrenaPlazoDeRespaldo(cuando: Long = System.currentTimeMillis()) {
        contexto.almacen.edit {
            if (it[Claves.RESPALDO_DESDE] == null) it[Claves.RESPALDO_DESDE] = cuando
        }
    }

    suspend fun marcaAvisoDeRespaldo(cuando: Long = System.currentTimeMillis()) {
        contexto.almacen.edit { it[Claves.ULTIMO_AVISO_RESPALDO] = cuando }
    }

    /** De que version ya se aviso. Evita anunciar la misma cada dia. */
    suspend fun marcaVersionAvisada(version: String) {
        contexto.almacen.edit { it[Claves.VERSION_AVISADA] = version }
    }

    /** Lo unico que pone el contador a cero es acertar el PIN. */
    suspend fun limpiaFallosPin() {
        contexto.almacen.edit { it.remove(Claves.PIN_FALLOS) }
    }
}

/**
 * Lee una clave comprobando su tipo, no confiando en el.
 *
 * DataStore guarda el tipo junto al valor, asi que pedir como texto algo que
 * una version anterior escribio como entero revienta. Y revienta lejos: con
 * los genericos borrados, el `checkcast` no queda dentro de esta funcion sino
 * en el punto donde se usa el valor, asi que envolverla en un `runCatching` no
 * atrapa nada. La unica forma de comprobarlo de verdad es preguntar por el tipo
 * en tiempo de ejecucion, que es lo que hace `as?` con un parametro `reified`.
 *
 * Importa porque esto corre dentro del Flow que alimenta el arranque y todas
 * las pantallas: una excepcion aqui no se queda en una preferencia perdida,
 * cierra la app en el telefono de quien actualiza. Un valor que no cuadra se
 * trata como ausente y se cae al de fabrica.
 *
 * La regla que evita llegar hasta aqui: **una clave no cambia de tipo nunca**.
 * Si el dato cambia de forma se estrena nombre, y el viejo se barre. Esto es la
 * red de abajo, para que el dia que se olvide no cueste una version publicada,
 * como le costo a Ollin Finanzas entre su 1.0.0 y su 1.0.1.
 */
private inline fun <reified T> Map<Preferences.Key<*>, Any>.lee(
    clave: Preferences.Key<T>
): T? = this[clave] as? T
