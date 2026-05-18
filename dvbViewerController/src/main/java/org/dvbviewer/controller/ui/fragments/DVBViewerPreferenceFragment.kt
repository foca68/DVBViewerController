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
            ?.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, _ ->
            activity?.recreate()
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
        private const val KEY_RS_SETTINGS = "KEY_RS_SETTINGS"
    }
}
