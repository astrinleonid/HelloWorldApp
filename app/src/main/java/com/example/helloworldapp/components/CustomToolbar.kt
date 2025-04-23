package com.example.helloworldapp.components

import android.content.Context
import android.util.AttributeSet
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
    }

    fun setOnlineMode(isOnline: Boolean) {
        // Set the appropriate icon based on online status
        statusIcon.setImageResource(
            if (isOnline) R.drawable.ic_status_online
            else R.drawable.ic_status_offline
        )

        // Optional: Add a content description for accessibility
        statusIcon.contentDescription =
            if (isOnline) "Online"
            else "Offline"
    }
}