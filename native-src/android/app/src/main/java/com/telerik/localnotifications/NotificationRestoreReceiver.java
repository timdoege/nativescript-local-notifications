package com.telerik.localnotifications;

import android.app.AlarmManager;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import java.util.Date;
import java.util.Map;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Notifications need to be restored when the device is rebooted,
 * that's what's this class is for.
 */
public class NotificationRestoreReceiver extends BroadcastReceiver {

  private static final String TAG = "NotifyRestoreReceiver";

  @Override
  public void onReceive(Context context, Intent intent) {
    if (context == null || !Intent.ACTION_BOOT_COMPLETED.equalsIgnoreCase(intent.getAction())) {
      // Log.i("SQDK NotifRestoreRec", "onReceive, no context ("+(context==null)+ ") or not reboot");
      return;
    }

    try {
      for (Map.Entry<String, String> entry : Store.getAll(context).entrySet()) {
        final String notificationString = entry.getValue();

        Log.e(TAG, "Will restore previously scheduled notification: " + notificationString);

        scheduleNotification(new JSONObject(notificationString), context);
      }
    } catch (Throwable e) {
      Log.e(TAG, "Notification could not be scheduled! " + e.getMessage(), e);
    }
  }

  /**
   * Ensure repeating notifications using alertWhileIdle (waking up in Doze mode) are rescheduled
   * manually since they can not be set as repeating
   *
   * @param options
   * @param context
   * @param id Notification id
   *
   */
  static void handleRepeatingScheduleOnActionOrClear(JSONObject options, Context context, int id) {
    final long interval = options.optLong("repeatInterval", 0); // in ms
    final boolean alertWhileIdle = options.optInt("alertWhileIdle", 0) == 1;

    // If a repeating alarm is also has "alertWhileIdle" we need to manually schedule the next alarm
    // since there is no setExactAndAllowWhileIdleRepeating method and the Android docs explicitly state
    // that this is the way to solve this
    if (alertWhileIdle) {
      Log.i("SQDK NotifClearReceiver", "onReceive, id "+id+" (alertWhileIdle), has repeat - re-scheduling");
      scheduleNotification(options, context, true);
    }
    else {
      Log.i("SQDK NotifClearReceiver", "onReceive, id "+id+" (non alertWhileIdle), has repeat, not removing");
    }

  }

  static void scheduleNotification(JSONObject options, Context context) {
    scheduleNotification(options, context, false);
  }

  static void scheduleNotification(JSONObject options, Context context, boolean skipImmediateNotifcations) {

    // We might create the notification IMMEDIATELY:

    // If no ID is provided, we automatically assign different IDs so that all notifications are persisted:
    final int notificationID = options.optInt("id", 0);
    final long triggerTime = options.optLong("atTime", 0);
    final boolean alertWhileIdle = options.optInt("alertWhileIdle", 0) == 1;

    if (triggerTime == 0 && !skipImmediateNotifcations) {
      // If we just want to show the notification immediately, there's no need to create an Intent,
      // we just send the notification to the Notification Service:

      Log.i("SQDK NotifRestoreRec", "scheduleNotification, triggerTime=0 for notificationId "+notificationID);

      ((NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE)).notify(
          notificationID, com.telerik.localnotifications.Builder.build(options, context, notificationID)
      );
      Log.i("SQDK NotifRestoreRec", "scheduleNotification, done calling NOTIFY for notificationId "+notificationID);
      return;
    }

    // Check if the notification has EXPIRED:

    final long interval = options.optLong("repeatInterval", 0); // in ms
    final Date triggerDate = new Date(triggerTime);

    if (interval == 0 && new Date().after(triggerDate)) {
      Log.i("SQDK NotifRestoreRec", "scheduleNotification, REMOVE "+notificationID+", interval="+interval+", triggerDate="+triggerDate);
      Store.remove(context, notificationID);
      return;
    }

    // Or SCHEDULE it for later:

    final AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);

    try {
      final Intent notificationIntent = new Intent(context, NotificationAlarmReceiver.class)
          .setAction(options.getString("id"))
          .putExtra(Builder.NOTIFICATION_ID, notificationID);

      final PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 0, notificationIntent, PendingIntent.FLAG_CANCEL_CURRENT);
      Log.i("SQDK NotifRestoreRec", "scheduleNotification, Schedule "+notificationID+" for later, interval="+interval+", triggerDate="+triggerDate);

      if (interval > 0) {
        if (alertWhileIdle) {
          // Calculate the next trigger time based on the interval
          long nextTriggerTime = triggerTime + interval;
          Log.i("SQDK NotifRestoreRec", "scheduleNotification, Schedule "+notificationID+" (repeat) excact and distruptive, next trigger date="+new Date(nextTriggerTime));
          alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextTriggerTime, pendingIntent);
        }
        else {
          alarmManager.setRepeating(AlarmManager.RTC_WAKEUP, triggerTime, interval, pendingIntent);
        }

      } else {
        // We know at this point that the trigger date has not been reached yet
        if (alertWhileIdle) {
          Log.i("SQDK NotifRestoreRec", "scheduleNotification, Schedule "+notificationID+" (none-repeating) excact and distruptive, triggerDate="+triggerDate);
          alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent);
        }
        else {
          alarmManager.set(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent);
        }
      }
    } catch (Throwable e) {
      Log.e(TAG, "Notification "+notificationID+" could not be scheduled!" + e.getMessage(), e);
    }
  }
}
