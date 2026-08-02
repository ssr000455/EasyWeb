// b站：绝望彻彻底底的绝望 [android/app/src/main/java/com/easyweb/app/engine/HistoryStack.kt]
// 页面历史栈管理

package com.easyweb.app.engine

import org.json.JSONObject

/**
 * 历史记录条目
 */
data class HistoryEntry(
    val state: String?,   // 序列化的状态对象
    val title: String,    // 页面标题
    val url: String?      // 相对/绝对 URL
)

/**
 * 页面历史栈
 * 管理 window.history 的 pushState/replaceState/go/back/forward
 */
class HistoryStack {

    private val entries = mutableListOf<HistoryEntry>()
    private var currentIndex = -1

    /**
     * popstate 事件回调
     * 参数: (state, url)
     */
    var onPopState: ((String?, String?) -> Unit)? = null

    /**
     * 初始化历史栈（页面加载时调用）
     */
    fun init(url: String) {
        entries.clear()
        currentIndex = 0
        entries.add(HistoryEntry(null, "", url))
    }

    /**
     * pushState: 添加新历史记录
     */
    fun pushState(state: String?, title: String, url: String?) {
        // 删除当前位置之后的所有条目
        while (entries.size > currentIndex + 1) {
            entries.removeAt(entries.size - 1)
        }
        entries.add(HistoryEntry(state, title, url))
        currentIndex++
    }

    /**
     * replaceState: 替换当前历史记录
     */
    fun replaceState(state: String?, title: String, url: String?) {
        if (currentIndex in entries.indices) {
            entries[currentIndex] = HistoryEntry(state, title, url)
        }
    }

    /**
     * go: 相对导航
     */
    fun go(delta: Int): Boolean {
        val newIndex = currentIndex + delta
        if (newIndex < 0 || newIndex >= entries.size) return false
        currentIndex = newIndex
        val entry = entries[currentIndex]
        onPopState?.invoke(entry.state, entry.url)
        return true
    }

    /**
     * back: 后退
     */
    fun back(): Boolean = go(-1)

    /**
     * forward: 前进
     */
    fun forward(): Boolean = go(1)

    /** 历史记录数量 */
    val length: Int get() = entries.size

    /** 当前条目 URL */
    val currentUrl: String? get() = entries.getOrNull(currentIndex)?.url

    /** 当前条目状态 */
    val currentState: String? get() = entries.getOrNull(currentIndex)?.state

    /** 是否可以后退 */
    val canGoBack: Boolean get() = currentIndex > 0

    /** 是否可以前进 */
    val canGoForward: Boolean get() = currentIndex < entries.size - 1
}