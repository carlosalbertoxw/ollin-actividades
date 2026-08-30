package com.carlosalbertoxw.ollin.actividades.di

import android.content.Context
import com.carlosalbertoxw.ollin.actividades.data.db.OllinDatabase
import com.carlosalbertoxw.ollin.actividades.data.db.Sembrador
import com.carlosalbertoxw.ollin.actividades.data.prefs.AjustesRepositorio
import com.carlosalbertoxw.ollin.actividades.data.recordatorios.CoordinadorRecordatorios
import com.carlosalbertoxw.ollin.actividades.data.recordatorios.PlanificadorRecordatorios
import com.carlosalbertoxw.ollin.actividades.data.repo.ActividadesRepositorio
import com.carlosalbertoxw.ollin.actividades.data.seguridad.ControlBloqueo

/**
 * Inyeccion de dependencias a mano.
 *
 * Con un solo modulo y media docena de objetos compartidos, Hilt aportaria
 * anotaciones y tiempo de compilacion sin resolver ningun problema real. Esto
 * se lee de arriba a abajo y no tiene magia.
 */
class Contenedor(
    contexto: Context,
    /**
     * Solo las pruebas la pasan, para colar una base en memoria. SQLCipher es
     * una biblioteca nativa de Android y en la JVM no existe, asi que sin esta
     * costura no habria forma de montar una pantalla sin un telefono enfrente.
     */
    private val abreBase: (() -> OllinDatabase)? = null
) {

    private val app = contexto.applicationContext

    val baseDeDatos: OllinDatabase by lazy { abreBase?.invoke() ?: OllinDatabase.obten(app) }

    val repositorio: ActividadesRepositorio by lazy {
        ActividadesRepositorio(baseDeDatos, app.contentResolver)
    }

    val ajustes: AjustesRepositorio by lazy { AjustesRepositorio(app) }

    val controlBloqueo: ControlBloqueo by lazy { ControlBloqueo(ajustes) }

    val sembrador: Sembrador by lazy { Sembrador(baseDeDatos.categoriaDao()) }

    val recordatorios: CoordinadorRecordatorios by lazy {
        CoordinadorRecordatorios(
            contexto = app,
            planificador = PlanificadorRecordatorios(
                baseDeDatos.habitoDao(),
                baseDeDatos.actividadDao()
            ),
            ajustes = ajustes
        )
    }
}
