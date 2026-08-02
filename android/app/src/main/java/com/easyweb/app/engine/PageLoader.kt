// b站：绝望彻彻底底的绝望 [android/app/src/main/java/com/easyweb/app/engine/PageLoader.kt]

package com.easyweb.app.engine

import java.util.HashMap

// 页面加载结果
data class PageData(
    val url: String,
    val title: String,
    val dom: Node,
    val stylesheet: Stylesheet,
    val styles: Map<Node, Style>,
    val layoutBox: LayoutBox?,
    val error: String? = null
)

// 页面加载状态
enum class PageState {
    Idle, Loading, Parsing, Rendering, Ready, Error
}

// 页面加载器 负责加载网页并编排渲染管线
class PageLoader(
    private val network: NetworkEngine = NetworkEngine(),
    private val jsEngine: JsEngine? = null
) {
    // 历史栈管理
    val historyStack = HistoryStack()

    // 加载URL并返回页面数据
    fun load(url: String, viewportWidth: Float, viewportHeight: Float): PageData {
        // 第一步：获取HTML
        val result = network.fetch(url)
        if (!result.success) {
            return PageData(
                url = url,
                title = "加载失败",
                dom = Node.createDocument(),
                stylesheet = Stylesheet(emptyList()),
                styles = emptyMap(),
                layoutBox = null,
                error = result.error ?: "未知错误"
            )
        }

        val baseUrl = result.url
        val html = result.content

        // 初始化历史栈
        historyStack.init(baseUrl)

        // 第二步：解析HTML
        val dom = Html.Parser.parse(html)

        // 提取页面标题
        val title = extractTitle(dom)

        // 第三步：收集内联样式和外联样式表
        val cssText = StringBuilder()
        val cssUrls = mutableListOf<String>()
        extractStyleResources(dom, cssText, cssUrls)

        // 第四步：获取外联样式表
        for (cssUrl in cssUrls) {
            val resolvedUrl = network.resolveUrl(baseUrl, cssUrl)
            val cssResult = network.fetch(resolvedUrl)
            if (cssResult.success) {
                cssText.append(cssResult.content)
                cssText.append('\n')
            }
        }

        // 第五步：解析样式表
        val stylesheet = CssParser.parse(cssText.toString())

        // 第六步：计算样式
        val styles = computeStyles(dom, stylesheet)

        // 第七步：执行JavaScript
        executeScripts(dom, baseUrl)

        // 第八步：布局
        val layoutBox = buildLayout(dom, styles, viewportWidth, viewportHeight)

        // 返回页面数据
        return PageData(
            url = baseUrl,
            title = title,
            dom = dom,
            stylesheet = stylesheet,
            styles = styles,
            layoutBox = layoutBox
        )
    }

    // 提取页面标题
    private fun extractTitle(node: Node): String {
        if (node.kind == NodeKind.Element && node.tagName() == "title") {
            for (child in node.children) {
                if (child.kind == NodeKind.Text && child.text != null) {
                    return child.text.trim()
                }
            }
        }
        for (child in node.children) {
            val result = extractTitle(child)
            if (result.isNotEmpty()) return result
        }
        return "无标题"
    }

    // 提取样式资源
    private fun extractStyleResources(
        node: Node,
        cssText: StringBuilder,
        cssUrls: MutableList<String>
    ) {
        if (node.kind == NodeKind.Element) {
            val tag = node.tagName() ?: ""
            val data = node.elementData ?: return

            if (tag == "style") {
                for (child in node.children) {
                    if (child.kind == NodeKind.Text && child.text != null) {
                        cssText.append(child.text)
                        cssText.append('\n')
                    }
                }
            }

            if (tag == "link") {
                val rel = data.getAttr("rel") ?: ""
                val href = data.getAttr("href") ?: ""
                if (rel == "stylesheet" && href.isNotEmpty()) {
                    cssUrls.add(href)
                }
            }
        }

        for (child in node.children) {
            extractStyleResources(child, cssText, cssUrls)
        }
    }

    // 执行JavaScript
    private fun executeScripts(node: Node, baseUrl: String) {
        val engine = jsEngine ?: return

        // 收集所有脚本
        val scripts = mutableListOf<Pair<String, String?>>() // script content, external url
        collectScripts(node, scripts)

        // 初始化JS引擎
        engine.init()

        // 注入页面上下文
        injectPageContext(engine, baseUrl)

        // 创建 DOM 桥接并挂载到文档
        val domBridge = DomBridge(engine, historyStack)
        domBridge.attachToDocument(node)

        // 执行脚本
        for ((content, url) in scripts) {
            if (url != null) {
                // 外联脚本
                val resolvedUrl = network.resolveUrl(baseUrl, url)
                val result = network.fetch(resolvedUrl)
                if (result.success) {
                    engine.evaluate(result.content)
                }
            } else {
                // 内联脚本
                engine.evaluate(content)
            }
        }
    }

    // 收集脚本
    private fun collectScripts(
        node: Node,
        scripts: MutableList<Pair<String, String?>>
    ) {
        if (node.kind == NodeKind.Element && node.tagName() == "script") {
            val data = node.elementData ?: return
            val src = data.getAttr("src")

            if (src != null) {
                // 外联脚本
                val content = StringBuilder()
                for (child in node.children) {
                    if (child.kind == NodeKind.Text && child.text != null) {
                        content.append(child.text)
                    }
                }
                scripts.add(Pair(content.toString(), src))
            } else {
                // 内联脚本
                val content = StringBuilder()
                for (child in node.children) {
                    if (child.kind == NodeKind.Text && child.text != null) {
                        content.append(child.text)
                    }
                }
                if (content.isNotEmpty()) {
                    scripts.add(Pair(content.toString(), null))
                }
            }
        }

        for (child in node.children) {
            collectScripts(child, scripts)
        }
    }

    // 注入页面上下文
    private fun injectPageContext(engine: JsEngine, baseUrl: String) {
        // 注入基础运行时环境（窗口、导航、控制台）
        // DOM API 由 DomBridge 通过 JS 注入提供
        engine.evaluate("""
            var window = this;
            window.location = { href: '$baseUrl' };
            window.navigator = { userAgent: 'EasyWeb/1.0' };
            window.screen = { width: 0, height: 0 };

            var console = {
                log: function(msg) { /* 空实现 */ },
                error: function(msg) { /* 空实现 */ },
                warn: function(msg) { /* 空实现 */ }
            };
        """.trimIndent())
    }

    // 更新视口后重新布局
    fun relayout(pageData: PageData, viewportWidth: Float, viewportHeight: Float): PageData {
        if (pageData.error != null) return pageData

        val layoutBox = buildLayout(
            pageData.dom,
            pageData.styles,
            viewportWidth,
            viewportHeight
        )

        return pageData.copy(layoutBox = layoutBox)
    }
}