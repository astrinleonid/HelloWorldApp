package com.example.helloworldapp.components

import AppConfig
import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.util.TypedValue
import android.widget.ImageButton
import android.widget.ImageView
import androidx.appcompat.widget.Toolbar
import com.example.helloworldapp.R
import com.example.helloworldapp.utils.SettingsUtils

class CustomToolbar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : Toolbar(context, attrs, defStyleAttr) {

    private val statusIcon: ImageView
    private val settingsButton: ImageButton

    init {
        inflate(context, R.layout.custom_toolbar, this)
        statusIcon = findViewById(R.id.online_offline_indicator)
        settingsButton = findViewById(R.id.settings_button)

        settingsButton.setOnClickListener {
            // Show settings dialog
            SettingsUtils.showSettingsDialog(context)
        }

        // Make the status icon clickable to toggle online/offline mode
        statusIcon.isClickable = true
        statusIcon.isFocusable = true

        // Add ripple effect for better touch feedback
        val outValue = TypedValue()
        context.theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, outValue, true)
        statusIcon.setBackgroundResource(outValue.resourceId)

        statusIcon.setOnClickListener {
            toggleOnlineMode(context)
        }
    }

    private fun toggleOnlineMode(context: Context) {
        if (!AppConfig.online) {
            // Currently offline, try to go online
            SettingsUtils.goOnline(context) { success ->
                Log.d("CustomToolbar", "Toggle to online mode result: $success")
                // The list refresh will be handled by the RecordManager's callback
            }
        } else {
            // Currently online, go offline
            SettingsUtils.goOffline(context)
            // For offline mode, we don't need to wait for any background operations
            Log.d("CustomToolbar", "Toggled to offline mode")
        }
    }

    fun setOnlineMode(isOnline: Boolean) {
        // Set the appropriate icon based on online status
        statusIcon.setImageResource(
            if (isOnline) R.drawable.ic_status_online
            else R.drawable.ic_status_offline
        )

        // Add a content description for accessibility
        statusIcon.contentDescription =
            if (isOnline) "Network connected (tap to go offline)"
            else "Network disconnected (tap to go online)"
    }
}