// b站：绝望彻彻底底的绝望 [android/app/src/main/java/com/easyweb/app/engine/Css.kt]

package com.easyweb.app.engine

// 简单选择器类型
sealed class SimpleSelector {
    data class Tag(val name: String) : SimpleSelector()
    data class Class(val name: String) : SimpleSelector()
    data class Id(val name: String) : SimpleSelector()
    object Universal : SimpleSelector()

    // 判断是否匹配节点
    fun matches(node: Node): Boolean {
        return when (this) {
            is Universal -> true
            is Tag -> node.tagName() == name
            is Class -> {
                node.elementData?.classes()?.contains(name) ?: false
            }
            is Id -> {
                node.elementData?.id() == name
            }
        }
    }
}

// 复合选择器
data class CompoundSelector(val simples: List<SimpleSelector>) {
    // 计算特异性
    fun specificity(): Specificity {
        var id = 0
        var cls = 0
        var tag = 0
        for (s in simples) {
            when (s) {
                is SimpleSelector.Id -> id++
                is SimpleSelector.Class -> cls++
                is SimpleSelector.Tag -> tag++
                is SimpleSelector.Universal -> {}
            }
        }
        return Specificity(id, cls, tag)
    }

    // 判断是否匹配节点
    fun matches(node: Node): Boolean {
        return simples.all { it.matches(node) }
    }
}

// 组合选择器
sealed class Selector {
    data class Compound(val compound: CompoundSelector) : Selector()
    data class Descendant(val ancestor: Selector, val compound: CompoundSelector) : Selector()
    data class Child(val parent: Selector, val compound: CompoundSelector) : Selector()

    // 计算特异性
    fun specificity(): Specificity {
        return when (this) {
            is Compound -> compound.specificity()
            is Descendant -> {
                val s = ancestor.specificity()
                val c = compound.specificity()
                Specificity(s.id + c.id, s.cls + c.cls, s.tag + c.tag)
            }
            is Child -> {
                val s = parent.specificity()
                val c = compound.specificity()
                Specificity(s.id + c.id, s.cls + c.cls, s.tag + c.tag)
            }
        }
    }

    // 简易匹配
    fun matches(node: Node): Boolean {
        return when (this) {
            is Compound -> compound.matches(node)
            is Descendant -> compound.matches(node)
            is Child -> compound.matches(node)
        }
    }

    // 提取内部的复合选择器
    fun innerCompound(): CompoundSelector? {
        return when (this) {
            is Compound -> compound
            else -> null
        }
    }
}

// 特异性值
data class Specificity(val id: Int, val cls: Int, val tag: Int) : Comparable<Specificity> {
    override fun compareTo(other: Specificity): Int {
        val c1 = id.compareTo(other.id)
        if (c1 != 0) return c1
        val c2 = cls.compareTo(other.cls)
        if (c2 != 0) return c2
        return tag.compareTo(other.tag)
    }
}

// 声明
data class Declaration(val name: String, val value: String)

// 规则
data class Rule(
    val selectors: List<Selector>,
    val declarations: List<Declaration>
)

// 样式表
data class Stylesheet(val rules: List<Rule>)

// CSS解析器
class CssParser(private val input: String) {
    private val chars = input.toCharArray().toList()
    private var pos = 0

    private fun eof(): Boolean = pos >= chars.size
    private fun peek(): Char? = chars.getOrNull(pos)
    private fun next(): Char? {
        val c = chars.getOrNull(pos)
        if (c != null) pos++
        return c
    }
    private fun skipWhitespace() {
        consumeWhile { it.isWhitespace() }
    }
    private fun consumeWhile(pred: (Char) -> Boolean): String {
        val sb = StringBuilder()
        while (true) {
            val c = peek() ?: break
            if (pred(c)) {
                sb.append(c)
                pos++
            } else break
        }
        return sb.toString()
    }
    private fun consumeUntilAny(delims: List<Char>): String {
        val sb = StringBuilder()
        while (true) {
            val c = peek() ?: break
            if (c in delims) break
            sb.append(c)
            pos++
        }
        return sb.toString()
    }
    private fun consumeUntilStr(delim: String) {
        val d = delim.toCharArray().toList()
        while (true) {
            if (pos + d.size > chars.size) {
                pos = chars.size
                break
            }
            if (chars.subList(pos, pos + d.size) == d) {
                pos += d.size
                break
            }
            pos++
        }
    }

    // 解析完整样式表
    fun parseStylesheet(): Stylesheet {
        val rules = mutableListOf<Rule>()
        while (!eof()) {
            skipWhitespace()
            if (eof()) break
            // 跳过注释
            if (chars.subList(pos, (pos + 2).coerceAtMost(chars.size)) == listOf('/', '*')) {
                pos += 2
                consumeUntilStr("*/")
                continue
            }
            val rule = parseRule() ?: break
            rules.add(rule)
        }
        return Stylesheet(rules)
    }

    // 解析一条规则
    private fun parseRule(): Rule? {
        val selectors = parseSelectors() ?: return null
        skipWhitespace()
        if (peek() != '{') return null
        next()
        val declarations = parseDeclarations()
        skipWhitespace()
        if (peek() == '}') next()
        return Rule(selectors, declarations)
    }

    // 解析选择器列表
    private fun parseSelectors(): List<Selector>? {
        skipWhitespace()
        val selectors = mutableListOf<Selector>()
        while (true) {
            val sel = parseSelector() ?: break
            selectors.add(sel)
            skipWhitespace()
            when (peek()) {
                ',' -> {
                    next()
                    skipWhitespace()
                }
                '{' -> break
                else -> break
            }
        }
        return if (selectors.isEmpty()) null else selectors
    }

    // 解析一个选择器
    private fun parseSelector(): Selector? {
        skipWhitespace()
        val compound = parseCompoundSelector() ?: return null
        // 检查组合器
        skipWhitespace()
        return when (peek()) {
            '>' -> {
                next()
                val right = parseSelector() ?: return Selector.Compound(compound)
                Selector.Child(Selector.Compound(compound), right.innerCompound() ?: return Selector.Compound(compound))
            }
            '+', '~' -> {
                next()
                val right = parseSelector() ?: return Selector.Compound(compound)
                Selector.Descendant(Selector.Compound(compound), right.innerCompound() ?: return Selector.Compound(compound))
            }
            ',', '{', null -> Selector.Compound(compound)
            else -> {
                // 检查后代选择器
                val saved = pos
                skipWhitespace()
                if (peek() != null && peek() != '{' && peek() != ','
                    && peek() != '>' && peek() != '+' && peek() != '~' && peek() != ')') {
                    val right = parseSelector()
                    if (right != null) {
                        return Selector.Descendant(
                            Selector.Compound(compound),
                            right.innerCompound() ?: return Selector.Compound(compound)
                        )
                    }
                }
                pos = saved
                Selector.Compound(compound)
            }
        }
    }

    // 解析复合选择器
    private fun parseCompoundSelector(): CompoundSelector? {
        val simples = mutableListOf<SimpleSelector>()
        while (true) {
            skipWhitespace()
            when (peek()) {
                '.' -> {
                    next()
                    val name = consumeWhile { it.isLetterOrDigit() || it == '-' || it == '_' }
                    if (name.isEmpty()) return null
                    simples.add(SimpleSelector.Class(name))
                }
                '#' -> {
                    next()
                    val name = consumeWhile { it.isLetterOrDigit() || it == '-' || it == '_' }
                    if (name.isEmpty()) return null
                    simples.add(SimpleSelector.Id(name))
                }
                '*' -> {
                    next()
                    simples.add(SimpleSelector.Universal)
                }
                else -> {
                    val c = peek()
                    if (c != null && (c.isLetterOrDigit())) {
                        val name = consumeWhile { it.isLetterOrDigit() || it == '-' || it == '_' }
                        simples.add(SimpleSelector.Tag(name))
                    } else break
                }
            }
        }
        return if (simples.isEmpty()) null else CompoundSelector(simples)
    }

    // 解析声明列表
    private fun parseDeclarations(): List<Declaration> {
        val decls = mutableListOf<Declaration>()
        while (true) {
            skipWhitespace()
            // 跳过注释
            if (pos + 2 <= chars.size && chars.subList(pos, pos + 2) == listOf('/', '*')) {
                pos += 2
                consumeUntilStr("*/")
                continue
            }
            if (peek() == '}' || peek() == null) break
            val decl = parseDeclaration() ?: break
            decls.add(decl)
        }
        return decls
    }

    // 解析一条声明
    private fun parseDeclaration(): Declaration? {
        skipWhitespace()
        val name = consumeWhile { it.isLetterOrDigit() || it == '-' || it == '_' }
        if (name.isEmpty()) return null
        skipWhitespace()
        if (peek() != ':') return null
        next()
        skipWhitespace()
        var value = consumeUntilAny(listOf(';', '}', '!')).trim()
        // 跳过important
        skipWhitespace()
        if (peek() == '!') {
            consumeUntilAny(listOf(';', '}'))
        }
        skipWhitespace()
        if (peek() == ';') next()
        return Declaration(name, value)
    }

    companion object {
        // 解析CSS字符串
        fun parse(input: String): Stylesheet {
            val parser = CssParser(input)
            return parser.parseStylesheet()
        }
    }
}

// 查找匹配节点的规则并按特异性排序
fun matchRules(node: Node, stylesheet: Stylesheet): List<Rule> {
    val matched = mutableListOf<Rule>()
    for (rule in stylesheet.rules) {
        for (selector in rule.selectors) {
            if (selector.matches(node)) {
                matched.add(rule)
                break
            }
        }
    }
    // 按特异性排序
    matched.sortBy { rule ->
        rule.selectors.maxOfOrNull { it.specificity() } ?: Specificity(0, 0, 0)
    }
    return matched
}