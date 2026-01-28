package com.example.ourmemories.Utils

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import java.util.Locale

object AutoStartPermissionHelper {

    /**
     * Пытается найти и вернуть Intent для открытия меню автозапуска/батареи
     * в зависимости от производителя телефона.
     */
    fun getAutoStartPermissionIntent(context: Context): Intent? {
        val manufacturer = Build.MANUFACTURER.lowercase(Locale.getDefault())

        val intents = when {
            manufacturer.contains("xiaomi") -> xiaomiIntents(context)
            manufacturer.contains("redmi") -> xiaomiIntents(context)
            manufacturer.contains("oppo") -> listOf(
                Intent().setComponent(
                    ComponentName(
                        "com.coloros.safecenter",
                        "com.coloros.safecenter.permission.startup.StartupAppListActivity"
                    )
                ), Intent().setComponent(
                    ComponentName(
                        "com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity"
                    )
                ), Intent().setComponent(
                    ComponentName(
                        "com.coloros.safecenter",
                        "com.coloros.safecenter.startupapp.StartupAppListActivity"
                    )
                )
            )

            manufacturer.contains("vivo") -> listOf(
                Intent().setComponent(
                    ComponentName(
                        "com.vivo.permissionmanager",
                        "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"
                    )
                ), Intent().setComponent(
                    ComponentName(
                        "com.iqoo.secure", "com.iqoo.safe.ui.manager.StartupManager"
                    )
                )
            )

            manufacturer.contains("honor") || manufacturer.contains("huawei") -> listOf(
                Intent().setComponent(
                    ComponentName(
                        "com.huawei.systemmanager",
                        "com.huawei.systemmanager.optimize.process.ProtectActivity"
                    )
                ), Intent().setComponent(
                    ComponentName(
                        "com.huawei.systemmanager",
                        "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"
                    )
                )
            )

            manufacturer.contains("asus") -> listOf(
                Intent().setComponent(
                    ComponentName(
                        "com.asus.mobilemanager",
                        "com.asus.mobilemanager.autostart.AutoStartActivity"
                    )
                ), Intent().setComponent(
                    ComponentName(
                        "com.asus.mobilemanager", "com.asus.mobilemanager.entry.FunctionActivity"
                    )
                )
            )

            manufacturer.contains("oneplus") -> listOf(
                Intent().setComponent(
                    ComponentName(
                        "com.oneplus.security",
                        "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity"
                    )
                )
            )
            manufacturer.contains("samsung") -> listOf(
                Intent().setComponent(
                    ComponentName(
                        "com.samsung.android.lool",
                        "com.samsung.android.sm.ui.battery.BatteryActivity"
                    )
                ), Intent().setComponent(
                    ComponentName(
                        "com.samsung.android.sm",
                        "com.samsung.android.sm.ui.battery.BatteryActivity"
                    )
                ), Intent().setComponent(
                    ComponentName(
                        "com.samsung.android.lool",
                        "com.samsung.android.sm.battery.ui.BatteryActivity"
                    )
                )
            )
            manufacturer.contains("meizu") -> listOf(
                Intent().setComponent(
                    ComponentName(
                        "com.meizu.safe", "com.meizu.safe.permission.SmartBGActivity"
                    )
                )
            )

            else -> emptyList()
        }

        for (intent in intents) {
            if (isCallable(context, intent)) {
                return intent
            }
        }
        return null
    }

    private fun xiaomiIntents(context: Context): List<Intent> {
        return listOf(
            Intent().setComponent(
                ComponentName(
                    "com.miui.securitycenter",
                    "com.miui.permcenter.autostart.AutoStartManagementActivity"
                )
            ), Intent().setComponent(
                ComponentName(
                    "com.miui.securitycenter",
                    "com.miui.permcenter.autostart.AutoStartManagementActivityV2"
                )
            )
        )
    }

    private fun isCallable(context: Context, intent: Intent): Boolean {
        return try {
            context.packageManager.resolveActivity(
                intent, PackageManager.MATCH_DEFAULT_ONLY
            ) != null
        } catch (e: Exception) {
            false
        }
    }
}