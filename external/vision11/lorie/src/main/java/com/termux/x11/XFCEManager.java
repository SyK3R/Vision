package com.termux.x11;

import android.content.Context;
import android.util.Log;
import android.os.Handler;
import android.os.Looper;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * XFCE Desktop Environment Manager
 * Handles automatic launch and management of XFCE session on top of Termux:X11
 */
public class XFCEManager {
    private static final String TAG = "XFCEManager";
    private static XFCEManager instance;
    private final Context context;
    private final Handler mainHandler;
    
    // Process management
    private Process xfceProcess;
    private Process x11Process;
    private AtomicBoolean isRunning = new AtomicBoolean(false);
    private AtomicBoolean isAutoRestarting = new AtomicBoolean(false);
    
    // Configuration
    private String desktopEnvironment = "xfce"; // xfce, kde, gnome
    private String displayNumber = ":1";
    private int restartDelayMs = 3000;
    private static final int MAX_RESTART_ATTEMPTS = 5;
    private int restartAttempts = 0;
    
    private XFCEManager(Context context) {
        this.context = context;
        this.mainHandler = new Handler(Looper.getMainLooper());
    }
    
    public static synchronized XFCEManager getInstance(Context context) {
        if (instance == null) {
            instance = new XFCEManager(context.getApplicationContext());
        }
        return instance;
    }
    
    /**
     * Start X11 server and XFCE session automatically
     */
    public void startXFCESession() {
        if (isRunning.getAndSet(true)) {
            Log.d(TAG, "XFCE session already running");
            return;
        }
        
        restartAttempts = 0;
        Log.i(TAG, "Starting XFCE session with " + desktopEnvironment);
        
        new Thread(() -> {
            try {
                // Start X11 server first
                if (!startX11Server()) {
                    Log.e(TAG, "Failed to start X11 server");
                    isRunning.set(false);
                    return;
                }
                
                // Wait for X11 socket to be ready
                if (!waitForX11Socket(5000)) {
                    Log.e(TAG, "X11 socket not ready after timeout");
                    stopX11Server();
                    isRunning.set(false);
                    return;
                }
                
                // Start desktop environment
                if (!startDesktopEnvironment()) {
                    Log.e(TAG, "Failed to start desktop environment");
                    stopX11Server();
                    isRunning.set(false);
                    return;
                }
                
                Log.i(TAG, "XFCE session started successfully");
                
            } catch (Exception e) {
                Log.e(TAG, "Error starting XFCE session", e);
                isRunning.set(false);
            }
        }).start();
    }
    
    /**
     * Start X11 server (termux-x11)
     */
    private boolean startX11Server() {
        try {
            String[] cmd = {
                "sh", "-c",
                "termux-x11 " + displayNumber + " -xstartup '/bin/true' &"
            };
            
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            x11Process = pb.start();
            
            Log.i(TAG, "X11 server started");
            return true;
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to start X11 server", e);
            return false;
        }
    }
    
    /**
     * Wait for X11 socket to become available
     */
    private boolean waitForX11Socket(long timeoutMs) {
        long startTime = System.currentTimeMillis();
        
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            try {
                // Check if X11 socket exists
                ProcessBuilder pb = new ProcessBuilder("sh", "-c", 
                    "test -S /tmp/.X11-unix/X" + displayNumber.substring(1) + " && echo 'ready'");
                pb.redirectErrorStream(true);
                Process p = pb.start();
                
                BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
                String line = reader.readLine();
                reader.close();
                
                int exitCode = p.waitFor();
                if (exitCode == 0 && line != null && line.contains("ready")) {
                    Log.d(TAG, "X11 socket is ready");
                    return true;
                }
                
                Thread.sleep(500);
                
            } catch (Exception e) {
                Log.d(TAG, "Waiting for X11 socket...");
            }
        }
        
        return false;
    }
    
    /**
     * Start desktop environment session
     */
    private boolean startDesktopEnvironment() {
        try {
            String sessionCommand = buildSessionCommand();
            
            String[] cmd = {
                "sh", "-c",
                "export DISPLAY=" + displayNumber + "; " +
                "export PATH=$PATH:/data/data/com.termux/files/usr/bin; " +
                sessionCommand
            };
            
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            pb.environment().put("DISPLAY", displayNumber);
            
            xfceProcess = pb.start();
            
            // Monitor process in background
            monitorProcess();
            
            Log.i(TAG, "Desktop environment (" + desktopEnvironment + ") started");
            return true;
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to start desktop environment", e);
            return false;
        }
    }
    
    /**
     * Build the session command based on selected DE
     */
    private String buildSessionCommand() {
        switch (desktopEnvironment.toLowerCase()) {
            case "kde":
                return "dbus-launch --exit-with-session startplasma-x11";
            case "gnome":
                return "dbus-launch --exit-with-session gnome-session";
            case "xfce":
            default:
                return "dbus-launch --exit-with-session xfce4-session";
        }
    }
    
    /**
     * Monitor process and restart if it crashes
     */
    private void monitorProcess() {
        new Thread(() -> {
            try {
                if (xfceProcess != null) {
                    int exitCode = xfceProcess.waitFor();
                    Log.w(TAG, "Desktop environment exited with code: " + exitCode);
                    
                    // Try to restart if it wasn't intentionally stopped
                    if (isRunning.get() && !isAutoRestarting.getAndSet(true)) {
                        if (restartAttempts < MAX_RESTART_ATTEMPTS) {
                            restartAttempts++;
                            Log.i(TAG, "Attempting to restart XFCE (attempt " + restartAttempts + 
                                    "/" + MAX_RESTART_ATTEMPTS + ")");
                            
                            mainHandler.postDelayed(() -> {
                                isAutoRestarting.set(false);
                                if (isRunning.get()) {
                                    startDesktopEnvironment();
                                }
                            }, restartDelayMs);
                        } else {
                            Log.e(TAG, "Max restart attempts reached");
                            isRunning.set(false);
                        }
                    }
                }
            } catch (InterruptedException e) {
                Log.d(TAG, "Process monitoring interrupted", e);
            }
        }).start();
    }
    
    /**
     * Stop X11 server
     */
    private void stopX11Server() {
        try {
            if (x11Process != null) {
                x11Process.destroy();
                x11Process.waitFor();
                Log.i(TAG, "X11 server stopped");
            }
            
            // Also kill any remaining termux-x11 processes
            ProcessBuilder pb = new ProcessBuilder("pkill", "-f", "termux-x11");
            pb.start().waitFor();
            
        } catch (Exception e) {
            Log.e(TAG, "Error stopping X11 server", e);
        }
    }
    
    /**
     * Stop XFCE session completely
     */
    public void stopXFCESession() {
        Log.i(TAG, "Stopping XFCE session");
        isRunning.set(false);
        isAutoRestarting.set(false);
        
        new Thread(() -> {
            try {
                // Kill desktop environment process
                if (xfceProcess != null) {
                    xfceProcess.destroy();
                    xfceProcess.waitFor();
                    xfceProcess = null;
                }
                
                // Stop X11
                stopX11Server();
                
                Log.i(TAG, "XFCE session stopped");
                
            } catch (Exception e) {
                Log.e(TAG, "Error stopping XFCE session", e);
            }
        }).start();
    }
    
    /**
     * Set desktop environment to use
     */
    public void setDesktopEnvironment(String de) {
        this.desktopEnvironment = de;
        Log.d(TAG, "Desktop environment set to: " + de);
    }
    
    /**
     * Set X display number
     */
    public void setDisplayNumber(String display) {
        this.displayNumber = display;
        Log.d(TAG, "Display number set to: " + display);
    }
    
    /**
     * Check if XFCE session is running
     */
    public boolean isSessionRunning() {
        return isRunning.get();
    }
    
    /**
     * Get current desktop environment
     */
    public String getDesktopEnvironment() {
        return desktopEnvironment;
    }
    
    /**
     * Get restart attempts count
     */
    public int getRestartAttempts() {
        return restartAttempts;
    }
}
