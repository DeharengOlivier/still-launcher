package app.still.helper

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import android.util.Log
import app.still.data.Prefs

/**
 * Some apps (banking apps in particular) refuse to run while any accessibility service is
 * enabled. Before such a "protected" app is launched, every enabled accessibility service is
 * switched off and remembered; when the home screen comes back, the remembered services are
 * switched on again. Only services this guard switched off are restored, so a service the
 * user disabled on purpose stays disabled.
 *
 * Writing these settings needs WRITE_SECURE_SETTINGS, which only `adb shell pm grant` can give.
 */
object AccessibilityGuard {

    /** The new value of `enabled_accessibility_services` and what to remember for later. */
    data class Plan(val enabled: String, val saved: String?)

    private const val SEPARATOR = ':'

    private fun parse(value: String?): List<String> =
        value.orEmpty().split(SEPARATOR).map { it.trim() }.filter { it.isNotEmpty() }.distinct()

    private fun join(services: List<String>): String = services.joinToString(SEPARATOR.toString())

    /** Null when no service is enabled, so there is nothing to switch off. */
    fun suspend(enabled: String?, alreadySaved: String?): Plan? {
        val running = parse(enabled)
        if (running.isEmpty()) return null
        return Plan(enabled = "", saved = join((parse(alreadySaved) + running).distinct()))
    }

    /** Null when nothing was switched off by this guard. */
    fun restore(enabled: String?, saved: String?): Plan? {
        val remembered = parse(saved)
        if (remembered.isEmpty()) return null
        return Plan(enabled = join((parse(enabled) + remembered).distinct()), saved = null)
    }
}

/** Applies [AccessibilityGuard] plans to the real secure settings. */
class AccessibilityGuardRunner(private val context: Context, private val prefs: Prefs) {

    sealed interface Outcome {
        data object NothingToDo : Outcome
        data object Applied : Outcome
        data object MissingPermission : Outcome
        data class Failed(val reason: String) : Outcome
    }

    fun hasPermission(): Boolean = context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) ==
        PackageManager.PERMISSION_GRANTED

    /**
     * Gesture apps are accessibility services, so switching them off can leave the user with no way
     * back. While they are suspended the system button bar is shown; restoring hides it again.
     */
    fun suspendForProtectedApp(): Outcome =
        apply(AccessibilityGuard.suspend(readEnabled(), prefs.suspendedAccessibilityServices))
            .also { if (it == Outcome.Applied) setButtonBar(visible = true) }

    fun restoreOnHome(): Outcome =
        apply(AccessibilityGuard.restore(readEnabled(), prefs.suspendedAccessibilityServices))
            .also { if (it == Outcome.Applied) setButtonBar(visible = false) }

    /** HyperOS/MIUI keys: force_fsg_nav_bar 0 = buttons, 1 = gestures (bar hidden). */
    private fun setButtonBar(visible: Boolean) {
        try {
            Settings.Global.putInt(context.contentResolver, FORCE_FSG_NAV_BAR, if (visible) 0 else 1)
            Settings.Secure.putInt(context.contentResolver, NAVIGATION_MODE, if (visible) 0 else 2)
        } catch (e: SecurityException) {
            Log.w(TAG, "could not switch the navigation bar", e)
        }
    }

    private fun readEnabled(): String? =
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)

    private fun apply(plan: AccessibilityGuard.Plan?): Outcome {
        if (plan == null) return Outcome.NothingToDo
        if (!hasPermission()) return Outcome.MissingPermission
        return try {
            val resolver = context.contentResolver
            // Remember before switching off, so a crash in between can still restore the services.
            if (plan.saved != null) prefs.suspendedAccessibilityServices = plan.saved
            Settings.Secure.putString(resolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, plan.enabled)
            Settings.Secure.putInt(
                resolver,
                Settings.Secure.ACCESSIBILITY_ENABLED,
                if (plan.enabled.isEmpty()) 0 else 1,
            )
            if (plan.saved == null) prefs.suspendedAccessibilityServices = null
            Outcome.Applied
        } catch (e: SecurityException) {
            Log.w(TAG, "accessibility guard could not write secure settings", e)
            Outcome.Failed(e.message ?: e.javaClass.simpleName)
        }
    }

    private companion object {
        const val TAG = "AccessibilityGuard"
        const val FORCE_FSG_NAV_BAR = "force_fsg_nav_bar"
        const val NAVIGATION_MODE = "navigation_mode"
    }
}
