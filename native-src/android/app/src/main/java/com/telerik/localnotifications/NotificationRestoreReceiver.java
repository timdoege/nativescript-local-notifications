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
      scheduleNotification(options, context, true);
    }
    else {
      // Log.c(TAG, "handleRepeatingScheduleOnActionOrClear, id "+id+" (non alertWhileIdle), has repeat, not removing");
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

      ((NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE)).notify(
          notificationID, com.telerik.localnotifications.Builder.build(options, context, notificationID)
      );
      return;
    }

    // Check if the notification has EXPIRED:

    final long interval = options.optLong("repeatInterval", 0); // in ms
    final Date triggerDate = new Date(triggerTime);
    final long nowMillis = System.currentTimeMillis();
    final Date now = new Date(nowMillis);
    if (interval == 0 && now.after(triggerDate)) {
      Store.remove(context, notificationID);
      return;
    }

    // Or SCHEDULE it for later:

    final AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);

    try {
      final Intent notificationIntent = new Intent(context, NotificationAlarmReceiver.class)
          .setAction(options.getString("id"))
          .putExtra(Builder.NOTIFICATION_ID, notificationID);

      if (interval > 0) {
        if (alertWhileIdle) {
          // Calculate the next trigger time based on the interval
          // Note that we at this point don't know how many alarm occurrences we may have missed and hence need to add the timer interval to the scheduled
          // alarm time until we reach a point ahead in time (ie. the user clears or activates a daily repeating notification 2 days later)
          // If the trigger date is in the past, we set up the next instance to be x + 1 times the interval from the trigger date
          long multiplier = (nowMillis - triggerTime) / interval;
          long nextTriggerTime = (multiplier + 1) * interval + triggerTime;

          final PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 0, notificationIntent, PendingIntent.FLAG_CANCEL_CURRENT);
          alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextTriggerTime, pendingIntent);
        }
        else {
          final PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 0, notificationIntent, PendingIntent.FLAG_CANCEL_CURRENT);
          alarmManager.setRepeating(AlarmManager.RTC_WAKEUP, triggerTime, interval, pendingIntent);
        }

      } else {
        // We know at this point that the trigger date has not been reached yet
        if (alertWhileIdle) {
          final PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 0, notificationIntent, PendingIntent.FLAG_CANCEL_CURRENT);
          alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent);
        }
        else {
          final PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 0, notificationIntent, PendingIntent.FLAG_CANCEL_CURRENT);
          alarmManager.set(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent);
        }
      }
    } catch (Throwable e) {
      Log.e(TAG, "Notification "+notificationID+" could not be scheduled!" + e.getMessage(), e);
    }
  }
}
