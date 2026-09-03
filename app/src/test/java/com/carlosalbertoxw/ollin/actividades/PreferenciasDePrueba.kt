package com.carlosalbertoxw.ollin.actividades

import kotlinx.coroutines.runBlocking
import com.carlosalbertoxw.ollin.actividades.data.excel.EsquemaExportacion
import com.carlosalbertoxw.ollin.actividades.data.excel.HojaExportable
import com.carlosalbertoxw.ollin.actividades.data.prefs.Ajustes
import com.carlosalbertoxw.ollin.actividades.data.prefs.AjustesRepositorio

/**
 * Devuelve las preferencias a su estado de fabrica.
 *
 * Hace falta porque el DataStore lo sirve un delegado que cachea una sola
 * instancia por nombre, y ese cache sobrevive de una prueba a la siguiente
 * dentro de la misma clase. Sin esto, lo que una prueba guarda condicionaria a
 * la que corra despues y el resultado de la suite dependeria del orden.
 *
 * Los valores salen de [Ajustes], que es donde estan declarados: si alguno
 * cambia, esto lo sigue solo.
 */
fun AjustesRepositorio.restauraDeFabrica() = runBlocking {
    val omision = Ajustes()
    guardaTema(omision.temaOscuro)
    guardaColorDinamico(omision.colorDinamico)
    guardaMetaTrabajo(omision.metaTrabajoMinutos)
    guardaMetaFisico(omision.metaFisicoMinutos)
    guardaDuracionRapida(omision.duracionRapidaMinutos)
    guardaMuestraCompletadas(omision.muestraCompletadasEnHoy)
    guardaRecordatorios(omision.recordatorios)
    guardaBuscarActualizaciones(omision.buscarActualizaciones)
    guardaAvisaRespaldo(omision.avisaRespaldo)
    reiniciaTutoriales()
    guardaEsquema(omision.esquema)
    guardaHojas(omision.hojas)
    guardaReemplazar(omision.reemplazarAlImportar)
    guardaCreaFaltantes(omision.creaFaltantesAlImportar)
    quitaBloqueo()
}
