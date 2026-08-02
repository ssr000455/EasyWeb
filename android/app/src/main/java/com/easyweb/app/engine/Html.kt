// b站：绝望彻彻底底的绝望 [android/app/src/main/java/com/easyweb/app/engine/Html.kt]

package com.easyweb.app.engine

import java.util.HashMap

// 词法分析产生的Token类型
sealed class Token {
    object DocType : Token()
    data class StartTag(
        val tag: String,
        val attrs: Map<String, String> = emptyMap(),
        val selfClosing: Boolean = false
    ) : Token()
    data class EndTag(val tag: String) : Token()
    data class Comment(val content: String) : Token()
    data class Text(val content: String) : Token()
}

// 自闭合标签列表
private val VOID_ELEMENTS = setOf(
    "area", "base", "br", "col", "embed", "hr", "img", "input",
    "link", "meta", "param", "source", "track", "wbr"
)

// 词法分析器
class Tokenizer(private val input: String) {
    private val chars = input.toCharArray().toList()
    private var pos = 0

    // 是否到达末尾
    private fun eof(): Boolean = pos >= chars.size
    // 查看当前字符
    private fun peek(): Char? = chars.getOrNull(pos)
    // 读取下一个字符
    private fun next(): Char? {
        val c = chars.getOrNull(pos)
        if (c != null) pos++
        return c
    }
    // 连续读取满足条件的字符
    private fun consumeWhile(pred: (Char) -> Boolean): String {
        val sb = StringBuilder()
        while (true) {
            val c = peek() ?: break
            if (pred(c)) {
                sb.append(c)
                pos++
            } else {
                break
            }
        }
        return sb.toString()
    }
    // 读取直到遇到指定字符
    private fun consumeUntil(pred: (Char) -> Boolean): String {
        val sb = StringBuilder()
        while (true) {
            val c = peek() ?: break
            if (pred(c)) break
            sb.append(c)
            pos++
        }
        return sb.toString()
    }
    // 跳过空白字符
    private fun skipWhitespace() {
        consumeWhile { it.isWhitespace() }
    }
    // 读取引号内的属性值
    private fun readAttrValue(quote: Char): String {
        val sb = StringBuilder()
        while (true) {
            val c = next() ?: break
            if (c == quote) break
            sb.append(c)
        }
        return sb.toString()
    }
    // 读取属性列表
    private fun readAttrs(): Map<String, String> {
        val attrs = HashMap<String, String>()
        while (true) {
            skipWhitespace()
            val c = peek()
            if (c == null || c == '>' || c == '/') break
            val name = consumeWhile { !it.isWhitespace() && it != '=' && it != '>' && it != '/' }
            if (name.isEmpty()) break
            skipWhitespace()
            val value = if (peek() == '=') {
                next()
                skipWhitespace()
                when (next()) {
                    '"' -> readAttrValue('"')
                    '\'' -> readAttrValue('\'')
                    else -> {
                        val sb = StringBuilder()
                        // 回退并重新读取未引用的值
                        pos--
                        sb.append(consumeWhile { !it.isWhitespace() && it != '>' })
                        sb.toString()
                    }
                }
            } else {
                ""
            }
            attrs[name.lowercase()] = value
        }
        return attrs
    }
    // 读取标签名并转为小写
    private fun readTagName(): String {
        return consumeWhile { it.isLetterOrDigit() || it == '-' || it == '_' }.lowercase()
    }
    // 读取直到遇到指定字符串
    private fun consumeUntilStr(delim: String): String {
        val d = delim.toCharArray().toList()
        val sb = StringBuilder()
        while (true) {
            if (pos + d.size > chars.size) {
                while (pos < chars.size) {
                    sb.append(chars[pos])
                    pos++
                }
                break
            }
            if (chars.subList(pos, pos + d.size) == d) {
                pos += d.size
                break
            }
            sb.append(chars[pos])
            pos++
        }
        return sb.toString()
    }

    // 执行词法分析
    fun tokenize(): List<Token> {
        val tokens = mutableListOf<Token>()
        while (!eof()) {
            when (peek()) {
                '<' -> {
                    next()
                    when (peek()) {
                        '/' -> {
                            // 结束标签
                            next()
                            skipWhitespace()
                            val tag = readTagName()
                            skipWhitespace()
                            consumeUntil { it == '>' }
                            next()
                            tokens.add(Token.EndTag(tag))
                        }
                        '!' -> {
                            // 注释或文档类型
                            next()
                            if (peek() == '-') {
                                next()
                                if (peek() == '-') {
                                    next()
                                    val content = consumeUntilStr("-->")
                                    tokens.add(Token.Comment(content))
                                } else {
                                    tokens.add(Token.Text("<!-"))
                                }
                            } else {
                                consumeUntil { it == '>' }
                                next()
                                tokens.add(Token.DocType)
                            }
                        }
                        '?' -> {
                            // 处理指令跳过
                            consumeUntil { it == '>' }
                            next()
                        }
                        else -> {
                            // 开始标签
                            skipWhitespace()
                            val tag = readTagName()
                            val attrs = readAttrs()
                            val selfClosing = peek() == '/'
                            if (selfClosing) next()
                            if (peek() == '>') next()
                            tokens.add(Token.StartTag(tag, attrs, selfClosing))
                        }
                    }
                }
                else -> {
                    // 文本内容
                    val text = consumeUntil { it == '<' }
                    if (text.isNotEmpty()) {
                        tokens.add(Token.Text(text))
                    }
                }
            }
        }
        return tokens
    }
}

// HTML解析器
class Parser(private val tokens: List<Token>) {
    private var pos = 0
    private val tree = Node.createDocument()

    // 构造函数
    constructor() : this(emptyList())

    // 获取下一个Token
    private fun next(): Token? {
        if (pos < tokens.size) {
            return tokens[pos++]
        }
        return null
    }

    // 执行解析
    private fun run() {
        val stack = mutableListOf(tree)
        // 插入模式
        var inTable = false

        while (true) {
            val token = next() ?: break
            when (token) {
                is Token.StartTag -> {
                    val isVoid = token.tag in VOID_ELEMENTS
                    val node = Node.createElement(token.tag, token.attrs)

                    // 处理表格嵌套
                    if (inTable && token.tag == "table") {
                        popUntil(stack, "table")
                        inTable = false
                    }

                    val parent = stack.lastOrNull()
                    parent?.append(node)

                    if (!isVoid && !token.selfClosing) {
                        stack.add(node)
                        // 跟踪表格模式
                        if (token.tag == "table") {
                            inTable = true
                        } else if (token.tag == "tbody" || token.tag == "thead"
                            || token.tag == "tfoot" || token.tag == "tr"
                            || token.tag == "td" || token.tag == "th") {
                            // 保持表格模式
                        } else if (inTable && token.tag != "caption") {
                            inTable = false
                        }
                    }
                }
                is Token.EndTag -> {
                    if (token.tag == "table") {
                        inTable = false
                    }
                    popUntil(stack, token.tag)
                }
                is Token.Text -> {
                    val parent = stack.lastOrNull()
                    if (parent != null) {
                        val parentTag = parent.tagName()
                        if (parentTag != "html" && parentTag != "head") {
                            parent.append(Node.createText(token.content))
                        }
                    }
                }
                is Token.Comment, is Token.DocType -> {
                    // 忽略
                }
            }
        }
    }

    // 从栈中弹出直到找到匹配的标签
    private fun popUntil(stack: MutableList<Node>, tag: String) {
        for (i in (stack.size - 1) downTo 1) {
            if (stack[i].tagName() == tag) {
                while (stack.size > i + 1) {
                    stack.removeAt(stack.size - 1)
                }
                return
            }
        }
    }

    companion object {
        // 解析HTML字符串
        fun parse(html: String): Node {
            val tokenizer = Tokenizer(html)
            val tokens = tokenizer.tokenize()
            val parser = Parser(tokens)
            parser.run()
            return parser.tree
        }
    }
}