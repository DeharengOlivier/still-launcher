package app.still.helper

import android.app.AlertDialog
import android.content.Context
import android.text.InputType
import android.widget.EditText
import android.widget.FrameLayout
import app.still.R
import app.still.data.HomeApp
import app.still.data.Prefs

/** Asks for a group name; [onName] only receives a non-blank name. */
fun askGroupName(context: Context, title: String, initial: String, onName: (String) -> Unit) =
    StillDialog.prompt(context, title, initial) { name ->
        if (name.isEmpty()) context.showToast(context.getString(R.string.group_name_empty))
        else onName(name)
    }

/** Lets the user put [app] in an existing group or in a new one. */
fun showAddToGroup(context: Context, prefs: Prefs, app: HomeApp) {
    val groups = prefs.homeLayout.groupNames
    val newGroup = context.getString(R.string.group_new)
    val add = { name: String ->
        prefs.homeLayout = prefs.homeLayout.addToGroup(name, app)
        context.showToast(context.getString(R.string.group_added, app.label, name))
    }
    AlertDialog.Builder(context)
        .setTitle(R.string.group_add_to)
        .setItems((groups + newGroup).toTypedArray()) { _, which ->
            if (which < groups.size) add(groups[which])
            else askGroupName(context, newGroup, "") { add(it) }
        }
        .show()
}
