package io.github.yanniboi.freeze

import android.Manifest
import android.app.Activity
import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.CompoundButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/** Setup screen: permissions, tile/widget hints, a safe test lock, and settings. */
class MainActivity : Activity() {
    private lateinit var prefs: Prefs

    private lateinit var overlayStatus: TextView
    private lateinit var overlayButton: Button
    private lateinit var notificationStatus: TextView
    private lateinit var notificationButton: Button
    private lateinit var startButton: Button
    private lateinit var testButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        prefs = Prefs(this)
        applySystemBarInsets(findViewById(R.id.content))

        overlayStatus = findViewById(R.id.overlay_status)
        overlayButton = findViewById(R.id.overlay_button)
        notificationStatus = findViewById(R.id.notification_status)
        notificationButton = findViewById(R.id.notification_button)
        startButton = findViewById(R.id.start_button)
        testButton = findViewById(R.id.test_button)

        overlayButton.setOnClickListener {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, "package:$packageName".toUri()))
        }
        notificationButton.setOnClickListener { requestNotifications() }
        startButton.setOnClickListener { FreezeService.send(this, FreezeService.ACTION_START) }
        testButton.setOnClickListener { FreezeService.send(this, FreezeService.ACTION_TEST_LOCK) }

        val tileButton = findViewById<Button>(R.id.tile_button)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            tileButton.setOnClickListener { requestAddTile() }
        } else {
            tileButton.visibility = View.GONE
        }

        bindSwitch(R.id.keep_screen_on_switch, prefs.keepScreenOn) { prefs.keepScreenOn = it }
        bindSwitch(R.id.auto_unlock_switch, prefs.autoUnlockOnCallEnd) { prefs.autoUnlockOnCallEnd = it }
        bindSwitch(R.id.emergency_switch, prefs.emergencyUnlock) { prefs.emergencyUnlock = it }

        val tintGroup = findViewById<RadioGroup>(R.id.tint_group)
        tintGroup.check(
            when (prefs.tint) {
                Tint.NONE -> R.id.tint_none
                Tint.FAINT -> R.id.tint_faint
                Tint.DIM -> R.id.tint_dim
            },
        )
        tintGroup.setOnCheckedChangeListener { _, checkedId ->
            prefs.tint = when (checkedId) {
                R.id.tint_none -> Tint.NONE
                R.id.tint_dim -> Tint.DIM
                else -> Tint.FAINT
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        refreshStatus()
    }

    private fun refreshStatus() {
        val overlayOk = Permissions.canDrawOverlays(this)
        overlayStatus.setText(if (overlayOk) R.string.status_allowed else R.string.status_needed)
        overlayButton.visibility = if (overlayOk) View.GONE else View.VISIBLE

        val notificationsOk = Permissions.canPostNotifications(this)
        notificationStatus.setText(if (notificationsOk) R.string.status_allowed else R.string.status_needed)
        notificationButton.visibility = if (notificationsOk) View.GONE else View.VISIBLE

        val ready = overlayOk && notificationsOk
        startButton.isEnabled = ready
        testButton.isEnabled = ready

        // Permissions may have just changed: the widget's tap target depends on them, and a running
        // service may be showing a "can't lock yet" notification.
        FreezeWidgetProvider.refresh(this)
        if (ready && FreezeState.mode != Mode.OFF) FreezeService.send(this, FreezeService.ACTION_REPOST)
    }

    private fun requestNotifications() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            (!prefs.askedForNotificationPermission || shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS))
        ) {
            prefs.askedForNotificationPermission = true
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_NOTIFICATIONS)
        } else {
            // Already refused (or turned off pre-13): only the settings screen can change it now.
            startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName),
            )
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun requestAddTile() {
        getSystemService(StatusBarManager::class.java).requestAddTileService(
            ComponentName(this, FreezeTileService::class.java),
            getString(R.string.tile_label),
            Icon.createWithResource(this, R.drawable.ic_stat_freeze),
            mainExecutor,
        ) { result ->
            val message = when (result) {
                StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED,
                StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED,
                -> R.string.tile_added
                else -> R.string.tile_not_added
            }
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun bindSwitch(id: Int, initial: Boolean, onChange: (Boolean) -> Unit) {
        findViewById<CompoundButton>(id).apply {
            isChecked = initial
            setOnCheckedChangeListener { _, checked -> onChange(checked) }
        }
    }

    /** targetSdk 35+ draws edge to edge, so keep the content clear of the status and nav bars. */
    private fun applySystemBarInsets(view: View) {
        val left = view.paddingLeft
        val top = view.paddingTop
        val right = view.paddingRight
        val bottom = view.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            v.setPadding(left + bars.left, top + bars.top, right + bars.right, bottom + bars.bottom)
            insets
        }
    }

    private companion object {
        const val REQUEST_NOTIFICATIONS = 1
    }
}
