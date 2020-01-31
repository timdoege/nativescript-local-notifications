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
      return;
    }

    /*
    Log.i(TAG, "Cancel Ctx " + context);
    Log.i(TAG, "Cancel Ctx2 " + context.getApplicationContext());

    Log.i(TAG, "Cancel CTX1 notification map: " + Store.getAll(context));
    Log.i(TAG, "Cancel CTX2 notification map: " + Store.getAll(context.getApplicationContext()));

    Log.i(TAG, "Cancel CTX1 Alarm fired map: " + Store.getAlarmsFiredMap(context));
    Log.i(TAG, "Cancel CTX2 Alarm fired map: " + Store.getAlarmsFiredMap(context.getApplicationContext()));
    */

    // Default value not used as above check ensures we have an actual value:
    final int id = intent.getIntExtra(Builder.NOTIFICATION_ID, 0);
    final JSONObject opts = Store.get(context, id);

    try {
      if (opts != null) {
        // In case we clear a non-recurring notification, we remove it
        if (opts.optInt("repeatInterval", 0) == 0) {
          // Remove the persisted notification data if it's not repeating:
          Store.remove(context, id);
        }
        else {
          NotificationRestoreReceiver.handleRepeatingScheduleOnActionOrClear(opts, context, id);
        }
        LocalNotificationsPlugin.executeOnMessageClearedCallback(opts);
      }
      else {
        Log.e(TAG, "onReceive, id "+id+", no opts");
      }
    } catch (Exception e) {
      Log.e(TAG, "Notification "+id+" could not be (re-)scheduled!" + e.getMessage(), e);
    }

  }
}