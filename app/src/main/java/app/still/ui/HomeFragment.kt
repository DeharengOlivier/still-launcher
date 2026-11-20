package app.still.ui

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.res.Configuration
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.core.view.setPadding
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import app.still.MainViewModel
import app.still.R
import app.still.data.AppModel
import app.still.data.Constants
import app.still.data.HomeApp
import app.still.data.HomeEntry
import app.still.data.HomeLayout
import app.still.data.Prefs
import app.still.helper.askGroupName
import android.app.AlertDialog
import android.widget.LinearLayout
import app.still.databinding.FragmentHomeBinding
import app.still.helper.appUsagePermissionGranted
import app.still.helper.dpToPx
import app.still.helper.expandNotificationDrawer
import app.still.helper.getChangedAppTheme
import app.still.helper.getUserHandleFromString
import app.still.helper.isPackageInstalled
import app.still.helper.openAlarmApp
import app.still.helper.openCalendar
import app.still.helper.openCameraApp
import app.still.helper.openDialerApp
import app.still.helper.setPlainWallpaperByTheme
import app.still.helper.showToast
import app.still.listener.OnSwipeTouchListener
import app.still.listener.ViewSwipeTouchListener
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HomeFragment : BaseFragment(), View.OnClickListener, View.OnLongClickListener {

    private lateinit var prefs: Prefs
    private lateinit var viewModel: MainViewModel
    private lateinit var deviceManager: DevicePolicyManager

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        prefs = Prefs(requireContext())
        viewModel = activity?.run {
            ViewModelProvider(this)[MainViewModel::class.java]
        } ?: throw Exception("Invalid Activity")

        deviceManager = context?.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager

        initObservers()
        setHomeAlignment(prefs.homeAlignment)
        initSwipeTouchListener()
        initClickListeners()
    }

    override fun onPause() {
        // Leaving home ends edit mode, like on iOS.
        setEditing(false)
        // The status bar is hidden on home only; the app list and settings show it again.
        showStatusBar()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        // Always come back to the list's resting position.
        binding.homeScroll.scrollTo(0, 0)
        populateHomeScreen(false)
        viewModel.isStillDefault()
        statusBarRevealed = false
        if (prefs.showStatusBar) showStatusBar()
        else hideStatusBar()
    }

    override fun onClick(view: View) {
        when (view.id) {
            R.id.lock -> {}
            // Home button for recents feature disabled
            // R.id.recents -> {}
            R.id.clock -> openClockApp()
            R.id.date -> openCalendarApp()
            R.id.setDefaultLauncher -> viewModel.resetLauncherLiveData.call()
            R.id.tvScreenTime -> openScreenTimeDigitalWellbeing()

            else -> {
                try { // Launch app
                    val appLocation = view.tag.toString().toInt()
                    homeAppClicked(appLocation)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    private fun openClockApp() {
        if (prefs.clockAppPackage.isBlank())
            openAlarmApp(requireContext())
        else
            launchApp(
                "Clock",
                prefs.clockAppPackage,
                prefs.clockAppClassName,
                prefs.clockAppUser
            )
    }

    private fun openCalendarApp() {
        if (prefs.calendarAppPackage.isBlank())
            openCalendar(requireContext())
        else
            launchApp(
                "Calendar",
                prefs.calendarAppPackage,
                prefs.calendarAppClassName,
                prefs.calendarAppUser
            )
    }

    override fun onLongClick(view: View): Boolean {
        when (view.id) {
            R.id.homeApp1 -> showAppList(Constants.FLAG_SET_HOME_APP_1, prefs.appName1.isNotEmpty(), true)
            R.id.homeApp2 -> showAppList(Constants.FLAG_SET_HOME_APP_2, prefs.appName2.isNotEmpty(), true)
            R.id.homeApp3 -> showAppList(Constants.FLAG_SET_HOME_APP_3, prefs.appName3.isNotEmpty(), true)
            R.id.homeApp4 -> showAppList(Constants.FLAG_SET_HOME_APP_4, prefs.appName4.isNotEmpty(), true)
            R.id.homeApp5 -> showAppList(Constants.FLAG_SET_HOME_APP_5, prefs.appName5.isNotEmpty(), true)
            R.id.homeApp6 -> showAppList(Constants.FLAG_SET_HOME_APP_6, prefs.appName6.isNotEmpty(), true)
            R.id.homeApp7 -> showAppList(Constants.FLAG_SET_HOME_APP_7, prefs.appName7.isNotEmpty(), true)
            R.id.homeApp8 -> showAppList(Constants.FLAG_SET_HOME_APP_8, prefs.appName8.isNotEmpty(), true)
            R.id.clock -> {
                showAppList(Constants.FLAG_SET_CLOCK_APP)
                prefs.clockAppPackage = ""
                prefs.clockAppClassName = ""
                prefs.clockAppUser = ""
            }

            R.id.date -> {
                showAppList(Constants.FLAG_SET_CALENDAR_APP)
                prefs.calendarAppPackage = ""
                prefs.calendarAppClassName = ""
                prefs.calendarAppUser = ""
            }

            R.id.tvScreenTime -> {
                showAppList(Constants.FLAG_SET_SCREEN_TIME_APP)
                prefs.screenTimeAppPackage = ""
                prefs.screenTimeAppClassName = ""
                prefs.screenTimeAppUser = ""
            }

            R.id.setDefaultLauncher -> {
                prefs.hideSetDefaultLauncher = true
                binding.setDefaultLauncher.visibility = View.GONE
                if (viewModel.isStillDefault.value != true) {
                    requireContext().showToast(R.string.set_as_default_launcher)
                    findNavController().navigate(R.id.action_mainFragment_to_settingsFragment)
                }
            }
        }
        return true
    }

    private fun initObservers() {
        if (prefs.firstSettingsOpen) {
            binding.firstRunTips.visibility = View.VISIBLE
            binding.setDefaultLauncher.visibility = View.GONE
        } else binding.firstRunTips.visibility = View.GONE

        viewModel.refreshHome.observe(viewLifecycleOwner) {
            populateHomeScreen(it)
        }
        viewModel.isStillDefault.observe(viewLifecycleOwner, Observer {
            if (it != true) {
                if (prefs.dailyWallpaper && prefs.appTheme == AppCompatDelegate.MODE_NIGHT_YES) {
                    prefs.dailyWallpaper = false
                    viewModel.cancelWallpaperWorker()
                }
                prefs.homeBottomAlignment = false
                setHomeAlignment()
            }
            if (binding.firstRunTips.isVisible) return@Observer
            binding.setDefaultLauncher.isVisible = it.not() && prefs.hideSetDefaultLauncher.not()
        })
        viewModel.homeAppAlignment.observe(viewLifecycleOwner) {
            setHomeAlignment(it)
        }
        viewModel.toggleDateTime.observe(viewLifecycleOwner) {
            populateDateTime()
        }
        viewModel.screenTimeValue.observe(viewLifecycleOwner) {
            it?.let { binding.tvScreenTime.text = it }
        }
        // Home button for recents feature disabled
        // viewModel.showRecentApps.observe(viewLifecycleOwner) {
        //     binding.recents.performClick()
        // }
    }

    private fun initSwipeTouchListener() {
        val context = requireContext()
        binding.mainLayout.setOnTouchListener(getSwipeGestureListener(context))
        binding.homeScroll.onSwipeLeft = { showAppList(Constants.FLAG_LAUNCH_APP) }
        binding.homeScroll.onLongPressEmpty = { setEditing(true) }
        binding.homeScroll.onPullDown = { if (!editing) pullDown() }
        binding.root.viewTreeObserver.addOnWindowFocusChangeListener(onShadeClosed)
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, exitEditOnBack)
        // Keep the list below the clock whatever the clock size, so app names never cover the time.
        binding.dateTimeLayout.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> placeListBelowClock() }
        binding.homeApp1.setOnTouchListener(getViewSwipeTouchListener(context, binding.homeApp1))
        binding.homeApp2.setOnTouchListener(getViewSwipeTouchListener(context, binding.homeApp2))
        binding.homeApp3.setOnTouchListener(getViewSwipeTouchListener(context, binding.homeApp3))
        binding.homeApp4.setOnTouchListener(getViewSwipeTouchListener(context, binding.homeApp4))
        binding.homeApp5.setOnTouchListener(getViewSwipeTouchListener(context, binding.homeApp5))
        binding.homeApp6.setOnTouchListener(getViewSwipeTouchListener(context, binding.homeApp6))
        binding.homeApp7.setOnTouchListener(getViewSwipeTouchListener(context, binding.homeApp7))
        binding.homeApp8.setOnTouchListener(getViewSwipeTouchListener(context, binding.homeApp8))
    }

    private fun initClickListeners() {
        binding.lock.setOnClickListener(this)
        // Home button for recents feature disabled
        // binding.recents.setOnClickListener(this)
        binding.clock.setOnClickListener(this)
        binding.date.setOnClickListener(this)
        binding.clock.setOnLongClickListener(this)
        binding.date.setOnLongClickListener(this)
        binding.setDefaultLauncher.setOnClickListener(this)
        binding.setDefaultLauncher.setOnLongClickListener(this)
        binding.tvScreenTime.setOnClickListener(this)
        binding.tvScreenTime.setOnLongClickListener(this)
        binding.cornerPhone.setOnClickListener { openDialerApp(requireContext()) }
        binding.cornerCamera.setOnClickListener { openCameraApp(requireContext()) }

        // These fire only on d-pad/keyboard events; touch is consumed by ViewSwipeTouchListener
        binding.homeApp1.setOnClickListener(this)
        binding.homeApp2.setOnClickListener(this)
        binding.homeApp3.setOnClickListener(this)
        binding.homeApp4.setOnClickListener(this)
        binding.homeApp5.setOnClickListener(this)
        binding.homeApp6.setOnClickListener(this)
        binding.homeApp7.setOnClickListener(this)
        binding.homeApp8.setOnClickListener(this)
        binding.homeApp1.setOnLongClickListener(this)
        binding.homeApp2.setOnLongClickListener(this)
        binding.homeApp3.setOnLongClickListener(this)
        binding.homeApp4.setOnLongClickListener(this)
        binding.homeApp5.setOnLongClickListener(this)
        binding.homeApp6.setOnLongClickListener(this)
        binding.homeApp7.setOnLongClickListener(this)
        binding.homeApp8.setOnLongClickListener(this)
    }

    private fun setHomeAlignment(horizontalGravity: Int = prefs.homeAlignment) {
        val verticalGravity = if (prefs.homeBottomAlignment) Gravity.BOTTOM else Gravity.CENTER_VERTICAL
        binding.homeAppsLayout.gravity = horizontalGravity or verticalGravity
        binding.dateTimeLayout.gravity = horizontalGravity
        binding.homeApp1.gravity = horizontalGravity
        binding.homeApp2.gravity = horizontalGravity
        binding.homeApp3.gravity = horizontalGravity
        binding.homeApp4.gravity = horizontalGravity
        binding.homeApp5.gravity = horizontalGravity
        binding.homeApp6.gravity = horizontalGravity
        binding.homeApp7.gravity = horizontalGravity
        binding.homeApp8.gravity = horizontalGravity
    }

    private fun populateDateTime() {
        binding.dateTimeLayout.isVisible = prefs.dateTimeVisibility != Constants.DateTime.OFF
        binding.clock.isVisible = Constants.DateTime.isTimeVisible(prefs.dateTimeVisibility)
        binding.date.isVisible = Constants.DateTime.isDateVisible(prefs.dateTimeVisibility)

//        var dateText = SimpleDateFormat("EEE, d MMM", Locale.getDefault()).format(Date())
        val dateFormat = SimpleDateFormat("EEE, d MMM", Locale.getDefault())
        var dateText = dateFormat.format(Date())

        if (!prefs.showStatusBar) {
            val battery = (requireContext().getSystemService(Context.BATTERY_SERVICE) as BatteryManager)
                .getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            if (battery > 0)
                dateText = getString(R.string.day_battery, dateText, battery)
        }
        binding.date.text = dateText.replace(".,", ",")
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun populateScreenTime() {
        if (requireContext().appUsagePermissionGranted().not()) return

        viewModel.getTodaysScreenTime()
        binding.tvScreenTime.visibility = View.VISIBLE

        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val horizontalMargin = if (isLandscape) 64.dpToPx() else 10.dpToPx()
        val marginTop = if (isLandscape) {
            if (prefs.dateTimeVisibility == Constants.DateTime.DATE_ONLY) 36.dpToPx() else 56.dpToPx()
        } else {
            if (prefs.dateTimeVisibility == Constants.DateTime.DATE_ONLY) 45.dpToPx() else 72.dpToPx()
        }
        val params = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = marginTop
            marginStart = horizontalMargin
            marginEnd = horizontalMargin
            gravity = if (prefs.homeAlignment == Gravity.END) Gravity.START else Gravity.END
        }
        binding.tvScreenTime.layoutParams = params
        binding.tvScreenTime.setPadding(10.dpToPx())
    }

    private fun populateHomeScreen(appCountUpdated: Boolean) {
        if (appCountUpdated) hideHomeApps()
        populateDateTime()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            populateScreenTime()

        applyClockSize()
        carryOverFixedSlots()
        hideHomeApps()
        populateGroups()
    }

    private fun placeListBelowClock() {
        val top = if (binding.dateTimeLayout.isVisible) binding.dateTimeLayout.bottom + 16.dpToPx() else 56.dpToPx()
        // Bottom band reserved for the phone and camera icons, so rows never slide under them.
        val bottom = 96.dpToPx()
        if (binding.homeScroll.paddingTop != top || binding.homeScroll.paddingBottom != bottom)
            binding.homeScroll.setPadding(0, top, 0, bottom)
    }

    /** The clock ignores the text size setting and uses its own multiplier. */
    private fun applyClockSize() {
        val base = resources.getDimension(R.dimen.time_size) / resources.displayMetrics.scaledDensity
        binding.clock.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, base * prefs.clockSizeScale)
    }

    /** Once, moves the apps of the eight fixed home slots of earlier versions into the unlimited home list. */
    private fun carryOverFixedSlots() {
        if (prefs.hasHomeLayout) return
        val slots = (1..prefs.homeAppsNum).mapNotNull { slot ->
            val packageName = prefs.getAppPackage(slot)
            if (packageName.isBlank() || prefs.getIsShortcut(slot)) null
            else HomeApp(prefs.getAppName(slot), packageName, prefs.getAppActivityClassName(slot), prefs.getAppUser(slot))
        }
        prefs.homeLayout = HomeLayout.fromSlots(slots)
    }

    /** The group whose apps are listed under it; only one is open at a time. */
    private var openGroup: String? = null

    /** Edit mode: rows jiggle, can be dragged to reorder and carry a cross to remove them. */
    private var editing = false
    private val jiggles = mutableListOf<android.animation.Animator>()
    private val exitEditOnBack = object : androidx.activity.OnBackPressedCallback(false) {
        override fun handleOnBackPressed() = setEditing(false)
    }

    private fun setEditing(on: Boolean) {
        if (editing == on) return
        editing = on
        exitEditOnBack.isEnabled = on
        if (on) openGroup = null
        populateGroups()
    }

    private fun populateGroups() {
        val context = requireContext()
        val stored = prefs.homeLayout
        val layout = stored.retainInstalled { isPackageInstalled(context, it.packageName, it.user) }
        if (layout != stored) prefs.homeLayout = layout
        if (openGroup !in layout.groupNames) openGroup = null

        jiggles.forEach { it.cancel() }
        jiggles.clear()
        val container = binding.groupsLayout
        container.removeAllViews()
        layout.entries.forEachIndexed { index, entry ->
            when (entry) {
                is HomeEntry.App -> container.addView(
                    if (editing) editRow(entry.app.label, index, onRemove = {
                        saveAndRefresh(prefs.homeLayout.removeApp(entry.app))
                    })
                    else groupRow(entry.app.label, indent = false,
                        onClick = { launchHomeApp(entry.app) },
                        onLongClick = { setEditing(true) }
                    )
                )
                is HomeEntry.Group -> {
                    val group = entry
                    val isOpen = group.name == openGroup
                    val toggle = {
                        openGroup = if (isOpen) null else group.name
                        populateGroups()
                    }
                    container.addView(
                        if (editing) editRow(group.name, index, isOpen = isOpen, onTap = toggle,
                            onLongPress = { renameGroup(group.name) },
                            onRemove = { confirmDeleteGroup(group.name) })
                        else groupRow(group.name, indent = false, onClick = toggle,
                            onLongClick = { setEditing(true) }
                        ).also { withGroupMarker(it, isOpen) }
                    )
                    if (isOpen) group.apps.forEach { app ->
                        container.addView(
                            if (editing) editRow(app.label, index = null, indent = true, onRemove = {
                                saveAndRefresh(prefs.homeLayout.removeFromGroup(group.name, app))
                            })
                            else groupRow(app.label, indent = true,
                                onClick = { launchHomeApp(app) },
                                onLongClick = { setEditing(true) }
                            )
                        )
                    }
                }
            }
        }
        if (editing) {
            container.addView(footerRow(getString(R.string.home_new_group)) { createGroup() })
            container.addView(footerRow(getString(R.string.home_edit_done)) { setEditing(false) })
        }
    }

    /** True once a first pull has shown the status bar during this visit to home. */
    private var statusBarRevealed = false

    /** First pull shows the hidden status bar; the next one opens the notification shade. */
    private fun pullDown() {
        if (!prefs.showStatusBar && !statusBarRevealed) {
            statusBarRevealed = true
            showStatusBar()
        } else {
            shadeOpened = true
            expandNotificationDrawer(requireContext())
        }
    }

    /** Set when this screen opened the shade, so closing it can hide the status bar again. */
    private var shadeOpened = false

    /** Home gets window focus back when the shade closes: start over with the bar hidden. */
    private val onShadeClosed = android.view.ViewTreeObserver.OnWindowFocusChangeListener { hasFocus ->
        if (hasFocus && shadeOpened) {
            shadeOpened = false
            statusBarRevealed = false
            if (!prefs.showStatusBar) hideStatusBar()
        }
    }

    private fun launchHomeApp(app: HomeApp) =
        launchApp(app.label, app.packageName, app.activityClassName, app.user)

    private fun saveAndRefresh(layout: HomeLayout) {
        prefs.homeLayout = layout
        populateGroups()
    }

    private fun createGroup() {
        val context = requireContext()
        askGroupName(context, getString(R.string.home_new_group), "") { name ->
            val before = prefs.homeLayout
            val after = before.createGroup(name)
            if (after == before) context.showToast(getString(R.string.group_name_taken))
            else saveAndRefresh(after)
        }
    }

    private fun renameGroup(groupName: String) {
        val context = requireContext()
        askGroupName(context, getString(R.string.group_rename), groupName) { newName ->
            val before = prefs.homeLayout
            val after = before.renameGroup(groupName, newName)
            if (after == before) context.showToast(getString(R.string.group_name_taken))
            else {
                if (openGroup == groupName) openGroup = newName.trim()
                saveAndRefresh(after)
            }
        }
    }

    private fun confirmDeleteGroup(groupName: String) {
        AlertDialog.Builder(requireContext())
            .setMessage(getString(R.string.group_delete_confirm, groupName))
            .setPositiveButton(R.string.group_delete) { _, _ -> saveAndRefresh(prefs.homeLayout.deleteGroup(groupName)) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /** A light chevron after a closed group's name, a short line once it is open. */
    private fun withGroupMarker(row: TextView, isOpen: Boolean) {
        val icon = androidx.core.content.ContextCompat.getDrawable(
            requireContext(), if (isOpen) R.drawable.ic_group_open else R.drawable.ic_group_closed
        )?.mutate() ?: return
        val size = (row.textSize * 0.7f).toInt()
        icon.setBounds(0, 0, size, size)
        icon.setTint(row.currentTextColor)
        icon.alpha = 140
        row.text = android.text.SpannableStringBuilder(row.text).append("  ").append(
            " ", android.text.style.ImageSpan(icon, android.text.style.DynamicDrawableSpan.ALIGN_CENTER), 0
        )
    }

    private fun groupRow(text: String, indent: Boolean, onClick: () -> Unit, onLongClick: () -> Unit): TextView {
        val context = requireContext()
        return TextView(context).apply {
            setTextAppearance(if (indent) R.style.TextMedium else R.style.TextLarge)
            this.text = text
            gravity = prefs.homeAlignment
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            val vertical = resources.getDimensionPixelSize(R.dimen.home_app_padding_vertical)
            setPadding(if (indent) 24.dpToPx() else 0, vertical, 0, vertical)
            setOnTouchListener(object : ViewSwipeTouchListener(context, this) {
                override fun onClick(view: View) = onClick()
                override fun onLongClick(view: View) = onLongClick()
            })
        }
    }

    /**
     * A row in edit mode: the label jiggles, a cross at the end removes it. Rows with an [index]
     * are top-level entries and can be dragged to a new place; group members cannot.
     */
    private fun editRow(
        text: String,
        index: Int?,
        indent: Boolean = false,
        isOpen: Boolean = false,
        onTap: (() -> Unit)? = null,
        onLongPress: (() -> Unit)? = null,
        onRemove: () -> Unit,
    ): View {
        val context = requireContext()
        val label = groupRow(text, indent, onClick = {}, onLongClick = {}).apply {
            setOnTouchListener(null)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            if (onTap != null) withGroupMarker(this, isOpen)
        }
        val cross = android.widget.ImageView(context).apply {
            setImageResource(R.drawable.ic_edit_remove)
            imageTintList = android.content.res.ColorStateList.valueOf(label.currentTextColor)
            alpha = 0.6f
            contentDescription = getString(R.string.home_edit_remove)
            val pad = 12.dpToPx()
            setPadding(pad, pad, pad, pad)
            layoutParams = LinearLayout.LayoutParams(48.dpToPx(), 48.dpToPx()).apply {
                gravity = android.view.Gravity.CENTER_VERTICAL
            }
            setOnClickListener { onRemove() }
        }
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            addView(label)
            addView(cross)
            if (index != null) tag = index
        }
        label.setOnTouchListener(EditRowTouch(row, index, onTap, onLongPress))
        jiggle(label)
        return row
    }

    private fun footerRow(text: String, onClick: () -> Unit): TextView =
        groupRow(text, indent = false, onClick = onClick, onLongClick = {}).apply {
            setTextAppearance(R.style.TextMedium)
            alpha = 0.6f
            setOnTouchListener(null)
            setOnClickListener { onClick() }
        }

    private fun jiggle(view: View) {
        val angle = 0.7f
        val animator = android.animation.ObjectAnimator.ofFloat(view, View.ROTATION, -angle, angle).apply {
            duration = 130L + (0..40).random()
            repeatCount = android.animation.ValueAnimator.INFINITE
            repeatMode = android.animation.ValueAnimator.REVERSE
            startDelay = (0..120).random().toLong()
        }
        view.pivotX = 0f
        animator.start()
        jiggles += animator
    }

    /** Tap, long press and vertical drag for a row in edit mode. */
    private inner class EditRowTouch(
        private val row: View,
        private val index: Int?,
        private val onTap: (() -> Unit)?,
        private val onLongPress: (() -> Unit)?,
    ) : View.OnTouchListener {
        private val slop = android.view.ViewConfiguration.get(requireContext()).scaledTouchSlop
        private var downY = 0f
        private var dragging = false
        private var longPressed = false
        private val longPress = Runnable { longPressed = true; onLongPress?.invoke() }

        override fun onTouch(view: View, event: android.view.MotionEvent): Boolean {
            when (event.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    downY = event.rawY
                    dragging = false
                    longPressed = false
                    if (onLongPress != null) view.postDelayed(longPress, android.view.ViewConfiguration.getLongPressTimeout().toLong())
                    view.parent.requestDisallowInterceptTouchEvent(true)
                }
                android.view.MotionEvent.ACTION_MOVE -> {
                    val dy = event.rawY - downY
                    if (!dragging && index != null && kotlin.math.abs(dy) > slop) {
                        view.removeCallbacks(longPress)
                        if (openGroup != null) {
                            // Member rows sit between the entries; close the group before reordering.
                            openGroup = null
                            populateGroups()
                            return true
                        }
                        dragging = true
                        row.elevation = 8f
                    }
                    if (dragging) {
                        row.translationY = dy
                        shiftOthers()
                    }
                }
                android.view.MotionEvent.ACTION_UP -> {
                    view.removeCallbacks(longPress)
                    if (dragging) drop()
                    else if (!longPressed && kotlin.math.abs(event.rawY - downY) <= slop) onTap?.invoke()
                }
                android.view.MotionEvent.ACTION_CANCEL -> {
                    view.removeCallbacks(longPress)
                    if (dragging) drop()
                }
            }
            return true
        }

        private fun entryRows(): List<View> = (0 until binding.groupsLayout.childCount)
            .map { binding.groupsLayout.getChildAt(it) }
            .filter { it.tag is Int }

        private fun target(): Int {
            val rows = entryRows()
            val centres = rows.map { it.top + it.height / 2f }
            val from = rows.indexOf(row)
            return app.still.data.HomeDrag.target(centres, from, row.top + row.height / 2f + row.translationY)
        }

        /** Slides the rows between the start and the landing place to open a gap. */
        private fun shiftOthers() {
            val rows = entryRows()
            val from = rows.indexOf(row)
            val to = target()
            rows.forEachIndexed { i, other ->
                if (other === row) return@forEachIndexed
                val shift = when {
                    to > from && i in (from + 1)..to -> -row.height.toFloat()
                    to < from && i in to until from -> row.height.toFloat()
                    else -> 0f
                }
                if (other.translationY != shift) other.animate().translationY(shift).setDuration(120).start()
            }
        }

        private fun drop() {
            val from = index ?: return
            val offset = target() - entryRows().indexOf(row)
            dragging = false
            saveAndRefresh(prefs.homeLayout.move(from, offset))
        }
    }

    private fun hideHomeApps() {
        binding.homeApp1.visibility = View.GONE
        binding.homeApp2.visibility = View.GONE
        binding.homeApp3.visibility = View.GONE
        binding.homeApp4.visibility = View.GONE
        binding.homeApp5.visibility = View.GONE
        binding.homeApp6.visibility = View.GONE
        binding.homeApp7.visibility = View.GONE
        binding.homeApp8.visibility = View.GONE
    }

    private fun launchAppOrShortcut(
        appName: String,
        packageName: String,
        activityClassName: String?,
        shortcutId: String?,
        isShortcut: Boolean,
        userString: String,
        fallback: (() -> Unit)? = null,
    ) {
        if (appName.isEmpty()) {
            showLongPressToast()
            return
        }
        if (isShortcut && !shortcutId.isNullOrEmpty()) {
            launchShortcut(
                packageName = packageName,
                shortcutId = shortcutId,
                shortcutLabel = appName,
                userString = userString
            )
        } else if (packageName.isNotEmpty()) {
            launchApp(
                appName = appName,
                packageName = packageName,
                activityClassName = activityClassName,
                userString = userString
            )
        } else {
            fallback?.invoke()
        }
    }

    private fun launchShortcut(shortcutId: String, packageName: String, shortcutLabel: String, userString: String) {
        viewModel.selectedApp(
            AppModel.PinnedShortcut(
                shortcutId = shortcutId,
                appLabel = shortcutLabel,
                user = getUserHandleFromString(requireContext(), userString),
                key = null,
                appPackage = packageName,
                isNew = false,
            ),
            Constants.FLAG_LAUNCH_APP
        )
    }

    private fun launchApp(appName: String, packageName: String, activityClassName: String?, userString: String) {
        viewModel.selectedApp(
            AppModel.App(
                appLabel = appName,
                key = null,
                appPackage = packageName,
                activityClassName = activityClassName,
                isNew = false,
                user = getUserHandleFromString(requireContext(), userString)
            ),
            Constants.FLAG_LAUNCH_APP
        )
    }

    private fun homeAppClicked(location: Int) {
        launchAppOrShortcut(
            appName = prefs.getAppName(location),
            packageName = prefs.getAppPackage(location),
            activityClassName = prefs.getAppActivityClassName(location),
            shortcutId = prefs.getShortcutId(location),
            isShortcut = prefs.getIsShortcut(location),
            userString = prefs.getAppUser(location)
        )
    }

    private fun showAppList(flag: Int, rename: Boolean = false, includeHiddenApps: Boolean = false) {
        viewModel.getAppList(includeHiddenApps)
        try {
            findNavController().navigate(
                R.id.action_mainFragment_to_appListFragment,
                bundleOf(
                    Constants.Key.FLAG to flag,
                    Constants.Key.RENAME to rename
                )
            )
        } catch (e: Exception) {
            findNavController().navigate(
                R.id.appListFragment,
                bundleOf(
                    Constants.Key.FLAG to flag,
                    Constants.Key.RENAME to rename
                )
            )
            e.printStackTrace()
        }
    }

    private fun lockPhone() {
        requireActivity().runOnUiThread {
            try {
                deviceManager.lockNow()
            } catch (e: SecurityException) {
                requireContext().showToast(getString(R.string.please_turn_on_double_tap_to_unlock), Toast.LENGTH_LONG)
                findNavController().navigate(R.id.action_mainFragment_to_settingsFragment)
            } catch (e: Exception) {
                requireContext().showToast(getString(R.string.launcher_failed_to_lock_device), Toast.LENGTH_LONG)
                prefs.lockModeOn = false
            }
        }
    }

    private fun showStatusBar() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
            requireActivity().window.insetsController?.show(WindowInsets.Type.statusBars())
        else
            @Suppress("DEPRECATION", "InlinedApi")
            requireActivity().window.decorView.apply {
                systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            }
    }

    private fun hideStatusBar() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
            requireActivity().window.insetsController?.hide(WindowInsets.Type.statusBars())
        else {
            @Suppress("DEPRECATION")
            requireActivity().window.decorView.apply {
                systemUiVisibility = View.SYSTEM_UI_FLAG_IMMERSIVE or View.SYSTEM_UI_FLAG_FULLSCREEN
            }
        }
    }

    private fun changeAppTheme() {
        if (prefs.dailyWallpaper.not()) return
        val changedAppTheme = getChangedAppTheme(requireContext(), prefs.appTheme)
        prefs.appTheme = changedAppTheme
        if (prefs.dailyWallpaper) {
            setPlainWallpaperByTheme(requireContext(), changedAppTheme)
            viewModel.setWallpaperWorker()
        }
        requireActivity().recreate()
    }

    private fun openScreenTimeDigitalWellbeing() {
        if (prefs.screenTimeAppPackage.isNotBlank()) {
            launchApp(
                "Screen Time",
                prefs.screenTimeAppPackage,
                prefs.screenTimeAppClassName,
                prefs.screenTimeAppUser
            )
            return
        }
        val intent = Intent()
        try {
            intent.setClassName(
                Constants.DIGITAL_WELLBEING_PACKAGE_NAME,
                Constants.DIGITAL_WELLBEING_ACTIVITY
            )
            startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
            try {
                intent.setClassName(
                    Constants.DIGITAL_WELLBEING_SAMSUNG_PACKAGE_NAME,
                    Constants.DIGITAL_WELLBEING_SAMSUNG_ACTIVITY
                )
                startActivity(intent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun showLongPressToast() = requireContext().showToast(getString(R.string.long_press_to_select_app))

    private fun textOnClick(view: View) = onClick(view)

    private fun textOnLongClick(view: View) = onLongClick(view)

    private fun getSwipeGestureListener(context: Context): View.OnTouchListener {
        return object : OnSwipeTouchListener(context) {


            override fun onSwipeLeft() {
                super.onSwipeLeft()
                showAppList(Constants.FLAG_LAUNCH_APP)
            }

            override fun onSwipeDown() {
                super.onSwipeDown()
                expandNotificationDrawer(requireContext())
            }

            override fun onLongClick() {
                super.onLongClick()
                setEditing(true)
            }

            override fun onDoubleClick() {
                super.onDoubleClick()
                if (!prefs.lockModeOn) return
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
                    binding.lock.performClick()
                else
                    lockPhone()
            }

            override fun onClick() {
                super.onClick()
                viewModel.checkForMessages.call()
            }
        }
    }

    private fun getViewSwipeTouchListener(context: Context, view: View): View.OnTouchListener {
        return object : ViewSwipeTouchListener(context, view) {


            override fun onSwipeLeft() {
                super.onSwipeLeft()
                showAppList(Constants.FLAG_LAUNCH_APP)
            }

            override fun onSwipeDown() {
                super.onSwipeDown()
                expandNotificationDrawer(requireContext())
            }

            override fun onLongClick(view: View) {
                super.onLongClick(view)
                textOnLongClick(view)
            }

            override fun onClick(view: View) {
                super.onClick(view)
                textOnClick(view)
            }
        }
    }

    override fun onDestroyView() {
        binding.root.viewTreeObserver.removeOnWindowFocusChangeListener(onShadeClosed)
        super.onDestroyView()
        _binding = null
    }
}