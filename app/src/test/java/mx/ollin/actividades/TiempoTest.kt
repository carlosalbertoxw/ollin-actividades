package mx.ollin.actividades

import mx.ollin.actividades.data.repo.ActividadesRepositorio
import mx.ollin.actividades.domain.model.DiasSemana
import mx.ollin.actividades.domain.model.Tiempo
import mx.ollin.actividades.domain.model.Unidad
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant

class TiempoTest {

    @Test
    fun `la duracion se lee en horas y minutos`() {
        assertEquals("0 min", Tiempo.duracion(0))
        assertEquals("45 min", Tiempo.duracion(45))
        assertEquals("1 h", Tiempo.duracion(60))
        assertEquals("2 h 05", Tiempo.duracion(125))
    }

    @Test
    fun `el cronometro pasa a horas cuando hace falta`() {
        assertEquals("00:30", Tiempo.cronometro(30))
        assertEquals("05:00", Tiempo.cronometro(300))
        assertEquals("1:00:01", Tiempo.cronometro(3_601))
    }

    @Test
    fun `los minutos entre dos instantes se redondean al mas cercano`() {
        val inicio = Instant.ofEpochMilli(0)
        // 89 segundos estan mas cerca de dos minutos que de uno.
        assertEquals(1, ActividadesRepositorio.minutosEntre(inicio, inicio.plusSeconds(80)))
        assertEquals(2, ActividadesRepositorio.minutosEntre(inicio, inicio.plusSeconds(100)))
        assertEquals(30, ActividadesRepositorio.minutosEntre(inicio, inicio.plusSeconds(1_800)))
    }

    @Test
    fun `un fin anterior al inicio no produce minutos negativos`() {
        val inicio = Instant.ofEpochMilli(60_000)
        assertEquals(0, ActividadesRepositorio.minutosEntre(inicio, Instant.ofEpochMilli(0)))
    }

    @Test
    fun `la mascara de dias reconoce entre semana`() {
        val entreSemana = DayOfWeek.entries
            .filter { it.value <= 5 }
            .fold(0) { acc, dia -> DiasSemana.alterna(acc, dia) }

        assertEquals("Entre semana", DiasSemana.etiqueta(entreSemana))
        assertEquals("Todos los dias", DiasSemana.etiqueta(DiasSemana.TODOS))
        assertEquals(true, DiasSemana.contiene(entreSemana, DayOfWeek.WEDNESDAY))
        assertEquals(false, DiasSemana.contiene(entreSemana, DayOfWeek.SUNDAY))
    }

    @Test
    fun `la unidad formatea sin decimales sobrantes`() {
        assertEquals("5 km", Unidad.KILOMETROS.formatea(5.0))
        assertEquals("5.25 km", Unidad.KILOMETROS.formatea(5.25))
        assertEquals("30 reps", Unidad.REPETICIONES.formatea(30.4))
    }
}
