// b站：绝望彻彻底底的绝望 [android/app/src/main/java/com/easyweb/app/engine/Layout.kt]

package com.easyweb.app.engine

// 矩形区域
data class Rect(
    var x: Float = 0.0f,
    var y: Float = 0.0f,
    var width: Float = 0.0f,
    var height: Float = 0.0f
)

// 边距值
data class Edges(
    var top: Float = 0.0f,
    var right: Float = 0.0f,
    var bottom: Float = 0.0f,
    var left: Float = 0.0f
) {
    companion object {
        // 四边设为相同值
        fun all(v: Float): Edges = Edges(top = v, right = v, bottom = v, left = v)
    }

    // 水平方向总宽度
    fun horizontal(): Float = left + right
    // 垂直方向总高度
    fun vertical(): Float = top + bottom
}

// 盒子类型
enum class BoxType {
    Block, Inline, InlineBlock, Text, Anonymous
}

// 布局盒子
class LayoutBox(
    var boxType: BoxType = BoxType.Block,
    val children: MutableList<LayoutBox> = mutableListOf(),
    var rect: Rect = Rect(),
    var padding: Edges = Edges(),
    var border: Edges = Edges(),
    var margin: Edges = Edges(),
    var style: Style? = null,
    var domNode: Node? = null
)

// 计算内容区域
fun contentArea(box: LayoutBox): Rect {
    return Rect(
        x = box.rect.x + box.padding.left + box.border.left,
        y = box.rect.y + box.padding.top + box.border.top,
        width = box.rect.width - box.padding.horizontal() - box.border.horizontal(),
        height = box.rect.height - box.padding.vertical() - box.border.vertical()
    )
}

// 计算内边距区域
fun paddingArea(box: LayoutBox): Rect {
    return Rect(
        x = box.rect.x + box.border.left,
        y = box.rect.y + box.border.top,
        width = box.rect.width - box.border.horizontal(),
        height = box.rect.height - box.border.vertical()
    )
}

// 布局引擎
class LayoutEngine(
    private val styles: Map<Node, Style>,
    private val viewportWidth: Float,
    private val viewportHeight: Float
) {
    // 构建并计算布局
    fun layout(root: Node): LayoutBox {
        val layoutRoot = buildLayoutTree(root)
        calculateLayout(layoutRoot)
        return layoutRoot
    }

    // 构建布局树
    private fun buildLayoutTree(node: Node): LayoutBox {
        val style = styles[node]
        return when (node.kind) {
            NodeKind.Document -> {
                val rootBox = LayoutBox(boxType = BoxType.Block, style = style)
                for (child in node.children) {
                    rootBox.children.add(buildLayoutTree(child))
                }
                rootBox
            }
            NodeKind.Element -> {
                val display = style?.display() ?: Display.Block
                when (display) {
                    Display.None -> LayoutBox(
                        boxType = BoxType.Block,
                        style = style,
                        domNode = node
                    )
                    Display.Inline -> {
                        val box = LayoutBox(boxType = BoxType.Inline, style = style, domNode = node)
                        for (child in node.children) {
                            val childBox = buildLayoutTree(child)
                            if (childBox.boxType != BoxType.Text || hasVisibleText(childBox)) {
                                box.children.add(childBox)
                            }
                        }
                        box
                    }
                    Display.InlineBlock -> {
                        val box = LayoutBox(boxType = BoxType.InlineBlock, style = style, domNode = node)
                        for (child in node.children) {
                            box.children.add(buildLayoutTree(child))
                        }
                        box
                    }
                    Display.Flex -> {
                        val box = LayoutBox(boxType = BoxType.Block, style = style, domNode = node)
                        for (child in node.children) {
                            box.children.add(buildLayoutTree(child))
                        }
                        box
                    }
                    Display.Block -> {
                        val box = LayoutBox(boxType = BoxType.Block, style = style, domNode = node)
                        buildBlockChildren(node, box)
                        box
                    }
                }
            }
            NodeKind.Text -> {
                LayoutBox(boxType = BoxType.Text, domNode = node)
            }
        }
    }

    // 检查是否有可见文本
    private fun hasVisibleText(box: LayoutBox): Boolean {
        val node = box.domNode ?: return false
        if (node.kind == NodeKind.Text) {
            return !(node.text?.trim().isNullOrEmpty())
        }
        return false
    }

    // 构建块级子元素
    private fun buildBlockChildren(node: Node, parent: LayoutBox) {
        val inlineGroup = mutableListOf<LayoutBox>()
        for (child in node.children) {
            val childBox = buildLayoutTree(child)
            val isInline = childBox.boxType == BoxType.Inline
                || childBox.boxType == BoxType.InlineBlock
                || childBox.boxType == BoxType.Text
            if (isInline) {
                if (hasVisibleText(childBox) || childBox.boxType != BoxType.Text) {
                    inlineGroup.add(childBox)
                }
            } else {
                // 刷新内联组为匿名块
                if (inlineGroup.isNotEmpty()) {
                    val anon = LayoutBox(boxType = BoxType.Anonymous)
                    anon.children.addAll(inlineGroup)
                    inlineGroup.clear()
                    parent.children.add(anon)
                }
                parent.children.add(childBox)
            }
        }
        // 刷新剩余内联组
        if (inlineGroup.isNotEmpty()) {
            val anon = LayoutBox(boxType = BoxType.Anonymous)
            anon.children.addAll(inlineGroup)
            parent.children.add(anon)
        }
    }

    // 计算布局
    private fun calculateLayout(box: LayoutBox) {
        setBoxProperties(box)
        layoutChildren(box)
        positionChildren(box)
    }

    // 设置盒子属性
    private fun setBoxProperties(box: LayoutBox) {
        val style = box.style ?: return
        // 解析边距
        box.margin = parseBoxEdge(style, "margin", 0.0f)
        // 解析内边距
        box.padding = parseBoxEdge(style, "padding", 0.0f)
        // 解析边框
        val borderWidth = style.length("border-width", 0.0f)
        val borderWidthFinal = if (borderWidth > 0.0f
            || style.get("border").contains("1px")
            || style.get("border").contains("solid")
        ) {
            borderWidth.coerceAtLeast(1.0f)
        } else {
            borderWidth
        }
        box.border = Edges.all(borderWidthFinal)
        // 设置宽高
        val w = style.lengthPct("width", 0.0f)
        val h = style.lengthPct("height", 0.0f)
        when (w) {
            is LengthValue.Px -> box.rect.width = w.value
            is LengthValue.Pct -> box.rect.width = viewportWidth * w.value / 100.0f
        }
        when (h) {
            is LengthValue.Px -> box.rect.height = h.value
            is LengthValue.Pct -> box.rect.height = viewportHeight * h.value / 100.0f
        }
    }

    // 解析边距缩写
    private fun parseBoxEdge(style: Style, base: String, default: Float): Edges {
        val full = style.get(base)
        if (full.isEmpty()) return Edges.all(default)
        val parts = full.split("\\s+".toRegex()).filter { it.isNotEmpty() }
        val vals = parts.map { s ->
            val trimmed = s.trim()
            if (trimmed.endsWith("px")) {
                trimmed.removeSuffix("px").trim().toFloatOrNull() ?: 0.0f
            } else {
                trimmed.toFloatOrNull() ?: 0.0f
            }
        }
        return when (vals.size) {
            1 -> Edges.all(vals[0])
            2 -> Edges(top = vals[0], right = vals[1], bottom = vals[0], left = vals[1])
            3 -> Edges(top = vals[0], right = vals[1], bottom = vals[2], left = vals[1])
            4 -> Edges(top = vals[0], right = vals[1], bottom = vals[2], left = vals[3])
            else -> Edges.all(default)
        }
    }

    // 布局子元素
    private fun layoutChildren(box: LayoutBox) {
        when (box.boxType) {
            BoxType.Block, BoxType.Anonymous -> {
                val isFlex = box.style?.display() == Display.Flex
                if (isFlex) {
                    layoutFlexChildren(box)
                } else {
                    layoutBlockChildren(box)
                }
            }
            BoxType.Inline, BoxType.InlineBlock -> {
                layoutInlineChildren(box)
            }
            BoxType.Text -> {
                // 文本节点没有子元素
            }
        }
    }

    // 布局块级子元素
    private fun layoutBlockChildren(box: LayoutBox) {
        val content = contentArea(box)
        var cursorY = content.y

        for (child in box.children) {
            calculateLayout(child)
            child.rect.x = content.x + child.margin.left
            child.rect.y = cursorY + child.margin.top
            // 宽度自适应
            val hasStyleWidth = child.style?.get("width")?.isNotEmpty() ?: false
            if (!hasStyleWidth) {
                child.rect.width = content.width - child.margin.horizontal()
            }
            cursorY = child.rect.y + child.rect.height + child.margin.bottom
        }

        // 自动高度
        if (box.rect.height == 0.0f && box.children.isNotEmpty()) {
            val last = box.children.last()
            box.rect.height = (last.rect.y + last.rect.height + last.margin.bottom
                + box.padding.bottom + box.border.bottom) - box.rect.y
        }
    }

    // 弹性布局子元素
    private fun layoutFlexChildren(box: LayoutBox) {
        val content = contentArea(box)
        val direction = box.style?.get("flex-direction") ?: ""
        val isRow = direction.isEmpty() || direction == "row"

        if (isRow) {
            var cursorX = content.x
            var totalFlex = 0.0f
            var totalWidth = 0.0f

            // 第一遍计算自然宽度
            for (child in box.children) {
                calculateLayout(child)
                val flexGrow = child.style?.get("flex-grow")?.toFloatOrNull() ?: 0.0f
                if (flexGrow > 0.0f) {
                    totalFlex += flexGrow
                } else {
                    totalWidth += child.rect.width + child.margin.horizontal()
                }
            }

            // 分配剩余空间
            val remaining = (content.width - totalWidth).coerceAtLeast(0.0f)
            val extraPerFlex = if (totalFlex > 0.0f) remaining / totalFlex else 0.0f

            for (child in box.children) {
                val flexGrow = child.style?.get("flex-grow")?.toFloatOrNull() ?: 0.0f
                if (flexGrow > 0.0f) {
                    child.rect.width = extraPerFlex * flexGrow
                }
                child.rect.x = cursorX + child.margin.left
                child.rect.y = content.y + child.margin.top
                cursorX = child.rect.x + child.rect.width + child.margin.right
            }
        } else {
            layoutBlockChildren(box)
        }

        // 自动高度
        if (box.rect.height == 0.0f && box.children.isNotEmpty()) {
            val last = box.children.last()
            box.rect.height = (last.rect.y + last.rect.height + last.margin.bottom
                + box.padding.bottom + box.border.bottom) - box.rect.y
        }
    }

    // 布局内联子元素
    private fun layoutInlineChildren(box: LayoutBox) {
        for (child in box.children) {
            calculateLayout(child)
        }
    }

    // 定位子元素
    private fun positionChildren(box: LayoutBox) {
        if (box.boxType == BoxType.Anonymous) {
            layoutInlineLine(box)
        }
        if (box.boxType == BoxType.Block) {
            val hasInlineChildren = box.children.any { it.boxType == BoxType.Anonymous }
            if (hasInlineChildren) {
                for (child in box.children) {
                    if (child.boxType == BoxType.Anonymous) {
                        layoutInlineLine(child)
                    }
                }
            }
        }
    }

    // 行内布局
    private fun layoutInlineLine(box: LayoutBox) {
        val content = contentArea(box)
        var cursorX = content.x
        var cursorY = content.y
        var maxLineHeight = 0.0f

        for (child in box.children) {
            calculateLayout(child)
            val fontSize = child.style?.length("font-size", 16.0f) ?: 16.0f
            val estimatedWidth = when (child.boxType) {
                BoxType.Text -> measureTextWidth(child, fontSize)
                else -> {
                    val charCount = getTextLength(child)
                    if (charCount > 0) measureTextWidth(child, fontSize) else child.rect.width
                }
            }
            child.rect.width = estimatedWidth
            child.rect.height = fontSize * 1.4f

            // 检查是否需要换行
            if (cursorX + estimatedWidth > content.x + content.width && cursorX > content.x) {
                cursorX = content.x
                cursorY += maxLineHeight
                maxLineHeight = 0.0f
            }

            child.rect.x = cursorX
            child.rect.y = cursorY
            cursorX += child.rect.width
            maxLineHeight = maxLineHeight.coerceAtLeast(child.rect.height)
        }

        // 设置匿名块高度
        if (box.children.isNotEmpty()) {
            val lastChild = box.children.last()
            box.rect.height = (lastChild.rect.y + lastChild.rect.height
                + box.padding.bottom + box.border.bottom) - box.rect.y
        }
    }

    // 测量文本宽度
    private fun measureTextWidth(box: LayoutBox, fontSize: Float): Float {
        val node = box.domNode ?: return 0.0f
        if (node.kind == NodeKind.Text) {
            val text = node.text?.trim() ?: return 0.0f
            var width = 0.0f
            for (ch in text) {
                width += fontSize * charWidthRatio(ch)
            }
            return width
        }
        return 0.0f
    }

    // 获取文本长度
    private fun getTextLength(box: LayoutBox): Int {
        val node = box.domNode ?: return 0
        if (node.kind == NodeKind.Text) {
            return node.text?.trim()?.length ?: 0
        }
        return 0
    }
}

// 字符宽度比例
private fun charWidthRatio(c: Char): Float {
    return when (c) {
        ' ' -> 0.35f
        '!' -> 0.33f
        '"' -> 0.45f
        '\'' -> 0.28f
        '(', ')', '[', ']', '{', '}' -> 0.35f
        ',' -> 0.28f
        '.' -> 0.28f
        ':', ';' -> 0.28f
        'I', 'l', '|', '/', '\\' -> 0.33f
        'f', 'i', 'j', 't' -> 0.35f
        '`' -> 0.28f
        in '0'..'9' -> 0.55f
        'a', 'b', 'c', 'd', 'e', 'g', 'h', 'k', 'n', 'o', 'p', 'q', 's', 'u', 'v', 'x', 'y', 'z' -> 0.52f
        'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'J', 'K', 'L', 'N', 'O', 'P', 'Q', 'R', 'S', 'T', 'U', 'V', 'X', 'Y', 'Z' -> 0.62f
        'm', 'w' -> 0.72f
        'M', 'W' -> 0.82f
        in '\u4e00'..'\u9fff', in '\u3000'..'\u303f', in '\uFF00'..'\uFFEF' -> 1.0f
        else -> 0.5f
    }
}

// 构建完整布局
fun buildLayout(
    doc: Node,
    styles: Map<Node, Style>,
    width: Float,
    height: Float
): LayoutBox {
    val engine = LayoutEngine(styles, width, height)
    return engine.layout(doc)
}

// 计算布局总高度
fun layoutHeight(root: LayoutBox): Float {
    return root.rect.y + root.rect.height
}