/*
 * Copyright (c) 2021 Salt Edge Inc.
 */
package com.saltedge.authenticator.core.tools

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat

/**
 * Build application and device info:
 * e.g.: Salt Edge Authenticator / 2.3.0(39); StandaloneInstall; (Xiaomi; Redmi Note 8; SDK 28)
 */
fun buildUserAgent(context: Context): String {
    with(context) {
        val versionName = try {
            this.getPackageInfo().versionName
        } catch (e: PackageManager.NameNotFoundException) {
            "nameNotFound"
        }
        val versionCode = try {
            PackageInfoCompat.getLongVersionCode(this.getPackageInfo()).toInt().toString()
        } catch (e: PackageManager.NameNotFoundException) {
            "versionCodeNotFound"
        }

        val applicationInfo = this.applicationInfo
        val appNameResId = applicationInfo.labelRes
        val appName =
            if (appNameResId == 0) applicationInfo.nonLocalizedLabel.toString()
            else context.getString(appNameResId)

        val manufacturer = Build.MANUFACTURER
        val model = Build.MODEL
        val version = Build.VERSION.RELEASE

        val installerName = this.getInstallerPackageName() ?: "StandaloneInstall"

        val userAgentValue = "$appName; $versionName($versionCode); $installerName;" +
            " $manufacturer; $model; Android $version"

        return userAgentValue.map { c ->
            if ((c <= '\u001f' && c != '\t') || c >= '\u007f') {
                ' '
            } else {
                c
            }
        }.joinToString("")
    }
}

@Suppress("DEPRECATION")
fun Context.getPackageInfo(): PackageInfo {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
    } else {
        packageManager.getPackageInfo(packageName, 0)
    }
}

@Suppress("DEPRECATION")
fun Context.getInstallerPackageName(): String? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        this.packageManager.getInstallSourceInfo(this.packageName).installingPackageName
    } else {
        this.packageManager.getInstallerPackageName(this.packageName)
    }
}
