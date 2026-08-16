package com.carlosalbertoxw.ollin.actividades.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.carlosalbertoxw.ollin.actividades.di.Contenedor
import com.carlosalbertoxw.ollin.actividades.ui.nav.Destino
import com.carlosalbertoxw.ollin.actividades.ui.nav.Rutas
import com.carlosalbertoxw.ollin.actividades.ui.screens.AcercaDePantalla
import com.carlosalbertoxw.ollin.actividades.ui.screens.ActividadesPantalla
import com.carlosalbertoxw.ollin.actividades.ui.screens.AjustesPantalla
import com.carlosalbertoxw.ollin.actividades.ui.screens.AnaliticaPantalla
import com.carlosalbertoxw.ollin.actividades.ui.screens.ArchivoPantalla
import com.carlosalbertoxw.ollin.actividades.ui.screens.CapturaPantalla
import com.carlosalbertoxw.ollin.actividades.ui.screens.CategoriasPantalla
import com.carlosalbertoxw.ollin.actividades.ui.screens.HabitosPantalla
import com.carlosalbertoxw.ollin.actividades.ui.screens.HoyPantalla

@Composable
fun OllinRaiz(contenedor: Contenedor) {
    val nav = rememberNavController()
    val entrada by nav.currentBackStackEntryAsState()
    val rutaActual = entrada?.destination?.route

    val destinoActual = remember(rutaActual) {
        Destino.entries.firstOrNull { it.ruta == rutaActual }
    }
    // En Habitos manda su propio boton; dos flotantes encimados no se entienden.
    val muestraCaptura = destinoActual != null && destinoActual != Destino.HABITOS

    Scaffold(
        bottomBar = {
            if (destinoActual != null) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                    Destino.entries.forEach { destino ->
                        NavigationBarItem(
                            selected = destino == destinoActual,
                            onClick = {
                                nav.navigate(destino.ruta) {
                                    popUpTo(Destino.HOY.ruta) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(destino.icono, contentDescription = destino.titulo) },
                            label = { Text(destino.titulo) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            AnimatedVisibility(
                visible = muestraCaptura,
                enter = scaleIn() + fadeIn(),
                exit = scaleOut() + fadeOut()
            ) {
                // La descripcion va en el icono a proposito: el boton flotante
                // extendido borra la semantica de su contenido, asi que su
                // rotulo no llega a un lector de pantalla y sin esto se
                // anunciaria como "boton" a secas.
                ExtendedFloatingActionButton(
                    onClick = { nav.navigate(Rutas.captura()) },
                    icon = { Icon(Icons.Filled.Add, contentDescription = "Registrar") },
                    text = { Text("Registrar") }
                )
            }
        }
    ) { relleno ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(relleno)
        ) {
            NavHost(navController = nav, startDestination = Destino.HOY.ruta) {

                composable(Destino.HOY.ruta) {
                    HoyPantalla(
                        contenedor = contenedor,
                        alAbrirActividad = { id -> nav.navigate(Rutas.captura(id)) },
                        alAbrirAjustes = { nav.navigate(Rutas.AJUSTES) }
                    )
                }

                composable(Destino.ACTIVIDADES.ruta) {
                    ActividadesPantalla(
                        contenedor = contenedor,
                        alAbrirActividad = { id -> nav.navigate(Rutas.captura(id)) }
                    )
                }

                composable(Destino.HABITOS.ruta) {
                    HabitosPantalla(contenedor)
                }

                composable(Destino.ANALITICA.ruta) {
                    AnaliticaPantalla(contenedor)
                }

                composable(
                    route = Rutas.CAPTURA_CON_ID,
                    arguments = listOf(
                        navArgument("id") { type = NavType.LongType; defaultValue = 0L }
                    )
                ) { destino ->
                    CapturaPantalla(
                        contenedor = contenedor,
                        actividadId = destino.arguments?.getLong("id")?.takeIf { it > 0L },
                        alCerrar = { nav.popBackStack() }
                    )
                }

                composable(Rutas.CATEGORIAS) {
                    CategoriasPantalla(contenedor) { nav.popBackStack() }
                }

                composable(Rutas.AJUSTES) {
                    AjustesPantalla(
                        contenedor = contenedor,
                        alAbrirCategorias = { nav.navigate(Rutas.CATEGORIAS) },
                        alAbrirArchivo = { nav.navigate(Rutas.ARCHIVO) },
                        alAbrirAcercaDe = { nav.navigate(Rutas.ACERCA_DE) },
                        alCerrar = { nav.popBackStack() }
                    )
                }

                composable(Rutas.ARCHIVO) {
                    ArchivoPantalla(contenedor) { nav.popBackStack() }
                }

                composable(Rutas.ACERCA_DE) {
                    AcercaDePantalla { nav.popBackStack() }
                }
            }
        }
    }
}
