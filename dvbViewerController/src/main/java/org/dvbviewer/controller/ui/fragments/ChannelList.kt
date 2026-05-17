/*
 * Copyright © 2012 dvbviewer-controller Project
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

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.View.OnClickListener
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.PopupMenu
import androidx.cursoradapter.widget.CursorAdapter
import androidx.loader.app.LoaderManager.LoaderCallbacks
import androidx.loader.content.CursorLoader
import androidx.loader.content.Loader
import com.squareup.picasso.Picasso
import okhttp3.ResponseBody
import org.dvbviewer.controller.R
import android.util.Log
import org.dvbviewer.controller.data.DbHelper
import org.dvbviewer.controller.data.ProviderConsts
import org.dvbviewer.controller.data.ProviderConsts.ChannelTbl
import org.dvbviewer.controller.data.ProviderConsts.EpgTbl
import org.dvbviewer.controller.data.entities.Channel
import org.dvbviewer.controller.data.entities.DVBViewerPreferences
import org.dvbviewer.controller.data.entities.EpgEntry
import org.dvbviewer.controller.data.xmltv.XmltvChannelMapper
import org.dvbviewer.controller.data.entities.Timer
import org.dvbviewer.controller.data.remote.RemoteRepository
import org.dvbviewer.controller.data.timer.TimerRepository
import org.dvbviewer.controller.ui.base.BaseListFragment
import org.dvbviewer.controller.ui.phone.StreamConfigActivity
import org.dvbviewer.controller.ui.phone.TimerDetailsActivity
import org.dvbviewer.controller.ui.widget.CheckableLinearLayout
import org.dvbviewer.controller.utils.*
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.util.*

/**
 * The Class ChannelList.
 *
 * @author RayBa
 */
class ChannelList : BaseListFragment(), LoaderCallbacks<Cursor>, OnClickListener, PopupMenu.OnMenuItemClickListener {
    private var mGroupId: Long = -1
    private var mGroupIndex = -1
    private var mChannelIndex = -1
    private var showFavs: Boolean = false
    private var mCHannelSelectedListener: OnChannelSelectedListener? = null
    private lateinit var prefs: DVBViewerPreferences
    private lateinit var mAdapter: ChannelAdapter
    private lateinit var mChannelPagedOberserver: ChannelPagedObserver
    private lateinit var timerRepository: TimerRepository
    private lateinit var remoteRepository: RemoteRepository

    /*
     * (non-Javadoc)
     *
     * @see android.support.v4.app.Fragment#onCreate(android.os.Bundle)
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = DVBViewerPreferences(context!!)
        showFavs = prefs.prefs.getBoolean(DVBViewerPreferences.KEY_CHANNELS_USE_FAVS, false)
        mAdapter = ChannelAdapter(context)
        timerRepository = TimerRepository(getDmsInterface())
        remoteRepository = RemoteRepository(getDmsInterface())
        getExtras(savedInstanceState)
        registerObserver()
    }

    private fun getExtras(savedInstanceState: Bundle?) {
        if (savedInstanceState == null) {
            if (arguments!!.containsKey(ChannelPager.KEY_GROUP_ID)) {
                mGroupId = arguments!!.getLong(ChannelPager.KEY_GROUP_ID)
            }
            if (arguments!!.containsKey(ChannelPager.KEY_GROUP_INDEX)) {
                mGroupIndex = arguments!!.getInt(ChannelPager.KEY_GROUP_INDEX)
            }
            mChannelIndex = arguments!!.getInt(KEY_CHANNEL_INDEX, mChannelIndex)
        } else {
            mGroupId = savedInstanceState.getLong(ChannelPager.KEY_GROUP_ID)
            mGroupIndex = savedInstanceState.getInt(ChannelPager.KEY_GROUP_INDEX)
            mChannelIndex = savedInstanceState.getInt(KEY_CHANNEL_INDEX, mChannelIndex)
        }
    }

    private fun registerObserver() {
        val handler = Handler()
        val contentUri = BASE_CONTENT_URI.buildUpon().appendPath(mGroupId.toString()).appendQueryParameter("index", mChannelIndex.toString()).build()
        mChannelPagedOberserver = ChannelPagedObserver(handler)
        context?.contentResolver?.registerContentObserver(contentUri, true, mChannelPagedOberserver)
    }

    /*
     * (non-Javadoc)
     *
     * @see
     * com.actionbarsherlock.app.SherlockListFragment#onAttach(android.app.Activity
     * )
     */
    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is OnChannelSelectedListener) {
            mCHannelSelectedListener = context
        }
    }

    /*
     * (non-Javadoc)
     *
     * @see android.support.v4.app.Fragment#onActivityCreated(android.os.Bundle)
     */
    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        listAdapter = mAdapter
        listView!!.choiceMode = ListView.CHOICE_MODE_SINGLE
        setEmptyText(if (showFavs) resources.getString(R.string.no_favourites) else resources.getString(R.string.no_channels))
        val loader = loaderManager.initLoader(LOADER_CHANNELLIST, savedInstanceState, this)
        setListShown(!(!isResumed || loader.isStarted))
        setSelection(mChannelIndex)
    }

    /*
     * (non-Javadoc)
     *
     * @see
     * android.support.v4.app.LoaderManager.LoaderCallbacks#onCreateLoader(int,
     * android.os.Bundle)
     */
    override fun onCreateLoader(loaderId: Int, bundle: Bundle?): Loader<Cursor> {
        val loader: Loader<Cursor>
        val selection = StringBuilder(ChannelTbl.FLAGS + " & " + Channel.FLAG_ADDITIONAL_AUDIO + "== 0")
        if (mGroupId > 0) {
            selection.append(" and ")
            selection.append(ChannelTbl.GROUP_ID).append(" = ").append(mGroupId)
        }

        loader = CursorLoader(context!!, ChannelTbl.CONTENT_URI_NOW, null, selection.toString(), null, ChannelTbl.POSITION)
        return loader
    }

    /*
     * (non-Javadoc)
     *
     * @see
     * android.support.v4.app.LoaderManager.LoaderCallbacks#onLoadFinished(android
     * .support.v4.content.Loader, java.lang.Object)
     */
    override fun onLoadFinished(loader: Loader<Cursor>, cursor: Cursor) {
        mAdapter!!.changeCursor(cursor)
        setSelection(mChannelIndex)
        listView!!.setSelectionFromTop(mChannelIndex, resources.getDimension(R.dimen.list_preferred_item_height_small).toInt() * 3)
        setListShown(true)
        if (activity != null) {
            activity!!.invalidateOptionsMenu()
        }
        // After DVBViewer now-EPG is loaded, fill in gaps from XMLTV cache.
        if (prefs.getBoolean(DVBViewerPreferences.KEY_XMLTV_ENABLED, false)) {
            val needsXmltv = collectChannelsWithoutEpg(cursor)
            if (needsXmltv.isNotEmpty()) loadXmltvNowCache(needsXmltv)
        }
    }

    /**
     * Collects DVBViewer channel names whose EPG title is empty in the "now" cursor.
     * Called on the main thread while cursor is fresh — safe to iterate here.
     */
    private fun collectChannelsWithoutEpg(cursor: Cursor): List<String> {
        val names = mutableListOf<String>()
        val savedPos = cursor.position
        if (cursor.moveToFirst()) {
            val nameIdx  = cursor.getColumnIndex(ChannelTbl.NAME)
            val titleIdx = cursor.getColumnIndex(EpgTbl.TITLE)
            do {
                val title = if (titleIdx >= 0) cursor.getString(titleIdx) else null
                if (TextUtils.isEmpty(title)) names.add(cursor.getString(nameIdx))
            } while (cursor.moveToNext())
        }
        cursor.moveToPosition(savedPos)
        return names
    }

    /**
     * Builds the XMLTV "now-playing" cache for [channelNames] in a background
     * thread and posts the result back to the adapter on the main thread.
     *
     * Strategy:
     *  1. Query xmltv_epg for programmes on air right now (single bulk query).
     *  2. For each DVBViewer channel name, use XmltvChannelMapper to find the
     *     best matching XMLTV channel (manual → exact → fuzzy ≥ 80%).
     *  3. Hand the Map<dvbName, EpgEntry> to the adapter.
     */
    private fun loadXmltvNowCache(channelNames: List<String>) {
        val ctx = context?.applicationContext ?: return
        Thread {
            val dbHelper    = DbHelper(ctx)
            val xmltvNow    = dbHelper.getXmltvNowPlaying(System.currentTimeMillis())
            if (xmltvNow.isEmpty()) return@Thread

            val mapper      = XmltvChannelMapper(ctx)
            val xmltvNames  = xmltvNow.keys.toList()
            val cache       = mutableMapOf<String, EpgEntry>()

            for (dvbName in channelNames) {
                val resolved = mapper.resolve(dvbName, xmltvNames) ?: continue
                xmltvNow[resolved.xmltvName]?.let { cache[dvbName] = it }
            }

            // ── Diagnostic: dump cache to verify title ≠ channel name ──
        Log.d(TAG_XMLTV, "XMLTV now-cache built: ${cache.size} entries")
        cache.entries.take(10).forEach { (dvb, entry) ->
            Log.d(TAG_XMLTV, "  dvb=\"$dvb\"  entry.channel=\"${entry.channel}\"" +
                    "  entry.title=\"${entry.title}\"" +
                    "  title==channel:${entry.title == entry.channel}")
        }

        if (cache.isNotEmpty()) {
                activity?.runOnUiThread {
                    mAdapter.setXmltvNowCache(cache)
                    mAdapter.notifyDataSetChanged()
                }
            }
        }.start()
    }

    /*
     * (non-Javadoc)
     *
     * @see
     * android.support.v4.app.LoaderManager.LoaderCallbacks#onLoaderReset(android
     * .support.v4.content.Loader)
     */
    override fun onLoaderReset(arg0: Loader<Cursor>) {

    }


    override fun onMenuItemClick(item: MenuItem): Boolean {
        val c = mAdapter!!.cursor
        c.moveToPosition(mChannelIndex)
        when (item.itemId) {
            R.id.menuStreamDirect -> {
                streamDirect(c)
                return true
            }
            R.id.menuStreamTranscoded -> {
                streamTranscoded(c)
                return true
            }
            R.id.menuStreamConfig -> {
                showStreamConfig(c)
                return true
            }
            R.id.menuTimer -> {
                showTimerDialog(c)
                return true
            }
            R.id.menuSwitch -> {
                switchChannel(c)
                return true
            }
            R.id.menuRecord -> {
                recordChannel(c)
                return true
            }

            else -> {
            }
        }
        return false
    }

    private fun streamDirect(c: Cursor) {
        try {
            val chan = cursorToChannel(c)
            val videoIntent = StreamUtils.getDirectUrl(chan.channelID, chan.name, FileType.CHANNEL)
            activity!!.startActivity(videoIntent)
            prefs.streamPrefs.edit().putBoolean(DVBViewerPreferences.KEY_STREAM_DIRECT, true).apply()
            val bundle = Bundle()
            bundle.putString(PARAM_START, START_MENU)
            bundle.putString(PARAM_TYPE, TYPE_DIRECT)
            bundle.putString(PARAM_NAME, chan.name)
            logEvent(EVENT_STREAM_LIVE_TV, bundle)
        } catch (e: ActivityNotFoundException) {
            val builder = AlertDialog.Builder(context!!)
            builder.setMessage(resources.getString(R.string.noFlashPlayerFound)).setPositiveButton(resources.getString(R.string.yes), null).setNegativeButton(resources.getString(R.string.no), null).show()
            e.printStackTrace()
        }

    }

    private fun streamTranscoded(c: Cursor) {
        try {
            val chan = cursorToChannel(c)
            val videoIntent = StreamUtils.getTranscodedUrl(context, chan.channelID, chan.name, FileType.CHANNEL)
            activity!!.startActivity(videoIntent)
            prefs.streamPrefs.edit().putBoolean(DVBViewerPreferences.KEY_STREAM_DIRECT, false).apply()
            val bundle = Bundle()
            bundle.putString(PARAM_START, START_MENU)
            bundle.putString(PARAM_TYPE, TYPE_TRANSCODED)
            bundle.putString(PARAM_NAME, chan.name)
            logEvent(EVENT_STREAM_LIVE_TV, bundle)
        } catch (e: ActivityNotFoundException) {
            val builder = AlertDialog.Builder(context!!)
            builder.setMessage(resources.getString(R.string.noFlashPlayerFound)).setPositiveButton(resources.getString(R.string.yes), null).setNegativeButton(resources.getString(R.string.no), null).show()
            e.printStackTrace()
        }

    }

    private fun switchChannel(c: Cursor) {
        val chan = cursorToChannel(c)
        val target = prefs!!.getString(DVBViewerPreferences.KEY_SELECTED_CLIENT)
        remoteRepository.switchChannel(target, chan.channelID.toString()).enqueue(object : Callback<ResponseBody> {
            override fun onResponse(call: Call<ResponseBody>, response: Response<ResponseBody>) {
                sendMessage(R.string.channel_switched)
            }

            override fun onFailure(call: Call<ResponseBody>, t: Throwable) {
                sendMessage(R.string.error_common)
            }
        })
    }

    private fun recordChannel(c: Cursor) {
        val timer = cursorToTimer(c)
        val call = timerRepository.saveTimer(timer)
        call.enqueue(object: Callback<ResponseBody> {
            override fun onResponse(call: Call<ResponseBody>, response: Response<ResponseBody>) {
                sendMessage(R.string.timer_saved)
                logEvent(EVENT_TIMER_CREATED)
            }
            override fun onFailure(call: Call<ResponseBody>, t: Throwable) {
                sendMessage(R.string.error_common)
            }
        })
    }

    private fun showTimerDialog(c: Cursor) {
        val timer = cursorToTimer(c)
        if (UIUtils.isTablet(context!!)) {
            val timerdetails = TimerDetails.newInstance()
            val args = TimerDetails.buildBundle(timer)
            timerdetails.arguments = args
            timerdetails.show(activity!!.supportFragmentManager, TimerDetails::class.java.name)
        } else {
            val timerIntent = Intent(context, TimerDetailsActivity::class.java)
            val extras = TimerDetails.buildBundle(timer)
            timerIntent.putExtras(extras)
            startActivity(timerIntent)
        }
    }

    private fun showStreamConfig(cursor: Cursor) {
        val chan = cursorToChannel(cursor)
        if (UIUtils.isTablet(context!!)) {
            val arguments = getIntentExtras(chan)
            val cfg = StreamConfig.newInstance()
            cfg.arguments = arguments
            cfg.show(activity!!.supportFragmentManager, StreamConfig::class.java.name)
        } else {
            val arguments = getIntentExtras(chan)
            val streamConfig = Intent(context, StreamConfigActivity::class.java)
            streamConfig.putExtras(arguments)
            startActivity(streamConfig)
        }
    }

    private fun getIntentExtras(chan: Channel): Bundle {
        val arguments = Bundle()
        arguments.putLong(StreamConfig.EXTRA_FILE_ID, chan.channelID)
        arguments.putParcelable(StreamConfig.EXTRA_FILE_TYPE, FileType.CHANNEL)
        arguments.putInt(StreamConfig.EXTRA_DIALOG_TITLE_RES, R.string.streamConfig)
        arguments.putString(StreamConfig.EXTRA_TITLE, chan.name)
        return arguments
    }

    /**
     * The Class ViewHolder.
     *
     * @author RayBa
     */
    private class ViewHolder {
        internal var v: CheckableLinearLayout? = null
        internal var icon: ImageView? = null
        internal var iconContainer: View? = null
        internal var position: TextView? = null
        internal var channelName: TextView? = null
        internal var epgTime: TextView? = null
        internal var progress: ProgressBar? = null
        internal var epgTitle: TextView? = null
        internal var contextMenu: ImageView? = null
    }

    /**
     * The Class ChannelAdapter.
     *
     * @author RayBa
     */
    inner class ChannelAdapter(context: Context?) :
            CursorAdapter(context, null, FLAG_REGISTER_CONTENT_OBSERVER) {

        /** XMLTV now-playing cache: DVBViewer channel name → current EpgEntry. */
        private var xmltvNowCache: Map<String, EpgEntry> = emptyMap()

        fun setXmltvNowCache(cache: Map<String, EpgEntry>) {
            xmltvNowCache = cache
        }

        override fun bindView(view: View, context: Context, c: Cursor) {
            val holder      = view.tag as ViewHolder
            val channelName = c.getString(c.getColumnIndex(ChannelTbl.NAME))
            val logoUrl     = c.getString(c.getColumnIndex(ChannelTbl.LOGO_URL))
            val epgTitle    = c.getString(c.getColumnIndex(EpgTbl.TITLE))
            val epgStart    = c.getLong(c.getColumnIndex(EpgTbl.START))
            val epgEnd      = c.getLong(c.getColumnIndex(EpgTbl.END))
            val position    = c.getInt(c.getColumnIndex(ChannelTbl.POSITION))

            holder.contextMenu!!.tag   = AdapterView.INVALID_POSITION
            holder.iconContainer!!.tag = AdapterView.INVALID_POSITION
            holder.icon!!.setImageBitmap(null)
            holder.channelName!!.text  = channelName
            holder.position!!.text     = position.toString()

            when {
                // Primary: DVBViewer "now" table has EPG data
                !TextUtils.isEmpty(epgTitle) ->
                    bindEpg(holder, context, epgTitle, epgStart, epgEnd)

                // Fallback: XMLTV cache. The cache already excludes entries that have
                // nothing useful (blank title AND blank subTitle AND blank episodeNum).
                xmltvNowCache.containsKey(channelName) -> {
                    val x = xmltvNowCache[channelName]!!
                    val displayTitle = xmltvDisplayTitle(x)
                    if (displayTitle != null) {
                        Log.d(TAG_XMLTV, "XMLTV epg: \"$channelName\" → \"$displayTitle\"")
                        bindEpg(holder, context, displayTitle, x.start.time, x.end.time)
                    } else {
                        hideEpg(holder)
                    }
                }

                // No EPG from either source
                else -> hideEpg(holder)
            }

            holder.contextMenu!!.tag   = c.position
            holder.iconContainer!!.tag = c.position
            holder.v!!.isChecked       = listView!!.isItemChecked(c.position)

            if (!TextUtils.isEmpty(logoUrl)) {
                Picasso.get()
                        .load(ServerConsts.REC_SERVICE_URL + "/" + logoUrl)
                        .fit()
                        .centerInside()
                        .into(holder.icon)
            }
        }

        private fun bindEpg(holder: ViewHolder, context: Context,
                            title: String, startMs: Long, endMs: Long) {
            holder.epgTitle!!.visibility = View.VISIBLE
            holder.epgTime!!.visibility  = View.VISIBLE
            holder.progress!!.visibility = View.VISIBLE
            val startFmt = DateUtils.formatDateTime(context, startMs, DateUtils.FORMAT_SHOW_TIME)
            val endFmt   = DateUtils.formatDateTime(context, endMs,   DateUtils.FORMAT_SHOW_TIME)
            val elapsed  = (Date().time - startMs).toFloat()
            val total    = (endMs - startMs).toFloat()
            holder.progress!!.progress = if (total > 0) ((elapsed / total) * 100).toInt().coerceIn(0, 100) else 0
            holder.epgTime!!.text  = "$startFmt - $endFmt"
            holder.epgTitle!!.text = title
        }

        private fun hideEpg(holder: ViewHolder) {
            holder.epgTime!!.visibility  = View.GONE
            holder.epgTitle!!.visibility = View.GONE
            holder.progress!!.visibility = View.GONE
        }

        /*
         * (non-Javadoc)
         *
         * @see
         * android.support.v4.widget.CursorAdapter#newView(android.content.Context
         * , android.database.Cursor, android.view.ViewGroup)
         */
        override fun newView(context: Context, cursor: Cursor, parent: ViewGroup): View {
            val view = LayoutInflater.from(context).inflate(R.layout.list_row_channel, parent, false)
            val holder = ViewHolder()
            holder.v = view as CheckableLinearLayout
            holder.iconContainer = view.findViewById(R.id.iconContainer)
            holder.icon = view.findViewById(R.id.icon)
            holder.position = view.findViewById(R.id.position)
            holder.channelName = view.findViewById(R.id.title)
            holder.epgTime = view.findViewById(R.id.epgTime)
            holder.progress = view.findViewById(R.id.progress)
            holder.epgTitle = view.findViewById(R.id.epgTitle)
            holder.contextMenu = view.findViewById(R.id.contextMenu)
            holder.contextMenu!!.setOnClickListener(this@ChannelList)
            holder.iconContainer!!.setOnClickListener(this@ChannelList)
            view.setTag(holder)
            return view
        }
    }

    /*
     * (non-Javadoc)
     *
     * @see
     * android.support.v4.app.ListFragment#onListItemClick(android.widget.ListView
     * , android.view.View, int, long)
     */
    override fun onListItemClick(l: ListView, v: View, position: Int, id: Long) {
        mChannelIndex = position
        if (mCHannelSelectedListener != null) {
            mCHannelSelectedListener!!.channelSelected(mGroupId, mGroupIndex, position)
            listView!!.setItemChecked(position, true)
        }
    }

    /* (non-Javadoc)
     * @see android.support.v4.app.Fragment#onResume()
     */
    override fun onResume() {
        super.onResume()
        setTitle()
    }

    private fun setTitle() {
        activity!!.setTitle(if (showFavs) R.string.favourites else R.string.channelList)
    }

    /**
     * Clears the selection of a ListView.
     */
    private fun clearSelection() {
        for (i in 0 until listAdapter!!.count) {
            listView!!.setItemChecked(i, false)
        }
        //		mAdapter.notifyDataSetChanged();
    }

    /*
     * (non-Javadoc)
     *
     * @see
     * android.support.v4.app.Fragment#onSaveInstanceState(android.os.Bundle)
     */
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putLong(ChannelPager.KEY_GROUP_ID, mGroupId)
        outState.putInt(ChannelPager.KEY_GROUP_INDEX, mGroupIndex)
        outState.putInt(KEY_CHANNEL_INDEX, mChannelIndex)
    }

    /*
     * (non-Javadoc)
     *
     * @see android.view.View.OnClickListener#onClick(android.view.View)
     */
    override fun onClick(v: View) {
        mChannelIndex = v.tag as Int
        when (v.id) {
            R.id.contextMenu -> {
                val popup = PopupMenu(context!!, v)
                popup.inflate(R.menu.context_menu_stream)
                popup.inflate(R.menu.context_menu_channellist)
                popup.setOnMenuItemClickListener(this)
                popup.show()
            }
            R.id.iconContainer -> try {
                val c = mAdapter!!.cursor
                c.moveToPosition(mChannelIndex)
                val chan = cursorToChannel(c)

                val videoIntent = StreamUtils.buildQuickUrl(context, chan.channelID, chan.name, FileType.CHANNEL)
                activity!!.startActivity(videoIntent)
                val direct = prefs.getBoolean(DVBViewerPreferences.KEY_STREAM_DIRECT, true)
                val bundle = Bundle()
                bundle.putString(PARAM_START, START_MENU)
                bundle.putString(PARAM_TYPE, if (direct) TYPE_DIRECT else TYPE_TRANSCODED)
                bundle.putString(PARAM_NAME, chan.name)
                logEvent(EVENT_STREAM_LIVE_TV, bundle)
            } catch (e: ActivityNotFoundException) {
                val builder = AlertDialog.Builder(context!!)
                builder.setMessage(resources.getString(R.string.noFlashPlayerFound)).setPositiveButton(resources.getString(R.string.yes), null).setNegativeButton(resources.getString(R.string.no), null).show()
                e.printStackTrace()
            }

            else -> {
            }
        }
    }

    /**
     * Cursor to timer.
     *
     * @param c the c
     * @return the timer©
     */
    private fun cursorToTimer(c: Cursor): Timer {
        val name = c.getString(c.getColumnIndex(ChannelTbl.NAME))
        val channelID = c.getLong(c.getColumnIndex(ChannelTbl.CHANNEL_ID))
        val epgTitle = if (!c.isNull(c.getColumnIndex(EpgTbl.TITLE))) c.getString(c.getColumnIndex(EpgTbl.TITLE)) else name
        val epgStart = c.getLong(c.getColumnIndex(EpgTbl.START))
        val epgEnd = c.getLong(c.getColumnIndex(EpgTbl.END))
        val prefs = DVBViewerPreferences(context!!)
        val epgBefore = prefs.prefs.getInt(DVBViewerPreferences.KEY_TIMER_TIME_BEFORE, DVBViewerPreferences.DEFAULT_TIMER_TIME_BEFORE)
        val epgAfter = prefs.prefs.getInt(DVBViewerPreferences.KEY_TIMER_TIME_AFTER, DVBViewerPreferences.DEFAULT_TIMER_TIME_AFTER)
        val start = if (epgStart > 0) Date(epgStart) else Date()
        val end = if (epgEnd > 0) Date(epgEnd) else Date(start.time + 1000 * 60 * 120)
        val eventId = c.getString(c.getColumnIndex(EpgTbl.EVENT_ID))
        val pdc = c.getString(c.getColumnIndex(EpgTbl.PDC))
        val timer = Timer()
        timer.title = epgTitle
        timer.channelId = channelID
        timer.channelName = name
        timer.start = start
        timer.end = end
        timer.pre = epgBefore
        timer.post = epgAfter
        timer.eventId = eventId
        timer.pdc = pdc
        timer.timerAction = prefs.prefs.getInt(DVBViewerPreferences.KEY_TIMER_DEF_AFTER_RECORD, 0)
        return timer
    }

    /**
     * The listener interface for receiving onChannelSelected events.
     * The class that is interested in processing a onChannelSelected
     * event implements this interface, and the object created
     * with that class is registered with a component using the
     * component's `addOnChannelSelectedListener` method. When
     * the onChannelSelected event occurs, that object's appropriate
     * method is invoked.
     *
     * @author RayBa
    `` */
    interface OnChannelSelectedListener {

        /**
         * Notifys about channel selections in the channel List
         *
         * @param groupId the groupId
         * @param groupIndex the groupIndex
         * @param channelIndex the channelIndex
         */
        fun channelSelected(groupId: Long, groupIndex: Int, channelIndex: Int)

    }

    /**
     * Sets the selected position.
     *
     * @param selectedPosition the new selected position
     */
    private fun setSelectedPosition(selectedPosition: Int) {
        this.mChannelIndex = selectedPosition
    }

    /* (non-Javadoc)
     * @see org.dvbviewer.controller.ui.base.BaseListFragment#setSelection(int)
     */
    override fun setSelection(position: Int) {
        setSelectedPosition(position)
        clearSelection()
        listView!!.setItemChecked(position, true)
        super.setSelection(position)
    }

    internal inner class ChannelPagedObserver(handler: Handler) : ContentObserver(handler) {

        override fun onChange(selfChange: Boolean, uri: Uri?) {
            if (uri != null) {
                val index = uri.getQueryParameter("index")?.let { Integer.parseInt(it) }
                index?.let { setSelection(it) }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        context!!.contentResolver.unregisterContentObserver(mChannelPagedOberserver!!)
    }

    companion object {

        val BASE_CONTENT_URI = Uri.parse(ProviderConsts.BASE_CONTENT_URI.toString() + "/channelselector")
        val KEY_CHANNEL_INDEX = ChannelList::class.java.name + "KEY_CHANNEL_INDEX"
        private val LOADER_CHANNELLIST = 101
        private const val TAG_XMLTV = "ChannelList.XMLTV"

        /**
         * Computes the string to display in the "now playing" row for a XMLTV entry.
         *
         * Priority:
         *  1. Real title (not a channel-name placeholder)        → title
         *  2. Placeholder title, episodeNum + subTitle both set  → "S07E17 - I Found Your Moustache"
         *  3. Placeholder title, only episodeNum                 → "S07E17"
         *  4. Placeholder title, only subTitle                   → "I Found Your Moustache"
         *  5. Nothing useful                                      → null (hide EPG row)
         */
        fun xmltvDisplayTitle(entry: EpgEntry): String? {
            val titleIsPlaceholder = entry.title.isBlank() ||
                    entry.title.equals(entry.channel, ignoreCase = true)
            if (!titleIsPlaceholder) return entry.title

            val ep  = entry.episodeNum.trim()
            val sub = entry.subTitle.trim()
            return when {
                ep.isNotEmpty() && sub.isNotEmpty() -> "$ep - $sub"
                ep.isNotEmpty()                     -> ep
                sub.isNotEmpty()                    -> sub
                else                                -> null
            }
        }

        /**
         * Reads the current cursorposition to a Channel.
         *
         * @param c the c
         * @return the Channel
         */
        fun cursorToChannel(c: Cursor): Channel {
            val channel = Channel()
            channel.id = c.getLong(c.getColumnIndex(ChannelTbl._ID))
            channel.channelID = c.getLong(c.getColumnIndex(ChannelTbl.CHANNEL_ID))
            channel.epgID = c.getLong(c.getColumnIndex(ChannelTbl.EPG_ID))
            channel.logoUrl = c.getString(c.getColumnIndex(ChannelTbl.LOGO_URL))
            channel.name = c.getString(c.getColumnIndex(ChannelTbl.NAME))
            channel.position = c.getInt(c.getColumnIndex(ChannelTbl.POSITION))
            channel.favPosition = c.getInt(c.getColumnIndex(ChannelTbl.FAV_POSITION))
            return channel
        }
    }
}
