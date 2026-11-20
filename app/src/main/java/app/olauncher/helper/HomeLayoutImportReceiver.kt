package app.olauncher.helper

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.os.Process
import android.util.Base64
import android.util.Log
import app.olauncher.data.HomeApp
import app.olauncher.data.HomeLayoutCodec
import app.olauncher.data.HomeLayoutImport
import app.olauncher.data.Prefs

/**
 * Replaces the home screen arrangement from a computer, over adb only:
 *
 *   adb shell am broadcast -a app.stilllauncher.IMPORT_HOME_LAYOUT \
 *     -n app.stilllauncher/app.olauncher.helper.HomeLayoutImportReceiver --es spec_b64 <base64 json>
 *
 * The manifest guards it with android.permission.DUMP, which the adb shell holds and no installed
 * app can obtain, so other apps cannot rewrite the home screen. The previous arrangement is kept
 * under a backup key before anything is replaced.
 */
class HomeLayoutImportReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) return
        val prefs = Prefs(context)
        if (intent.getBooleanExtra(EXTRA_RESTORE_BACKUP, false)) {
            restoreBackup(prefs)
            return
        }
        intent.getStringExtra(EXTRA_PROTECT)?.let { list ->
            // Protected apps get accessibility services switched off while they run (banking apps).
            prefs.protectedApps = list.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()
            setResult(Activity.RESULT_OK, "protected=${prefs.protectedApps.sorted()}", null)
            return
        }
        try {
            val encoded = intent.getStringExtra(EXTRA_SPEC_B64)
                ?: throw IllegalArgumentException("missing extra $EXTRA_SPEC_B64")
            val spec = String(Base64.decode(encoded, Base64.DEFAULT), Charsets.UTF_8)
            val result = HomeLayoutImport.parse(spec) { resolve(context, it) }
            prefs.homeLayoutBackup = HomeLayoutCodec.encode(prefs.homeLayout)
            prefs.homeLayout = result.layout
            val summary = "imported ${result.layout.entries.size} entries; missing=${result.missing}"
            Log.i(TAG, summary)
            setResult(Activity.RESULT_OK, summary, null)
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "home layout import refused", e)
            setResult(Activity.RESULT_CANCELED, "refused: ${e.message}", null)
        }
    }

    /** Puts back the arrangement saved before the last import; the current one becomes the backup. */
    private fun restoreBackup(prefs: Prefs) {
        val backup = prefs.homeLayoutBackup
        val restored = HomeLayoutCodec.decodeOrNull(backup)
        if (backup == null || restored == null) {
            setResult(Activity.RESULT_CANCELED, "refused: no readable backup", null)
            return
        }
        prefs.homeLayoutBackup = HomeLayoutCodec.encode(prefs.homeLayout)
        prefs.homeLayout = restored
        setResult(Activity.RESULT_OK, "restored ${restored.entries.size} entries", null)
    }

    private fun resolve(context: Context, packageName: String): HomeApp? {
        val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
        val user = Process.myUserHandle()
        val activity = launcherApps.getActivityList(packageName, user).firstOrNull() ?: return null
        return HomeApp(activity.label.toString(), packageName, activity.componentName.className, user.toString())
    }

    companion object {
        const val ACTION = "app.stilllauncher.IMPORT_HOME_LAYOUT"
        const val EXTRA_SPEC_B64 = "spec_b64"
        const val EXTRA_RESTORE_BACKUP = "restore_backup"
        /** Comma-separated packages; replaces the whole protected list. */
        const val EXTRA_PROTECT = "protect_packages"
        private const val TAG = "HomeLayoutImport"
    }
}
