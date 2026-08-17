package com.carlosalbertoxw.ollin.actividades.ui.seguridad

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.Composable
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/** Hay patron, PIN o contrasena que pedir prestada. */
fun telefonoAsegurado(contexto: Context): Boolean =
    contexto.getSystemService(KeyguardManager::class.java)?.isDeviceSecure == true

/**
 * Devuelve la accion que pide la credencial del telefono.
 *
 * La usan la pantalla de bloqueo para dejarte entrar y la de ajustes para
 * confirmar que eres tu antes de quitar el candado. Antes de Android 11 el
 * dialogo unificado no admite credencial del dispositivo, asi que ahi se abre
 * la pantalla de desbloqueo del sistema.
 */
@Composable
fun pedirCredencialDelSistema(
    actividad: FragmentActivity,
    titulo: String,
    alLograr: () -> Unit,
    alFallar: (String) -> Unit,
    /**
     * Se invoca solo en el camino anterior a Android 11, que es el unico que
     * abre una actividad y por lo tanto manda Ollin al fondo. El dialogo
     * unificado de Android 11 en adelante se monta encima sin detenerla, asi
     * que avisar ahi dejaria concedida una gracia que nadie va a gastar.
     */
    alSalirAlSistema: () -> Unit = {}
): () -> Unit {
    val lanzador = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { resultado ->
        if (resultado.resultCode == Activity.RESULT_OK) alLograr()
        else alFallar("No se pudo verificar. Inténtalo de nuevo.")
    }

    return {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            BiometricPrompt(
                actividad,
                ContextCompat.getMainExecutor(actividad),
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(
                        resultado: BiometricPrompt.AuthenticationResult
                    ) = alLograr()

                    override fun onAuthenticationError(codigo: Int, descripcion: CharSequence) {
                        alFallar(descripcion.toString())
                    }
                }
            ).authenticate(
                BiometricPrompt.PromptInfo.Builder()
                    .setTitle(titulo)
                    .setSubtitle("Usa tu huella, patrón o PIN")
                    .setAllowedAuthenticators(BIOMETRIC_WEAK or DEVICE_CREDENTIAL)
                    .build()
            )
        } else {
            val guardia = actividad.getSystemService(KeyguardManager::class.java)
            @Suppress("DEPRECATION")
            val intencion = guardia?.createConfirmDeviceCredentialIntent(
                titulo,
                "Usa tu patron, PIN o contrasena"
            )
            if (intencion != null) {
                alSalirAlSistema()
                lanzador.launch(intencion)
            } else {
                alFallar("Tu teléfono ya no tiene patrón ni PIN configurado.")
            }
        }
    }
}
