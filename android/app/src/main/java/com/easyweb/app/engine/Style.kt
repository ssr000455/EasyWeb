// b站：绝望彻彻底底的绝望 [android/app/src/main/java/com/easyweb/app/engine/Style.kt]

package com.easyweb.app.engine

import java.util.HashMap

// 长度值类型
sealed class LengthValue {
    data class Px(val value: Float) : LengthValue()
    data class Pct(val value: Float) : LengthValue()
}

// 显示类型
enum class Display {
    None, Block, Inline, InlineBlock, Flex
}

// 计算后的样式
class Style {
    val properties = HashMap<String, String>()

    // 获取属性值
    fun get(name: String): String = properties[name] ?: ""

    // 解析长度值
    fun length(name: String, default: Float): Float {
        val value = get(name)
        if (value.isEmpty()) return default
        val trimmed = value.trim()
        return if (trimmed.endsWith("px")) {
            trimmed.removeSuffix("px").trim().toFloatOrNull() ?: default
        } else {
            trimmed.toFloatOrNull() ?: default
        }
    }

    // 解析带百分比的长度值
    fun lengthPct(name: String, default: Float): LengthValue {
        val value = get(name)
        if (value.isEmpty()) return LengthValue.Px(default)
        val trimmed = value.trim()
        return if (trimmed.endsWith("px")) {
            LengthValue.Px(trimmed.removeSuffix("px").trim().toFloatOrNull() ?: default)
        } else if (trimmed.endsWith("%")) {
            LengthValue.Pct(trimmed.removeSuffix("%").trim().toFloatOrNull() ?: 0.0f)
        } else {
            LengthValue.Px(trimmed.toFloatOrNull() ?: default)
        }
    }

    // 解析颜色值
    fun color(name: String, default: Long): Long {
        val value = get(name)
        if (value.isEmpty()) return default
        return parseColor(value) ?: default
    }

    // 获取显示类型
    fun display(): Display {
        return when (get("display")) {
            "none" -> Display.None
            "inline" -> Display.Inline
            "inline-block" -> Display.InlineBlock
            "flex" -> Display.Flex
            else -> Display.Block
        }
    }
}

// 解析颜色字符串 返回ARGB格式的Long
// 颜色存储在Long中 低32位为ARGB
fun parseColor(s: String): Long? {
    val trimmed = s.trim()
    return when {
        trimmed.startsWith("#") -> {
            val hex = trimmed.substring(1)
            when (hex.length) {
                3 -> {
                    val r = hex.substring(0, 1).toIntOrNull(16)?.times(17) ?: return null
                    val g = hex.substring(1, 2).toIntOrNull(16)?.times(17) ?: return null
                    val b = hex.substring(2, 3).toIntOrNull(16)?.times(17) ?: return null
                    toArgb(r, g, b, 255)
                }
                6 -> {
                    val r = hex.substring(0, 2).toIntOrNull(16) ?: return null
                    val g = hex.substring(2, 4).toIntOrNull(16) ?: return null
                    val b = hex.substring(4, 6).toIntOrNull(16) ?: return null
                    toArgb(r, g, b, 255)
                }
                8 -> {
                    val r = hex.substring(0, 2).toIntOrNull(16) ?: return null
                    val g = hex.substring(2, 4).toIntOrNull(16) ?: return null
                    val b = hex.substring(4, 6).toIntOrNull(16) ?: return null
                    val a = hex.substring(6, 8).toIntOrNull(16) ?: return null
                    toArgb(r, g, b, a)
                }
                else -> null
            }
        }
        trimmed.startsWith("rgb(") -> {
            val inner = trimmed.removePrefix("rgb(").removeSuffix(")").trim()
            val parts = inner.split(",")
            if (parts.size == 3) {
                val r = parts[0].trim().toIntOrNull() ?: return null
                val g = parts[1].trim().toIntOrNull() ?: return null
                val b = parts[2].trim().toIntOrNull() ?: return null
                toArgb(r, g, b, 255)
            } else null
        }
        trimmed.startsWith("rgba(") -> {
            val inner = trimmed.removePrefix("rgba(").removeSuffix(")").trim()
            val parts = inner.split(",")
            if (parts.size == 4) {
                val r = parts[0].trim().toIntOrNull() ?: return null
                val g = parts[1].trim().toIntOrNull() ?: return null
                val b = parts[2].trim().toIntOrNull() ?: return null
                val a = (parts[3].trim().toFloatOrNull()?.times(255)?.toInt()) ?: return null
                toArgb(r, g, b, a)
            } else null
        }
        else -> {
            // 命名颜色
            val named = namedColors[trimmed.lowercase()]
            named?.let { (r, g, b) ->
                if (trimmed.lowercase() == "transparent") toArgb(r, g, b, 0)
                else toArgb(r, g, b, 255)
            }
        }
    }
}

// 将RGBA转换为ARGB long
fun toArgb(r: Int, g: Int, b: Int, a: Int): Long {
    return ((a.toLong() and 0xFF) shl 24) or
           ((r.toLong() and 0xFF) shl 16) or
           ((g.toLong() and 0xFF) shl 8) or
           (b.toLong() and 0xFF)
}

// 提取ARGB分量
fun alphaOf(color: Long): Int = ((color shr 24) and 0xFF).toInt()
fun redOf(color: Long): Int = ((color shr 16) and 0xFF).toInt()
fun greenOf(color: Long): Int = ((color shr 8) and 0xFF).toInt()
fun blueOf(color: Long): Int = (color and 0xFF).toInt()

// 常用命名颜色
private val namedColors = mapOf(
    "black" to Triple(0, 0, 0),
    "white" to Triple(255, 255, 255),
    "red" to Triple(255, 0, 0),
    "green" to Triple(0, 128, 0),
    "blue" to Triple(0, 0, 255),
    "yellow" to Triple(255, 255, 0),
    "orange" to Triple(255, 165, 0),
    "purple" to Triple(128, 0, 128),
    "gray" to Triple(128, 128, 128),
    "grey" to Triple(128, 128, 128),
    "pink" to Triple(255, 192, 203),
    "brown" to Triple(165, 42, 42),
    "navy" to Triple(0, 0, 128),
    "teal" to Triple(0, 128, 128),
    "transparent" to Triple(0, 0, 0)
)

// 从样式表中收集所有样式
fun collectStyles(doc: Node): Stylesheet {
    val cssText = StringBuilder()
    collectStyleNodes(doc, cssText)
    return CssParser.parse(cssText.toString())
}

// 递归收集style标签内容
private fun collectStyleNodes(node: Node, css: StringBuilder) {
    if (node.kind == NodeKind.Element && node.tagName() == "style") {
        for (child in node.children) {
            if (child.kind == NodeKind.Text && child.text != null) {
                css.append(child.text)
            }
        }
    }
    for (child in node.children) {
        collectStyleNodes(child, css)
    }
}

// 计算单个节点的样式
fun computeStyle(node: Node, stylesheet: Stylesheet): Style {
    val style = Style()

    // 应用默认样式
    applyDefaults(node.tagName(), style)

    // 应用匹配的规则
    val matched = matchRules(node, stylesheet)
    for (rule in matched) {
        for (decl in rule.declarations) {
            style.properties[decl.name] = decl.value
        }
    }

    // 应用内联样式
    val data = node.elementData
    if (data != null) {
        val inline = data.getAttr("style")
        if (inline != null) {
            val inlineSs = CssParser.parse("_ { $inline }")
            for (rule in inlineSs.rules) {
                for (decl in rule.declarations) {
                    style.properties[decl.name] = decl.value
                }
            }
        }
    }

    return style
}

// 应用默认样式
private fun applyDefaults(tag: String?, style: Style) {
    when (tag) {
        "body", "html", "div", "p", "h1", "h2", "h3", "h4", "h5", "h6",
        "ul", "ol", "li", "table", "tr", "td", "th",
        "header", "footer", "nav", "section", "article", "main", "aside", "hr" -> {
            style.properties["display"] = "block"
        }
        "span", "a", "b", "i", "strong", "em" -> {
            style.properties["display"] = "inline"
        }
    }

    // 标题默认字号
    when (tag) {
        "h1" -> style.properties["font-size"] = "32px"
        "h2" -> style.properties["font-size"] = "24px"
        "h3" -> style.properties["font-size"] = "18px"
        "h4" -> style.properties["font-size"] = "16px"
        "h5" -> style.properties["font-size"] = "14px"
        "h6" -> style.properties["font-size"] = "12px"
    }

    // 粗体标签
    when (tag) {
        "b", "strong" -> style.properties["font-weight"] = "bold"
    }

    // 默认边距
    if (tag == "body") {
        style.properties["margin"] = "8px"
    }
    if (tag == "p") {
        style.properties["margin"] = "16px 0"
    }
    if (tag == "ul" || tag == "ol") {
        style.properties["padding-left"] = "40px"
    }
    if (tag == "hr") {
        style.properties["border"] = "1px solid black"
        style.properties["margin"] = "8px 0"
    }
}

// 递归计算所有节点的样式
fun computeStyles(doc: Node, stylesheet: Stylesheet): Map<Node, Style> {
    val map = HashMap<Node, Style>()
    computeStylesRecursive(doc, stylesheet, map)
    return map
}

// 递归计算样式
private fun computeStylesRecursive(node: Node, stylesheet: Stylesheet, map: MutableMap<Node, Style>) {
    map[node] = computeStyle(node, stylesheet)
    for (child in node.children) {
        computeStylesRecursive(child, stylesheet, map)
    }
}