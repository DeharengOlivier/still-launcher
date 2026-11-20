package app.olauncher.data

import org.json.JSONException
import org.json.JSONObject

/**
 * Reads a home screen arrangement written by hand (or by a script over adb):
 * `{"entries":[{"app":"<package>"},{"group":"<name>","apps":["<package>", ...]}]}`.
 * Labels come from the device, so the spec only names packages.
 */
object HomeLayoutImport {

    data class Result(val layout: HomeLayout, val missing: List<String>)

    /** Throws [IllegalArgumentException] on a malformed spec, so a typo never empties the home screen. */
    fun parse(spec: String, resolve: (packageName: String) -> HomeApp?): Result {
        val missing = mutableListOf<String>()
        fun app(packageName: String): HomeApp? = resolve(packageName) ?: null.also { missing += packageName }

        try {
            val entries = JSONObject(spec).getJSONArray("entries")
            var layout = HomeLayout()
            for (i in 0 until entries.length()) {
                val entry = entries.getJSONObject(i)
                layout = when {
                    entry.has("app") -> app(entry.getString("app"))?.let(layout::addApp) ?: layout
                    entry.has("group") -> {
                        val name = entry.getString("group")
                        val apps = entry.getJSONArray("apps")
                        (0 until apps.length()).mapNotNull { app(apps.getString(it)) }
                            .fold(layout.createGroup(name)) { acc, member -> acc.addToGroup(name, member) }
                    }
                    else -> throw IllegalArgumentException("entry $i has neither \"app\" nor \"group\"")
                }
            }
            return Result(layout, missing)
        } catch (e: JSONException) {
            throw IllegalArgumentException("home layout spec is not valid: ${e.message}", e)
        }
    }
}
