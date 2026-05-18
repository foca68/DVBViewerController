package org.dvbviewer.controller.utils

import android.content.Context
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import org.dvbviewer.controller.R
import org.dvbviewer.controller.data.entities.DVBViewerPreferences

object ThemeHelper {

    private const val TAG = "ThemeDebug"

    fun getThemeResId(context: Context): Int {
        val theme = context
            .getSharedPreferences(DVBViewerPreferences.PREFS, Context.MODE_PRIVATE)
            .getString(DVBViewerPreferences.KEY_APP_THEME, null)
        Log.d(TAG, "getThemeResId: theme=\"$theme\"")
        return when (theme) {
            "purple" -> R.style.Theme_DVB_Purple
            "green"  -> R.style.Theme_DVB_Green
            "red"    -> R.style.Theme_DVB_Red
            "orange" -> R.style.Theme_DVB_Orange
            "gray"   -> R.style.Theme_DVB_Gray
            else     -> R.style.Theme_DVB_Blue
        }
    }

    fun applyTheme(activity: AppCompatActivity) {
        val resId = getThemeResId(activity)
        Log.d(TAG, "applyTheme: ${activity.localClassName} resId=$resId")
        activity.setTheme(resId)
    }

    fun currentTheme(context: Context): String {
        val value = context
            .getSharedPreferences(DVBViewerPreferences.PREFS, Context.MODE_PRIVATE)
            .getString(DVBViewerPreferences.KEY_APP_THEME, null)
        Log.d(TAG, "currentTheme: value=\"$value\"")
        return value ?: "blue"
    }
}
