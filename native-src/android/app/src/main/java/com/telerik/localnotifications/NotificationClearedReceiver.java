package com.telerik.localnotifications;

import android.content.Context;
import android.content.Intent;

import android.content.BroadcastReceiver;
import androidx.annotation.Nullable;

import org.json.JSONObject;
import android.util.Log;

public class NotificationClearedReceiver extends BroadcastReceiver {
  private static String TAG = "NotificationClearedReceiver";

  /**
   * Called when the notification is cleared from the notification center.
   *
   * @param context Application context
   * @param intent  Received intent with notification ID
   */
  @Override
  public void onReceive(Context context, @Nullable Intent intent) {
    if (intent == null || !intent.hasExtra(Builder.NOTIFICATION_ID)) {
      Log.i("SQDK NotifClearReceiver", "onReceive, no intent ("+(intent==null)+ ") or no notification id");
      return;
    }

    // Default value not used as above check ensures we have an actual value:
    final int id = intent.getIntExtra(Builder.NOTIFICATION_ID, 0);
    final JSONObject opts = Store.get(context, id);
    final boolean alertWhileIdle = opts.optInt("alertWhileIdle", 0) == 1;
    Log.i("SQDK NotifClearReceiver", "onReceive, id "+id+", alertWhileIdle="+alertWhileIdle);

    if (opts != null) {
      if (opts.optInt("repeatInterval", 0) == 0) {
        Log.i("SQDK NotifClearReceiver", "onReceive, id "+id+", no repeat, removing from Store");
        // Remove the persisted notification data if it's not repeating:
        Store.remove(context, id);
        Log.i("SQDK NotifClearReceiver", "onReceive, id "+id+" done removing from Store");
      }
      else {
        // If a repeating alarm is also has "alertWhileIdle" we need to manually schedule the next alarm
        // since there is no setExactAndAllowWhileIdleRepeating method and the Android docs explicitly state
        // that this is the way to solve this
        if (alertWhileIdle) {
          try {
            Log.i("SQDK NotifClearReceiver", "onReceive, id "+id+" (alertWhileIdle), has repeat - re-scheduling");
            NotificationRestoreReceiver.scheduleNotification(opts, context);
          } catch (Exception e) {
            Log.e(TAG, "Notification "+id+" (alertWhileIdle) could not be (re-)scheduled!" + e.getMessage(), e);
          }
        }
        else {
          Log.i("SQDK NotifClearReceiver", "onReceive, id "+id+" (non alertWhileIdle), has repeat, not removing");
        }
      }
      LocalNotificationsPlugin.executeOnMessageClearedCallback(opts);
    }
    else {
      Log.i("SQDK NotifClearReceiver", "onReceive, id "+id+", no opts");
    }
  }
}