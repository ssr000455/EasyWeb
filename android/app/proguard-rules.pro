# EasyWeb ProGuard Rules
# 保留 JNI 方法
-keepclasseswithmembernames class * {
    native <methods>;
}

# 保留 QuickJS 相关类
-keep class com.easyweb.app.engine.JsEngine { *; }

# 保留 DOM 桥接类
-keep class com.easyweb.app.engine.DomBridge { *; }