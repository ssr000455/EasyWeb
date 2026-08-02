// b站：绝望彻彻底底的绝望 android/app/src/main/cpp/jni_wrapper/js_engine.c
// QuickJS 与 Kotlin 之间的 JNI 桥接层

#include <jni.h>
#include <string.h>
#include <stdlib.h>
#include "quickjs.h"
#include "quickjs-libc.h"

// 全局 JS 运行时和上下文
static JSRuntime *g_rt = NULL;
static JSContext *g_ctx = NULL;

// 保存 Java VM 和回调引用，用于从 JS 回调 Kotlin
static JavaVM *g_jvm = NULL;
static jobject g_callback_obj = NULL;
static jmethodID g_callback_method = NULL;

// ───────── 工具函数 ─────────

// 将 JSValue 转为 Java 字符串
static jstring jsval_to_jstring(JNIEnv *env, JSContext *ctx, JSValue val) {
    const char *str = JS_ToCString(ctx, val);
    if (!str) str = "null";
    jstring result = (*env)->NewStringUTF(env, str);
    JS_FreeCString(ctx, str);
    return result;
}

// 将 Java 字符串转为 JS 字符串
static JSValue jstring_to_jsval(JSContext *ctx, JNIEnv *env, jstring jstr) {
    if (!jstr) return JS_NewString(ctx, "");
    const char *utf = (*env)->GetStringUTFChars(env, jstr, NULL);
    JSValue val = JS_NewString(ctx, utf);
    (*env)->ReleaseStringUTFChars(env, jstr, utf);
    return val;
}

// ───────── JS 原生函数：桥接回调 ─────────
// 当 JS 调用 __easyweb_bridge(method, jsonArgs) 时，
// 通过 JNI 回调到 Kotlin 的 onBridgeCall 方法

static JSValue js_bridge_callback(JSContext *ctx, JSValueConst this_val,
                                    int argc, JSValueConst *argv, int magic) {
    (void)this_val;
    (void)magic;

    if (argc < 2 || !g_jvm || !g_callback_obj) {
        return JS_UNDEFINED;
    }

    // 获取当前线程的 JNIEnv
    JNIEnv *env;
    int attach = (*g_jvm)->GetEnv(g_jvm, (void**)&env, JNI_VERSION_1_6);
    if (attach == JNI_EDETACHED) {
        attach = (*g_jvm)->AttachCurrentThread(g_jvm, &env, NULL);
    }
    if (attach != JNI_OK || !env) {
        return JS_UNDEFINED;
    }

    // 从 JS 参数中提取方法名和参数 JSON
    const char *method_cstr = JS_ToCString(ctx, argv[0]);
    const char *args_cstr = JS_ToCString(ctx, argv[1]);

    jstring jmethod = (*env)->NewStringUTF(env, method_cstr ? method_cstr : "");
    jstring jargs = (*env)->NewStringUTF(env, args_cstr ? args_cstr : "[]");

    // 调用 Kotlin 的 onBridgeCall 方法
    jstring jresult = (jstring)(*env)->CallObjectMethod(env, g_callback_obj,
                                                         g_callback_method, jmethod, jargs);

    // 将结果转回 JSValue
    JSValue result;
    if (jresult) {
        const char *result_str = (*env)->GetStringUTFChars(env, jresult, NULL);
        result = JS_NewString(ctx, result_str ? result_str : "null");
        (*env)->ReleaseStringUTFChars(env, jresult, result_str);
        (*env)->DeleteLocalRef(env, jresult);
    } else {
        result = JS_NewString(ctx, "null");
    }

    // 清理
    JS_FreeCString(ctx, method_cstr);
    JS_FreeCString(ctx, args_cstr);
    (*env)->DeleteLocalRef(env, jmethod);
    (*env)->DeleteLocalRef(env, jargs);

    return result;
}

// ───────── JNI 导出函数 ─────────

// 初始化 JS 引擎
JNIEXPORT jlong JNICALL
Java_com_easyweb_app_engine_JsEngine_nativeInitEngine(JNIEnv *env, jobject thiz) {
    if (g_ctx) {
        return (jlong)(intptr_t)g_ctx;
    }

    // 保存 JVM 引用
    (*env)->GetJavaVM(env, &g_jvm);

    g_rt = JS_NewRuntime();
    if (!g_rt) return 0;

    g_ctx = JS_NewContext(g_rt);
    if (!g_ctx) {
        JS_FreeRuntime(g_rt);
        g_rt = NULL;
        return 0;
    }

    // 注入标准库
    js_std_add_helpers(g_ctx, -1, NULL);

    // 注册全局 __easyweb 对象
    JSValue global = JS_GetGlobalObject(g_ctx);
    JSValue easyweb = JS_NewObject(g_ctx);
    JS_SetPropertyStr(g_ctx, global, "__easyweb", easyweb);
    JS_FreeValue(g_ctx, global);
    JS_FreeValue(g_ctx, easyweb);

    return (jlong)(intptr_t)g_ctx;
}

// 注册 Kotlin 回调，并在 JS 中创建 __easyweb_bridge 函数
JNIEXPORT void JNICALL
Java_com_easyweb_app_engine_JsEngine_nativeRegisterBridgeCallback(
        JNIEnv *env, jobject thiz, jobject callback) {
    if (!g_ctx) return;

    // 保存回调对象引用
    if (g_callback_obj) {
        (*env)->DeleteGlobalRef(env, g_callback_obj);
    }
    g_callback_obj = (*env)->NewGlobalRef(env, callback);

    // 获取 onBridgeCall 方法 ID
    jclass callbackClass = (*env)->GetObjectClass(env, callback);
    g_callback_method = (*env)->GetMethodID(env, callbackClass, "onBridgeCall",
                                             "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;");
    (*env)->DeleteLocalRef(env, callbackClass);

    if (!g_callback_method) return;

    // 在 JS 全局作用域注册 __easyweb_bridge 函数
    JSValue global = JS_GetGlobalObject(g_ctx);
    JSValue func = JS_NewCFunctionMagic(g_ctx, js_bridge_callback,
                                         "__easyweb_bridge", 2,
                                         JS_CFUNC_generic_magic, 0);
    JS_SetPropertyStr(g_ctx, global, "__easyweb_bridge", func);
    JS_FreeValue(g_ctx, global);
}

// 执行 JS 脚本
JNIEXPORT jstring JNICALL
Java_com_easyweb_app_engine_JsEngine_nativeEvaluate(JNIEnv *env, jobject thiz,
                                                     jstring script, jstring filename) {
    if (!g_ctx) {
        return (*env)->NewStringUTF(env, "JS engine not initialized");
    }

    const char *script_str = (*env)->GetStringUTFChars(env, script, NULL);
    const char *file_str = filename
        ? (*env)->GetStringUTFChars(env, filename, NULL)
        : "<eval>";

    JSValue result = JS_Eval(g_ctx, script_str, strlen(script_str),
                              file_str, JS_EVAL_TYPE_GLOBAL);

    (*env)->ReleaseStringUTFChars(env, script, script_str);
    if (filename) (*env)->ReleaseStringUTFChars(env, filename, file_str);

    if (JS_IsException(result)) {
        JSValue exc = JS_GetException(g_ctx);
        jstring err = jsval_to_jstring(env, g_ctx, exc);
        JS_FreeValue(g_ctx, exc);
        JS_FreeValue(g_ctx, result);
        return err;
    }

    jstring jresult = jsval_to_jstring(env, g_ctx, result);
    JS_FreeValue(g_ctx, result);
    return jresult;
}

// 获取全局变量
JNIEXPORT jstring JNICALL
Java_com_easyweb_app_engine_JsEngine_nativeGetGlobal(JNIEnv *env, jobject thiz,
                                                      jstring name) {
    if (!g_ctx) return (*env)->NewStringUTF(env, "");

    const char *key = (*env)->GetStringUTFChars(env, name, NULL);
    JSValue global = JS_GetGlobalObject(g_ctx);
    JSValue val = JS_GetPropertyStr(g_ctx, global, key);
    JS_FreeValue(g_ctx, global);

    jstring result = jsval_to_jstring(env, g_ctx, val);
    JS_FreeValue(g_ctx, val);
    (*env)->ReleaseStringUTFChars(env, name, key);
    return result;
}

// 设置全局变量
JNIEXPORT void JNICALL
Java_com_easyweb_app_engine_JsEngine_nativeSetGlobal(JNIEnv *env, jobject thiz,
                                                      jstring name, jstring value) {
    if (!g_ctx) return;

    const char *key = (*env)->GetStringUTFChars(env, name, NULL);
    JSValue global = JS_GetGlobalObject(g_ctx);
    JSValue val = jstring_to_jsval(g_ctx, env, value);
    JS_SetPropertyStr(g_ctx, global, key, val);
    JS_FreeValue(g_ctx, global);
    (*env)->ReleaseStringUTFChars(env, name, key);
}

// 调用 JS 函数
JNIEXPORT jstring JNICALL
Java_com_easyweb_app_engine_JsEngine_nativeCallFunction(JNIEnv *env, jobject thiz,
                                                         jstring name, jstring arg) {
    if (!g_ctx) return (*env)->NewStringUTF(env, "");

    const char *func_name = (*env)->GetStringUTFChars(env, name, NULL);
    JSValue global = JS_GetGlobalObject(g_ctx);
    JSValue func = JS_GetPropertyStr(g_ctx, global, func_name);
    JS_FreeValue(g_ctx, global);

    if (!JS_IsFunction(g_ctx, func)) {
        JS_FreeValue(g_ctx, func);
        (*env)->ReleaseStringUTFChars(env, name, func_name);
        return (*env)->NewStringUTF(env, "");
    }

    JSValue arg_val;
    if (arg) {
        arg_val = jstring_to_jsval(g_ctx, env, arg);
    } else {
        arg_val = JS_UNDEFINED;
    }

    JSValue result = JS_Call(g_ctx, func, JS_UNDEFINED, 1, &arg_val);
    JS_FreeValue(g_ctx, func);
    if (arg) JS_FreeValue(g_ctx, arg_val);

    jstring jresult = jsval_to_jstring(env, g_ctx, result);
    JS_FreeValue(g_ctx, result);
    (*env)->ReleaseStringUTFChars(env, name, func_name);
    return jresult;
}

// 销毁 JS 引擎
JNIEXPORT void JNICALL
Java_com_easyweb_app_engine_JsEngine_nativeDestroy(JNIEnv *env, jobject thiz) {
    if (g_ctx) {
        JS_FreeContext(g_ctx);
        g_ctx = NULL;
    }
    if (g_rt) {
        JS_FreeRuntime(g_rt);
        g_rt = NULL;
    }
    if (g_callback_obj) {
        (*env)->DeleteGlobalRef(env, g_callback_obj);
        g_callback_obj = NULL;
    }
}