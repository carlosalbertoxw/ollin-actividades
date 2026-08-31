package com.carlosalbertoxw.ollin.actividades.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.carlosalbertoxw.ollin.actividades.data.actualizaciones.ComprobadorActualizaciones
import com.carlosalbertoxw.ollin.actividades.data.actualizaciones.Resultado
import com.carlosalbertoxw.ollin.actividades.data.actualizaciones.Version
import com.carlosalbertoxw.ollin.actividades.data.prefs.AjustesRepositorio
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Lo que la pantalla de Acerca de sabe de las versiones.
 *
 * El estado sale de dos sitios que no se parecen: lo que quedo escrito de la
 * ultima comprobacion —que sobrevive a cerrar la app— y lo que esta pasando
 * ahora mismo con el boton —que no—. Se combinan aqui para que la pantalla
 * reciba un solo objeto y no tenga que decidir cual manda.
 */
class AcercaDeVm(
    private val repo: AjustesRepositorio,
    private val comprobador: ComprobadorActualizaciones,
    /** La version de este APK, de `BuildConfig.VERSION_NAME`. */
    val instalada: String
) : ViewModel() {

    data class Estado(
        val instalada: String = "",
        /** Lo ultimo que anuncio el sitio. Nulo si nunca se ha preguntado. */
        val disponible: Version? = null,
        val urlDeDescarga: String? = null,
        val notas: String? = null,
        /** Milisegundos epoch de la ultima consulta. Cero: ninguna todavia. */
        val ultimaComprobacion: Long = 0L,
        val activa: Boolean = true,
        val consultando: Boolean = false,
        /** Solo lo deja el boton, y se borra al volver a pulsarlo. */
        val aviso: String? = null
    ) {
        /**
         * Cierto solo cuando lo publicado es estrictamente mas nuevo.
         *
         * Se compara con [Version] y no con texto: "1.10.0" es posterior a
         * "1.9.0" aunque el orden alfabetico diga lo contrario.
         */
        val hayVersionNueva: Boolean
            get() {
                val puesta = Version.de(instalada) ?: return false
                return disponible != null && disponible > puesta
            }
    }

    /** Lo que solo vive mientras la pantalla esta abierta. */
    private data class Volatil(val consultando: Boolean = false, val aviso: String? = null)

    private val volatil = MutableStateFlow(Volatil())

    val estado: StateFlow<Estado> = combine(repo.ajustes, volatil) { ajustes, ahora ->
        Estado(
            instalada = instalada,
            disponible = Version.de(ajustes.versionDisponible),
            urlDeDescarga = ajustes.urlDeDescarga,
            notas = ajustes.notasDeVersion,
            ultimaComprobacion = ajustes.ultimaComprobacion,
            activa = ajustes.buscarActualizaciones,
            consultando = ahora.consultando,
            aviso = ahora.aviso
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        Estado(instalada = instalada)
    )

    /**
     * La comprobacion a mano.
     *
     * No mira si toca: quien pulsa el boton quiere saberlo ahora, y hacerle
     * esperar al dia siguiente porque ya se pregunto por la manana convierte un
     * boton en un adorno.
     */
    fun compruebaAhora() {
        if (volatil.value.consultando) return
        volatil.update { Volatil(consultando = true, aviso = null) }

        viewModelScope.launch {
            val aviso = when (val resultado = comprobador.compruebaAhora()) {
                is Resultado.HayVersionNueva -> null
                Resultado.AlDia -> "Ya tienes la última versión."
                // El motivo real —tiempo agotado, DNS, un 503— no le dice nada
                // a quien mira la pantalla y de paso cuenta como esta hecha la
                // app. El detalle se queda en el resultado; aqui va la frase.
                is Resultado.Fallo -> resultado.motivo
                Resultado.NoTocaba -> "La búsqueda de actualizaciones está apagada."
            }
            volatil.value = Volatil(consultando = false, aviso = aviso)
        }
    }
}
