package org.dvbviewer.controller.ui.fragments

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.preference.CheckBoxPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import org.dvbviewer.controller.R
import org.dvbviewer.controller.data.entities.DVBViewerPreferences
import org.dvbviewer.controller.data.xmltv.XmltvRefreshWorker
import org.dvbviewer.controller.ui.phone.ConnectionPreferencesActivity
import org.dvbviewer.controller.ui.phone.XmltvMappingActivity

class DVBViewerPreferenceFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(bundle: Bundle?, s: String?) {
        val prefMgr = preferenceManager
        prefMgr.sharedPreferencesName = DVBViewerPreferences.PREFS
        addPreferencesFromResource(R.xml.preferences)

        // Restart activity immediately when theme changes
        preferenceScreen.findPreference<ListPreference>(DVBViewerPreferences.KEY_APP_THEME)
            ?.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { pref, newValue ->
            val newTheme = newValue as? String ?: "?"
            val prefsFile = DVBViewerPreferences.PREFS
            val key = DVBViewerPreferences.KEY_APP_THEME
            Log.d(TAG_THEME, "KEY_APP_THEME changing to \"$newTheme\"")
            Log.d(TAG_THEME, "PreferenceManager sharedPrefsName=\"${prefMgr.sharedPreferencesName}\" file expected=\"$prefsFile\"")
            // returning true tells PreferenceManager to persist newValue AFTER this listener returns
            // recreate() is async (posts to handler) so persistence happens first
            Log.d(TAG_THEME, "Scheduling recreate() — value will be persisted before recreation")
            activity?.recreate()
            // Verify after a short delay what was actually saved (on UI thread, after persistence)
            pref.context.mainLooper.let { looper ->
                android.os.Handler(looper).postDelayed({
                    val saved = pref.context
                        .getSharedPreferences(prefsFile, android.content.Context.MODE_PRIVATE)
                        .getString(key, null)
                    Log.d(TAG_THEME, "POST-PERSIST check: key=\"$key\" saved value=\"$saved\" in file=\"$prefsFile\"")
                }, 200)
            }
            true
        }

        // Navigate to connection settings screen
        preferenceScreen.findPreference<Preference>(KEY_RS_SETTINGS)
            ?.onPreferenceClickListener = Preference.OnPreferenceClickListener {
            startActivity(Intent(context, ConnectionPreferencesActivity::class.java))
            false
        }

        // Open Channel Name Mapping screen
        preferenceScreen.findPreference<Preference>(DVBViewerPreferences.KEY_XMLTV_CHANNEL_MAPPING)
            ?.onPreferenceClickListener = Preference.OnPreferenceClickListener {
            startActivity(Intent(context, XmltvMappingActivity::class.java))
            false
        }

        // FIX: schedule / cancel the XMLTV worker immediately when the toggle changes
        // so the user doesn't have to restart the app to trigger a download.
        preferenceScreen.findPreference<CheckBoxPreference>(DVBViewerPreferences.KEY_XMLTV_ENABLED)
            ?.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, newValue ->
            val enabled = newValue as? Boolean ?: false
            Log.d(TAG, "KEY_XMLTV_ENABLED changed to $enabled")
            if (enabled) {
                XmltvRefreshWorker.schedule(requireContext())
            } else {
                XmltvRefreshWorker.cancel(requireContext())
            }
            true  // allow the change to be persisted
        }
    }

    companion object {
        private const val TAG            = "DVBViewerPrefFragment"
        private const val TAG_THEME      = "ThemeDebug"
        private const val KEY_RS_SETTINGS = "KEY_RS_SETTINGS"
    }
}
