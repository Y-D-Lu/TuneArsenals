package cn.arsenals.utils

import android.content.Context
import cn.arsenals.library.basic.AccessibleServiceState
import cn.arsenals.library.shell.AccessibilityServiceUtils
import cn.arsenals.tunearsenals.AccessibilityTuneArsenals

/**
 * Created by Hello on 2018/06/03.
 */

class AccessibleServiceHelper {
    // 场景模式服务是否正在运行
    fun serviceRunning(context: Context): Boolean {
        return AccessibleServiceState().serviceRunning(context, "AccessibilityTuneArsenals")
    }

    // 停止场景模式服务
    fun stopTuneArsenalsModeService(context: Context): Boolean {
        return AccessibilityServiceUtils().stopService("${context.packageName}/${AccessibilityTuneArsenals::class.java.name}")
    }

    fun serviceRunning(context: Context, serviceName: String): Boolean {
        return AccessibleServiceState().serviceRunning(context, serviceName)
    }
}
