package app.still.helper

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import app.still.R

/**
 * Full-screen dialogs drawn like the home screen: the theme background, the launcher font,
 * left-aligned text and plain text actions, instead of the platform's boxed alert dialog.
 */
object StillDialog {

    /** Asks for a name. A blank answer is passed through; callers decide what it means. */
    fun prompt(
        context: Context,
        title: String,
        initial: String,
        hint: String = "",
        onDone: (String) -> Unit,
    ) {
        val dialog = base(context)
        val input = EditText(context).apply {
            setTextAppearance(R.style.TextLarge)
            setText(initial)
            this.hint = hint
            setHintTextColor(ColorUtils.setAlphaComponent(colorOf(context, R.attr.primaryColor), 90))
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            imeOptions = EditorInfo.IME_ACTION_DONE
            setSingleLine()
            setSelectAllOnFocus(true)
            backgroundTintList = android.content.res.ColorStateList.valueOf(
                ColorUtils.setAlphaComponent(colorOf(context, R.attr.primaryColor), 110)
            )
        }
        val finish = {
            dialog.dismiss()
            onDone(input.text.toString().trim())
        }
        input.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) finish()
            actionId == EditorInfo.IME_ACTION_DONE
        }
        dialog.setContentView(column(context, title, input,
            context.getString(android.R.string.cancel) to { dialog.dismiss() },
            context.getString(android.R.string.ok) to finish,
        ))
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        dialog.show()
        input.requestFocus()
    }

    /** Asks to confirm an action described by [message]. */
    fun confirm(context: Context, message: String, confirmLabel: String, onConfirm: () -> Unit) {
        val dialog = base(context)
        val body = TextView(context).apply {
            setTextAppearance(R.style.TextMedium)
            text = message
        }
        dialog.setContentView(column(context, null, body,
            context.getString(android.R.string.cancel) to { dialog.dismiss() },
            confirmLabel to { dialog.dismiss(); onConfirm() },
        ))
        dialog.show()
    }

    private fun base(context: Context) = Dialog(context, android.R.style.Theme_Translucent_NoTitleBar).apply {
        window?.setBackgroundDrawable(
            ColorDrawable(ColorUtils.setAlphaComponent(colorOf(context, R.attr.primaryInverseColor), 245))
        )
        window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    }

    private fun column(
        context: Context,
        title: String?,
        content: android.view.View,
        dismiss: Pair<String, () -> Unit>,
        accept: Pair<String, () -> Unit>,
    ) = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_VERTICAL
        val side = 24.dpToPx()
        setPadding(side, side, side, side)
        if (title != null) addView(TextView(context).apply {
            setTextAppearance(R.style.TextSmall)
            text = title
            alpha = 0.6f
            setPadding(4.dpToPx(), 0, 0, 12.dpToPx())
        })
        addView(content)
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            setPadding(0, 24.dpToPx(), 0, 0)
            addView(action(context, dismiss.first, 0.5f, dismiss.second))
            addView(action(context, accept.first, 1f, accept.second))
        })
        setOnClickListener { }
    }

    private fun action(context: Context, label: String, alpha: Float, onClick: () -> Unit) =
        TextView(context).apply {
            setTextAppearance(R.style.TextMedium)
            text = label
            this.alpha = alpha
            val pad = 16.dpToPx()
            setPadding(pad, pad, pad, pad)
            setOnClickListener { onClick() }
        }

    private fun colorOf(context: Context, attr: Int): Int {
        val value = TypedValue()
        return if (context.theme.resolveAttribute(attr, value, true)) value.data else Color.WHITE
    }
}
