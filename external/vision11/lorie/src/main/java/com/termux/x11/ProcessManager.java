package com.termux.x11;

import android.util.Log;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Linux Process Manager
 * Handles execution and monitoring of shell commands within Termux environment
 */
public class ProcessManager {
    private static final String TAG = "ProcessManager";
    
    private Process currentProcess;
    private final List<ProcessListener> listeners = new ArrayList<>();
    private AtomicBoolean isProcessRunning = new AtomicBoolean(false);
    
    public interface ProcessListener {
        void onProcessStarted();
        void onProcessOutput(String line);
        void onProcessError(String line);
        void onProcessFinished(int exitCode);
        void onProcessCrashed(Exception e);
    }
    
    /**
     * Execute a shell command
     */
    public boolean executeCommand(String command) {
        return executeCommand(command, null);
    }
    
    /**
     * Execute a shell command with environment variables
     */
    public boolean executeCommand(String command, java.util.Map<String, String> environment) {
        if (isProcessRunning.getAndSet(true)) {
            Log.w(TAG, "A process is already running");
            return false;
        }
        
        try {
            Log.d(TAG, "Executing command: " + command);
            
            ProcessBuilder pb = new ProcessBuilder("sh", "-c", command);
            pb.redirectErrorStream(false);
            
            if (environment != null) {
                pb.environment().putAll(environment);
            }
            
            // Add Termux environment variables
            pb.environment().put("PATH", "/data/data/com.termux/files/usr/bin:" + 
                                         "/data/data/com.termux/files/usr/sbin:" +
                                         pb.environment().getOrDefault("PATH", ""));
            pb.environment().put("HOME", "/data/data/com.termux/files/home");
            pb.environment().put("PREFIX", "/data/data/com.termux/files/usr");
            
            currentProcess = pb.start();
            
            notifyListeners(listener -> listener.onProcessStarted());
            monitorProcess();
            
            return true;
            
        } catch (Exception e) {
            Log.e(TAG, "Error executing command", e);
            isProcessRunning.set(false);
            notifyListeners(listener -> listener.onProcessCrashed(e));
            return false;
        }
    }
    
    /**
     * Monitor process output and completion
     */
    private void monitorProcess() {
        new Thread(() -> {
            try {
                if (currentProcess == null) return;
                
                // Read stdout
                Thread stdoutThread = new Thread(() -> {
                    try {
                        BufferedReader reader = new BufferedReader(
                            new InputStreamReader(currentProcess.getInputStream()));
                        String line;
                        while ((line = reader.readLine()) != null && isProcessRunning.get()) {
                            Log.d(TAG, "[STDOUT] " + line);
                            notifyListeners(listener -> listener.onProcessOutput(line));
                        }
                        reader.close();
                    } catch (Exception e) {
                        Log.e(TAG, "Error reading stdout", e);
                    }
                });
                
                // Read stderr
                Thread stderrThread = new Thread(() -> {
                    try {
                        BufferedReader reader = new BufferedReader(
                            new InputStreamReader(currentProcess.getErrorStream()));
                        String line;
                        while ((line = reader.readLine()) != null && isProcessRunning.get()) {
                            Log.w(TAG, "[STDERR] " + line);
                            notifyListeners(listener -> listener.onProcessError(line));
                        }
                        reader.close();
                    } catch (Exception e) {
                        Log.e(TAG, "Error reading stderr", e);
                    }
                });
                
                stdoutThread.start();
                stderrThread.start();
                
                // Wait for process completion
                int exitCode = currentProcess.waitFor();
                Log.i(TAG, "Process finished with exit code: " + exitCode);
                
                isProcessRunning.set(false);
                notifyListeners(listener -> listener.onProcessFinished(exitCode));
                
                stdoutThread.join(1000);
                stderrThread.join(1000);
                
            } catch (Exception e) {
                Log.e(TAG, "Error monitoring process", e);
                isProcessRunning.set(false);
                notifyListeners(listener -> listener.onProcessCrashed(e));
            }
        }).start();
    }
    
    /**
     * Kill the current process
     */
    public void killProcess() {
        try {
            if (currentProcess != null) {
                Log.i(TAG, "Killing process");
                currentProcess.destroy();
                
                // Wait a bit, then force kill if still alive
                if (!currentProcess.waitFor(2, java.util.concurrent.TimeUnit.SECONDS)) {
                    Log.w(TAG, "Process did not terminate gracefully, force killing");
                    currentProcess.destroyForcibly();
                }
                
                isProcessRunning.set(false);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error killing process", e);
        }
    }
    
    /**
     * Kill process by name
     */
    public static boolean killProcessByName(String processName) {
        try {
            String[] cmd = {"pkill", "-f", processName};
            Process p = new ProcessBuilder(cmd).start();
            int exitCode = p.waitFor();
            Log.i(TAG, "Killed process '" + processName + "' with exit code: " + exitCode);
            return exitCode == 0;
        } catch (Exception e) {
            Log.e(TAG, "Error killing process by name", e);
            return false;
        }
    }
    
    /**
     * Check if a process is running
     */
    public static boolean isProcessRunning(String processName) {
        try {
            String[] cmd = {"pgrep", "-f", processName};
            Process p = new ProcessBuilder(cmd).start();
            int exitCode = p.waitFor();
            return exitCode == 0;
        } catch (Exception e) {
            Log.e(TAG, "Error checking process", e);
            return false;
        }
    }
    
    /**
     * Check if current process is running
     */
    public boolean isRunning() {
        return isProcessRunning.get() && currentProcess != null && currentProcess.isAlive();
    }
    
    /**
     * Add a process listener
     */
    public void addListener(ProcessListener listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener);
        }
    }
    
    /**
     * Remove a process listener
     */
    public void removeListener(ProcessListener listener) {
        listeners.remove(listener);
    }
    
    /**
     * Notify all listeners
     */
    private void notifyListeners(java.util.function.Consumer<ProcessListener> action) {
        for (ProcessListener listener : new ArrayList<>(listeners)) {
            try {
                action.accept(listener);
            } catch (Exception e) {
                Log.e(TAG, "Error notifying listener", e);
            }
        }
    }
    
    /**
     * Get the current process (for advanced usage)
     */
    public Process getProcess() {
        return currentProcess;
    }
}
