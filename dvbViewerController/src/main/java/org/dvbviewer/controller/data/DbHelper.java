package org.dvbviewer.controller.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import org.dvbviewer.controller.data.ProviderConsts.ChannelTbl;
import org.dvbviewer.controller.data.ProviderConsts.EpgTbl;
import org.dvbviewer.controller.data.ProviderConsts.GroupTbl;
import org.dvbviewer.controller.data.ProviderConsts.MediaTbl;
import org.dvbviewer.controller.data.ProviderConsts.NowTbl;
import org.dvbviewer.controller.data.ProviderConsts.RootTbl;
import org.dvbviewer.controller.data.ProviderConsts.XmltvTbl;
import org.dvbviewer.controller.data.entities.Channel;
import org.dvbviewer.controller.data.entities.ChannelGroup;
import org.dvbviewer.controller.data.entities.ChannelRoot;
import org.dvbviewer.controller.data.entities.EpgEntry;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class DbHelper extends SQLiteOpenHelper {

	private static final String DATABASE_NAME    = "dvbviewercontroller.db";
	private static final int    DATABASE_VERSION = 5;

	private Context mContext;

	public DbHelper(Context context) {
		super(context, DATABASE_NAME, null, DATABASE_VERSION);
		mContext = context;
	}

	@Override
	public void onCreate(SQLiteDatabase db) {
		createChannelTable(db);
		createXmltvTable(db);
	}

	public void createChannelTable(SQLiteDatabase db) {
		db.execSQL("CREATE TABLE " + ChannelTbl.TABLE_NAME + "(" + ChannelTbl._ID + " INTEGER PRIMARY KEY AUTOINCREMENT," + ChannelTbl.CHANNEL_ID + " INTEGER," + ChannelTbl.GROUP_ID + " INTEGER," + ChannelTbl.FAV_GROUP_ID + " INTEGER," + ChannelTbl.NAME + " TEXT," + ChannelTbl.POSITION + " INTEGER, " + ChannelTbl.FAV_POSITION + " INTEGER, " + ChannelTbl.FAV_ID + " INTEGER," + ChannelTbl.EPG_ID + " INTEGER," + ChannelTbl.LOGO_URL + " TEXT," + ChannelTbl.FLAGS + " INTEGER);");
		db.execSQL("CREATE TABLE " + EpgTbl.TABLE_NAME + "(" + EpgTbl._ID + " INTEGER PRIMARY KEY AUTOINCREMENT," + EpgTbl.EPG_ID + " INTEGER," + EpgTbl.START + " INTEGER, " + EpgTbl.END + " INTEGER," + EpgTbl.TITLE + " TEXT," + EpgTbl.SUBTITLE + " TEXT," + EpgTbl.DESC + " TEXT," + EpgTbl.EVENT_ID + " TEXT," + EpgTbl.PDC + " TEXT);");
		db.execSQL("CREATE TABLE " + NowTbl.TABLE_NAME + "(" + NowTbl._ID + " INTEGER PRIMARY KEY AUTOINCREMENT," + NowTbl.EPG_ID + " INTEGER," + NowTbl.START + " INTEGER, " + NowTbl.END + " INTEGER," + NowTbl.TITLE + " TEXT," + NowTbl.SUBTITLE + " TEXT," + NowTbl.DESC + " TEXT," + NowTbl.EVENT_ID + " TEXT," + NowTbl.PDC + " TEXT);");
		db.execSQL("CREATE TABLE " + RootTbl.TABLE_NAME + "(" + RootTbl._ID + " INTEGER PRIMARY KEY AUTOINCREMENT," + RootTbl.NAME + " TEXT);");
		db.execSQL("CREATE TABLE " + GroupTbl.TABLE_NAME + "(" + GroupTbl._ID + " INTEGER PRIMARY KEY AUTOINCREMENT," + GroupTbl.ROOT_ID + " INTEGER," + GroupTbl.NAME + " TEXT," + GroupTbl.TYPE + " INTEGER);");
		db.execSQL("CREATE TABLE " + MediaTbl.TABLE_NAME + "(" + MediaTbl._ID + " INTEGER PRIMARY KEY AUTOINCREMENT," + MediaTbl.PARENT + " INTEGER," + MediaTbl.NAME + " TEXT," + MediaTbl.DIR_ID + " INTEGER);");
	}

	public void createXmltvTable(SQLiteDatabase db) {
		db.execSQL("CREATE TABLE IF NOT EXISTS " + XmltvTbl.TABLE_NAME + "("
				+ XmltvTbl._ID          + " INTEGER PRIMARY KEY AUTOINCREMENT,"
				+ XmltvTbl.CHANNEL_NAME + " TEXT,"
				+ XmltvTbl.START        + " INTEGER,"
				+ XmltvTbl.END          + " INTEGER,"
				+ XmltvTbl.TITLE        + " TEXT,"
				+ XmltvTbl.SUBTITLE     + " TEXT,"
				+ XmltvTbl.DESC         + " TEXT,"
				+ XmltvTbl.EPISODE_NUM  + " TEXT DEFAULT ''"
				+ ");");
		db.execSQL("CREATE INDEX IF NOT EXISTS idx_xmltv_channel ON "
				+ XmltvTbl.TABLE_NAME + "(" + XmltvTbl.CHANNEL_NAME + ");");
	}

	@Override
	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
		Log.i(this.getClass().getSimpleName(), "Upgrading database from version " + oldVersion + " to " + newVersion);
		if (oldVersion < 4) {
			createXmltvTable(db);
		} else if (oldVersion == 4) {
			// v4 → v5: add episode_num column to xmltv_epg
			try {
				db.execSQL("ALTER TABLE " + XmltvTbl.TABLE_NAME
						+ " ADD COLUMN " + XmltvTbl.EPISODE_NUM + " TEXT DEFAULT ''");
				Log.i(getClass().getSimpleName(), "Migrated xmltv_epg to v5: added episode_num");
			} catch (Exception ex) {
				Log.w(getClass().getSimpleName(), "episode_num migration skipped (column may already exist)", ex);
			}
		} else {
			db.execSQL("DROP TABLE IF EXISTS " + ChannelTbl.TABLE_NAME);
			db.execSQL("DROP TABLE IF EXISTS " + EpgTbl.TABLE_NAME);
			db.execSQL("DROP TABLE IF EXISTS " + NowTbl.TABLE_NAME);
			db.execSQL("DROP TABLE IF EXISTS " + GroupTbl.TABLE_NAME);
			db.execSQL("DROP TABLE IF EXISTS " + RootTbl.TABLE_NAME);
			db.execSQL("DROP TABLE IF EXISTS " + MediaTbl.TABLE_NAME);
			db.execSQL("DROP TABLE IF EXISTS " + XmltvTbl.TABLE_NAME);
			createChannelTable(db);
			createXmltvTable(db);
		}
	}

	@Override
	public synchronized void close() {
		try {
			super.close();
		} catch (Exception ignore) {
		}
	}

	public List<ChannelRoot> saveChannelRoots(List<ChannelRoot> rootElements) {
		if (rootElements == null || rootElements.size() <= 0) {
			return rootElements;
		}
		SQLiteDatabase db = getWritableDatabase();
		try {
			db.execSQL("DELETE FROM " + RootTbl.TABLE_NAME);
			db.execSQL("DELETE FROM " + GroupTbl.TABLE_NAME);
			db.execSQL("DELETE FROM " + ChannelTbl.TABLE_NAME);
			db.beginTransaction();

			for (ChannelRoot channelRoot : rootElements) {
				long rootId = db.insert(RootTbl.TABLE_NAME, null, channelRoot.toContentValues());
				for (ChannelGroup group : channelRoot.getGroups()) {
					group.setRootId(rootId);
					long groupId = db.insert(GroupTbl.TABLE_NAME, null, group.toContentValues());
					for (Channel chan : group.getChannels()) {
						chan.setGroupId(groupId);
						long id = db.insert(ChannelTbl.TABLE_NAME, null, chan.toContentValues());
						chan.setId(id);
					}
				}
			}
			db.setTransactionSuccessful();
		} catch (Exception e) {
			Log.e(this.getClass().getSimpleName(), "Error saving ChannelRoots", e);
		} finally {
			db.endTransaction();
			db.close();
			mContext.getContentResolver().notifyChange(GroupTbl.CONTENT_URI, null);
		}
		return rootElements;
	}

	public void saveNowPlaying(List<EpgEntry> epgEntries) {
		if (epgEntries == null || epgEntries.size() <= 0) {
			return;
		}
		final SQLiteDatabase db = getWritableDatabase();
		db.beginTransaction();
		db.execSQL("DELETE FROM " + NowTbl.TABLE_NAME);
		try {
			for (EpgEntry epgEntrie : epgEntries) {
				db.insert(NowTbl.TABLE_NAME, null, epgEntrie.toContentValues());
			}
			db.setTransactionSuccessful();
		} catch (Exception e) {
			Log.e(this.getClass().getSimpleName(), "Error saving NowPlaying", e);
		} finally {
			db.endTransaction();
			db.close();
			mContext.getContentResolver().notifyChange(ChannelTbl.CONTENT_URI_NOW, null);
		}
	}

	/**
	 * Replaces all XMLTV EPG entries for the given channel name.
	 * Called by XmltvParser after a successful download.
	 */
	public void saveXmltvEntries(String channelName, List<EpgEntry> entries) {
		if (entries == null || entries.isEmpty()) return;
		final SQLiteDatabase db = getWritableDatabase();
		db.beginTransaction();
		try {
			db.delete(XmltvTbl.TABLE_NAME,
					"LOWER(" + XmltvTbl.CHANNEL_NAME + ") = LOWER(?)",
					new String[]{channelName});
			for (EpgEntry e : entries) {
				ContentValues cv = new ContentValues();
				cv.put(XmltvTbl.CHANNEL_NAME, channelName);
				cv.put(XmltvTbl.START, e.getStart().getTime());
				cv.put(XmltvTbl.END, e.getEnd().getTime());
				cv.put(XmltvTbl.TITLE, e.getTitle());
				cv.put(XmltvTbl.SUBTITLE, e.getSubTitle());
				cv.put(XmltvTbl.DESC, e.getDescription());
				db.insert(XmltvTbl.TABLE_NAME, null, cv);
			}
			db.setTransactionSuccessful();
		} catch (Exception ex) {
			Log.e(getClass().getSimpleName(), "Error saving XMLTV entries for " + channelName, ex);
		} finally {
			db.endTransaction();
			db.close();
		}
	}

	/**
	 * Replaces the entire XMLTV table with fresh data from a full download.
	 * Called by XmltvRepository after parsing a complete XMLTV file.
	 */
	public void replaceAllXmltvEntries(java.util.Map<String, List<EpgEntry>> entriesByChannel) {
		if (entriesByChannel == null || entriesByChannel.isEmpty()) return;
		final SQLiteDatabase db = getWritableDatabase();
		db.beginTransaction();
		try {
			db.delete(XmltvTbl.TABLE_NAME, null, null);
			for (java.util.Map.Entry<String, List<EpgEntry>> entry : entriesByChannel.entrySet()) {
				String channelName = entry.getKey();
				for (EpgEntry e : entry.getValue()) {
					ContentValues cv = new ContentValues();
					cv.put(XmltvTbl.CHANNEL_NAME, channelName);
					cv.put(XmltvTbl.START, e.getStart().getTime());
					cv.put(XmltvTbl.END, e.getEnd().getTime());
					cv.put(XmltvTbl.TITLE, e.getTitle());
					cv.put(XmltvTbl.SUBTITLE, e.getSubTitle());
					cv.put(XmltvTbl.DESC, e.getDescription());
					cv.put(XmltvTbl.EPISODE_NUM, e.getEpisodeNum());
					db.insert(XmltvTbl.TABLE_NAME, null, cv);
				}
			}
			db.setTransactionSuccessful();
		} catch (Exception ex) {
			Log.e(getClass().getSimpleName(), "Error replacing XMLTV entries", ex);
		} finally {
			db.endTransaction();
			db.close();
		}
	}

	/**
	 * Returns all XMLTV programmes that are currently on air at [now] (epoch ms).
	 * Result: XMLTV channel_name → EpgEntry (one entry per channel; first/latest start wins).
	 * Used by ChannelList to fill in EPG for channels that have no DVBViewer "now" data.
	 */
	public java.util.Map<String, EpgEntry> getXmltvNowPlaying(long now) {
		java.util.Map<String, EpgEntry> result = new java.util.LinkedHashMap<>();
		SQLiteDatabase db = getReadableDatabase();
		Cursor c = null;
		try {
			c = db.query(
					XmltvTbl.TABLE_NAME,
					null,
					XmltvTbl.START + " <= ? AND " + XmltvTbl.END + " >= ?",
					new String[]{String.valueOf(now), String.valueOf(now)},
					null, null,
					XmltvTbl.START + " DESC"   // latest-start first so we keep the most recent
			);
			if (c != null) {
				int idxChannel    = c.getColumnIndex(XmltvTbl.CHANNEL_NAME);
				int idxTitle      = c.getColumnIndex(XmltvTbl.TITLE);
				int idxSubtitle   = c.getColumnIndex(XmltvTbl.SUBTITLE);
				int idxDesc       = c.getColumnIndex(XmltvTbl.DESC);
				int idxStart      = c.getColumnIndex(XmltvTbl.START);
				int idxEnd        = c.getColumnIndex(XmltvTbl.END);
				int idxEpisodeNum = c.getColumnIndex(XmltvTbl.EPISODE_NUM);

				while (c.moveToNext()) {
					String channelName = c.getString(idxChannel);
					if (channelName == null || result.containsKey(channelName)) continue;

					String rawTitle      = c.getString(idxTitle);
					String rawSubtitle   = c.getString(idxSubtitle);
					String rawDesc       = c.getString(idxDesc);
					String rawEpisodeNum = (idxEpisodeNum >= 0) ? c.getString(idxEpisodeNum) : null;

					String safeTitle      = (rawTitle      != null) ? rawTitle.trim()      : "";
					String safeSubTitle   = (rawSubtitle   != null) ? rawSubtitle.trim()   : "";
					String safeEpisodeNum = (rawEpisodeNum != null) ? rawEpisodeNum.trim() : "";

					// ErsatzTV writes the channel display-name in <title> when it has no
					// real EPG data. Keep the entry only when there is something else
					// meaningful to show (subTitle or episodeNum).
					boolean titleIsPlaceholder =
							safeTitle.isEmpty() || safeTitle.equalsIgnoreCase(channelName);
					if (titleIsPlaceholder && safeSubTitle.isEmpty() && safeEpisodeNum.isEmpty()) {
						Log.d("DbHelper", "  XMLTV skip (no useful data): \"" + channelName + "\"");
						continue;
					}

					EpgEntry e = new EpgEntry();
					e.setChannel(channelName);
					e.setTitle(safeTitle);
					e.setSubTitle(safeSubTitle);
					e.setDescription(rawDesc != null ? rawDesc.trim() : "");
					e.setEpisodeNum(safeEpisodeNum);
					e.setStart(new Date(c.getLong(idxStart)));
					e.setEnd  (new Date(c.getLong(idxEnd)));
					result.put(channelName, e);

					Log.d("DbHelper", "  XMLTV now: channel=\"" + channelName
							+ "\"  title=\"" + safeTitle
							+ "\"  sub=\"" + safeSubTitle
							+ "\"  ep=\"" + safeEpisodeNum + "\"");
				}
			}
			Log.d("DbHelper", "getXmltvNowPlaying: " + result.size() + " channels on air");
		} catch (Exception ex) {
			Log.e("DbHelper", "getXmltvNowPlaying error", ex);
		} finally {
			if (c != null) c.close();
			db.close();
		}
		return result;
	}

	/**
	 * Returns all distinct channel_name values stored in xmltv_epg, sorted alphabetically.
	 * Used by XmltvRepository to diagnose name-mismatch failures.
	 */
	public List<String> getAllXmltvChannelNames() {
		List<String> names = new ArrayList<>();
		SQLiteDatabase db = getReadableDatabase();
		Cursor c = null;
		try {
			c = db.rawQuery(
					"SELECT DISTINCT " + XmltvTbl.CHANNEL_NAME +
					" FROM " + XmltvTbl.TABLE_NAME +
					" ORDER BY " + XmltvTbl.CHANNEL_NAME + " ASC",
					null);
			if (c != null) {
				while (c.moveToNext()) {
					String n = c.getString(0);
					if (n != null) names.add(n);
				}
			}
		} catch (Exception ex) {
			Log.e("DbHelper", "getAllXmltvChannelNames error", ex);
		} finally {
			if (c != null) c.close();
			db.close();
		}
		return names;
	}

	/**
	 * Returns XMLTV EPG entries for a channel within the given time range.
	 * Case-insensitive channel name match (LOWER on both sides).
	 * Null safety: getString() can return null for NULL columns → use "" as default.
	 */
	public List<EpgEntry> getXmltvEntries(String channelName, long start, long end) {
		List<EpgEntry> result = new ArrayList<>();

		// Diagnostic: log total row count in table so we know if data was ever saved
		SQLiteDatabase dbCount = null;
		try {
			dbCount = getReadableDatabase();
			Cursor countC = dbCount.rawQuery("SELECT COUNT(*) FROM " + XmltvTbl.TABLE_NAME, null);
			long total = 0;
			if (countC.moveToFirst()) total = countC.getLong(0);
			countC.close();
			Log.d("DbHelper", "getXmltvEntries(\"" + channelName + "\", " + start + ", " + end + ")" +
					" — total rows in xmltv_epg: " + total);
		} catch (Exception ex) {
			Log.w("DbHelper", "Could not count xmltv_epg rows", ex);
		} finally {
			if (dbCount != null) dbCount.close();
		}

		SQLiteDatabase db = getReadableDatabase();
		Cursor c = null;
		try {
			String selection = "LOWER(" + XmltvTbl.CHANNEL_NAME + ") = LOWER(?) AND "
					+ XmltvTbl.END + " > ? AND " + XmltvTbl.START + " < ?";
			String[] selArgs = {channelName, String.valueOf(start), String.valueOf(end)};
			Log.d("DbHelper", "  SQL: SELECT * FROM " + XmltvTbl.TABLE_NAME +
					" WHERE " + selection + " [args: " + channelName + ", " + start + ", " + end + "]");

			c = db.query(XmltvTbl.TABLE_NAME, null, selection, selArgs,
					null, null, XmltvTbl.START + " ASC");

			Log.d("DbHelper", "  Query returned " + (c != null ? c.getCount() : "null cursor") + " rows");

			if (c != null) {
				int idxTitle    = c.getColumnIndex(XmltvTbl.TITLE);
				int idxSubtitle = c.getColumnIndex(XmltvTbl.SUBTITLE);
				int idxDesc     = c.getColumnIndex(XmltvTbl.DESC);
				int idxStart    = c.getColumnIndex(XmltvTbl.START);
				int idxEnd      = c.getColumnIndex(XmltvTbl.END);
				while (c.moveToNext()) {
					EpgEntry e = new EpgEntry();
					// FIX: c.getString() returns null for NULL columns; use "" to avoid NPE
					// when Kotlin non-nullable setter is called from Java.
					String title    = c.getString(idxTitle);
					String subtitle = c.getString(idxSubtitle);
					String desc     = c.getString(idxDesc);
					e.setTitle(title    != null ? title    : "");
					e.setSubTitle(subtitle != null ? subtitle : "");
					e.setDescription(desc != null ? desc   : "");
					e.setStart(new Date(c.getLong(idxStart)));
					e.setEnd(new Date(c.getLong(idxEnd)));
					e.setChannel(channelName);
					result.add(e);
				}
			}
			Log.d("DbHelper", "  Returning " + result.size() + " EpgEntry objects for \"" + channelName + "\"");
		} catch (Exception ex) {
			Log.e("DbHelper", "Error reading XMLTV entries for " + channelName, ex);
		} finally {
			if (c != null) c.close();
			db.close();
		}
		return result;
	}
}
