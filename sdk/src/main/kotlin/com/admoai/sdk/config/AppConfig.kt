package com.admoai.sdk.config

import com.admoai.sdk.core.Clearable
import android.content.Context
import android.content.pm.PackageManager

/**
 * Configuration related to the application.
 * Contains information about the app that can be used for tracking and targeting purposes.
 *
 * @property appName The name of the application
 * @property appVersion The version of the application (e.g., "1.0.0")
 * @property packageName The package name of the application (equivalent to bundleId on iOS)
 * @property buildNumber The build number of the application
 * @property language The default language of the application
 */
data class AppConfig(
    val appName: String? = null, // e.g., "AdMoai SDK Example"
    val appVersion: String? = null, // e.g., "1.0.0"
    val packageName: String? = null, // e.g., "com.admoai.sdk.example"
    val buildNumber: String? = null, // e.g., "42"
    val language: String? = null // e.g., "en-US"
) : Clearable<AppConfig> {

    /**
     * Returns a new instance with all properties set to their defaults (null).
     */
    override fun resetToDefaults(): AppConfig = AppConfig()

    /**
     * Returns a new instance with all properties set to null.
     */
    override fun clear(): AppConfig = AppConfig()
    
    companion object {
        /**
         * Creates an AppConfig with system default values automatically populated from the application context.
         * 
         * This factory method extracts app information such as name, version, and package name
         * from the Android Context.
         */
        @JvmStatic
        fun systemDefault(context: Context? = null): AppConfig {
            if (context == null) return AppConfig()
            
            try {
                val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
                val appName = context.packageManager.getApplicationLabel(context.applicationInfo).toString()
                val appVersion = packageInfo.versionName
                val packageName = context.packageName
                
                return AppConfig(
                    appName = appName,
                    appVersion = appVersion,
                    packageName = packageName
                )
            } catch (e: PackageManager.NameNotFoundException) {
                // Return default values if package info can't be retrieved
                return AppConfig()
            }
        }
    }
}
