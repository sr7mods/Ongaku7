package com.example.player

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.annotation.DrawableRes
import com.example.R

data class AppIconOption(
    val id: String,
    val title: String,
    val subtitle: String,
    val aliasName: String,
    @param:DrawableRes val previewRes: Int
)

object AppIconManager {
    const val ICON_DEFAULT = "default"
    const val ICON_HOLIDAYS = "holidays"
    const val ICON_SPRING_DAYS = "holidays"
    const val ICON_CUTENESS = "cuteness"
    const val ICON_RAMADAN = "ramadan"
    const val ICON_PIXELART = "pixelart"

    private const val PREFS_NAME = "ongaku7_icon_prefs"
    private const val KEY_ACTIVE_ICON = "active_icon_id"

    val ICON_OPTIONS = listOf(
        AppIconOption(
            id = "default",
            title = "Default Icon",
            subtitle = "Designer: SR7 Mods",
            aliasName = "com.example.MainActivityDefault",
            previewRes = R.drawable.ic_preview_default
        ),
        AppIconOption(
            id = "holidays",
            title = "Spring Days",
            subtitle = "Designer: SR7 Crasher",
            aliasName = "com.example.MainActivityHolidays",
            previewRes = R.drawable.ic_art_spring_days
        ),
        AppIconOption(
            id = "cuteness",
            title = "Cuteness",
            subtitle = "Designer: SR7 Mods",
            aliasName = "com.example.MainActivityCuteness",
            previewRes = R.drawable.ic_art_cuteness
        ),
        AppIconOption(
            id = "ramadan",
            title = "Ramadan Night",
            subtitle = "Designer: SR7 Crasher",
            aliasName = "com.example.MainActivityRamadan",
            previewRes = R.drawable.ic_art_ramadan
        ),
        AppIconOption(
            id = "pixelart",
            title = "Pixel Art",
            subtitle = "Designer: SR7 Mods",
            aliasName = "com.example.MainActivityPixelArt",
            previewRes = R.drawable.ic_art_pixelart
        )
    )

    fun getActiveIconId(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_ACTIVE_ICON, "default") ?: "default"
    }

    fun setAppIcon(context: Context, targetId: String): Boolean {
        val targetOption = ICON_OPTIONS.firstOrNull { it.id == targetId } ?: return false
        try {
            val pm = context.packageManager
            val packageName = context.packageName

            // Enable target component first
            pm.setComponentEnabledSetting(
                ComponentName(packageName, targetOption.aliasName),
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP
            )

            // Disable other aliases
            ICON_OPTIONS.filter { it.id != targetId }.forEach { option ->
                pm.setComponentEnabledSetting(
                    ComponentName(packageName, option.aliasName),
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP
                )
            }

            // Save active icon preference
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_ACTIVE_ICON, targetId)
                .apply()

            return true
        } catch (e: Exception) {
            Log.e("AppIconManager", "Failed to switch app icon: ${e.message}", e)
            return false
        }
    }
}
