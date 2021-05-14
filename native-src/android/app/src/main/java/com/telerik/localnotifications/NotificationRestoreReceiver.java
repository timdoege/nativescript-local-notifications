package com.telerik.localnotifications;

import android.app.AlarmManager;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.Map;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

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

    Map<String,String> alarmsFiredMap = Store.getAlarmsFiredMap(context);
    // Log.d(TAG, "Restore CTX1 Alarm fired map: " + alarmsFiredMap);
    // Log.d(TAG, "Restore CTX2 Alarm fired map: " + Store.getAlarmsFiredMap(context.getApplicationContext()));
    // Process all notifications for rescheduling
    // and showing notifications if they have fired while the device was offline
    final Map<String,String> storeContentMap = Store.getAll(context);
    try {
      for (String key : storeContentMap.keySet()) {
        // Re-schedule notification
        final String notificationString = storeContentMap.get(key);
        if (notificationString != null) {
          Log.i(TAG, "Process previously scheduled notification: " + notificationString);
          JSONObject json = new JSONObject(notificationString);
          scheduleNotification(json, context, alarmsFiredMap, false);
        }
      }
    } catch (Throwable e) {
      Log.e(TAG, "Notification could not be processed! " + e.getMessage(), e);
    }

  }

  static void scheduleNotification(JSONObject options, Context context) {
    scheduleNotification(options, context, null, false);
  }

  /**
   * Ensure repeating notifications using alertWhileIdle (waking up in Doze mode) are rescheduled
   * manually since they can not be set as repeating using the AlarmManager
   *
   * @param options
   * @param context
   * @param id Notification id
   *
   */
  static void handleRepeatingScheduleOnActionOrClear(JSONObject options, Context context, int id) {
    final long interval = options.optLong("repeatInterval", 0); // in ms
    final boolean alertWhileIdle = options.optInt("alertWhileIdle", 0) == 1;
    Log.d(TAG, "handleRepeatingScheduleOnActionOrClear, id "+id+" (alertWhileIdle="+alertWhileIdle+")");

    // If a repeating alarm is also has "alertWhileIdle" we need to manually schedule the next alarm
    // since there is no setExactAndAllowWhileIdleRepeating method and the Android docs explicitly state
    // that this is the way to handle this
    if (alertWhileIdle) {
      scheduleNotification(options, context, null,true);
    }
    else {
      Log.d(TAG, "handleRepeatingScheduleOnActionOrClear, id "+id+" (non alertWhileIdle), has repeat, not removing");
    }

  }

  /**
   * Handles scheduling of notifications - and displaying notifications that are due/overdue
   *
   * @param options
   * @param context
   * @param alarmsFiredMap
   * @param skipImmediateNotifications
   */
  static void scheduleNotification(JSONObject options, Context context, Map<String,String> alarmsFiredMap, boolean skipImmediateNotifications) {

    // We might create the notification IMMEDIATELY:

    // If no ID is provided, we automatically assign different IDs so that all notifications are persisted:
    final int notificationID = options.optInt("id", 0);
    final long triggerTime = options.optLong("atTime", 0);
    final Date triggerDate = new Date(triggerTime);
    final long nowMillis = System.currentTimeMillis();
    final Date now = new Date(nowMillis);
    final boolean alertWhileIdle = options.optInt("alertWhileIdle", 0) == 1;
    final long interval = options.optLong("repeatInterval", 0); // in ms
    final String intervalText = options.optString("repeatIntervalText", "");
    Log.d(TAG, "scheduleNotification - Alarm "+ notificationID+" has atTime="+triggerTime+", converted to trigger date "+triggerDate+", interval "
            +interval+", skipImmediateNotifications="+skipImmediateNotifications+", triggerTime="+triggerTime);

    // In case the notification is set to alertWhileIdle, we need to check if we missed any alarms while the device was shut down
    // - this is ony relevant if the notification has a trigger time (atTime) set
    if (alertWhileIdle && !skipImmediateNotifications && triggerTime != 0) {
      boolean doSendNotification = false;
      // Check if the trigger time is in the past - otherwise it will be handled later
      if (triggerTime <= nowMillis) {
        Log.d(TAG, "Alarm "+ notificationID+" has trigger time in the past, interval "+interval);

        // Trigger time is in the past - for repeating - and non-repeating - notifications we need to check when the last alarm was fired
        // as we only store the initial trigger time (atTime)
        // Check when the alarm was last fired
        long lastFiredTs = -1;
        if (alarmsFiredMap != null) {
          String lastFiredTsStr = alarmsFiredMap.get(Integer.toString(notificationID));
          if (lastFiredTsStr != null) {
            try {
              lastFiredTs = Long.parseLong(lastFiredTsStr);
              Log.d(TAG, "Alarm "+ notificationID+" - last fired found "+lastFiredTs);

            } catch (NumberFormatException e) {
              Log.e(TAG, "Notification "+notificationID+" - unable to parse last fired timestamp." + e.getMessage(), e);
            }
          }
        }
        if (lastFiredTs < 0) {
          Log.d(TAG, "No alarm fired info found for notification "+notificationID);
        }
        if (interval > 0) {
          // Determine if the last fired timestamp has not been set yet or if the last fired timestamp
          // plus the interval is in the past - if so, we missed this notification and show it now
          doSendNotification = lastFiredTs < 0 || lastFiredTs + interval < nowMillis;
        }
        else {
          // For non-repeating alarms we just check if it should have been fired but has not (or was not recorded as fired)
          doSendNotification = lastFiredTs < 0;
        }
      }

      Log.d(TAG, "Alarm "+ notificationID+" - showNotification="+doSendNotification);
      if (doSendNotification) {
        ((NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE)).notify(
                notificationID, com.telerik.localnotifications.Builder.build(options, context, notificationID)
        );
        // Also, register this alarm as fired "now" since we send the notification directly
        Store.registerAlarmFired(context, notificationID);
      }
    }

    if (triggerTime == 0 && !skipImmediateNotifications) {
      // If we just want to show the notification immediately, there's no need to create an Intent,
      // we just send the notification to the Notification Service:

      ((NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE)).notify(
          notificationID, com.telerik.localnotifications.Builder.build(options, context, notificationID)
      );
      // Also, register this alarm as fired "now" since we send the notification directly
      Store.registerAlarmFired(context, notificationID);

      if (triggerTime == 0) {
        return;
      }
    }

    // Check if the notification has EXPIRED:
    if (interval == 0 && now.after(triggerDate)) {
      Log.d(TAG, "Alarm "+ notificationID+" - has expired and is removed, interval="+interval);
      Store.remove(context, notificationID);
      return;
    }

    // Or SCHEDULE it for later:

    final AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);

    try {
      final Intent notificationIntent = new Intent(context, NotificationAlarmReceiver.class)
          .setAction(options.getString("id"))
          .putExtra(Builder.NOTIFICATION_ID, notificationID);
      Log.d(TAG, "Alarm "+ notificationID+" - interval="+interval+", schedule for later");

      if (interval > 0) {
        if (alertWhileIdle) {

          long nextTriggerTime = calcNextAlertWhileIdleTriggerTime(triggerTime, interval, notificationID);

          final PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 0, notificationIntent, PendingIntent.FLAG_CANCEL_CURRENT);

          Log.d(TAG, "Alarm "+ notificationID+" - alarmManager.setExactAndAllowWhileIdle nextMillis="+nextTriggerTime+", date="+new Date(nextTriggerTime));

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

  /**
   * Calculates the next trigger time for an alertWhileIdle alarm based on the stored time and interval
   *
   * Note: If the interval is YEAR, the plugin does not handle leap years as it always assumes 365 days / year (in Javascript code)
   *
   * @param triggerTime
   * @param interval
   * @param notificationID
   * @return
   */
  public static long calcNextAlertWhileIdleTriggerTime(long triggerTime, long interval, int notificationID) {
    long nextTriggerTime = triggerTime;
    Log.d(TAG, "Alarm "+ notificationID+" - calcNextTriggerTime based on trigger time="+triggerTime+", interval="+interval);
    final long nowMillis = System.currentTimeMillis();

    // Note that we at this point don't know how many alarm occurrences we may have missed and hence need to add the timer interval to the scheduled
    // alarm time until we reach a point ahead in time (ie. the user clears or activates a daily repeating notification 2 days later)
    // If the trigger date is in the past, we set up the next instance to be x + 1 times the interval from the trigger date
    if (nextTriggerTime <= nowMillis) {
      long multiplier = (nowMillis - triggerTime) / interval;
      Calendar cal = GregorianCalendar.getInstance();
      // Set the calendar to the initial trigger time + the number of times it has been triggered
      cal.setTimeInMillis(multiplier * interval + triggerTime);
      // Now, calculate how much to add to that point in time (which is before the next trigger time)
      // Note: We need to use the Calendar's add method to take DST into account
      // - since that only can handle Integers in the MILLISECOND field, we need to add DAYS if the interval is larger
      if (interval < AlarmManager.INTERVAL_DAY) {
        cal.add(Calendar.MILLISECOND, (int)interval);
      }
      else {
        // Get the number of days the interval represents (max interval is 1 year, hence int cast is ok)
        int days = (int)(interval / AlarmManager.INTERVAL_DAY);
        cal.add(Calendar.DATE, days);
        int rest = (int)(interval % AlarmManager.INTERVAL_DAY);
        cal.add(Calendar.MILLISECOND, rest);
      }
      nextTriggerTime = cal.getTimeInMillis();
      Log.d(TAG, "Alarm "+ notificationID+" - calculated next alertWhileIdle trigger time to millis="+nextTriggerTime+", date="+cal.getTime());
    }
    return nextTriggerTime;
  }

  public static void main(String[] args) {
    long cMillis = 1620991841373L; // System.currentTimeMillis() - AlarmManager.INTERVAL_HOUR;
    Date tt = new Date(cMillis);
    System.out.println("Start "+cMillis+" - "+tt);
    // long interval = 3*AlarmManager.INTERVAL_DAY+3*AlarmManager.INTERVAL_HOUR;
    long interval = 2*365*AlarmManager.INTERVAL_DAY+3*AlarmManager.INTERVAL_HOUR;
    long nextTrigger = calcNextAlertWhileIdleTriggerTime(cMillis, interval, 111);
    tt = new Date(nextTrigger);
    System.out.println("Next: "+nextTrigger+" - "+tt);
  }
}
