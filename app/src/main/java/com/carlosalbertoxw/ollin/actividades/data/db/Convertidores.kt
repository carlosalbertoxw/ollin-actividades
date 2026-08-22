package com.carlosalbertoxw.ollin.actividades.data.db

import androidx.room.TypeConverter
import com.carlosalbertoxw.ollin.actividades.domain.model.Ambito
import com.carlosalbertoxw.ollin.actividades.domain.model.EstadoActividad
import com.carlosalbertoxw.ollin.actividades.domain.model.Frecuencia
import com.carlosalbertoxw.ollin.actividades.domain.model.Unidad
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

class Convertidores {

    /** Milisegundos epoch: el instante exacto, sin huso pegado. */
    @TypeConverter fun instanteAMillis(v: Instant?): Long? = v?.toEpochMilli()
    @TypeConverter fun millisAInstante(v: Long?): Instant? = v?.let(Instant::ofEpochMilli)

    /** Dia epoch: entero, ordenable y comparable en SQL sin funciones de fecha. */
    @TypeConverter fun fechaADia(v: LocalDate?): Long? = v?.toEpochDay()
    @TypeConverter fun diaAFecha(v: Long?): LocalDate? = v?.let(LocalDate::ofEpochDay)

    /**
     * Segundo del dia: entero pequeno y ordenable. La hora de un recordatorio
     * no lleva zona; ver [com.carlosalbertoxw.ollin.actividades.data.db.Habito.horaRecordatorio].
     */
    @TypeConverter fun horaASegundos(v: LocalTime?): Int? = v?.toSecondOfDay()
    @TypeConverter fun segundosAHora(v: Int?): LocalTime? = v?.let { LocalTime.ofSecondOfDay(it.toLong()) }

    @TypeConverter fun estadoATexto(v: EstadoActividad?): String? = v?.name
    @TypeConverter fun textoAEstado(v: String?): EstadoActividad? = v?.let(EstadoActividad::valueOf)

    @TypeConverter fun ambitoATexto(v: Ambito?): String? = v?.name
    @TypeConverter fun textoAAmbito(v: String?): Ambito? = v?.let(Ambito::valueOf)

    @TypeConverter fun unidadATexto(v: Unidad?): String? = v?.name
    @TypeConverter fun textoAUnidad(v: String?): Unidad? = v?.let(Unidad::valueOf)

    @TypeConverter fun frecuenciaATexto(v: Frecuencia?): String? = v?.name
    @TypeConverter fun textoAFrecuencia(v: String?): Frecuencia? = v?.let(Frecuencia::valueOf)
}
