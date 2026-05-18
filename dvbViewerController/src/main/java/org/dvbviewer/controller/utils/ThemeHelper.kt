package org.dvbviewer.controller.utils

import android.content.Context
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import org.dvbviewer.controller.R
import org.dvbviewer.controller.data.entities.DVBViewerPreferences

object ThemeHelper {

    private const val TAG = "ThemeDebug"

    fun applyTheme(activity: AppCompatActivity) {
        val prefsFile = DVBViewerPreferences.PREFS
        val key       = DVBViewerPreferences.KEY_APP_THEME
        val prefs     = activity.getSharedPreferences(prefsFile, Context.MODE_PRIVATE)

        val allKeys = prefs.all.keys.joinToString()
        Log.d(TAG, "applyTheme: reading from SharedPreferences file=\"$prefsFile\"")
        Log.d(TAG, "applyTheme: all keys in file=[$allKeys]")

        val theme = prefs.getString(key, null)
        Log.d(TAG, "applyTheme: raw value for key=\"$key\" → \"$theme\" (null means key absent)")

        val resolvedTheme = theme ?: "blue"
        val resId = when (resolvedTheme) {
            "purple" -> R.style.Theme_DVB_Purple
            "green"  -> R.style.Theme_DVB_Green
            "red"    -> R.style.Theme_DVB_Red
            "orange" -> R.style.Theme_DVB_Orange
            "gray"   -> R.style.Theme_DVB_Gray
            else     -> R.style.Theme_DVB_Blue
        }
        Log.d(TAG, "applyTheme: resolved theme=\"$resolvedTheme\" → resId=$resId calling setTheme()")
        activity.setTheme(resId)
        Log.d(TAG, "applyTheme: setTheme() called on ${activity.localClassName}")
    }

    fun currentTheme(context: Context): String {
        val prefsFile = DVBViewerPreferences.PREFS
        val key       = DVBViewerPreferences.KEY_APP_THEME
        val value     = context.getSharedPreferences(prefsFile, Context.MODE_PRIVATE)
            .getString(key, null)
        Log.d(TAG, "currentTheme: file=\"$prefsFile\" key=\"$key\" value=\"$value\"")
        return value ?: "blue"
    }
}
