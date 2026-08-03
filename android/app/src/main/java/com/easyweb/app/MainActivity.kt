// b站：绝望彻彻底底的绝望 [android/app/src/main/java/com/easyweb/app/MainActivity.kt]

package com.easyweb.app

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.easyweb.app.engine.*

// 主活动
class MainActivity : AppCompatActivity() {

    private lateinit var webView: EasyWebView
    private lateinit var urlInput: EditText
    private lateinit var progressBar: ProgressBar
    private lateinit var pageLoader: PageLoader
    private lateinit var network: NetworkEngine

    private var currentPageData: PageData? = null
    private var isLoading = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 创建网络引擎和页面加载器
        network = NetworkEngine()
        pageLoader = PageLoader(network)

        // 创建主布局
        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        // 创建顶部工具栏
        val toolbar = createToolbar()
        rootLayout.addView(toolbar)

        // 创建进度条
        progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dpToPx(3)
            )
            max = 100
            isIndeterminate = true
            visibility = View.GONE
        }
        rootLayout.addView(progressBar)

        // 创建WebView
        webView = EasyWebView(this)
        rootLayout.addView(webView, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
            1.0f
        ))

        setContentView(rootLayout)

        // 加载默认页面
        loadUrl("assets://test.html")
    }

    // 创建工具栏
    private fun createToolbar(): View {
        val toolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setPadding(dpToPx(4), dpToPx(4), dpToPx(4), dpToPx(4))
            setBackgroundColor(Color.parseColor("#f0f0f0"))
        }

        // 后退按钮
        toolbar.addView(Button(this).apply {
            text = "\u2190"
            setOnClickListener { goBack() }
            layoutParams = LinearLayout.LayoutParams(dpToPx(40), dpToPx(40))
        })

        // 前进按钮
        toolbar.addView(Button(this).apply {
            text = "\u2192"
            setOnClickListener { goForward() }
            layoutParams = LinearLayout.LayoutParams(dpToPx(40), dpToPx(40))
        })

        // 刷新按钮
        toolbar.addView(Button(this).apply {
            text = "\u21BB"
            setOnClickListener { refresh() }
            layoutParams = LinearLayout.LayoutParams(dpToPx(40), dpToPx(40))
        })

        // URL输入框
        urlInput = EditText(this).apply {
            hint = "输入网址"
            textSize = 14f
            layoutParams = LinearLayout.LayoutParams(0, dpToPx(40), 1.0f)
            setPadding(dpToPx(8), 0, dpToPx(8), 0)
            imeOptions = EditorInfo.IME_ACTION_GO
            setOnEditorActionListener { _, actionId, event ->
                if (actionId == EditorInfo.IME_ACTION_GO
                    || (event != null && event.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)) {
                    loadUrl(urlInput.text.toString())
                    true
                } else false
            }
        }
        toolbar.addView(urlInput)

        return toolbar
    }

    // 加载URL
    private fun loadUrl(url: String) {
        if (isLoading) return
        isLoading = true
        progressBar.visibility = View.VISIBLE

        // 在后台线程加载页面
        Thread {
            try {
                val pageData = if (url.startsWith("assets://")) {
                    loadFromAssets(url.removePrefix("assets://"))
                } else {
                    pageLoader.load(url, webView.width.toFloat().coerceAtLeast(320f),
                        webView.height.toFloat().coerceAtLeast(240f))
                }

                runOnUiThread {
                    currentPageData = pageData
                    urlInput.setText(pageData.url)
                    webView.loadPageData(pageData)
                    isLoading = false
                    progressBar.visibility = View.GONE

                    // 显示加载错误
                    if (pageData.error != null) {
                        urlInput.error = pageData.error
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    val errorPage = PageData(
                        url = url,
                        title = "加载失败",
                        dom = Node.createDocument(),
                        stylesheet = Stylesheet(emptyList()),
                        styles = emptyMap(),
                        layoutBox = null,
                        error = "加载失败: ${e.localizedMessage ?: e.message}"
                    )
                    currentPageData = errorPage
                    webView.loadPageData(errorPage)
                    isLoading = false
                    progressBar.visibility = View.GONE
                }
            }
        }.start()
    }

    // 从assets加载HTML文件
    private fun loadFromAssets(path: String): PageData {
        return try {
            val html = assets.open(path).bufferedReader().use { it.readText() }
            val dom = Parser.parse(html)
            val title = extractTitle(dom)
            val cssText = StringBuilder()
            val cssUrls = mutableListOf<String>()
            extractStyleResources(dom, cssText, cssUrls)
            val stylesheet = CssParser.parse(cssText.toString())
            val styles = computeStyles(dom, stylesheet)
            val w = webView.width.toFloat().coerceAtLeast(320f)
            val h = webView.height.toFloat().coerceAtLeast(240f)
            val layoutBox = buildLayout(dom, styles, w, h)

            PageData(
                url = "assets://$path",
                title = title,
                dom = dom,
                stylesheet = stylesheet,
                styles = styles,
                layoutBox = layoutBox
            )
        } catch (e: Exception) {
            PageData(
                url = "assets://$path",
                title = "加载失败",
                dom = Node.createDocument(),
                stylesheet = Stylesheet(emptyList()),
                styles = emptyMap(),
                layoutBox = null,
                error = "无法加载assets文件: ${e.localizedMessage ?: e.message}"
            )
        }
    }

    // 提取标题
    private fun extractTitle(node: Node): String {
        if (node.kind == NodeKind.Element && node.tagName() == "title") {
            for (child in node.children) {
                if (child.kind == NodeKind.Text) {
                    val text = child.text
                    if (text != null) return text.trim()
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
    private fun extractStyleResources(node: Node, cssText: StringBuilder, cssUrls: MutableList<String>) {
        if (node.kind == NodeKind.Element) {
            val tag = node.tagName() ?: ""
            if (tag == "style") {
                for (child in node.children) {
                    if (child.kind == NodeKind.Text && child.text != null) {
                        cssText.append(child.text)
                        cssText.append('\n')
                    }
                }
            }
            if (tag == "link") {
                val data = node.elementData ?: return
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

    // 后退
    private fun goBack() {
        // 页面历史导航预留
    }

    // 前进
    private fun goForward() {
        // 页面历史导航预留
    }

    // 刷新
    private fun refresh() {
        val url = urlInput.text.toString()
        if (url.isNotEmpty()) {
            loadUrl(url)
        }
    }

    // dp转px
    private fun dpToPx(dp: Int): Int {
        val density = resources.displayMetrics.density
        return (dp * density).toInt()
    }

    // 自定义WebView
    inner class EasyWebView(context: android.content.Context) : View(context) {

        private var pageData: PageData? = null
        private var renderer: Renderer? = null

        // 滚动位置
        private var scrollX = 0.0f
        private var scrollY = 0.0f
        private var maxScrollY = 0.0f
        private var maxScrollX = 0.0f

        // 缩放
        private var scaleFactor = 1.0f
        private var minScale = 0.5f
        private var maxScale = 3.0f

        // 手势检测
        private val scaleDetector = ScaleGestureDetector(context,
            object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                override fun onScale(detector: ScaleGestureDetector): Boolean {
                    scaleFactor = (scaleFactor * detector.scaleFactor)
                        .coerceIn(minScale, maxScale)
                    invalidate()
                    return true
                }
            })

        // 上次触摸位置
        private var lastTouchX = 0.0f
        private var lastTouchY = 0.0f
        private var isDragging = false

        // 加载页面数据
        fun loadPageData(data: PageData) {
            pageData = data
            val layoutBox = data.layoutBox
            if (layoutBox != null) {
                maxScrollX = (layoutBox.rect.width - width.toFloat()).coerceAtLeast(0.0f)
                maxScrollY = (layoutBox.rect.height - height.toFloat()).coerceAtLeast(0.0f)
            }
            scrollX = 0.0f
            scrollY = 0.0f
            scaleFactor = 1.0f
            invalidate()
        }

        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
            super.onSizeChanged(w, h, oldw, oldh)
            // 窗口大小变化时重新布局
            val data = pageData ?: return
            if (data.layoutBox != null && w > 0 && h > 0) {
                val relayout = pageLoader.relayout(data, w.toFloat(), h.toFloat())
                pageData = relayout
                maxScrollY = (relayout.layoutBox?.rect?.height ?: 0f - h.toFloat()).coerceAtLeast(0.0f)
                invalidate()
            }
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val data = pageData ?: return

            // 如果有错误信息，显示错误页面
            if (data.error != null) {
                drawErrorPage(canvas, data.error)
                return
            }

            val layoutBox = data.layoutBox ?: return

            // 创建渲染器并渲染
            renderer = Renderer(
                canvas = canvas,
                scrollX = scrollX,
                scrollY = scrollY,
                viewportWidth = width.toFloat() / scaleFactor,
                viewportHeight = height.toFloat() / scaleFactor,
                scaleFactor = scaleFactor
            )
            renderer?.render(layoutBox)
        }

        // 绘制错误页面
        private fun drawErrorPage(canvas: Canvas, error: String) {
            canvas.drawColor(Color.WHITE)
            val paint = Paint().apply {
                color = Color.RED
                textSize = 18f
            }
            canvas.drawText("加载失败", 20f, 60f, paint)
            paint.color = Color.DKGRAY
            paint.textSize = 14f
            canvas.drawText(error, 20f, 100f, paint)
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            scaleDetector.onTouchEvent(event)

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    lastTouchX = event.x
                    lastTouchY = event.y
                    isDragging = true
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (isDragging) {
                        val dx = lastTouchX - event.x
                        val dy = lastTouchY - event.y
                        scrollX = (scrollX + dx).coerceIn(0.0f, maxScrollX)
                        scrollY = (scrollY + dy).coerceIn(0.0f, maxScrollY)
                        lastTouchX = event.x
                        lastTouchY = event.y
                        invalidate()
                        return true
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    isDragging = false
                }
            }
            return super.onTouchEvent(event)
        }
    }
}