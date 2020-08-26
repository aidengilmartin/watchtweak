package com.aidengilmartin.watchtweak

import android.content.Intent
import android.os.IBinder
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import org.greenrobot.eventbus.EventBus

class NotificationService : NotificationListenerService() {

    override fun onBind(intent: Intent): IBinder? {
        Log.i("NotificationService", "onBind")
        return super.onBind(intent)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Register with the EventBus
        EventBus.getDefault().register(this)
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onDestroy() {
        // Unregister with the EventBus
        EventBus.getDefault().unregister(this)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn != null) {
            Log.i("NotificationService", "onNotificationPosted ${sbn.packageName}")
            // Post a NotificationEvent to the EventBus
            EventBus.getDefault().post(NotificationEvent(sbn))
        } else {
            Log.e("NotificationService", "onNotificationPosted, Null Notification")
        }
    }
}
