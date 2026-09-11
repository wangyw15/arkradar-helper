package me.nanip.arkradarhelper.accessibility

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import android.widget.Toast
import me.nanip.arkradarhelper.R

/**
 * 无障碍权限助手：负责无障碍服务的注册协作与授权状态查询。
 *
 * 注册本身由系统完成（见 AndroidManifest 中的 service 声明与
 * res/xml/accessibility_service_config.xml 配置），本类提供：
 * - [isAccessibilityEnabled]：查询本应用的无障碍服务是否已被用户授权
 * - [openAccessibilitySettings]：跳转到系统无障碍设置页，引导用户开启
 * - [service]：系统绑定成功后持有服务实例，供后续自动点击逻辑调用
 */
object AccessibilityHelper {

    /** 目标应用包名：方舟雷达 */
    const val TARGET_PACKAGE = "com.linktech.arkradar"

//    /**
//     * 待执行的自动打招呼标志。
//     * 界面层置位后拉起目标应用，服务检测到目标窗口出现后消费该标志并导航至好友列表。
//     */
//    @Volatile
//    var autoGreetingPending = false

    /**
     * 当前已连接的无障碍服务实例。
     * 为 null 表示服务未运行（未授权或已被系统回收）。
     */
    @Volatile
    var service: ArkRadarAccessibilityService? = null
        internal set

    /**
     * 标识无障碍权限是否已授权。
     *
     * 通过 AccessibilityManager 查询系统已启用的无障碍服务列表，
     * 判断其中是否包含本应用包名下的服务。
     *
     * @return true = 已授权且服务已启用；false = 未授权
     */
    fun isAccessibilityEnabled(context: Context): Boolean {
        val manager =
            context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabledServices =
            manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        return enabledServices.any { info ->
            info.resolveInfo.serviceInfo.packageName == context.packageName
        }
    }

    /**
     * 跳转到系统无障碍设置页，引导用户手动开启本应用的无障碍服务。
     */
    fun openAccessibilitySettings(context: Context) {
        Toast.makeText(
            context,
            R.string.grant_a11y_permission,
            Toast.LENGTH_SHORT
        ).show()

        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    /**
     * 请求开始自动打招呼。
     *
     * 若服务已连接则立即导航至好友列表；否则置位 [service?.autoGreetingPending]，
     * 由服务在检测到目标应用窗口出现后导航。
     */
    fun requestAutoGreeting() {
        service?.autoGreetingPending = true
    }

    /**
     * 拉起目标应用（方舟雷达）。
     *
     * @return true = 已发起启动；false = 目标应用未安装
     */
    fun launchTargetApp(context: Context): Boolean {
        val intent = context.packageManager.getLaunchIntentForPackage(TARGET_PACKAGE)
            ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        return true
    }
}
