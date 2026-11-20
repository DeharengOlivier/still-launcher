package app.still.data

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** An app placed on the home screen, identified by package and user profile. */
data class HomeApp(
    val label: String,
    val packageName: String,
    val activityClassName: String?,
    val user: String,
) {
    fun sameAppAs(other: HomeApp) = packageName == other.packageName && user == other.user
}

sealed interface HomeEntry {
    data class App(val app: HomeApp) : HomeEntry
    data class Group(val name: String, val apps: List<HomeApp>) : HomeEntry
}

/**
 * What the home screen shows, in order: single apps and named groups of apps.
 * Every operation returns a new layout and leaves this one untouched.
 */
data class HomeLayout(val entries: List<HomeEntry> = emptyList()) {

    fun isOnHome(app: HomeApp) = entries.any { it is HomeEntry.App && it.app.sameAppAs(app) }

    val groupNames: List<String> get() = entries.filterIsInstance<HomeEntry.Group>().map { it.name }

    fun addApp(app: HomeApp): HomeLayout =
        if (isOnHome(app)) this else copy(entries = entries + HomeEntry.App(app))

    fun removeApp(app: HomeApp): HomeLayout =
        copy(entries = entries.filterNot { it is HomeEntry.App && it.app.sameAppAs(app) })

    fun addToGroup(groupName: String, app: HomeApp): HomeLayout {
        val name = groupName.trim()
        if (name.isEmpty()) return this
        if (name !in groupNames) return copy(entries = entries + HomeEntry.Group(name, listOf(app)))
        return mapGroup(name) { group ->
            if (group.apps.any { it.sameAppAs(app) }) group else group.copy(apps = group.apps + app)
        }
    }

    fun createGroup(groupName: String): HomeLayout {
        val name = groupName.trim()
        if (name.isEmpty() || name in groupNames) return this
        return copy(entries = entries + HomeEntry.Group(name, emptyList()))
    }

    fun removeFromGroup(groupName: String, app: HomeApp): HomeLayout =
        mapGroup(groupName) { group -> group.copy(apps = group.apps.filterNot { it.sameAppAs(app) }) }

    fun renameGroup(from: String, to: String): HomeLayout {
        val name = to.trim()
        if (name.isEmpty() || name in groupNames) return this
        return mapGroup(from) { it.copy(name = name) }
    }

    fun deleteGroup(groupName: String): HomeLayout =
        copy(entries = entries.filterNot { it is HomeEntry.Group && it.name == groupName })

    /** Moves the entry at [index] by [offset] places; a move past either end does nothing. */
    fun move(index: Int, offset: Int): HomeLayout {
        val target = index + offset
        if (index !in entries.indices || target !in entries.indices) return this
        val reordered = entries.toMutableList()
        reordered.add(target, reordered.removeAt(index))
        return copy(entries = reordered)
    }

    /** Drops apps that are no longer installed, wherever they are. */
    fun retainInstalled(isInstalled: (HomeApp) -> Boolean): HomeLayout = copy(
        entries = entries.mapNotNull { entry ->
            when (entry) {
                is HomeEntry.App -> entry.takeIf { isInstalled(it.app) }
                is HomeEntry.Group -> entry.copy(apps = entry.apps.filter(isInstalled))
            }
        }
    )

    private fun mapGroup(name: String, change: (HomeEntry.Group) -> HomeEntry.Group) = copy(
        entries = entries.map { if (it is HomeEntry.Group && it.name == name) change(it) else it }
    )

    companion object {
        /** Carries over the apps set in the eight fixed home slots of earlier versions. */
        fun fromSlots(apps: List<HomeApp>): HomeLayout = apps.fold(HomeLayout()) { layout, app -> layout.addApp(app) }
    }
}

/** Stores a [HomeLayout] as JSON in the preferences. */
object HomeLayoutCodec {

    private const val VERSION = 1

    fun encode(layout: HomeLayout): String {
        val entries = JSONArray()
        layout.entries.forEach { entry ->
            entries.put(
                when (entry) {
                    is HomeEntry.App -> JSONObject().put("type", "app").put("app", encodeApp(entry.app))
                    is HomeEntry.Group -> JSONObject()
                        .put("type", "group")
                        .put("name", entry.name)
                        .put("apps", JSONArray().apply { entry.apps.forEach { put(encodeApp(it)) } })
                }
            )
        }
        return JSONObject().put("version", VERSION).put("entries", entries).toString()
    }

    /** Null when [value] is present but unreadable, so the caller can report it. */
    fun decodeOrNull(value: String?): HomeLayout? {
        if (value.isNullOrBlank()) return HomeLayout()
        return try {
            val entries = JSONObject(value).getJSONArray("entries")
            HomeLayout((0 until entries.length()).map { decodeEntry(entries.getJSONObject(it)) })
        } catch (e: JSONException) {
            null
        }
    }

    fun decode(value: String?): HomeLayout = decodeOrNull(value) ?: HomeLayout()

    private fun decodeEntry(json: JSONObject): HomeEntry = when (val type = json.getString("type")) {
        "app" -> HomeEntry.App(decodeApp(json.getJSONObject("app")))
        "group" -> {
            val apps = json.getJSONArray("apps")
            HomeEntry.Group(json.getString("name"), (0 until apps.length()).map { decodeApp(apps.getJSONObject(it)) })
        }
        else -> throw JSONException("unknown home entry type $type")
    }

    private fun encodeApp(app: HomeApp) = JSONObject()
        .put("label", app.label)
        .put("package", app.packageName)
        .put("activity", app.activityClassName ?: JSONObject.NULL)
        .put("user", app.user)

    private fun decodeApp(json: JSONObject) = HomeApp(
        label = json.getString("label"),
        packageName = json.getString("package"),
        activityClassName = if (json.isNull("activity")) null else json.getString("activity"),
        user = json.getString("user"),
    )
}
