package com.alarmise.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.alarmise.app.service.AlarmScheduler
import com.alarmise.app.utils.AlarmLogger
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Comprehensive BootReceiver handles device boot and app update scenarios
 * Critical for ensuring alarm persistence across device restarts
 * Implements the core requirement: alarms must work regardless of app state
 */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {
    
    @Inject
    lateinit var alarmScheduler: AlarmScheduler
    
    // Use a coroutine scope that survives the receiver's lifecycle
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    override fun onReceive(context: Context, intent: Intent) {
        // Use goAsync() to ensure we have time to complete async operations
        val pendingResult = goAsync()
        
        AlarmLogger.logSessionStart("Boot Receiver - ${intent.action}")
        
        try {
            when (intent.action) {
                Intent.ACTION_BOOT_COMPLETED -> {
                    AlarmLogger.logSystemEvent("Device Boot Completed", mapOf(
                        "packageName" to context.packageName,
                        "bootTime" to System.currentTimeMillis()
                    ))
                    handleBootCompleted(context, pendingResult)
                }
                
                Intent.ACTION_MY_PACKAGE_REPLACED -> {
                    AlarmLogger.logSystemEvent("App Package Replaced", mapOf(
                        "packageName" to context.packageName,
                        "replaceTime" to System.currentTimeMillis()
                    ))
                    handlePackageReplaced(context, pendingResult)
                }
                
                Intent.ACTION_PACKAGE_REPLACED -> {
                    val packageName = intent.dataString
                    if (packageName?.contains(context.packageName) == true) {
                        AlarmLogger.logSystemEvent("Package Replaced", mapOf(
                            "packageName" to packageName,
                            "replaceTime" to System.currentTimeMillis()
                        ))
                        handlePackageReplaced(context, pendingResult)
                    } else {
                        AlarmLogger.logDebug("Package Replaced", mapOf(
                            "packageName" to (packageName ?: "unknown"),
                            "reason" to "Not our package, ignoring"
                        ))
                        pendingResult.finish()
                    }
                }
                
                else -> {
                    AlarmLogger.logWarning("Boot Receiver", "Unexpected action received: ${intent.action}")
                    pendingResult.finish()
                }
            }
        } catch (e: Exception) {
            AlarmLogger.logError("Boot Receiver", null, e, mapOf(
                "action" to (intent.action ?: "unknown"),
                "packageName" to context.packageName
            ))
            pendingResult.finish()
        }
    }
    
    /**
     * Handle device boot completion
     * Critical: Must reschedule all alarms that were scheduled before reboot
     */
    private fun handleBootCompleted(context: Context, pendingResult: BroadcastReceiver.PendingResult) {
        scope.launch {
            try {
                AlarmLogger.logSystemEvent("Starting Boot Recovery", mapOf(
                    "deviceBootTime" to System.currentTimeMillis(),
                    "canScheduleExact" to alarmScheduler.canScheduleExactAlarms()
                ))
                
                // Check if we can schedule exact alarms
                if (!alarmScheduler.canScheduleExactAlarms()) {
                    AlarmLogger.logWarning("Boot Recovery", "Cannot schedule exact alarms - permission required")
                    // Continue anyway - some alarms might still work
                }
                
                // Reschedule all active alarms
                val result = alarmScheduler.rescheduleAllAlarms()
                
                if (result.isSuccess) {
                    val rescheduledCount = result.getOrNull() ?: 0
                    AlarmLogger.logSuccess("Boot Recovery", null, "Rescheduled $rescheduledCount alarms")
                    
                    // Log system state after recovery
                    val diagnostics = alarmScheduler.getDiagnostics()
                    AlarmLogger.logSystemEvent("Boot Recovery Complete", diagnostics)
                } else {
                    AlarmLogger.logError("Boot Recovery", null, 
                        result.exceptionOrNull() ?: Exception("Unknown error during reschedule"))
                }
                
                AlarmLogger.logSessionEnd("Boot Receiver - Boot Recovery", result.isSuccess)
                
            } catch (e: Exception) {
                AlarmLogger.logError("Boot Recovery", null, e)
                AlarmLogger.logSessionEnd("Boot Receiver - Boot Recovery", false)
            } finally {
                pendingResult.finish()
            }
        }
    }
    
    /**
     * Handle app package replacement (app update)
     * Need to reschedule alarms after app update
     */
    private fun handlePackageReplaced(context: Context, pendingResult: BroadcastReceiver.PendingResult) {
        scope.launch {
            try {
                AlarmLogger.logSystemEvent("Starting Package Replace Recovery", mapOf(
                    "packageName" to context.packageName,
                    "replaceTime" to System.currentTimeMillis()
                ))
                
                // Similar to boot recovery, but for app updates
                val result = alarmScheduler.rescheduleAllAlarms()
                
                if (result.isSuccess) {
                    val rescheduledCount = result.getOrNull() ?: 0
                    AlarmLogger.logSuccess("Package Replace Recovery", null, "Rescheduled $rescheduledCount alarms")
                } else {
                    AlarmLogger.logError("Package Replace Recovery", null, 
                        result.exceptionOrNull() ?: Exception("Unknown error during reschedule"))
                }
                
                AlarmLogger.logSessionEnd("Boot Receiver - Package Replace Recovery", result.isSuccess)
                
            } catch (e: Exception) {
                AlarmLogger.logError("Package Replace Recovery", null, e)
                AlarmLogger.logSessionEnd("Boot Receiver - Package Replace Recovery", false)
            } finally {
                pendingResult.finish()
            }
        }
    }
    
    /**
     * Validate system state after boot/update
     * Helps diagnose issues with alarm scheduling after recovery
     */
    private suspend fun validateSystemState(): Map<String, Any> {
        return try {
            val diagnostics = alarmScheduler.getDiagnostics()
            AlarmLogger.logSystemEvent("System State Validation", diagnostics)
            diagnostics
        } catch (e: Exception) {
            AlarmLogger.logError("System State Validation", null, e)
            mapOf(
                "error" to (e.message ?: "Unknown error"),
                "timestamp" to System.currentTimeMillis()
            )
        }
    }
}
