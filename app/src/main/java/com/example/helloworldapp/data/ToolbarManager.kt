package com.example.helloworldapp.data
import AppConfig
import android.content.Context
import android.graphics.drawable.Drawable
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.text.style.ImageSpan
import android.util.Log
import android.view.Menu
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.helloworldapp.R
import com.example.helloworldapp.utils.SettingsUtils
import com.google.android.material.appbar.MaterialToolbar


class ToolbarManager {
    companion object {
        /**
         * Set up the toolbar for an activity
         *
         * @param activity The activity where the toolbar is being set up
         * @param title Optional title to display in the toolbar
         */
// In ToolbarManager.kt
        fun setupToolbar(
            activity: AppCompatActivity,
            title: String? = null
        ) {
            // Find the toolbar within the activity
            val toolbar = activity.findViewById<MaterialToolbar>(R.id.top_app_bar)
                ?: return  // Return early if toolbar is not found

            // Set the toolbar as the action bar
            activity.setSupportActionBar(toolbar)

            // Set the title immediately
            val displayTitle = title ?: activity.getString(R.string.app_name)
            val modeText = if (AppConfig.online) "Online" else "Offline"
            activity.supportActionBar?.title = "$displayTitle ($modeText)"

            // Do NOT inflate menu here, let the activity handle it

            Log.d("ToolbarManager", "Toolbar setup complete. Action bar set.")
        }

        // Make this function visible but not used by default
        fun directlySetupMenu(activity: AppCompatActivity, toolbar: MaterialToolbar) {
            // Only use this if the normal menu inflation isn't working
            toolbar.inflateMenu(R.menu.menu_main)
            toolbar.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.settings_menu -> {
                        SettingsUtils.showSettingsDialog(activity)
                        true
                    }
                    else -> false
                }
            }
            Log.d("ToolbarManager", "Menu directly inflated on toolbar")
        }

        /**
         * Updates the toolbar's appearance including title and online/offline indication
         *
         * @param activity The activity containing the toolbar
         * @param title Optional title to display (defaults to app name if null)
         */
        fun updateToolbarAppearance(
            activity: AppCompatActivity,
            title: String? = null
        ) {
            val isOnline = AppConfig.online
            val toolbar = activity.findViewById<MaterialToolbar>(R.id.top_app_bar)
                ?: return  // Return early if toolbar is not found

            // Adjust toolbar background color based on mode
            val backgroundColor = if (isOnline) {
                ContextCompat.getColor(activity, R.color.colorPrimary) // Default color
            } else {
                ContextCompat.getColor(activity, R.color.colorPrimaryDark) // Darker shade for offline
            }
            toolbar.setBackgroundColor(backgroundColor)

            // Create and set the status indicator
            val indicatorDrawable = getStatusIndicator(activity, isOnline)

            // Create a title with the mode text
            val displayTitle = title ?: activity.getString(R.string.app_name)

            // Create a SpannableString to add the indicator
            val spannable = SpannableString("  $displayTitle")
            indicatorDrawable?.let {
                spannable.setSpan(
                    ImageSpan(it, ImageSpan.ALIGN_BASELINE),
                    0, 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }

            // Add the online/offline text
            val modeText = if (isOnline) " (Online)" else " (Offline)"
            val fullSpannable = SpannableString("$spannable$modeText")

            fullSpannable.setSpan(
                ForegroundColorSpan(
                    ContextCompat.getColor(activity, if (isOnline) R.color.colorOnline else R.color.colorOffline)
                ),
                spannable.length, fullSpannable.length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )

            activity.supportActionBar?.title = fullSpannable
        }

        /**
         * Get the appropriate status indicator drawable based on online/offline status
         *
         * @param context The context to use for getting resources
         * @param isOnline Whether the app is in online mode
         * @return A drawable configured for use as a status indicator
         */
        private fun getStatusIndicator(context: Context, isOnline: Boolean): Drawable? {
            val indicatorDrawable = if (isOnline) {
                ContextCompat.getDrawable(context, R.drawable.online_indicator)
            } else {
                ContextCompat.getDrawable(context, R.drawable.offline_indicator)
            }

            indicatorDrawable?.setBounds(0, 0, 24, 24)
            return indicatorDrawable
        }

        /**
         * Create the options menu for the toolbar
         *
         * @param activity The activity where the menu is being created
         * @param menu The menu to be populated
         * @return True if the menu was successfully created
         */
        fun createOptionsMenu(activity: AppCompatActivity, menu: Menu): Boolean {
            val inflater = activity.menuInflater
            inflater.inflate(R.menu.menu_main, menu)
            return true
        }
    }
}