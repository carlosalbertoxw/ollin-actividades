package com.carlosalbertoxw.ollin.actividades.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.carlosalbertoxw.ollin.actividades.data.db.Categoria
import com.carlosalbertoxw.ollin.actividades.data.repo.HabitoConAvance
import com.carlosalbertoxw.ollin.actividades.di.Contenedor
import com.carlosalbertoxw.ollin.actividades.domain.model.Ambito
import com.carlosalbertoxw.ollin.actividades.domain.model.EstadoActividad
import com.carlosalbertoxw.ollin.actividades.domain.model.Tiempo
import com.carlosalbertoxw.ollin.actividades.ui.components.AyudaDePantalla
import com.carlosalbertoxw.ollin.actividades.ui.components.DialogoDeshacerHabito
import com.carlosalbertoxw.ollin.actividades.ui.components.Punto
import com.carlosalbertoxw.ollin.actividades.ui.components.RenglonActividad
import com.carlosalbertoxw.ollin.actividades.ui.components.SeccionTitulo
import com.carlosalbertoxw.ollin.actividades.ui.components.Tutorial
import com.carlosalbertoxw.ollin.actividades.ui.recuerdaVm
import com.carlosalbertoxw.ollin.actividades.ui.theme.LocalColoresOllin
import com.carlosalbertoxw.ollin.actividades.ui.theme.colorDeCategoria
import java.time.LocalDate

@Composable
fun HoyPantalla(
    contenedor: Contenedor,
    alAbrirActividad: (Long) -> Unit,
    /** Abre la captura del habito en el dia que se esta viendo, no siempre hoy. */
    alRegistrarHabito: (Long, LocalDate) -> Unit,
    alAbrirAjustes: () -> Unit,
    /** Lo abre el aviso de respaldo. */
    alAbrirArchivo: () -> Unit = {}
) {
    val vm = recuerdaVm("hoy") {
        HoyVm(contenedor.repositorio, contenedor.ajustes, contenedor.avisoDeRespaldo)
    }
    val avisoRespaldo by vm.avisoRespaldo.collectAsStateWithLifecycle()
    val dia by vm.dia.collectAsStateWithLifecycle()
    val enCurso by vm.enCurso.collectAsStateWithLifecycle()
    val delDia by vm.delDia.collectAsStateWithLifecycle()
    val pendientes by vm.pendientes.collectAsStateWithLifecycle()
    val habitos by vm.habitos.collectAsStateWithLifecycle()
    val categorias by vm.categorias.collectAsStateWithLifecycle()
    val indiceCategorias by vm.indiceCategorias.collectAsStateWithLifecycle()
    val categoriaRapida by vm.categoriaRapida.collectAsStateWithLifecycle()
    val minutos by vm.minutosPorAmbito.collectAsStateWithLifecycle()
    val ajustes by vm.ajustes.collectAsStateWithLifecycle()
    val colores = LocalColoresOllin.current

    /** El habito cuyo deshacer se esta confirmando. */
    var deshaciendo by remember { mutableStateOf<HabitoConAvance?>(null) }

    // El latido se toma sin `by` a proposito: leerlo aqui haria que cada segundo
    // se recompusiera la pantalla entera —las metas, los habitos, la bitacora—
    // para mover un solo numero. Se pasa como funcion y lo lee quien lo pinta.
    val ahora = vm.ahora.collectAsStateWithLifecycle()

    // Derivados de listas que pueden ser largas. Sin recordarlos se recalculan
    // en cada recomposicion, incluidas las que provoca el cronometro.
    val completadas = remember(delDia) {
        delDia.filter { it.actividad.estado == EstadoActividad.COMPLETADO }
    }
    val totalHoy = remember(completadas) {
        completadas.sumOf { it.actividad.duracionMinutos ?: 0 }
    }
    val habitosDeHoy = remember(habitos) { ordenDeHoy(habitos) }
    val cumplidosDeHoy = remember(habitosDeHoy) { habitosDeHoy.count { it.cumplidoHoy } }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 96.dp)
    ) {
        item(key = "encabezado") {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 8.dp, top = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(Tiempo.diaRelativo(dia), style = MaterialTheme.typography.headlineSmall)
                    Text(
                        if (totalHoy > 0) {
                            "${Tiempo.duracionLarga(totalHoy)} registradas"
                        } else {
                            "Sin tiempo registrado todavía"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = colores.textoTenue
                    )
                }
                IconButton(onClick = alAbrirAjustes) {
                    Icon(Icons.Filled.Settings, contentDescription = "Ajustes")
                }
            }
        }

        // Arriba de todo lo demas: es lo unico de esta pantalla que, si se
        // ignora, puede costar la bitacora entera.
        avisoRespaldo?.let { titulo ->
            item(key = "aviso-respaldo") {
                AvisoRespaldo(
                    titulo = titulo,
                    alExportar = alAbrirArchivo,
                    alQuitar = vm::descartaAvisoRespaldo,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp)
                )
            }
        }

        item(key = "ayuda") {
            AyudaDePantalla(
                contenedor,
                Tutorial.HOY,
                Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp)
            )
        }

        item(key = "cronometro") {
            TarjetaCronometro(
                enCurso = enCurso,
                ahora = { ahora.value },
                categorias = categorias,
                categoriaRapida = categoriaRapida,
                alElegirCategoria = vm::eligeCategoriaRapida,
                alIniciar = vm::inicia,
                alDetener = vm::detiene,
                alAbrir = { enCurso?.actividad?.id?.let(alAbrirActividad) },
                modifier = Modifier.padding(16.dp, 12.dp, 16.dp, 4.dp)
            )
        }

        item(key = "metas") {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MetaDelDia(
                    etiqueta = "Trabajo",
                    minutos = minutos[Ambito.TRABAJO] ?: 0,
                    meta = ajustes.metaTrabajoMinutos,
                    color = colores.trabajo,
                    modifier = Modifier.weight(1f)
                )
                MetaDelDia(
                    etiqueta = "Movimiento",
                    minutos = minutos[Ambito.FISICO] ?: 0,
                    meta = ajustes.metaFisicoMinutos,
                    color = colores.fisico,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // La seccion se decide con la lista ya filtrada: un dia en que ningun
        // habito toca no debe abrir un apartado vacio, y la cuenta tiene que
        // contar sobre lo mismo que se enseña o acabaria diciendo "3 de 2".
        if (habitosDeHoy.isNotEmpty()) {
            item(key = "titulo-habitos") {
                SeccionTitulo(
                    "Hábitos de hoy",
                    Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        "$cumplidosDeHoy de ${habitosDeHoy.size}",
                        style = MaterialTheme.typography.bodySmall,
                        color = colores.textoTenue
                    )
                }
            }
            // Las claves llevan prefijo porque en esta lista conviven habitos y
            // actividades, y sus identificadores salen de tablas distintas: el
            // primer habito y la primera actividad son los dos el 1, y una clave
            // repetida en un LazyColumn no es un aviso, es un cierre en seco.
            items(habitosDeHoy, key = { "habito-${it.habito.id}" }) { avance ->
                RenglonHabitoHoy(
                    avance = avance,
                    categoria = avance.habito.categoriaId?.let(indiceCategorias::get),
                    alAlternar = {
                        if (avance.cumplidoHoy) {
                            deshaciendo = avance
                        } else {
                            alRegistrarHabito(avance.habito.id, dia)
                        }
                    },
                    alCronometrar = { vm.cronometraHabito(avance.habito) }
                )
            }
        }

        if (pendientes.isNotEmpty()) {
            item(key = "titulo-pendientes") {
                SeccionTitulo(
                    "Pendientes",
                    Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
            // Sin `ahora`: una pendiente no lleva tiempo corriendo, asi que el
            // renglon se pinta igual con cualquier reloj.
            items(pendientes, key = { "pendiente-${it.actividad.id}" }) { detalle ->
                RenglonActividad(
                    detalle = detalle,
                    alPulsar = { alAbrirActividad(detalle.actividad.id) },
                    accion = {
                        Row {
                            IconButton(onClick = { vm.arranca(detalle.actividad.id) }) {
                                Icon(
                                    Icons.Filled.PlayArrow,
                                    contentDescription = "Iniciar",
                                    tint = colores.enCurso
                                )
                            }
                            IconButton(onClick = {
                                vm.completaSinCronometro(detalle.actividad.id)
                            }) {
                                Icon(
                                    Icons.Filled.Check,
                                    contentDescription = "Marcar como hecha",
                                    tint = colores.completado
                                )
                            }
                        }
                    }
                )
            }
        }

        if (ajustes.muestraCompletadasEnHoy && completadas.isNotEmpty()) {
            item(key = "titulo-registro") {
                SeccionTitulo(
                    "Registro de hoy",
                    Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        Tiempo.duracion(totalHoy),
                        style = MaterialTheme.typography.bodySmall,
                        color = colores.textoTenue
                    )
                }
            }
            items(completadas, key = { "hecho-${it.actividad.id}" }) { detalle ->
                RenglonActividad(
                    detalle = detalle,
                    alPulsar = { alAbrirActividad(detalle.actividad.id) }
                )
                HorizontalDivider(color = colores.trazoSuave)
            }
        }
    }

    deshaciendo?.let { avance ->
        DialogoDeshacerHabito(
            nombre = avance.habito.nombre,
            dia = dia,
            alConfirmar = { vm.deshace(avance) },
            alCerrar = { deshaciendo = null }
        )
    }
}

/**
 * Los habitos que hoy hay que atender, con lo vencido arriba.
 *
 * Lo vencido sube porque es lo unico de la lista que ya se fallo, y entre lo
 * vencido manda lo que lleva mas esperando: una deuda de tres semanas no puede
 * quedar debajo de una de ayer solo porque se creo despues.
 *
 * El resto conserva el orden que eligio su dueno, y eso depende de que la
 * ordenacion sea estable: si lo que toca hoy se reacomodara cada vez que algo
 * vence, la lista dejaria de estar donde uno la dejo.
 */
internal fun ordenDeHoy(habitos: List<HabitoConAvance>): List<HabitoConAvance> = habitos
    .filter { it.tocaHoy }
    .sortedWith(compareBy(nullsLast<LocalDate>()) { it.vencidoDesde })

@Composable
private fun RenglonHabitoHoy(
    avance: HabitoConAvance,
    categoria: Categoria?,
    alAlternar: () -> Unit,
    alCronometrar: () -> Unit
) {
    val colores = LocalColoresOllin.current
    val cumplido = avance.cumplidoHoy
    val colorCategoria = colorDeCategoria(
        categoria?.colorHex,
        colores.de(categoria?.ambito ?: Ambito.HABITO)
    )

    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(if (cumplido) colores.completado else colores.trazoSuave),
            contentAlignment = Alignment.Center
        ) {
            if (cumplido) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(avance.habito.nombre, style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(2.dp))
            // La categoria abre el renglon: es lo que dice a que area de la vida
            // pertenece el habito, y su punto de color repite el codigo que la
            // analitica usa para agrupar.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Punto(colorCategoria, 7)
                Spacer(Modifier.width(6.dp))
                Text(
                    buildString {
                        append(categoria?.nombre ?: "Sin categoría")
                        append(" · ")
                        if (avance.racha.actual > 0) {
                            append("Racha de ${avance.racha.actual} ${avance.racha.unidad}")
                        } else {
                            append("Sin racha activa")
                        }
                        val meta = avance.habito.metaDiaria
                        if (meta > 1) append(" · ${avance.vecesHoy}/$meta hoy")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = colores.textoTenue,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            // Lo vencido se ve aqui y no en el renglon de arriba: la deuda es
            // lo que decide si vale la pena ponerse ahora, y compitiendo por el
            // mismo renglon con la categoria y la racha se la comian los
            // puntos suspensivos justo cuando mas importa.
            avance.vencidoDesde?.let { desde ->
                Spacer(Modifier.height(2.dp))
                Text(
                    "Vencido · tocaba el ${Tiempo.fechaCortaConAnio(desde)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        IconButton(onClick = alCronometrar) {
            Icon(
                Icons.Filled.PlayArrow,
                contentDescription = "Cronometrar",
                tint = colores.textoTenue
            )
        }
        IconButton(onClick = alAlternar) {
            Icon(
                if (cumplido) Icons.AutoMirrored.Filled.Undo else Icons.Filled.Check,
                contentDescription = if (cumplido) "Deshacer" else "Marcar",
                tint = if (cumplido) colores.textoTenue else colores.completado
            )
        }
    }
}
