package com.telerik.localnotifications;

import android.content.Context;
import androidx.annotation.Nullable;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.LinkedList;
import java.util.Map;

/**
 * Application Store facilitating Android SharedPreferences
 *
 * Stores notification data as JSON and info to track when alarms have been fired
 */
public final class Store {

    private static final String TAG = "Store";
    // The plugin uses two key-values stores (private): One for the notifications and one for tracking fired alarms
    // (in order to determine if an alarm using the alertWhileIdle setting should have been fired while the device was shut off)
    private static final String SHARED_PREFERENCES_KEY = "LocalNotificationsPlugin";
    private static final String SHARED_PREFERENCES_ALARMS_FIRED_KEY = "LocalNotificationsPluginAlarmsFired";

    /**
     * Get a notification from the store
     *
     * @param context
     * @param id
     * @return
     */
    public static @Nullable JSONObject get(Context context, int id) {
        return get(context, id, true);
    }

    /**
     *
     * Get a notification from the store, allowing null return values
     *
     * @param context
     * @param id
     * @param nullable
     * @return
     */
    public static @Nullable JSONObject get(Context context, int id, boolean nullable) {
        try {
            return new JSONObject(context
                .getSharedPreferences(SHARED_PREFERENCES_KEY, Context.MODE_PRIVATE)
                .getString(String.valueOf(id), ""));
        } catch (Throwable e) {
            Log.e(TAG, "Error parsing options" + e.getMessage(), e);
        }

        return nullable ? null : new JSONObject();
    }

    /**
     * Get a map of all notifications
     *
     * @param context
     * @return
     */
    public static Map<String, String> getAll(Context context) {
        return (Map<String, String>) context.getSharedPreferences(SHARED_PREFERENCES_KEY, Context.MODE_PRIVATE).getAll();
    }

    /**
     * Called from NS API
     * @param context
     * @return Notification keys
     */
    public static String[] getKeys(Context context) {
        return getAll(context).keySet().toArray(new String[0]);
    }

    /**
     * Save a notification
     *
     * @param context
     * @param opts
     */
    public static void save(Context context, JSONObject opts) {
        save(context, opts.optInt("id", 0), opts);
    }

    /**
     * Save a notification to the store
     * @param context
     * @param id
     * @param opts
     */
    public static void save(Context context, int id, String opts) {
        try {
            save(context, id, new JSONObject(opts));
        } catch (Throwable e) {
            Log.e(TAG, "Error saving options" + e.getMessage(), e);
        }
    }

    /**
     * Save a notification with given id
     *
     * @param context
     * @param id
     * @param opts
     */
    public static void save(Context context, int id, JSONObject opts) {
        context.getSharedPreferences(SHARED_PREFERENCES_KEY, Context.MODE_PRIVATE).edit().putString(String.valueOf(id), opts.toString()).apply();
    }

    /**
     * Remove a notification from the store given an id
     *
     * @param context
     * @param id
     */
    public static void remove(Context context, int id) {
        context.getSharedPreferences(SHARED_PREFERENCES_KEY, Context.MODE_PRIVATE).edit().remove(String.valueOf(id)).apply();
        // Also remove any alarms fired data (if any)
        context.getSharedPreferences(SHARED_PREFERENCES_ALARMS_FIRED_KEY, Context.MODE_PRIVATE).edit().remove(String.valueOf(id)).apply();
    }

    /**
     * Store timestamp info regarding when alarm with given id is fired. This is used on device reboot to determine if any alarms have been missed.
     * @param context
     * @param id
     */
    public static void registerAlarmFired(Context context, int id) {
        String now = Long.toString(System.currentTimeMillis());
        context.getSharedPreferences(SHARED_PREFERENCES_ALARMS_FIRED_KEY, Context.MODE_PRIVATE).edit().putString(String.valueOf(id),
                now).apply();
        Log.i(TAG, "Alarm "+id+" registered fired at "+now);
    }

    /**
     * Get the last fired timestamp for an alarm with the given id
     *
     * @param context
     * @param id ID of the alarm (notification)
     * @return Timestamp in millis if an entry is found, 0 otherwise
     */
    public static long getAlarmLastFiredTimestamp(Context context, int id) {
        long res = 0;
        try {
            final String alarmFiredTs = context
                    .getSharedPreferences(SHARED_PREFERENCES_ALARMS_FIRED_KEY, Context.MODE_PRIVATE)
                    .getString(String.valueOf(id), null);
            if (alarmFiredTs != null) {
                res = Long.parseLong(alarmFiredTs);
            }
        } catch (Throwable e) {
            Log.e(TAG, "Error parsing alarm fired timestamp" + e.getMessage(), e);
        }
        return res;
    }

    /**
     * Get a map with all recorded info from alarms fired
     *
     * @param context
     * @return
     */
    public static Map<String, String> getAlarmsFiredMap(Context context) {
        return (Map<String, String>) context.getSharedPreferences(SHARED_PREFERENCES_ALARMS_FIRED_KEY, Context.MODE_PRIVATE).getAll();
    }
}
