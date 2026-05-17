/*
 * Copyright © 2013 dvbviewer-controller Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.dvbviewer.controller.ui.fragments

import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.DialogInterface
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.preference.PreferenceManager
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.View.OnClickListener
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.AdapterView.OnItemSelectedListener
import android.widget.ArrayAdapter
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import com.google.gson.Gson
import org.apache.commons.lang3.StringUtils
import org.apache.commons.lang3.math.NumberUtils
import org.dvbviewer.controller.R
import org.dvbviewer.controller.data.api.APIClient
import org.dvbviewer.controller.data.api.ApiResponse
import org.dvbviewer.controller.data.api.DMSInterface
import org.dvbviewer.controller.data.entities.DVBViewerPreferences
import org.dvbviewer.controller.data.entities.FFMpegPresetList
import org.dvbviewer.controller.data.entities.Preset
import org.dvbviewer.controller.data.stream.StreamRepository
import org.dvbviewer.controller.data.stream.StreamViewModel
import org.dvbviewer.controller.data.stream.StreamViewModelFactory
import org.dvbviewer.controller.databinding.FragmentStreamConfigBinding
import org.dvbviewer.controller.ui.base.BaseDialogFragment
import org.dvbviewer.controller.utils.*
import java.util.*

/**
 * DialogFragment to show the stream settings.
 */
class StreamConfig : BaseDialogFragment(), OnClickListener, DialogInterface.OnClickListener, OnItemSelectedListener {

    private var _binding: FragmentStreamConfigBinding? = null
    private val binding get() = _binding!!

    private var preTime: String? = null
    private var title = 0
    private var seekable = false
    private var mTitle: String? = StringUtils.EMPTY
    private var mStreamType: StreamType? = null
    private var mFileType: FileType? = null
    private var mFileId: Long = -1
    private lateinit var prefs: SharedPreferences
    private lateinit var dmsInterface: DMSInterface

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val dvbvPrefs = DVBViewerPreferences(context!!)
        prefs = dvbvPrefs.streamPrefs
        if (savedInstanceState != null) {
            title = savedInstanceState.getInt("titleRes")
        }
        mFileId = arguments!!.getLong(EXTRA_FILE_ID)
        mFileType = arguments!!.getParcelable(EXTRA_FILE_TYPE)
        mStreamType = StreamType.DIRECT
        mTitle = arguments!!.getString(EXTRA_TITLE)
        seekable = mFileType != FileType.CHANNEL

        if (seekable) {
            preTime = dvbvPrefs.prefs.getInt(DVBViewerPreferences.KEY_TIMER_TIME_BEFORE, DVBViewerPreferences.DEFAULT_TIMER_TIME_BEFORE).toString()
        }
        dmsInterface = APIClient.client.create(DMSInterface::class.java)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dia = super.onCreateDialog(savedInstanceState)
        dia.setTitle(R.string.streamConfig)
        return dia
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentStreamConfigBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.qualitySpinner.requestFocus()
        binding.startHours.clearFocus()
        binding.collapsable.visibility = View.GONE
        binding.qualitySpinner.onItemSelectedListener = this
        val encodingSpeed = StreamUtils.getEncodingSpeedIndex(context!!, prefs)
        binding.encodingSpeedSpinner.setSelection(encodingSpeed)
        binding.encodingSpeedSpinner.onItemSelectedListener = this
        binding.audioSpinner.onItemSelectedListener = this
        val audioTracks = LinkedList<String>()
        audioTracks.add(resources.getString(R.string.def))
        audioTracks.add(resources.getString(R.string.common_all))
        audioTracks.addAll(Arrays.asList(*resources.getStringArray(R.array.tracks)))
        val audioAdapter = ArrayAdapter(context!!, android.R.layout.simple_spinner_item, audioTracks.toTypedArray())
        audioAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.audioSpinner.adapter = audioAdapter
        binding.subTitleSpinner.onItemSelectedListener = this
        val subTitleTracks = LinkedList<String>()
        subTitleTracks.add(resources.getString(R.string.none))
        subTitleTracks.add(resources.getString(R.string.common_all))
        subTitleTracks.addAll(Arrays.asList(*resources.getStringArray(R.array.tracks)))
        val subAdapter = ArrayAdapter(context!!, android.R.layout.simple_spinner_item, subTitleTracks.toTypedArray())
        subAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.subTitleSpinner.adapter = subAdapter
        binding.startDirectButton.setOnClickListener(this)
        binding.startTranscodedButton.setOnClickListener(this)
        if (!seekable) {
            binding.streamPositionContainer.visibility = View.GONE
        }
        if (!TextUtils.isEmpty(preTime)) {
            binding.startMinutes.setText(preTime)
        }
        binding.qualitySpinner.requestFocus()
    }

    override fun onActivityCreated(arg0: Bundle?) {
        super.onActivityCreated(arg0)
        val streamRepository = StreamRepository(dmsInterface)
        val mediaFac = StreamViewModelFactory(activity!!.application, streamRepository)
        val streamViewModel = ViewModelProviders.of(this, mediaFac)
                .get(StreamViewModel::class.java)
        val configObserver = Observer<ApiResponse<FFMpegPresetList>> { response ->
            val presets = response!!.data
            if (presets != null && presets.presets.isNotEmpty()) {
                val dataAdapter = ArrayAdapter(context!!, android.R.layout.simple_spinner_item, presets.presets)
                dataAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                val pos = presets.presets.indexOf(StreamUtils.getDefaultPreset(prefs))
                binding.startHours.clearFocus()
                binding.qualitySpinner.adapter = dataAdapter
                binding.qualitySpinner.setSelection(pos)
                val vg = binding.collapsable.parent as ViewGroup
                val widthMeasureSpec = View.MeasureSpec.makeMeasureSpec(vg.width, View.MeasureSpec.AT_MOST)
                val heightMeasureSpec = View.MeasureSpec.makeMeasureSpec(1073741823, View.MeasureSpec.AT_MOST)
                binding.collapsable.measure(widthMeasureSpec, heightMeasureSpec)
                binding.collapsable.visibility = View.VISIBLE
            }
        }
        streamViewModel.getFFMpegPresets().observe(this@StreamConfig, configObserver)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onSaveInstanceState(bundle: Bundle) {
        super.onSaveInstanceState(bundle)
        bundle.putInt("titleRes", title)
    }

    override fun onClick(v: View?) {
        when (v!!.id) {
            R.id.startTranscodedButton -> {
                prefs.edit().putBoolean(DVBViewerPreferences.KEY_STREAM_DIRECT, false).apply()
                mStreamType = StreamType.TRANSCODED
                startStreaming(false, mFileType)
                logStreaming(TYPE_TRANSCODED)
            }
            R.id.startDirectButton -> {
                prefs.edit().putBoolean(DVBViewerPreferences.KEY_STREAM_DIRECT, true).apply()
                mStreamType = StreamType.DIRECT
                startStreaming(true, mFileType)
                logStreaming(TYPE_DIRECT)
            }

            else -> {
            }
        }
    }

    private fun logStreaming(type: String) {
        val bundle = Bundle()
        bundle.putString(PARAM_START, START_DIALOG)
        bundle.putString(PARAM_TYPE, type)
        bundle.putString(PARAM_NAME, mTitle)
        val event = when (mFileType) {
            FileType.CHANNEL -> EVENT_STREAM_LIVE_TV
            FileType.RECORDING -> EVENT_STREAM_RECORDING
            else -> EVENT_STREAM_MEDIA
        }
        logEvent(event, bundle)
    }

    private fun startStreaming(direct: Boolean, fileType: FileType?) {
        try {
            startVideoIntent(fileType)
        } catch (e: ActivityNotFoundException) {
            val builder = AlertDialog.Builder(context!!)
            builder.setMessage(resources.getString(R.string.noFlashPlayerFound)).setPositiveButton(resources.getString(R.string.yes), this).setNegativeButton(resources.getString(R.string.no), this).show()
        }
    }

    private fun startVideoIntent(fileType: FileType?) {
        val videoIntent: Intent = getVideoIntent(fileType) ?: return
        startActivity(videoIntent)
        if (getDialog() != null) {
            getDialog()?.dismiss()
        } else {
            activity!!.finish()
        }
    }

    private fun getVideoIntent(fileType: FileType?): Intent? {
        if (mStreamType == StreamType.DIRECT) {
            return StreamUtils.getDirectUrl(mFileId, mTitle, fileType!!)
        } else if (binding.qualitySpinner.selectedItemPosition >= 0) {
            val preset = binding.qualitySpinner.selectedItem as Preset
            val encodingSpeed = binding.encodingSpeedSpinner.selectedItemPosition
            val hours = if (TextUtils.isEmpty(binding.startHours.text)) 0 else NumberUtils.toInt(binding.startHours.text.toString())
            val minutes = if (TextUtils.isEmpty(binding.startMinutes.text)) 0 else NumberUtils.toInt(binding.startMinutes.text.toString())
            val seconds = if (TextUtils.isEmpty(binding.startSeconds.text)) 0 else NumberUtils.toInt(binding.startSeconds.text.toString())
            val start = 3600 * hours + 60 * minutes + seconds
            preset.encodingSpeed = encodingSpeed
            return StreamUtils.getTranscodedUrl(context, mFileId, mTitle, preset, fileType, start)
        }
        return null
    }

    override fun onClick(dialog: DialogInterface, which: Int) {
        when (which) {
            DialogInterface.BUTTON_POSITIVE -> {
                val prefs = PreferenceManager.getDefaultSharedPreferences(context)
                val editor = prefs.edit()
                editor.putBoolean("stream_external", false)
                editor.apply()
                onClick(binding.startTranscodedButton)
                if (getDialog() != null) {
                    getDialog()?.dismiss()
                } else {
                    activity?.finish()
                }
            }

            else -> {
            }
        }
    }

    override fun onItemSelected(parent: AdapterView<*>, view: View, position: Int, id: Long) {
        val editor = prefs.edit()
        val p = binding.qualitySpinner.selectedItem as Preset
        when (parent.id) {
            R.id.encodingSpeedSpinner -> p.encodingSpeed = position
            R.id.audioSpinner -> {
                val audioTrack: Int
                when (position) {
                    0 -> audioTrack = 0
                    1 -> audioTrack = -1
                    else -> audioTrack = position - 2
                }
                p.audioTrack = audioTrack
            }
            R.id.subTitleSpinner -> {
                val subtTitleTrack: Int
                when (position) {
                    0 -> subtTitleTrack = -1
                    1 -> subtTitleTrack = 0
                    else -> subtTitleTrack = position - 2
                }
                p.subTitle = subtTitleTrack
            }
            else -> {
            }
        }
        editor.putString(DVBViewerPreferences.KEY_STREAM_PRESET, gson.toJson(p))
        editor.apply()
    }

    override fun onNothingSelected(parent: AdapterView<*>) {
        return
    }

    companion object {

        private val gson = Gson()
        val EXTRA_FILE_ID = "_fileID"
        val EXTRA_FILE_TYPE = "_fileType"
        val EXTRA_DIALOG_TITLE_RES = "_dialog_title_res"
        val EXTRA_TITLE = "title"

        fun newInstance(): StreamConfig {
            return StreamConfig()
        }

    }

}
