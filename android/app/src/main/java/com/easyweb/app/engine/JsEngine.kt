// b站：绝望彻彻底底的绝望 android/app/src/main/java/com/easyweb/app/engine/JsEngine.kt
// QuickJS JavaScript 引擎的 Kotlin 封装

package com.easyweb.app.engine

/**
 * JavaScript 引擎封装
 * 通过 JNI 调用 QuickJS 原生库
 */
class JsEngine {

    private var contextPtr: Long = 0
    private var initialized = false
    private var bridgeCallback: ((String, String) -> String)? = null

    companion object {
        init {
            System.loadLibrary("js_engine")
        }
    }

    // ─── JNI 原生方法 ───

    private external fun nativeInitEngine(): Long
    private external fun nativeEvaluate(script: String, filename: String?): String
    private external fun nativeGetGlobal(name: String): String
    private external fun nativeSetGlobal(name: String, value: String)
    private external fun nativeCallFunction(name: String, arg: String?): String
    private external fun nativeRegisterBridgeCallback(callback: JsEngine)
    private external fun nativeDestroy()

    // ─── 公开 API ───

    /**
     * 初始化 JS 引擎
     */
    fun init() {
        if (initialized) return
        contextPtr = nativeInitEngine()
        if (contextPtr == 0L) {
            throw RuntimeException("无法初始化 JS 引擎")
        }
        initialized = true

        // 注入基础 DOM 骨架
        evaluate("""
            if (typeof window === 'undefined') {
                window = {};
                document = {
                    getElementById: function(id) { return null; },
                    createElement: function(tag) { return { tagName: tag, style: {} }; },
                    body: { style: {} }
                };
                console = { log: function() {}, error: function() {}, warn: function() {} };
                setTimeout = function(fn, ms) { return 0; };
                clearTimeout = function(id) {};
                setInterval = function(fn, ms) { return 0; };
                clearInterval = function(id) {};
            }
        """.trimIndent())
    }

    /**
     * 执行 JavaScript 脚本
     */
    fun evaluate(script: String): String {
        checkInitialized()
        return nativeEvaluate(script, "app.js")
    }

    /**
     * 执行 JavaScript 脚本（指定文件名）
     */
    fun evaluate(script: String, filename: String): String {
        checkInitialized()
        return nativeEvaluate(script, filename)
    }

    /**
     * 获取 JS 全局变量
     */
    fun getGlobal(name: String): String {
        checkInitialized()
        return nativeGetGlobal(name)
    }

    /**
     * 设置 JS 全局变量
     */
    fun setGlobal(name: String, value: String) {
        checkInitialized()
        nativeSetGlobal(name, value)
    }

    /**
     * 调用 JS 函数
     */
    fun callFunction(name: String, arg: String? = null): String {
        checkInitialized()
        return nativeCallFunction(name, arg)
    }

    /**
     * 注册桥接回调
     * 注册后 JS 中可通过 __easyweb_bridge(method, jsonArgs) 调用 Kotlin 代码
     */
    fun registerBridgeCallback(callback: (String, String) -> String) {
        bridgeCallback = callback
        nativeRegisterBridgeCallback(this)
    }

    /**
     * 被 C 层 JNI 调用的桥接方法
     * 当 JS 调用 __easyweb_bridge(method, jsonArgs) 时，C 层回调此方法
     */
    fun onBridgeCall(method: String, jsonArgs: String): String {
        return bridgeCallback?.invoke(method, jsonArgs) ?: "null"
    }

    /**
     * 销毁引擎，释放资源
     */
    fun destroy() {
        if (initialized) {
            nativeDestroy()
            initialized = false
            contextPtr = 0
            bridgeCallback = null
        }
    }

    private fun checkInitialized() {
        if (!initialized) {
            throw IllegalStateException("JS 引擎未初始化，请先调用 init()")
        }
    }
}