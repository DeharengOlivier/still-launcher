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
fun askGroupName(context: Context, title: String, initial: String, onName: (String) -> Unit) {
    val input = EditText(context).apply {
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        setText(initial)
        setSelectAllOnFocus(true)
    }
    val frame = FrameLayout(context).apply {
        val side = 24.dpToPx()
        setPadding(side, 8.dpToPx(), side, 0)
        addView(input)
    }
    AlertDialog.Builder(context)
        .setTitle(title)
        .setView(frame)
        .setPositiveButton(android.R.string.ok) { _, _ ->
            val name = input.text.toString().trim()
            if (name.isEmpty()) context.showToast(context.getString(R.string.group_name_empty))
            else onName(name)
        }
        .setNegativeButton(android.R.string.cancel, null)
        .show()
    input.requestFocus()
    input.showKeyboard()
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
