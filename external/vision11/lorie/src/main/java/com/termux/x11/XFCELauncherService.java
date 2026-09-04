package com.termux.x11;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

/**
 * XFCE Launcher Foreground Service
 * Keeps XFCE session running even when app is backgrounded
 */
public class XFCELauncherService extends Service {
    private static final String TAG = "XFCELauncherService";
    private static final int NOTIFICATION_ID = 7893;
    private static final String CHANNEL_ID = "xfce_launcher";
    
    private final IBinder binder = new LocalBinder();
    private XFCEManager xfceManager;
    private ProcessManager processManager;
    
    public class LocalBinder extends Binder {
        XFCELauncherService getService() {
            return XFCELauncherService.this;
        }
    }
    
    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Service created");
        
        xfceManager = XFCEManager.getInstance(this);
        processManager = new ProcessManager();
        
        createNotificationChannel();
    }
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "Service started");
        
        if (intent != null) {
            String action = intent.getAction();
            
            if ("START_XFCE".equals(action)) {
                String desktopEnv = intent.getStringExtra("desktop_env");
                if (desktopEnv != null) {
                    xfceManager.setDesktopEnvironment(desktopEnv);
                }
                startXFCE();
            } else if ("STOP_XFCE".equals(action)) {
                stopXFCE();
            }
        }
        
        // Keep service running
        startForeground(NOTIFICATION_ID, buildNotification());
        return START_STICKY;
    }
    
    /**
     * Start XFCE session
     */
    private void startXFCE() {
        Log.i(TAG, "Starting XFCE from service");
        xfceManager.startXFCESession();
    }
    
    /**
     * Stop XFCE session
     */
    private void stopXFCE() {
        Log.i(TAG, "Stopping XFCE from service");
        xfceManager.stopXFCESession();
    }
    
    /**
     * Create notification channel for foreground service
     */
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            android.app.NotificationChannel channel = new android.app.NotificationChannel(
                CHANNEL_ID,
                "XFCE Launcher",
                android.app.NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("XFCE Desktop Environment Service");
            
            android.app.NotificationManager manager = getSystemService(android.app.NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }
    
    /**
     * Build foreground notification
     */
    private android.app.Notification buildNotification() {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("XFCE Desktop Environment")
            .setContentText("Running " + xfceManager.getDesktopEnvironment() + " session...")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build();
    }
    
    @Override
    public void onDestroy() {
        Log.d(TAG, "Service destroyed");
        xfceManager.stopXFCESession();
        super.onDestroy();
    }
    
    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }
    
    /**
     * Public method to start XFCE service
     */
    public static void startService(Context context, String desktopEnv) {
        Intent intent = new Intent(context, XFCELauncherService.class);
        intent.setAction("START_XFCE");
        intent.putExtra("desktop_env", desktopEnv);
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
    }
    
    /**
     * Public method to stop XFCE service
     */
    public static void stopService(Context context) {
        Intent intent = new Intent(context, XFCELauncherService.class);
        intent.setAction("STOP_XFCE");
        context.startService(intent);
        context.stopService(intent);
    }
    
    /**
     * Get XFCE Manager instance
     */
    public XFCEManager getXFCEManager() {
        return xfceManager;
    }
    
    /**
     * Get Process Manager instance
     */
    public ProcessManager getProcessManager() {
        return processManager;
    }
}
