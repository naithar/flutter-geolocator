package com.baseflow.geolocator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.baseflow.geolocator.location.GeolocationManager;
import com.baseflow.geolocator.location.LocationAccuracyManager;
import com.baseflow.geolocator.permission.PermissionManager;
import io.flutter.embedding.engine.plugins.FlutterPlugin;
import io.flutter.embedding.engine.plugins.activity.ActivityAware;
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding;

import android.app.*;
import android.location.*;
import android.content.*;
import android.os.*;
import androidx.core.app.NotificationCompat;

import 	android.content.res.*;

/** GeolocatorNotificationManager */
public class GeolocatorNotificationManager extends Service {

    private static final String TAG = "GeolocatorNotificationManager";
    private static final String PACKAGE_NAME = "com.google.android.gms.location.sample.locationupdatesforegroundservice";
    private static final String EXTRA_STARTED_FROM_NOTIFICATION = "$PACKAGE_NAME.started_from_notification";

    public class LocalBinder extends Binder {
        GeolocatorNotificationManager getService() {
            return GeolocatorNotificationManager.this;
        }
    }

    private final IBinder mBinder = new LocalBinder();

    @Nullable private NotificationManager mNotificationManager;
    @Nullable private Handler mServiceHandler;

    private boolean isStarted = false;

    BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive (Context context, Intent intent) {
            if (intent.getAction() == "stop_service") {
                removeLocationUpdates();
            }
        }
    };

    NotificationCompat.Builder notificationBuilder() {
        Intent intent = new Intent(this, getMainActivityClass(this));
        intent.putExtra(EXTRA_STARTED_FROM_NOTIFICATION, true);
        intent.setAction("Localisation");

        PendingIntent pendingIntent;

        //intent.setClass(this, getMainActivityClass(this))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            pendingIntent = PendingIntent.getActivity(this, 1, intent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        } else {
            pendingIntent = PendingIntent.getActivity(this, 1, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        }

        int iconID = getResources().getIdentifier("@mipmap/ic_launcher", "mipmap", this.getPackageName());

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, "BackgroundLocation")
                .setSmallIcon(iconID)
                .setLargeIcon(android.graphics.BitmapFactory.decodeResource(getResources(), iconID))
                .setContentTitle("Background Service")
                .setContentText("Prevents app from sleeping")
                .setOngoing(true)
                .setSound(null)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setWhen(System.currentTimeMillis())
                .setContentIntent(pendingIntent);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder.setChannelId("channel_01");
        }

        return builder;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public void onCreate() {
        HandlerThread handlerThread = new HandlerThread(GeolocatorNotificationManager.TAG);
        handlerThread.start();
        this.mServiceHandler = new Handler(handlerThread.getLooper());

        this.mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            String name = "com.lapti.GeolocatorNotificationManager";
            NotificationChannel mChannel = new NotificationChannel("channel_01", name, NotificationManager.IMPORTANCE_LOW);
            mChannel.setSound(null, null);
            this.mNotificationManager.createNotificationChannel(mChannel);
        }

        IntentFilter filter = new IntentFilter();
        filter.addAction("stop_service");
        registerReceiver(this.broadcastReceiver, filter);

        updateNotification();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        isStarted = false;
        unregisterReceiver(this.broadcastReceiver);
        try {
            mNotificationManager.cancel(12345678);
        } catch (Exception e) { }
    }

    void updateNotification() {
        if (!isStarted) {
            isStarted = true;
            startForeground(12345678, notificationBuilder().build());
        } else {
            NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            notificationManager.notify(12345678, notificationBuilder().build());
        }
    }

    void removeLocationUpdates() {
        stopForeground(true);
        stopSelf();
    }

    private Class getMainActivityClass(Context context) {
        String packageName = context.getPackageName();
        Intent launchIntent = context.getPackageManager().getLaunchIntentForPackage(packageName);

        try {
            String className = launchIntent.getComponent().getClassName();
            return Class.forName(className);
        } catch (Exception e) {
            return null;
        }
    }
}
