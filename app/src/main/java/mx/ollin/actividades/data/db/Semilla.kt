package mx.ollin.actividades.data.db

import mx.ollin.actividades.domain.model.Ambito

/**
 * Catalogo inicial. Una app de registro que abre vacia obliga a inventar la
 * taxonomia antes de poder anotar nada, y ahi es donde se abandona. Estas
 * categorias se pueden renombrar, archivar o borrar sin consecuencias.
 */
object Semilla {

    data class PlantillaCategoria(
        val nombre: String,
        val ambito: Ambito,
        val colorHex: String
    )

    val CATEGORIAS: List<PlantillaCategoria> = listOf(
        PlantillaCategoria("Enfoque profundo", Ambito.TRABAJO, "#3D6DB5"),
        PlantillaCategoria("Reuniones", Ambito.TRABAJO, "#5B87C9"),
        PlantillaCategoria("Correo y administracion", Ambito.TRABAJO, "#7BA0D6"),
        PlantillaCategoria("Aprendizaje", Ambito.TRABAJO, "#9BB9E3"),

        PlantillaCategoria("Correr", Ambito.FISICO, "#2F9E6E"),
        PlantillaCategoria("Gimnasio", Ambito.FISICO, "#48B183"),
        PlantillaCategoria("Caminata", Ambito.FISICO, "#6FCFA1"),
        PlantillaCategoria("Movilidad y estiramiento", Ambito.FISICO, "#95DDBB"),

        PlantillaCategoria("Lectura", Ambito.HABITO, "#E9A13B"),
        PlantillaCategoria("Meditacion", Ambito.HABITO, "#F0B563"),
        PlantillaCategoria("Hidratacion", Ambito.HABITO, "#F5C57E"),
        PlantillaCategoria("Descanso", Ambito.HABITO, "#F8D7A5"),

        PlantillaCategoria("Familia", Ambito.PERSONAL, "#C4453F"),
        PlantillaCategoria("Ocio", Ambito.PERSONAL, "#D46B66"),
        PlantillaCategoria("Hogar", Ambito.PERSONAL, "#E08C88"),
        PlantillaCategoria("Traslados", Ambito.PERSONAL, "#EBADAA")
    )
}

/**
 * Deja el catalogo listo la primera vez que corre la app. Es idempotente: si ya
 * hay categorias, no toca nada.
 */
class Sembrador(private val categoriaDao: CategoriaDao) {

    suspend fun sembrarSiHaceFalta() {
        if (categoriaDao.cuenta() > 0) return
        Semilla.CATEGORIAS.forEachIndexed { indice, plantilla ->
            categoriaDao.inserta(
                Categoria(
                    nombre = plantilla.nombre,
                    ambito = plantilla.ambito,
                    colorHex = plantilla.colorHex,
                    orden = indice
                )
            )
        }
    }
}
