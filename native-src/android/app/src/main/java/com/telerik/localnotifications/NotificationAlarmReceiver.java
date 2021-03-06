package com.telerik.localnotifications;

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import org.json.JSONObject;

import java.util.Map;

public class NotificationAlarmReceiver extends BroadcastReceiver {

  private static final String TAG = "NotificationAlarmRcvr";

  public void onReceive(Context context, Intent intent) {
    final int id = intent.getIntExtra(Builder.NOTIFICATION_ID, 0);
    final JSONObject opts = Store.get(context.getApplicationContext(), id);

    if (opts == null) {
      Log.e(TAG, "Notification could not be created, options are null");
      return;
    }

    /*
    Log.i(TAG, "Receive Ctx " + context);
    Log.i(TAG, "Receive Ctx2 " + context.getApplicationContext());

    Log.i(TAG, "Receive CTX1 notification map: " + Store.getAll(context));
    Log.i(TAG, "Receive CTX2 notification map: " + Store.getAll(context.getApplicationContext()));
    */
    // Register timestamp of this alarm being fired
    Store.registerAlarmFired(context.getApplicationContext(), id);

    /*
    Log.i(TAG, "Receive CTX1 Alarm fired map: " + Store.getAlarmsFiredMap(context));
    Log.i(TAG, "Receive CTX2 Alarm fired map: " + Store.getAlarmsFiredMap(context.getApplicationContext()));
    */

    // Create the notification:
    try {
      ((NotificationManager) context
        .getSystemService(Context.NOTIFICATION_SERVICE))
        .notify(id, Builder.build(opts, context, id));
    } catch (Throwable t) {
      Log.e(TAG, "Notification could not be created!" + t.getMessage(), t);
    }

    // Note we don't unpersist this notification just yet, as it might still need to be restored
    // after a reboot.

    // In order to support firing a notification if an alarm was missed due to the device being
    // turned off, we record the current timestamp for this alarm. This data is used in the part of
    // NotificationRestoreReceiver that runs on device reboot.


  }
}
