package org.dvbviewer.controller.utils;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.util.Log;

import org.dvbviewer.controller.ui.player.PlayerActivity;

import com.google.gson.Gson;

import org.dvbviewer.controller.R;
import org.dvbviewer.controller.data.entities.DVBViewerPreferences;
import org.dvbviewer.controller.data.entities.Preset;

import java.util.Arrays;

import okhttp3.HttpUrl;

/**
 * Created by RayBa82 on 24.01.16.
 *
 * Util class for Streaming
 */
public class StreamUtils {

    private static final String	Tag				        = StreamUtils.class.getSimpleName();
    private static final Gson gson					    = new Gson();
    public static final String	DEFAULT_ENCODING_SPEED	= "ultrafast";
    public static final String EXTRA_TITLE = "title";
    public static final String M3U8_MIME_TYPE = "video/m3u8";

    public static Preset getDefaultPreset(SharedPreferences prefs) {
        Preset p = null;
        try {
            final String jsonPreset = prefs.getString(DVBViewerPreferences.KEY_STREAM_PRESET, null);
            p = gson.fromJson(jsonPreset, Preset.class);
        } catch (Exception e) {
            Log.d(Tag, "Error parsing default Preset", e);
        }
        if (p == null) {
            p = new Preset();
            p.setTitle("HLS Mid 1200 kbit");
            p.setMimeType(M3U8_MIME_TYPE);
        }
        return p;
    }

    public static int getEncodingSpeedIndex(final Context context, final SharedPreferences prefs) {
        final Preset preset = getDefaultPreset(prefs);
        int encodingSpeed = preset.getEncodingSpeed();
        String[] availableSpeeds = context.getResources().getStringArray(R.array.ffmpegPresets);
        if (encodingSpeed < 0 || encodingSpeed >= availableSpeeds.length){
            encodingSpeed = Arrays.asList(availableSpeeds).indexOf(DEFAULT_ENCODING_SPEED);
        }
        return encodingSpeed;
    }

    public static String getEncodingSpeedName(final Context context, final Preset preset) {
        return context.getResources().getStringArray(R.array.ffmpegPresets)[preset.getEncodingSpeed()];
    }

    /**
     * Entry point for "quick stream" (tap on channel thumbnail / media icon).
     * Builds the stream URL based on the saved direct/transcoded preference,
     * then routes to the internal ExoPlayer or an external player app depending
     * on KEY_USE_INTERNAL_PLAYER stored in dvbviewer_preferences.
     *
     * NOTE: StreamConfig uses its own call chain that also checks
     * KEY_USE_INTERNAL_PLAYER in startVideoIntent(). This method handles the
     * separate "quick tap" path that bypasses StreamConfig entirely.
     */
    public static Intent buildQuickUrl(Context context, long id, String title, FileType fileType) {
        final DVBViewerPreferences dvbPrefs  = new DVBViewerPreferences(context);
        final SharedPreferences    streamSp  = dvbPrefs.getStreamPrefs();

        // 1. Build the stream URL using the saved direct/transcoded preference
        final boolean direct = streamSp.getBoolean(DVBViewerPreferences.KEY_STREAM_DIRECT, true);
        final Intent  externalIntent;
        if (direct) {
            externalIntent = getDirectUrl(id, title, fileType);
        } else {
            externalIntent = getTranscodedUrl(context, id, title,
                    StreamUtils.getDefaultPreset(streamSp), fileType, 0);
        }

        // 2. Decide: internal ExoPlayer or external app?
        //    Reads from dvbviewer_preferences (main file), NOT stream prefs.
        final boolean useInternal = dvbPrefs.getBoolean(
                DVBViewerPreferences.KEY_USE_INTERNAL_PLAYER, true);
        final String  url      = externalIntent.getDataString();
        final String  mimeType = externalIntent.getType() != null ? externalIntent.getType() : "";

        Log.d(Tag, "buildQuickUrl: direct=" + direct
                + "  useInternal=" + useInternal
                + "  url=" + url);

        if (useInternal && url != null && !url.isEmpty()) {
            Log.d(Tag, "buildQuickUrl → PlayerActivity (internal)");
            return getInternalPlayerIntent(context, url, mimeType, title);
        }

        Log.d(Tag, "buildQuickUrl → external player");
        return externalIntent;
    }

    private static Intent addTitle(Intent intent, String title) {
        intent.putExtra(EXTRA_TITLE, title);
        return intent;
    }

    public static Intent getTranscodedUrl(Context context, final long id, String title, final FileType fileType) {
        final SharedPreferences prefs = new DVBViewerPreferences(context).getStreamPrefs();
        return getTranscodedUrl(context, id, title, StreamUtils.getDefaultPreset(prefs), fileType, 0);
    }

    public static Intent getTranscodedUrl(Context context, final long id, String title, final Preset preset, final FileType fileType, final int start) {
        final HttpUrl.Builder builder = URLUtil.buildProtectedRSUrl();
        if (M3U8_MIME_TYPE.equals(preset.getMimeType())) {
            builder.addPathSegment(ServerConsts.URL_M3U8);
        } else {
            builder.addPathSegments(ServerConsts.URL_FLASHSTREAM + preset.getExtension());
        }
        builder.addQueryParameter("preset", preset.getTitle());
        builder.addQueryParameter("ffPreset", StreamUtils.getEncodingSpeedName(context, preset));
        builder.addQueryParameter(fileType.transcodedParam, String.valueOf(id));
        builder.addQueryParameter("track", String.valueOf(preset.getAudioTrack()));
        if (start > 0) {
            builder.addQueryParameter("start", String.valueOf(start));
        }
        if (preset.getSubTitle() >= 0) {
            builder.addQueryParameter("subs", String.valueOf(preset.getSubTitle()));
        }
        final Intent videoIntent = new Intent(Intent.ACTION_VIEW);
        final String url = builder.build().toString();
        Log.d(Tag, "playing video: " + url);
        videoIntent.setDataAndType(Uri.parse(url), preset.getMimeType());
        addTitle(videoIntent, title);
        return videoIntent;
    }

    /**
     * Creates an Intent targeting the internal player activity.
     * Called instead of an ACTION_VIEW intent when KEY_USE_INTERNAL_PLAYER is true.
     */
    public static Intent getInternalPlayerIntent(Context context, String url, String mimeType, String title) {
        return getInternalPlayerIntent(context, url, mimeType, title, "");
    }

    /** Variant that also carries the current EPG programme title. */
    public static Intent getInternalPlayerIntent(Context context, String url, String mimeType,
                                                 String title, String epgTitle) {
        Intent intent = new Intent(context, PlayerActivity.class);
        intent.putExtra(PlayerActivity.EXTRA_URL, url);
        intent.putExtra(PlayerActivity.EXTRA_MIME_TYPE, mimeType != null ? mimeType : "");
        intent.putExtra(PlayerActivity.EXTRA_TITLE, title != null ? title : "");
        intent.putExtra(PlayerActivity.EXTRA_EPG_TITLE, epgTitle != null ? epgTitle : "");
        return intent;
    }

    public static Intent getDirectUrl(long id, String title, FileType fileType) {
        final HttpUrl.Builder builder = URLUtil.buildProtectedRSUrl();
        builder.addPathSegment("upnp");
        builder.addPathSegment(fileType.directPath);
        builder.addPathSegment(String.valueOf(id) + ".ts");
        final String videoUrl = builder.build().toString();
        Log.d(Tag, "playing video: " + videoUrl);
        Intent videoIntent = new Intent(Intent.ACTION_VIEW);
        videoIntent.setDataAndType(Uri.parse(videoUrl), "video/mpeg");
        addTitle(videoIntent, title);
        return videoIntent;
    }

}
