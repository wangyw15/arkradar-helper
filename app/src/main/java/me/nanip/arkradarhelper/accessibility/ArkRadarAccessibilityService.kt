package me.nanip.arkradarhelper.accessibility

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.view.accessibility.AccessibilityEvent

/**
 * 方舟雷达自动点击无障碍服务。
 *
 * 服务本体：系统绑定成功后由 [onServiceConnected] 回调，
 * 自动点击等核心逻辑后续在 [onAccessibilityEvent] / 主动手势分发中实现。
 */
@SuppressLint("AccessibilityPolicy")
class ArkRadarAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        // 服务就绪后把实例交给 AccessibilityHelper，供界面层查询与调用
        AccessibilityHelper.service = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // TODO: 占位 —— 在此处理界面变化事件，驱动自动点击流程
    }

    override fun onInterrupt() {
        // TODO: 占位 —— 服务被系统中断时的清理逻辑
    }

    override fun onDestroy() {
        // 服务断开时清空引用，避免界面层误用失效实例
        if (AccessibilityHelper.service === this) {
            AccessibilityHelper.service = null
        }
        super.onDestroy()
    }
}
