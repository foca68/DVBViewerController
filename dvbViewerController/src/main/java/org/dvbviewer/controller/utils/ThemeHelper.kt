package org.dvbviewer.controller.utils

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import org.dvbviewer.controller.R
import org.dvbviewer.controller.data.entities.DVBViewerPreferences

object ThemeHelper {

    fun applyTheme(activity: AppCompatActivity) {
        val prefs = activity.getSharedPreferences(DVBViewerPreferences.PREFS, Context.MODE_PRIVATE)
        val theme = prefs.getString(DVBViewerPreferences.KEY_APP_THEME, "blue") ?: "blue"
        activity.setTheme(
            when (theme) {
                "purple" -> R.style.Theme_DVB_Purple
                "green"  -> R.style.Theme_DVB_Green
                "red"    -> R.style.Theme_DVB_Red
                "orange" -> R.style.Theme_DVB_Orange
                "gray"   -> R.style.Theme_DVB_Gray
                else     -> R.style.Theme_DVB_Blue
            }
        )
    }

    fun currentTheme(context: Context): String =
        context.getSharedPreferences(DVBViewerPreferences.PREFS, Context.MODE_PRIVATE)
            .getString(DVBViewerPreferences.KEY_APP_THEME, "blue") ?: "blue"
}
