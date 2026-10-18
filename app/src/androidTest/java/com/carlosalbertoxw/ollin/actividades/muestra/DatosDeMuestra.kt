package com.carlosalbertoxw.ollin.actividades.muestra

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.carlosalbertoxw.ollin.actividades.OllinApp
import com.carlosalbertoxw.ollin.actividades.data.db.Actividad
import com.carlosalbertoxw.ollin.actividades.data.db.Habito
import com.carlosalbertoxw.ollin.actividades.domain.model.EstadoActividad
import com.carlosalbertoxw.ollin.actividades.domain.model.Frecuencia
import com.carlosalbertoxw.ollin.actividades.domain.model.Tiempo
import com.carlosalbertoxw.ollin.actividades.domain.model.Unidad
import com.carlosalbertoxw.ollin.actividades.ui.restauraDeFabrica
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import kotlin.random.Random

/**
 * Llena la app `.debug` del telefono con seis semanas de una bitacora
 * inventada, para sacar las capturas de la ficha de Play sin ensenar datos de
 * nadie.
 *
 * Escribe en la base de verdad, no en una en memoria como las demas pruebas, y
 * antes la vacia. Por eso no corre si no se le pide con el argumento
 * `muestra=si`: la suite completa pasa de largo por aqui y no se lleva los
 * datos de quien la corre en su telefono. Ver docs/publicacion.md.
 *
 * La semilla del azar es fija, asi que dos cargas el mismo dia dan lo mismo.
 */
@RunWith(AndroidJUnit4::class)
class DatosDeMuestra {

    private val contenedor = ApplicationProvider.getApplicationContext<OllinApp>().contenedor
    private val repo = contenedor.repositorio
    private val azar = Random(SEMILLA)
    private val hoy = Tiempo.hoy()
    private val ahora = Tiempo.ahora()

    @Test
    fun carga() = runBlocking {
        val argumentos = InstrumentationRegistry.getArguments()
        // Sin el argumento termina sin tocar nada. No es un assume: el informe
        // de Gradle cuenta la suposicion fallida como prueba fallida.
        if (argumentos.getString("muestra") != "si") return@runBlocking

        // El arranque de la app siembra el catalogo en segundo plano; se espera
        // a que la cuenta deje de moverse para no vaciar la base a medio
        // sembrar. Sembrar desde aqui a la vez chocaria con el nombre unico de
        // las categorias.
        val categorias = contenedor.baseDeDatos.categoriaDao()
        val limite = System.currentTimeMillis() + ESPERA_SIEMBRA_MS
        var antes = -1
        while (System.currentTimeMillis() < limite) {
            val cuenta = categorias.cuenta()
            if (cuenta > 0 && cuenta == antes) break
            antes = cuenta
            Thread.sleep(PAUSA_MS)
        }
        contenedor.baseDeDatos.clearAllTables()
        contenedor.sembrador.sembrarSiHaceFalta()

        contenedor.ajustes.restauraDeFabrica()
        contenedor.ajustes.guardaTema(true)
        contenedor.ajustes.guardaMuestraTutoriales(false)
        contenedor.ajustes.marcaRespaldo()

        val categoria = repo.observaTodasLasCategorias().first().associate { it.nombre to it.id }
        val desde = hoy.minusDays(DIAS_DE_HISTORIA)
        val creado = Tiempo.instante(desde.atStartOfDay()).toEpochMilli()

        val leer = habito(
            Habito(
                nombre = "Leer 20 páginas",
                categoriaId = categoria["Lectura"],
                minutosSugeridos = 25,
                horaRecordatorio = LocalTime.of(21, 30),
                orden = 0,
                creadoEn = creado
            )
        )
        val meditar = habito(
            Habito(
                nombre = "Meditar",
                categoriaId = categoria["Meditación"],
                minutosSugeridos = 10,
                horaRecordatorio = LocalTime.of(7, 0),
                orden = 1,
                creadoEn = creado
            )
        )
        val agua = habito(
            Habito(
                nombre = "Tomar 2 litros de agua",
                categoriaId = categoria["Hidratación"],
                minutosSugeridos = 0,
                orden = 2,
                creadoEn = creado
            )
        )
        val correr = habito(
            Habito(
                nombre = "Correr",
                categoriaId = categoria["Correr"],
                frecuencia = Frecuencia.SEMANAL,
                metaSemanal = 3,
                minutosSugeridos = 35,
                orden = 3,
                creadoEn = creado
            )
        )
        val gimnasio = habito(
            Habito(
                nombre = "Gimnasio",
                categoriaId = categoria["Gimnasio"],
                frecuencia = Frecuencia.DIAS_ELEGIDOS,
                diasSemana = LUNES or MIERCOLES or VIERNES,
                minutosSugeridos = 60,
                horaRecordatorio = LocalTime.of(19, 0),
                orden = 4,
                creadoEn = creado
            )
        )
        val familia = habito(
            Habito(
                nombre = "Llamar a la familia",
                categoriaId = categoria["Familia"],
                frecuencia = Frecuencia.CADA_DIAS,
                intervaloDias = 7,
                minutosSugeridos = 20,
                orden = 5,
                creadoEn = creado
            )
        )

        var dia = desde
        while (dia.isBefore(hoy)) {
            val finDeSemana = dia.dayOfWeek in FIN_DE_SEMANA
            if (finDeSemana) diaDeDescanso(dia, categoria) else diaDeTrabajo(dia, categoria)

            // Las ultimas semanas sin fallar, para que las rachas se vean; lo
            // de antes con algun hueco, que es lo que pasa de verdad.
            val constancia = if (dia.isAfter(hoy.minusDays(DIAS_SIN_FALLAR))) 100 else 80
            if (azar.nextInt(100) < constancia) cumple(meditar, dia, 7, 5)
            if (azar.nextInt(100) < constancia) {
                cumple(leer, dia, 21, 40, cantidad = 20.0, unidad = Unidad.PAGINAS)
            }
            if (azar.nextInt(100) < constancia) {
                cumple(agua, dia, 20, 0, cantidad = 8.0, unidad = Unidad.VASOS)
            }
            if (dia.dayOfWeek in DIAS_DE_GIMNASIO && azar.nextInt(100) < constancia) {
                cumple(gimnasio, dia, 19, 10)
            }
            if (dia.dayOfWeek in DIAS_DE_CORRER) {
                val km = 5.0 + azar.nextInt(0, 30) / 10.0
                val minutos = (km * MINUTOS_POR_KM).toInt()
                cumple(correr, dia, 7, 30, minutos, km, Unidad.KILOMETROS)
            }
            if (dia.toEpochDay() % 7 == 0L) cumple(familia, dia, 20, 0)
            dia = dia.plusDays(1)
        }

        // Hoy: lo de la manana ya hecho, una lectura corriendo en el
        // cronometro y lo de la tarde pendiente. Lo que todavia no pasa a esta
        // hora queda pendiente en vez de inventarse en el futuro.
        cumpleSiYaPaso(meditar, 7, 5)
        registro(
            "Caminata por el parque",
            categoria["Caminata"],
            hoy,
            8,
            0,
            45,
            4_800.0,
            Unidad.PASOS
        )
        if (hoy.dayOfWeek in FIN_DE_SEMANA) {
            registro("Desayuno en familia", categoria["Familia"], hoy, 9, 0, 60)
            registro("Planear la semana", categoria["Correo y administración"], hoy, 10, 15, 40)
            registro("Limpieza de la casa", categoria["Hogar"], hoy, 11, 0, 75)
            registro("Comida familiar", categoria["Familia"], hoy, 15, 0, 120)
            registro("Película", categoria["Ocio"], hoy, 21, 0, 110)
        } else {
            registro("Diseño del módulo de reportes", categoria["Enfoque profundo"], hoy, 9, 0, 120)
            registro("Junta de equipo", categoria["Reuniones"], hoy, 11, 30, 45)
            registro("Correo y pendientes", categoria["Correo y administración"], hoy, 17, 30, 30)
        }
        repo.iniciaAhora("Lectura de la tarde", categoria["Lectura"]).let { id ->
            val corriendo = repo.actividad(id) ?: return@let
            repo.guarda(corriendo.copy(inicio = ahora.minusSeconds(MINUTOS_CORRIENDO * 60L)))
        }
    }

    private suspend fun habito(habito: Habito): Habito = habito.copy(id = repo.guardaHabito(habito))

    private suspend fun cumple(
        habito: Habito,
        dia: LocalDate,
        hora: Int,
        minuto: Int,
        minutos: Int? = null,
        cantidad: Double? = null,
        unidad: Unidad = Unidad.NINGUNA
    ) {
        repo.guarda(
            Actividad(
                titulo = habito.nombre,
                categoriaId = habito.categoriaId,
                habitoId = habito.id,
                estado = EstadoActividad.COMPLETADO,
                inicio = Tiempo.instante(dia.atTime(hora, minuto)),
                dia = dia,
                duracionMinutos = minutos ?: habito.minutosSugeridos ?: 0,
                cantidad = cantidad,
                unidad = unidad
            )
        )
    }

    private suspend fun cumpleSiYaPaso(
        habito: Habito,
        hora: Int,
        minuto: Int,
        minutos: Int? = null,
        cantidad: Double? = null,
        unidad: Unidad = Unidad.NINGUNA
    ) {
        val fin = Tiempo.instante(
            hoy.atTime(hora, minuto)
        ).plusSeconds((minutos ?: habito.minutosSugeridos ?: 0) * 60L)
        if (fin.isBefore(ahora)) cumple(habito, hoy, hora, minuto, minutos, cantidad, unidad)
    }

    /** Un registro suelto: completado si ya termino a esta hora, pendiente si no. */
    private suspend fun registro(
        titulo: String,
        categoriaId: Long?,
        dia: LocalDate,
        hora: Int,
        minuto: Int,
        minutos: Int,
        cantidad: Double? = null,
        unidad: Unidad = Unidad.NINGUNA
    ) {
        val inicio = Tiempo.instante(dia.atTime(hora, minuto))
        val yaPaso = inicio.plusSeconds(minutos * 60L).isBefore(ahora)
        repo.guarda(
            Actividad(
                titulo = titulo,
                categoriaId = categoriaId,
                estado = if (yaPaso) EstadoActividad.COMPLETADO else EstadoActividad.PENDIENTE,
                inicio = inicio,
                dia = dia,
                duracionMinutos = minutos,
                cantidad = cantidad,
                unidad = unidad
            )
        )
    }

    private suspend fun diaDeTrabajo(dia: LocalDate, categoria: Map<String, Long>) {
        if (azar.nextInt(100) <
            60
        ) {
            registro("Traslado a la oficina", categoria["Traslados"], dia, 8, 10, 35)
        }
        registro(
            PROYECTOS.random(azar),
            categoria["Enfoque profundo"],
            dia,
            9,
            0,
            azar.nextInt(90, 151)
        )
        registro(
            JUNTAS.random(azar),
            categoria["Reuniones"],
            dia,
            11,
            30,
            listOf(30, 45, 60).random(azar)
        )
        registro(
            "Correo y pendientes",
            categoria["Correo y administración"],
            dia,
            13,
            0,
            azar.nextInt(20, 41)
        )
        registro(
            PROYECTOS.random(azar),
            categoria["Enfoque profundo"],
            dia,
            15,
            0,
            azar.nextInt(60, 121)
        )
        if (azar.nextInt(100) <
            40
        ) {
            registro("Curso de Kotlin", categoria["Aprendizaje"], dia, 17, 30, 45)
        }
        if (azar.nextInt(100) < 50) {
            val pasos = azar.nextInt(3_000, 6_000).toDouble()
            registro("Caminata", categoria["Caminata"], dia, 20, 0, 30, pasos, Unidad.PASOS)
        }
    }

    private suspend fun diaDeDescanso(dia: LocalDate, categoria: Map<String, Long>) {
        registro("Limpieza de la casa", categoria["Hogar"], dia, 10, 0, azar.nextInt(60, 121))
        registro("Comida familiar", categoria["Familia"], dia, 14, 0, azar.nextInt(90, 151))
        registro(OCIO.random(azar), categoria["Ocio"], dia, 21, 0, azar.nextInt(80, 131))
        if (azar.nextInt(100) <
            50
        ) {
            registro("Estiramiento", categoria["Movilidad y estiramiento"], dia, 9, 0, 20)
        }
    }

    private companion object {
        const val SEMILLA = 2026
        const val DIAS_DE_HISTORIA = 42L
        const val MINUTOS_CORRIENDO = 18L
        const val ESPERA_SIEMBRA_MS = 10_000L
        const val PAUSA_MS = 300L
        const val DIAS_SIN_FALLAR = 24L
        const val MINUTOS_POR_KM = 6.2
        const val LUNES = 0b0000001
        const val MIERCOLES = 0b0000100
        const val VIERNES = 0b0010000

        val FIN_DE_SEMANA = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
        val DIAS_DE_GIMNASIO = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)
        val DIAS_DE_CORRER = setOf(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY, DayOfWeek.SATURDAY)

        val PROYECTOS = listOf(
            "Diseño del módulo de reportes",
            "Revisión de código",
            "Propuesta para el cliente",
            "Migración de la base de datos",
            "Pruebas de la nueva versión"
        )
        val JUNTAS =
            listOf(
                "Junta de equipo",
                "Uno a uno",
                "Planeación del sprint",
                "Revisión con el cliente"
            )
        val OCIO = listOf("Película", "Serie", "Videojuegos", "Salida con amigos")
    }
}
