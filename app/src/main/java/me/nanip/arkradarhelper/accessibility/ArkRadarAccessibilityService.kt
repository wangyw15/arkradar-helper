package me.nanip.arkradarhelper.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.annotation.SuppressLint
import android.graphics.Path
import android.graphics.Rect
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.nanip.arkradarhelper.R
import kotlin.time.Duration.Companion.milliseconds

/**
 * 方舟雷达自动点击无障碍服务。
 *
 * 服务本体：系统绑定成功后由 [onServiceConnected] 回调。
 * 自动向下滚动：检测到目标应用窗口出现后启动循环，
 * 通过 [dispatchGesture] 周期性分发上滑手势（内容随之向下滚动）。
 */
@SuppressLint("AccessibilityPolicy")
class ArkRadarAccessibilityService : AccessibilityService() {

    var autoGreetingPending = false
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    @Volatile private var greetingJob: Job? = null

    suspend fun autoGreeting(): Boolean {
        var friendListContainer: AccessibilityNodeInfo?

        val root = rootInActiveWindow ?: return false

        Log.d(TAG, "跳转「同调网络」好友列表")
        if (!navigateToFriendList(root)) {
            return false
        }

        Log.d(TAG, "获取好友数量")
        val friendCount = getFriendsCount(root)
        if (friendCount == -1) {
            return false
        }

        Log.d(TAG, "寻找好友列表容器")
        friendListContainer = findFriendListContainer(root) ?: return false

        Log.d(TAG, "滚动到顶端")
        if (!scrollToTop(friendListContainer, friendCount)) {
            return false
        }

        Log.d(TAG, "开始批量打招呼")
        if (!batchGreeting(friendListContainer, friendCount)) {
            return false
        }

        return true
    }

    suspend fun navigateToFriendList(root: AccessibilityNodeInfo): Boolean {
        fun _navigate(): Boolean {
            val target = findNodeByDesc(
                root,
                VIEW_CLASS_NAME,
                FRIEND_LIST_ENTRY_DESC
            )
            if (target == null) return false

            val clicked = performClick(target)
            return clicked
        }

        var attempts = 0
        while (attempts < LOADING_MAX_RETRY) {
            currentCoroutineContext().ensureActive()

            if (!root.refresh()) {
                Log.w(TAG, "根节点已失效，停止遍历")
                return false
            }

            if (!root.refresh()) {
                Log.w(TAG, "根节点已失效，停止遍历")
                break
            }

            if (_navigate()) {
                Log.d(TAG, "「同调网络」入口点击成功")
                return true
            }

            attempts++
            delay(RETRY_INTERVAL.milliseconds)
        }

        Log.w(TAG, "未找到「同调网络」入口，已达最大重试次数")
        return false
    }

    suspend fun getFriendsCount(root: AccessibilityNodeInfo): Int {
        var target: AccessibilityNodeInfo? = null
        var attempts = 0
        var count = -1
        while (attempts < LOADING_MAX_RETRY) {
            currentCoroutineContext().ensureActive()

            if (!root.refresh()) {
                Log.w(TAG, "根节点已失效，停止遍历")
                return -1
            }

            target = findNodeByDesc(root, FRIENDS_COUNT_CLASS_NAME, Regex("""\s\(\d+\)"""))
            if (target != null) {
                val desc = target.contentDescription?.toString() ?: return -1
                count = extractInt(desc) ?: -1
                if (count > 0) {
                    Log.d(TAG, "读取到好友数量：$count，弹出 Toast")
                    showToast(getString(R.string.friends_count_toast, count))
                    return count
                }
            }

            attempts++
            delay(RETRY_INTERVAL.milliseconds)
            Log.d(TAG, "获取好友数量 $attempts / $LOADING_MAX_RETRY")
        }

        Log.w(TAG, "获取好友数量已达最大重试次数")
        return count
    }

    fun findNormalFriendIndicator(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        return findNodeByDesc(node, IMAGE_VIEW_CLASS_NAME, Regex("""一般通讯[\s\S]+\(.+?\d+.+?\).+"""))
    }

    fun findSpecialFriendIndicator(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        return findNodeByDesc(node, IMAGE_VIEW_CLASS_NAME, Regex("""特别通讯[\s\S]+\(.+?\d+.+?\).+"""))
    }

    suspend fun scrollToTop(
        container: AccessibilityNodeInfo,
        totalFriendsCount: Int
    ): Boolean {
        var attempts = 0
        while (attempts < BATCH_MAX_SCROLL_STEPS) {
            currentCoroutineContext().ensureActive()

            if (!container.refresh()) {
                Log.w(TAG, "容器节点已失效，停止遍历")
                return false
            }

            Log.d(TAG, "查找「特别通讯」标识")
            val specialIndicator = findSpecialFriendIndicator(container)
            if (specialIndicator != null) {
                Log.d(TAG, "发现「特别通讯」标识，好友列表已滚动到顶部")
                return true
            }

            Log.d(TAG, "查找「一般通讯」标识")
            val normalIndicator = findNormalFriendIndicator(container)
            if (normalIndicator != null) {
                Log.d(TAG, "发现「一般通讯」标识")
                val desc = normalIndicator.contentDescription?.toString()
                if (!desc.isNullOrBlank()) {
                    val normalCount = extractInt(desc)
                    if (normalCount == totalFriendsCount) {
                        Log.d(TAG, "「一般通讯」标识好友数量等于全部好友数量，好友列表已滚动到顶部")
                        return true
                    }
                }
            }

            performScroll(container, true)
            attempts++
            delay(SCROLL_INTERVAL_MS.milliseconds)
            Log.d(TAG, "向上滚动第 $attempts 次")
        }

        Log.w(TAG, "无法滚动到顶部，已达最大滚动次数")
        return false
    }

    suspend fun batchGreeting(container: AccessibilityNodeInfo, totalFriendsCount: Int): Boolean {
        var attempt = 0
        val greetedFriend = mutableSetOf<String>()

        while (attempt < BATCH_MAX_SCROLL_STEPS) {
            currentCoroutineContext().ensureActive()

            if (!container.refresh()) {
                Log.w(TAG, "容器节点已失效，停止遍历")
                return false
            }

            for (i in 0 until container.childCount) {
                currentCoroutineContext().ensureActive()

                val child = container.getChild(i) ?: continue

                // 好友名称
                if (child.childCount < 2) continue
                val friendInfoNode = child.getChild(0) ?: continue
                val friendName = friendInfoNode.contentDescription?.toString()?.lineSequence()?.firstOrNull()?.trim() ?: continue
                val greetButtonNode = child.getChild(1) ?: continue

                if (greetedFriend.add(friendName)) {
                    performClick(greetButtonNode)
                    Log.d(TAG, "已向 $friendName 打招呼  ${greetedFriend.size} / $totalFriendsCount")
                }
            }

            if (greetedFriend.size >= totalFriendsCount) {
                return true
            }

            var scrollAttempt = 0
            while (scrollAttempt < LOADING_MAX_RETRY) {
                if (container.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)) {
                    break
                }
                scrollAttempt++
                delay(RETRY_INTERVAL.milliseconds)
                Log.d(TAG, "好友列表加载等待中，$scrollAttempt / $LOADING_MAX_RETRY")
            }
            if (scrollAttempt == LOADING_MAX_RETRY) {
                Log.d(TAG, "打招呼已完成，列表已滚动到最底部")
                return true
            }

            attempt++
            delay(SCROLL_INTERVAL_MS.milliseconds)
        }

        Log.w(TAG, "打招呼未完成，已达最大滚动次数")
        return false
    }

    private suspend fun findFriendListContainer(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var attempt = 0
        var friendRowNode: AccessibilityNodeInfo? = null

        while (attempt < LOADING_MAX_RETRY) {
            friendRowNode = findNodeByDesc(root, VIEW_CLASS_NAME, Regex("""[\s\S]*初识至今[\s\S]*"""))
            if (friendRowNode != null) {
                Log.d(TAG, "已找到好友行控件")
                break
            }

            attempt++
            delay(RETRY_INTERVAL.milliseconds)
        }

        if (friendRowNode == null) {
            Log.w(TAG, "已达最大重试次数，无法找到好友列表")
            return null
        }

        var container: AccessibilityNodeInfo? = friendRowNode.parent
        while (container != null) {
            if (container.isScrollable) {
                Log.d(TAG, "已找到好友列表容器")
                return container
            }
            container = container.parent
        }
        return null
    }

    /**
     * 在 [node] 的可见区域内执行一次滑动手势，驱使内容滚动。
     *
     * 手势的起止点完全落在节点边界内，避免起点被列表外的头部控件
     * （标题栏、分组标签等）拦截导致列表收不到滚动事件。
     *
     * @param towardTop true = 手指下滑（内容向顶部回滚）；false = 手指上滑（内容向底部滚动）
     * @return true = 手势已成功分发
     */
    private fun performScroll(node: AccessibilityNodeInfo, towardTop: Boolean = false): Boolean {
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        if (bounds.isEmpty) {
            Log.w(TAG, "容器可见区域为空，无法分发滚动手势")
            return false
        }

        val centerX = bounds.exactCenterX()
        val upperY = bounds.top + bounds.height() * SCROLL_START_RATIO
        val lowerY = bounds.top + bounds.height() * SCROLL_END_RATIO
        val path = Path().apply {
            if (towardTop) {
                moveTo(centerX, upperY)     // 手指从上往下滑
                lineTo(centerX, lowerY)
            } else {
                moveTo(centerX, lowerY)     // 手指从下往上滑
                lineTo(centerX, upperY)
            }
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, SCROLL_DURATION_MS))
            .build()
        val dispatched = dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                Log.d(TAG, "滚动手势执行完成 towardTop=$towardTop")
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                Log.w(TAG, "滚动手势被取消 towardTop=$towardTop")
            }
        }, null)
        if (!dispatched) {
            Log.w(TAG, "滚动手势分发失败 towardTop=$towardTop")
        }
        return dispatched
    }

    /**
     * 深度优先遍历视图树，返回第一个 className 与 contentDescription 均匹配的节点。
     * 未命中的子节点及时 recycle，避免内存泄漏。
     */
    private fun findNodeByDesc(
        node: AccessibilityNodeInfo,
        className: String,
        desc: String
    ): AccessibilityNodeInfo? {
        if (node.className == className && node.contentDescription?.toString() == desc) {
            return node
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findNodeByDesc(child, className, desc)
            if (found != null) return found
        }
        return null
    }

    private fun findNodeByDesc(
        node: AccessibilityNodeInfo,
        className: String,
        desc: Regex
    ): AccessibilityNodeInfo? {
        val _desc = node.contentDescription?.toString() ?: ""
        if (node.className == className && desc.matches(_desc)) {
            return node
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findNodeByDesc(child, className, desc)
            if (found != null) return found
        }
        return null
    }

    private fun extractInt(content: String): Int? {
        return content.filter({ it.isDigit() }).toIntOrNull()
    }

    /**
     * 点击节点：
     * 1. 节点自身可点击则直接 ACTION_CLICK
     * 2. 否则向上查找最近的可点击祖先（ImageView 常把点击事件挂在父布局上）
     * 3. 都没有可点击祖先时，退化为在节点可见区域中心分发一次点按手势
     */
    private fun performClick(node: AccessibilityNodeInfo): Boolean {
        var current: AccessibilityNodeInfo? = node
        while (current != null) {
            if (current.isClickable) {
                val result = current.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                return result
            }
            val parent = current.parent
            current = parent
        }

        // 手势兜底：点击节点可见区域中心
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        if (bounds.isEmpty) return false
        val path = Path().apply { moveTo(bounds.exactCenterX(), bounds.exactCenterY()) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, TAP_DURATION_MS))
            .build()
        return dispatchGesture(gesture, null, null)
    }

    private suspend fun showToast(message: String) {
        withContext(Dispatchers.Main) {
            Toast.makeText(this@ArkRadarAccessibilityService, message, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        // 服务就绪后把实例交给 AccessibilityHelper，供界面层查询与调用
        AccessibilityHelper.service = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // 目标应用窗口出现后，如有待执行的自动打招呼则导航至好友列表
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            event.packageName == AccessibilityHelper.TARGET_PACKAGE &&
            autoGreetingPending &&
            greetingJob?.isActive != true
        ) {
            Log.d(TAG, "目标应用窗口出现，开始自动打招呼")
            greetingJob = serviceScope.launch { autoGreeting() }
            greetingJob?.invokeOnCompletion { autoGreetingPending = false }
        }
    }

    override fun onInterrupt() {
        greetingJob?.cancel()
    }

    override fun onDestroy() {
        greetingJob?.cancel()

        // 服务断开时清空引用，避免界面层误用失效实例
        if (AccessibilityHelper.service === this) {
            AccessibilityHelper.service = null
        }
        super.onDestroy()
    }

    companion object {
        private const val TAG = "ArkRadarHelper"
        /** 两次滚动之间的间隔 */
        private const val SCROLL_INTERVAL_MS = 200L
        /** 单次滑动手势的时长 */
        private const val SCROLL_DURATION_MS = 100L
        /** 最大重试次数 */
        private const val LOADING_MAX_RETRY = 5
        /** 重试等待时间 */
        private const val RETRY_INTERVAL = 500L
        /** 手势起点/终点在容器可见区域内的高度比例 */
        private const val SCROLL_START_RATIO = 0.05f
        private const val SCROLL_END_RATIO = 0.95f
        /** 手势兜底点按时长 */
        private const val TAP_DURATION_MS = 50L
        /** “打招呼”按钮的类名 */
        private const val IMAGE_VIEW_CLASS_NAME = "android.widget.ImageView"
        /** 批量遍历的最大滚动轮数（兜底，防止异常情况下无限滚动） */
        private const val BATCH_MAX_SCROLL_STEPS = 1000
        /** “同调网络”入口的 contentDescription */
        private const val FRIEND_LIST_ENTRY_DESC = "同调网络"
        private const val VIEW_CLASS_NAME = "android.view.View"
        private const val FRIENDS_COUNT_CLASS_NAME = "android.view.View"
    }
}
