// b站：绝望彻彻底底的绝望 [android/app/src/main/java/com/easyweb/app/engine/Dom.kt]

package com.easyweb.app.engine

import java.util.HashMap

// 节点类型枚举
enum class NodeKind {
    Document,
    Element,
    Text
}

// 元素数据类
data class ElementData(
    val tag: String,
    val attrs: MutableMap<String, String> = HashMap()
) {
    // 获取id属性
    fun id(): String? = attrs["id"]
    // 获取class列表
    fun classes(): List<String> {
        val cls = attrs["class"] ?: return emptyList()
        return cls.split("\\s+".toRegex()).filter { it.isNotEmpty() }
    }
    // 获取指定属性
    fun getAttr(name: String): String? = attrs[name]
}

// DOM节点类
class Node(
    var kind: NodeKind = NodeKind.Document,
    var elementData: ElementData? = null,
    var text: String? = null,
    val children: MutableList<Node> = mutableListOf()
) {
    // 父节点引用
    var parent: Node? = null

    // 判断是否为元素节点
    fun isElement(): Boolean = kind == NodeKind.Element
    // 判断是否为文本节点
    fun isText(): Boolean = kind == NodeKind.Text
    // 获取标签名
    fun tagName(): String? = elementData?.tag

    companion object {
        // 创建元素节点
        fun createElement(tag: String, attrs: Map<String, String> = emptyMap()): Node {
            return Node(
                kind = NodeKind.Element,
                elementData = ElementData(tag, attrs.toMutableMap())
            )
        }
        // 创建文本节点
        fun createText(content: String): Node {
            return Node(
                kind = NodeKind.Text,
                text = content
            )
        }
        // 创建文档节点
        fun createDocument(): Node {
            return Node(kind = NodeKind.Document)
        }
    }

    // 添加子节点
    fun append(child: Node) {
        child.parent = this
        children.add(child)
    }

    // 调试输出DOM树
    fun dump(): String {
        val sb = StringBuilder()
        dumpRecursive(sb, 0)
        return sb.toString()
    }

    private fun dumpRecursive(sb: StringBuilder, depth: Int) {
        val indent = "  ".repeat(depth)
        when (kind) {
            NodeKind.Document -> {
                sb.appendLine("${indent}Document")
            }
            NodeKind.Element -> {
                val e = elementData ?: return
                sb.append("${indent}<${e.tag}")
                for ((k, v) in e.attrs) {
                    sb.append(" $k=\"$v\"")
                }
                sb.appendLine(">")
            }
            NodeKind.Text -> {
                val trimmed = text?.trim() ?: ""
                if (trimmed.isNotEmpty()) {
                    sb.appendLine("$indent\"$trimmed\"")
                }
                return
            }
        }
        for (child in children) {
            child.dumpRecursive(sb, depth + 1)
        }
    }
}
