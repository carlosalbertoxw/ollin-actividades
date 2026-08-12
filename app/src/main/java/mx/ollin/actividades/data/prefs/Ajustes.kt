package mx.ollin.actividades.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import mx.ollin.actividades.data.excel.EsquemaExportacion
import mx.ollin.actividades.data.excel.HojaExportable

private val Context.almacen by preferencesDataStore(name = "ollin_actividades_ajustes")

/** Con que se desbloquea Ollin al abrirla. */
enum class ModoBloqueo(val etiqueta: String) {
    NINGUNO("Sin bloqueo"),
    /** El patron, PIN, contrasena o huella del propio telefono. */
    SISTEMA("Del telefono"),
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
    /** Interruptor maestro de las tarjetas de ayuda de cada pantalla. */
    val muestraTutoriales: Boolean = true,
    /** Claves de [mx.ollin.actividades.ui.components.Tutorial] ya descartadas. */
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
    val pinSal: String? = null
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
    }

    val ajustes: Flow<Ajustes> = contexto.almacen.data.map(::mapea)

    private fun mapea(p: Preferences): Ajustes = Ajustes(
        temaOscuro = when (p[Claves.TEMA]) {
            "oscuro" -> true
            "claro" -> false
            else -> null
        },
        colorDinamico = p[Claves.DINAMICO] ?: false,
        metaTrabajoMinutos = p[Claves.META_TRABAJO] ?: 300,
        metaFisicoMinutos = p[Claves.META_FISICO] ?: 30,
        duracionRapidaMinutos = p[Claves.DURACION_RAPIDA] ?: 25,
        muestraCompletadasEnHoy = p[Claves.COMPLETADAS_HOY] ?: true,
        muestraTutoriales = p[Claves.TUTORIALES] ?: true,
        tutorialesOcultos = p[Claves.TUTORIALES_OCULTOS] ?: emptySet(),
        esquema = p[Claves.ESQUEMA]
            ?.let { runCatching { EsquemaExportacion.valueOf(it) }.getOrNull() }
            ?: EsquemaExportacion.EXTENDIDO,
        hojas = p[Claves.HOJAS]
            ?.mapNotNull { runCatching { HojaExportable.valueOf(it) }.getOrNull() }
            ?.toSet()
            ?.takeIf { it.isNotEmpty() }
            ?: HojaExportable.PREDETERMINADAS,
        reemplazarAlImportar = p[Claves.REEMPLAZAR] ?: true,
        creaFaltantesAlImportar = p[Claves.CREA_FALTANTES] ?: true,
        modoBloqueo = p[Claves.BLOQUEO]
            ?.let { runCatching { ModoBloqueo.valueOf(it) }.getOrNull() }
            ?: ModoBloqueo.NINGUNO,
        pinHash = p[Claves.PIN_HASH],
        pinSal = p[Claves.PIN_SAL]
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
        }
    }

    suspend fun activaBloqueoPin(hash: String, sal: String) {
        contexto.almacen.edit {
            it[Claves.BLOQUEO] = ModoBloqueo.PIN.name
            it[Claves.PIN_HASH] = hash
            it[Claves.PIN_SAL] = sal
        }
    }

    suspend fun quitaBloqueo() {
        contexto.almacen.edit {
            it.remove(Claves.BLOQUEO)
            it.remove(Claves.PIN_HASH)
            it.remove(Claves.PIN_SAL)
        }
    }
}
