package com.baseflow.geolocator;

import android.app.Activity;
import android.content.Context;
import android.location.Location;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.baseflow.geolocator.errors.ErrorCodes;
import com.baseflow.geolocator.errors.PermissionUndefinedException;
import com.baseflow.geolocator.location.FlutterLocationServiceListener;
import com.baseflow.geolocator.location.GeolocationManager;
import com.baseflow.geolocator.location.LocationAccuracyStatus;
import com.baseflow.geolocator.location.LocationAccuracyManager;
import com.baseflow.geolocator.location.LocationClient;
import com.baseflow.geolocator.location.LocationMapper;
import com.baseflow.geolocator.location.LocationOptions;
import com.baseflow.geolocator.permission.LocationPermission;
import com.baseflow.geolocator.permission.PermissionManager;
import com.baseflow.geolocator.utils.Utils;

import io.flutter.plugin.common.BinaryMessenger;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import java.util.Map;

import android.content.ServiceConnection;
import android.content.Context;
import android.os.Build;
import android.app.Service;
import android.os.Binder;
import android.os.IBinder;
import android.content.Intent;
import android.os.Handler;
import android.os.HandlerThread;
import android.content.ComponentName;

/**
 * Translates incoming Geolocator MethodCalls into well formed Java function calls for {@link
 * GeolocationManager}.
 */
class MethodCallHandlerImpl implements MethodChannel.MethodCallHandler {

  private static final String TAG = "MethodCallHandlerImpl";
  private final PermissionManager permissionManager;
  private final GeolocationManager geolocationManager;
  private final LocationAccuracyManager locationAccuracyManager;

  @Nullable private GeolocatorNotificationManager service;
  private boolean bound;
  private ServiceConnection serviceConnection = new ServiceConnection() {
      public void onServiceConnected(ComponentName className, IBinder binder) {
        bound = true;
        service = ((GeolocatorNotificationManager.LocalBinder)binder).getService();
      }

      public void onServiceDisconnected(ComponentName className) {
          service = null;
          bound = false;
      }
  };

  @Nullable private Context context;

  @Nullable private Activity activity;

  MethodCallHandlerImpl(
      PermissionManager permissionManager,
      GeolocationManager geolocationManager,
      LocationAccuracyManager locationAccuracyManager) {
    this.permissionManager = permissionManager;
    this.geolocationManager = geolocationManager;
    this.locationAccuracyManager = locationAccuracyManager;
  }

  @Nullable private MethodChannel channel;

  @Override
  public void onMethodCall(@NonNull MethodCall call, @NonNull MethodChannel.Result result) {
    switch (call.method) {
      case "checkPermission":
        onCheckPermission(result);
        break;
      case "isLocationServiceEnabled":
        onIsLocationServiceEnabled(result);
        break;
      case "requestPermission":
        onRequestPermission(result);
        break;
      case "getLastKnownPosition":
        onGetLastKnownPosition(call, result);
        break;
      case "getLocationAccuracy":
        getLocationAccuracy(result, this.context);
        break;
      case "getCurrentPosition":
        onGetCurrentPosition(call, result);
        break;
      case "openAppSettings":
        boolean hasOpenedAppSettings = Utils.openAppSettings(this.context);
        result.success(hasOpenedAppSettings);
        break;
      case "openLocationSettings":
        boolean hasOpenedLocationSettings = Utils.openLocationSettings(this.context);
        result.success(hasOpenedLocationSettings);
        break;
      default:
        result.notImplemented();
        break;
    }
  }

  /**
   * Registers this instance as a method call handler on the given {@code messenger}.
   *
   * <p>Stops any previously started and unstopped calls.
   *
   * <p>This should be cleaned with {@link #stopListening} once the messenger is disposed of.
   */
  void startListening(Context context, BinaryMessenger messenger) {
    if (channel != null) {
      Log.w(TAG, "Setting a method call handler before the last was disposed.");
      stopListening();
    }

    channel = new MethodChannel(messenger, "flutter.baseflow.com/geolocator");
    channel.setMethodCallHandler(this);
    this.context = context;

    startNotificationService();
  }

  /**
   * Clears this instance from listening to method calls.
   *
   * <p>Does nothing if {@link #startListening} hasn't been called, or if we're already stopped.
   */
  void stopListening() {
    if (channel == null) {
      Log.d(TAG, "Tried to stop listening when no MethodChannel had been initialized.");
      return;
    }

    channel.setMethodCallHandler(null);
    channel = null;

    stopNotificationService();
  }

  void setActivity(@Nullable Activity activity) {
    this.activity = activity;
  }

  private void onCheckPermission(MethodChannel.Result result) {
    try {
      LocationPermission permission = this.permissionManager.checkPermissionStatus(context);
      result.success(permission.toInt());
    } catch (PermissionUndefinedException e) {
      ErrorCodes errorCode = ErrorCodes.permissionDefinitionsNotFound;
      result.error(errorCode.toString(), errorCode.toDescription(), null);
    }
  }

  private void onIsLocationServiceEnabled(MethodChannel.Result result) {
    geolocationManager.isLocationServiceEnabled(
        context, new FlutterLocationServiceListener(result));
  }

  private void onRequestPermission(MethodChannel.Result result) {
    try {
      this.permissionManager.requestPermission(
          this.activity,
          (LocationPermission permission) -> result.success(permission.toInt()),
          (ErrorCodes errorCode) ->
              result.error(errorCode.toString(), errorCode.toDescription(), null));
    } catch (PermissionUndefinedException e) {
      ErrorCodes errorCode = ErrorCodes.permissionDefinitionsNotFound;
      result.error(errorCode.toString(), errorCode.toDescription(), null);
    }
  }

  private void getLocationAccuracy(MethodChannel.Result result, Context context) {
    final LocationAccuracyStatus status =
        this.locationAccuracyManager.getLocationAccuracy(
            context,
            (ErrorCodes errorCode) ->
                result.error(errorCode.toString(), errorCode.toDescription(), null));
    if (result != null) {
      result.success(status.ordinal());
    }
  }

  private void onGetLastKnownPosition(MethodCall call, MethodChannel.Result result) {
    Boolean forceLocationManager = call.argument("forceAndroidLocationManager");

    this.geolocationManager.getLastKnownPosition(
        this.context,
        this.activity,
        forceLocationManager != null && forceLocationManager,
        (Location location) -> result.success(LocationMapper.toHashMap(location)),
        (ErrorCodes errorCode) ->
            result.error(errorCode.toString(), errorCode.toDescription(), null));
  }

  private void startNotificationService() {
    if (bound) { return; }

    Intent intent = new Intent(this.context, GeolocatorNotificationManager.class);
    this.context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
  }

  private void stopNotificationService() {
    if (!bound) { return; }

    context.unbindService(serviceConnection);
    bound = false;
  }

  private void onGetCurrentPosition(MethodCall call, MethodChannel.Result result) {
    Boolean forceLocationManager = call.argument("forceAndroidLocationManager");
    @SuppressWarnings("unchecked")
    Map<String, Object> map = (Map<String, Object>) call.arguments;
    LocationOptions locationOptions = LocationOptions.parseArguments(map);
    final boolean[] replySubmitted = {false};

    LocationClient locationClient =
        geolocationManager.createLocationClient(
            this.context, forceLocationManager != null && forceLocationManager, locationOptions);

    geolocationManager.startPositionUpdates(
        this.context,
        this.activity,
        locationClient,
        (Location location) -> {
          if (replySubmitted[0]) {
            return;
          }

          replySubmitted[0] = true;
          geolocationManager.stopPositionUpdates(locationClient);
          result.success(LocationMapper.toHashMap(location));
        },
        (ErrorCodes errorCode) -> {
          if (replySubmitted[0]) {
            return;
          }

          replySubmitted[0] = true;
          geolocationManager.stopPositionUpdates(locationClient);
          result.error(errorCode.toString(), errorCode.toDescription(), null);
        });
  }
}
