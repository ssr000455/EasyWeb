// b站：绝望彻彻底底的绝望 android/app/src/main/java/com/easyweb/app/engine/DomBridge.kt
// JS 与 Kotlin DOM 树之间的桥接层

package com.easyweb.app.engine

import org.json.JSONArray
import org.json.JSONObject

/**
 * DOM 桥接
 * 连接 JS 引擎与 Kotlin DOM 树
 * 通过 __easyweb_bridge(method, jsonArgs) 协议与 JS 通信
 */
class DomBridge(
    private val jsEngine: JsEngine,
    private val historyStack: HistoryStack? = null
) {

    private var documentNode: Node? = null
    private val nodeRegistry = HashMap<String, Node>()
    private var nextNodeId = 0
    private val eventListeners = HashMap<String, MutableMap<String, MutableList<String>>>()

    companion object {
        private const val BRIDGE_JS = """
(function() {
    if (typeof __easyweb_dom_loaded !== 'undefined') return;
    __easyweb_dom_loaded = true;

    var nodes = {};
    var _listenerId = 0;
    var _listeners = {};

    function bridge(method, args) {
        try {
            var result = __easyweb_bridge(method, JSON.stringify(args || []));
            return JSON.parse(result);
        } catch(e) {
            return null;
        }
    }

    function createNodeRef(domId) {
        if (!domId) return null;
        if (nodes[domId]) return nodes[domId];
        var ref = new NodeRef(domId);
        nodes[domId] = ref;
        return ref;
    }

    function NodeRef(domId) {
        this.__domId = domId;
    }

    NodeRef.prototype = {
        get tagName() {
            return bridge('tagName', [this.__domId]) || '';
        },
        get id() {
            return bridge('id', [this.__domId]) || '';
        },
        set id(val) {
            bridge('setId', [this.__domId, val]);
        },
        get className() {
            return bridge('className', [this.__domId]) || '';
        },
        set className(val) {
            bridge('setClassName', [this.__domId, val]);
        },
        get innerHTML() {
            return bridge('innerHTML', [this.__domId]) || '';
        },
        set innerHTML(val) {
            bridge('setInnerHTML', [this.__domId, val]);
        },
        get textContent() {
            return bridge('textContent', [this.__domId]) || '';
        },
        set textContent(val) {
            bridge('setTextContent', [this.__domId, val]);
        },
        get parentNode() {
            var p = bridge('parentNode', [this.__domId]);
            return p ? createNodeRef(p) : null;
        },
        get children() {
            var ids = bridge('children', [this.__domId]) || [];
            return ids.map(function(id) { return createNodeRef(id); });
        },
        get firstChild() {
            var c = bridge('firstChild', [this.__domId]);
            return c ? createNodeRef(c) : null;
        },
        get lastChild() {
            var c = bridge('lastChild', [this.__domId]);
            return c ? createNodeRef(c) : null;
        },
        get nextSibling() {
            var s = bridge('nextSibling', [this.__domId]);
            return s ? createNodeRef(s) : null;
        },
        get previousSibling() {
            var s = bridge('previousSibling', [this.__domId]);
            return s ? createNodeRef(s) : null;
        },
        getAttribute: function(name) {
            return bridge('getAttribute', [this.__domId, name]);
        },
        setAttribute: function(name, value) {
            bridge('setAttribute', [this.__domId, name, value]);
        },
        removeAttribute: function(name) {
            bridge('removeAttribute', [this.__domId, name]);
        },
        appendChild: function(child) {
            if (child && child.__domId) {
                var id = bridge('appendChild', [this.__domId, child.__domId]);
                return id ? createNodeRef(id) : null;
            }
            return null;
        },
        removeChild: function(child) {
            if (child && child.__domId) {
                bridge('removeChild', [this.__domId, child.__domId]);
            }
        },
        insertBefore: function(newChild, refChild) {
            if (newChild && newChild.__domId) {
                var refId = refChild ? refChild.__domId : null;
                var id = bridge('insertBefore', [this.__domId, newChild.__domId, refId]);
                return id ? createNodeRef(id) : null;
            }
            return null;
        },
        replaceChild: function(newChild, oldChild) {
            if (newChild && newChild.__domId && oldChild && oldChild.__domId) {
                var id = bridge('replaceChild', [this.__domId, newChild.__domId, oldChild.__domId]);
                return id ? createNodeRef(id) : null;
            }
            return null;
        },
        cloneNode: function(deep) {
            var id = bridge('cloneNode', [this.__domId, !!deep]);
            return id ? createNodeRef(id) : null;
        },
        getElementsByClassName: function(name) {
            var ids = bridge('getElementsByClassName', [this.__domId, name]) || [];
            return ids.map(function(id) { return createNodeRef(id); });
        },
        getElementsByTagName: function(name) {
            var ids = bridge('getElementsByTagName', [this.__domId, name]) || [];
            return ids.map(function(id) { return createNodeRef(id); });
        },
        querySelector: function(sel) {
            var id = bridge('querySelector', [this.__domId, sel]);
            return id ? createNodeRef(id) : null;
        },
        querySelectorAll: function(sel) {
            var ids = bridge('querySelectorAll', [this.__domId, sel]) || [];
            return ids.map(function(id) { return createNodeRef(id); });
        },
        addEventListener: function(type, handler) {
            var id = '__evt_' + (++_listenerId);
            _listeners[id] = { handler: handler, type: type, nodeId: this.__domId };
            bridge('addEventListener', [this.__domId, type, id]);
        },
        removeEventListener: function(type, handler) {
            for (var id in _listeners) {
                var l = _listeners[id];
                if (l.nodeId === this.__domId && l.type === type && l.handler === handler) {
                    delete _listeners[id];
                    bridge('removeEventListener', [this.__domId, type, id]);
                    return;
                }
            }
        },
        click: function() {
            bridge('click', [this.__domId]);
        },
        focus: function() {
            bridge('focus', [this.__domId]);
        },
        get nodeType() {
            return bridge('nodeType', [this.__domId]) || 1;
        },
        get nodeName() {
            return bridge('nodeName', [this.__domId]) || '';
        },
        toString: function() {
            return '[object ' + (this.tagName || 'Node') + ']';
        }
    };

    // 重写 document 对象
    document.getElementById = function(id) {
        var domId = bridge('document.getElementById', [id]);
        return domId ? createNodeRef(domId) : null;
    };

    document.querySelector = function(sel) {
        var domId = bridge('document.querySelector', [sel]);
        return domId ? createNodeRef(domId) : null;
    };

    document.querySelectorAll = function(sel) {
        var ids = bridge('document.querySelectorAll', [sel]) || [];
        return ids.map(function(id) { return createNodeRef(id); });
    };

    document.createElement = function(tag) {
        var domId = bridge('document.createElement', [tag]);
        return domId ? createNodeRef(domId) : null;
    };

    document.createTextNode = function(text) {
        var domId = bridge('document.createTextNode', [text]);
        return domId ? createNodeRef(domId) : null;
    };

    document.getElementsByClassName = function(name) {
        var ids = bridge('document.getElementsByClassName', [name]) || [];
        return ids.map(function(id) { return createNodeRef(id); });
    };

    document.getElementsByTagName = function(name) {
        var ids = bridge('document.getElementsByTagName', [name]) || [];
        return ids.map(function(id) { return createNodeRef(id); });
    };

    document.addEventListener = function(type, handler) {
        var id = '__evt_' + (++_listenerId);
        _listeners[id] = { handler: handler, type: type, nodeId: 'document' };
        bridge('document.addEventListener', [type, id]);
    };

    document.removeEventListener = function(type, handler) {
        for (var id in _listeners) {
            var l = _listeners[id];
            if (l.nodeId === 'document' && l.type === type && l.handler === handler) {
                delete _listeners[id];
                bridge('document.removeEventListener', [type, id]);
                return;
            }
        }
    };

    window.addEventListener = function(type, handler) {
        var id = '__evt_' + (++_listenerId);
        _listeners[id] = { handler: handler, type: type, nodeId: 'window' };
    };

    window.removeEventListener = function(type, handler) {
        for (var id in _listeners) {
            var l = _listeners[id];
            if (l.nodeId === 'window' && l.type === type && l.handler === handler) {
                delete _listeners[id];
                return;
            }
        }
    };

    // 定义 document.title getter/setter
    Object.defineProperty(document, 'title', {
        get: function() { return bridge('document.title', []) || ''; },
        set: function(val) { bridge('document.setTitle', [val]); }
    });

    // 定义 document.body getter
    Object.defineProperty(document, 'body', {
        get: function() {
            var domId = bridge('document.body', []);
            return domId ? createNodeRef(domId) : null;
        }
    });

    // 定义 document.documentElement getter
    Object.defineProperty(document, 'documentElement', {
        get: function() {
            var domId = bridge('document.documentElement', []);
            return domId ? createNodeRef(domId) : null;
        }
    });

    // 定义 document.cookie
    document.cookie = '';

    // 动态创建 style 元素支持
    var oldCreateElement = document.createElement;
    document.createElement = function(tag) {
        var el = oldCreateElement(tag);
        if (tag && tag.toLowerCase() === 'style') {
            el._textContent = '';
            Object.defineProperty(el, 'textContent', {
                get: function() { return el._textContent || ''; },
                set: function(val) {
                    el._textContent = val;
                    bridge('createStyleElement', [val]);
                }
            });
            el.appendChild = function(textNode) {
                if (textNode && textNode.textContent) {
                    el._textContent = textNode.textContent;
                    bridge('createStyleElement', [textNode.textContent]);
                }
            };
        }
        return el;
    };

    // ─── window.history 支持 ───
    window.history = {};

    window.history.pushState = function(state, title, url) {
        bridge('history.pushState', [JSON.stringify(state), title, url || null]);
    };

    window.history.replaceState = function(state, title, url) {
        bridge('history.replaceState', [JSON.stringify(state), title, url || null]);
    };

    window.history.back = function() {
        bridge('history.back', []);
    };

    window.history.forward = function() {
        bridge('history.forward', []);
    };

    window.history.go = function(delta) {
        bridge('history.go', [delta || 0]);
    };

    Object.defineProperty(window.history, 'length', {
        get: function() { return bridge('history.length', []) || 0; }
    });

    Object.defineProperty(window.history, 'state', {
        get: function() {
            var s = bridge('history.state', []);
            try { return s ? JSON.parse(s) : null; } catch(e) { return null; }
        }
    });

    // 暴露 popstate 事件触发
    window.__firePopState = function(state, url) {
        var event = { type: 'popstate', state: state };
        if (window.onpopstate) {
            window.onpopstate(event);
        }
        if (window.dispatchEvent) {
            window.dispatchEvent(event);
        }
    };

    // 从 Kotlin 触发元素事件，派发给所有匹配的监听器
    window.__fireEvent = function(nodeId, type, eventJson) {
        var event = JSON.parse(eventJson);
        event.type = type;
        event.target = createNodeRef(nodeId);
        for (var id in _listeners) {
            var l = _listeners[id];
            if (l.nodeId === nodeId && l.type === type) {
                try { l.handler(event); } catch(e) {}
            }
        }
    };

    // 触发窗口级别事件（load, resize, scroll 等）
    window.__fireWindowEvent = function(type, eventJson) {
        var event = JSON.parse(eventJson);
        event.type = type;
        event.target = window;
        var handlerName = 'on' + type;
        if (typeof window[handlerName] === 'function') {
            try { window[handlerName](event); } catch(e) {}
        }
        for (var id in _listeners) {
            var l = _listeners[id];
            if (l.nodeId === 'window' && l.type === type) {
                try { l.handler(event); } catch(e) {}
            }
        }
    };
})();
"""
    }

    /**
     * 将桥接器挂载到 DOM 树
     */
    fun attachToDocument(doc: Node) {
        this.documentNode = doc
        nodeRegistry.clear()
        nextNodeId = 0

        // 注册文档节点
        registerNode(doc)

        // 注册为 JS 引擎的回调
        jsEngine.registerBridgeCallback { method, jsonArgs ->
            handleBridgeCall(method, jsonArgs)
        }

        // 注入 JS 桥接代码
        jsEngine.evaluate(BRIDGE_JS, "dom_bridge.js")
    }

    /**
     * 获取文档节点
     */
    fun getDocument(): Node? = documentNode

    // ─── 节点注册 ───

    /**
     * 注册节点并返回唯一 ID
     */
    private fun registerNode(node: Node): String {
        val id = "n${nextNodeId++}"
        nodeRegistry[id] = node
        return id
    }

    /**
     * 确保节点已注册，返回其 ID
     */
    private fun ensureNodeId(node: Node): String {
        for ((id, n) in nodeRegistry) {
            if (n === node) return id
        }
        return registerNode(node)
    }

    /**
     * 根据 ID 获取节点
     */
    private fun getNode(id: String): Node? = nodeRegistry[id]

    // ─── 桥接处理 ───

    /**
     * 处理来自 JS 的桥接调用
     */
    private fun handleBridgeCall(method: String, jsonArgs: String): String {
        try {
            val args = if (jsonArgs.isNotEmpty()) JSONArray(jsonArgs) else JSONArray()
            return processMethod(method, args)
        } catch (e: Exception) {
            return JSONObject().apply {
                put("error", e.message ?: "unknown error")
            }.toString()
        }
    }

    /**
     * 分发方法调用
     */
    private fun processMethod(method: String, args: JSONArray): String {
        return when (method) {
            // 文档级操作
            "document.getElementById" -> docGetElementById(args.optString(0, ""))
            "document.querySelector" -> docQuerySelector(args.optString(0, ""))
            "document.querySelectorAll" -> docQuerySelectorAll(args.optString(0, ""))
            "document.createElement" -> docCreateElement(args.optString(0, ""))
            "document.createTextNode" -> docCreateTextNode(args.optString(0, ""))
            "document.getElementsByClassName" -> docGetElementsByClassName(args.optString(0, ""))
            "document.getElementsByTagName" -> docGetElementsByTagName(args.optString(0, ""))
            "document.title" -> getDocumentTitle()
            "document.setTitle" -> setDocumentTitle(args.optString(0, ""))
            "document.body" -> getDocumentBody()
            "document.documentElement" -> getDocumentElement()
            "document.addEventListener" -> eventDocAddListener(args.optString(0, ""), args.optString(1, ""))
            "document.removeEventListener" -> eventDocRemoveListener(args.optString(0, ""), args.optString(1, ""))
            "createStyleElement" -> createStyleElement(args.optString(0, ""))

            // 元素操作
            "tagName" -> elementTagName(args.optString(0, ""))
            "id" -> elementId(args.optString(0, ""))
            "setId" -> elementSetId(args.optString(0, ""), args.optString(1, ""))
            "className" -> elementClassName(args.optString(0, ""))
            "setClassName" -> elementSetClassName(args.optString(0, ""), args.optString(1, ""))
            "innerHTML" -> elementInnerHTML(args.optString(0, ""))
            "setInnerHTML" -> elementSetInnerHTML(args.optString(0, ""), args.optString(1, ""))
            "textContent" -> elementTextContent(args.optString(0, ""))
            "setTextContent" -> elementSetTextContent(args.optString(0, ""), args.optString(1, ""))
            "getAttribute" -> elementGetAttribute(args.optString(0, ""), args.optString(1, ""))
            "setAttribute" -> elementSetAttribute(args.optString(0, ""), args.optString(1, ""), args.optString(2, ""))
            "removeAttribute" -> elementRemoveAttribute(args.optString(0, ""), args.optString(1, ""))
            "parentNode" -> elementParentNode(args.optString(0, ""))
            "children" -> elementChildren(args.optString(0, ""))
            "firstChild" -> elementFirstChild(args.optString(0, ""))
            "lastChild" -> elementLastChild(args.optString(0, ""))
            "nextSibling" -> elementNextSibling(args.optString(0, ""))
            "previousSibling" -> elementPreviousSibling(args.optString(0, ""))
            "appendChild" -> elementAppendChild(args.optString(0, ""), args.optString(1, ""))
            "removeChild" -> elementRemoveChild(args.optString(0, ""), args.optString(1, ""))
            "insertBefore" -> elementInsertBefore(args.optString(0, ""), args.optString(1, ""), args.optString(2, ""))
            "replaceChild" -> elementReplaceChild(args.optString(0, ""), args.optString(1, ""), args.optString(2, ""))
            "cloneNode" -> elementCloneNode(args.optString(0, ""), args.optBoolean(1, false))
            "getElementsByClassName" -> elementGetElementsByClassName(args.optString(0, ""), args.optString(1, ""))
            "getElementsByTagName" -> elementGetElementsByTagName(args.optString(0, ""), args.optString(1, ""))
            "querySelector" -> elementQuerySelector(args.optString(0, ""), args.optString(1, ""))
            "querySelectorAll" -> elementQuerySelectorAll(args.optString(0, ""), args.optString(1, ""))
            "nodeType" -> elementNodeType(args.optString(0, ""))
            "nodeName" -> elementNodeName(args.optString(0, ""))
            "addEventListener" -> eventAddListener(args.optString(0, ""), args.optString(1, ""), args.optString(2, ""))
            "removeEventListener" -> eventRemoveListener(args.optString(0, ""), args.optString(1, ""), args.optString(2, ""))
            "click" -> eventClick(args.optString(0, ""))
            "focus" -> eventFocus(args.optString(0, ""))

            // 历史栈操作
            "history.pushState" -> historyPushState(args.optString(0, "null"), args.optString(1, ""), args.optString(2, "null"))
            "history.replaceState" -> historyReplaceState(args.optString(0, "null"), args.optString(1, ""), args.optString(2, "null"))
            "history.back" -> historyBack()
            "history.forward" -> historyForward()
            "history.go" -> historyGo(args.optInt(0, 0))
            "history.length" -> historyLength()
            "history.state" -> historyState()
            else -> JSONObject().apply { put("error", "未知方法: $method") }.toString()
        }
    }

    // ─── 文档级方法 ───

    private fun docGetElementById(id: String): String {
        val doc = documentNode ?: return "null"
        return findElementById(doc, id)?.let { nodeId(it) } ?: "null"
    }

    private fun docQuerySelector(selector: String): String {
        val doc = documentNode ?: return "null"
        return findElementBySelector(doc, selector)?.let { nodeId(it) } ?: "null"
    }

    private fun docQuerySelectorAll(selector: String): String {
        val doc = documentNode ?: return "[]"
        val results = mutableListOf<String>()
        findAllBySelector(doc, selector, results)
        return JSONArray(results).toString()
    }

    private fun docCreateElement(tag: String): String {
        val node = Node.createElement(tag)
        return nodeId(node)
    }

    private fun docCreateTextNode(text: String): String {
        val node = Node.createText(text)
        return nodeId(node)
    }

    private fun docGetElementsByClassName(name: String): String {
        val doc = documentNode ?: return "[]"
        val results = mutableListOf<String>()
        findAllByClassName(doc, name, results)
        return JSONArray(results).toString()
    }

    private fun docGetElementsByTagName(name: String): String {
        val doc = documentNode ?: return "[]"
        val results = mutableListOf<String>()
        findAllByTagName(doc, name, results)
        return JSONArray(results).toString()
    }

    private fun getDocumentTitle(): String {
        val doc = documentNode ?: return ""
        return findElementByTag(doc, "title")?.let { titleNode ->
            titleNode.children.firstOrNull()?.text ?: ""
        } ?: ""
    }

    private fun setDocumentTitle(title: String) {
        val doc = documentNode ?: return
        var titleEl = findElementByTag(doc, "title")
        if (titleEl == null) {
            titleEl = Node.createElement("title")
            var head = findElementByTag(doc, "head")
            if (head == null) {
                head = Node.createElement("head")
                doc.children.add(0, head)
            }
            head.children.add(titleEl)
        }
        titleEl.children.clear()
        titleEl.children.add(Node.createText(title))
    }

    private fun getDocumentBody(): String {
        val doc = documentNode ?: return "null"
        return findElementByTag(doc, "body")?.let { nodeId(it) } ?: "null"
    }

    private fun getDocumentElement(): String {
        val doc = documentNode ?: return "null"
        // 文档的第一个元素子节点就是 html
        for (child in doc.children) {
            if (child.isElement()) return nodeId(child)
        }
        return "null"
    }

    private fun createStyleElement(cssText: String) {
        // 将动态创建的 style 内容添加到样式表中
        // 当前暂不处理动态样式更新
    }

    // ─── 历史栈方法 ───

    private fun historyPushState(state: String, title: String, url: String?): String {
        historyStack?.pushState(state.takeIf { it != "null" }, title, url.takeIf { it != "null" })
        return "null"
    }

    private fun historyReplaceState(state: String, title: String, url: String?): String {
        historyStack?.replaceState(state.takeIf { it != "null" }, title, url.takeIf { it != "null" })
        return "null"
    }

    private fun historyBack(): String {
        historyStack?.back()
        return "null"
    }

    private fun historyForward(): String {
        historyStack?.forward()
        return "null"
    }

    private fun historyGo(delta: Int): String {
        historyStack?.go(delta)
        return "null"
    }

    private fun historyLength(): String {
        return (historyStack?.length ?: 0).toString()
    }

    private fun historyState(): String {
        return historyStack?.currentState ?: "null"
    }

    // ─── 事件处理 ───

    /**
     * 注册元素事件监听器 (由 JS addEventListener 调用)
     */
    private fun eventAddListener(nodeId: String, type: String, listenerId: String): String {
        val listeners = eventListeners.getOrPut(nodeId) { HashMap() }
        listeners.getOrPut(type) { mutableListOf() }.add(listenerId)
        return "null"
    }

    /**
     * 移除元素事件监听器
     */
    private fun eventRemoveListener(nodeId: String, type: String, listenerId: String): String {
        eventListeners[nodeId]?.get(type)?.remove(listenerId)
        return "null"
    }

    /**
     * 注册文档事件监听器
     */
    private fun eventDocAddListener(type: String, listenerId: String): String {
        return eventAddListener("document", type, listenerId)
    }

    /**
     * 移除文档事件监听器
     */
    private fun eventDocRemoveListener(type: String, listenerId: String): String {
        return eventRemoveListener("document", type, listenerId)
    }

    /**
     * 从 Kotlin 侧触发元素事件 (如点击)
     * 通过 JS 端 __fireEvent 派发给所有注册的 JS 监听器
     */
    fun triggerEvent(nodeId: String, type: String, eventData: JSONObject = JSONObject()) {
        eventData.put("type", type)
        eventData.put("target", nodeId)
        // 安全转义：将 eventJson 中的单引号转义
        val eventJson = eventData.toString().replace("\\", "\\\\").replace("'", "\\'")
        jsEngine.evaluate("__fireEvent('$nodeId', '$type', '$eventJson')")
    }

    /**
     * 从 Kotlin 侧触发窗口级别事件 (load, resize, scroll 等)
     */
    fun fireWindowEvent(type: String, eventData: JSONObject = JSONObject()) {
        eventData.put("type", type)
        eventData.put("target", "window")
        val eventJson = eventData.toString().replace("\\", "\\\\").replace("'", "\\'")
        jsEngine.evaluate("__fireWindowEvent('$type', '$eventJson')")
    }

    /**
     * 处理元素 click 事件 (由 JS bridge 调用)
     */
    private fun eventClick(nodeId: String): String {
        triggerEvent(nodeId, "click", JSONObject().apply {
            put("bubbles", true)
            put("cancelable", true)
        })
        return "null"
    }

    /**
     * 处理元素 focus 事件
     */
    private fun eventFocus(nodeId: String): String {
        triggerEvent(nodeId, "focus", JSONObject().apply {
            put("bubbles", false)
            put("cancelable", false)
        })
        return "null"
    }

    // ─── 元素方法 ───

    private fun elementTagName(id: String): String {
        val node = getNode(id) ?: return ""
        return node.tagName() ?: ""
    }

    private fun elementId(id: String): String {
        val node = getNode(id) ?: return ""
        return node.elementData?.id() ?: ""
    }

    private fun elementSetId(id: String, value: String) {
        val node = getNode(id) ?: return
        node.elementData?.attrs?.put("id", value)
    }

    private fun elementClassName(id: String): String {
        val node = getNode(id) ?: return ""
        return node.elementData?.getAttr("class") ?: ""
    }

    private fun elementSetClassName(id: String, value: String) {
        val node = getNode(id) ?: return
        node.elementData?.attrs?.put("class", value)
    }

    private fun elementInnerHTML(id: String): String {
        val node = getNode(id) ?: return ""
        val sb = StringBuilder()
        serializeInnerHtml(node, sb)
        return sb.toString()
    }

    private fun elementSetInnerHTML(id: String, html: String) {
        val node = getNode(id) ?: return
        node.children.clear()
        if (html.isNotBlank()) {
            // 将 HTML 片段包装在临时 div 中解析
            val fragmentDoc = Parser.parse("<div>$html</div>")
            // 找到临时 div 的子节点
            for (child in fragmentDoc.children) {
                if (child.isElement() && child.tagName() == "div") {
                    node.children.addAll(child.children)
                    break
                }
            }
        }
    }

    private fun elementTextContent(id: String): String {
        val node = getNode(id) ?: return ""
        val sb = StringBuilder()
        collectTextContent(node, sb)
        return sb.toString()
    }

    private fun elementSetTextContent(id: String, text: String) {
        val node = getNode(id) ?: return
        node.children.clear()
        node.children.add(Node.createText(text))
    }

    private fun elementGetAttribute(id: String, name: String): String {
        val node = getNode(id) ?: return "null"
        return node.elementData?.getAttr(name) ?: "null"
    }

    private fun elementSetAttribute(id: String, name: String, value: String) {
        val node = getNode(id) ?: return
        node.elementData?.attrs?.put(name, value)
    }

    private fun elementRemoveAttribute(id: String, name: String) {
        val node = getNode(id) ?: return
        node.elementData?.attrs?.remove(name)
    }

    private fun elementParentNode(id: String): String {
        val node = getNode(id) ?: return "null"
        return node.parent?.let { nodeId(it) } ?: "null"
    }

    private fun elementChildren(id: String): String {
        val node = getNode(id) ?: return "[]"
        val ids = node.children.filter { it.isElement() }.map { ensureNodeId(it) }
        return JSONArray(ids).toString()
    }

    private fun elementFirstChild(id: String): String {
        val node = getNode(id) ?: return "null"
        return node.children.firstOrNull()?.let { nodeId(it) } ?: "null"
    }

    private fun elementLastChild(id: String): String {
        val node = getNode(id) ?: return "null"
        return node.children.lastOrNull()?.let { nodeId(it) } ?: "null"
    }

    private fun elementNextSibling(id: String): String {
        val node = getNode(id) ?: return "null"
        val parent = node.parent ?: return "null"
        val idx = parent.children.indexOf(node)
        if (idx < 0 || idx >= parent.children.size - 1) return "null"
        return nodeId(parent.children[idx + 1])
    }

    private fun elementPreviousSibling(id: String): String {
        val node = getNode(id) ?: return "null"
        val parent = node.parent ?: return "null"
        val idx = parent.children.indexOf(node)
        if (idx <= 0) return "null"
        return nodeId(parent.children[idx - 1])
    }

    private fun elementAppendChild(parentId: String, childId: String): String {
        val parent = getNode(parentId) ?: return "null"
        val child = getNode(childId) ?: return "null"
        // 从原父节点移除
        child.parent?.children?.remove(child)
        child.parent = parent
        parent.children.add(child)
        return childId
    }

    private fun elementRemoveChild(parentId: String, childId: String): String {
        val parent = getNode(parentId) ?: return "null"
        val child = getNode(childId) ?: return "null"
        parent.children.remove(child)
        child.parent = null
        return "null"
    }

    private fun elementInsertBefore(parentId: String, newChildId: String, refChildId: String?): String {
        val parent = getNode(parentId) ?: return "null"
        val newChild = getNode(newChildId) ?: return "null"
        // 从原父节点移除
        newChild.parent?.children?.remove(newChild)
        newChild.parent = parent
        if (refChildId != null) {
            val refChild = getNode(refChildId)
            val idx = refChild?.let { parent.children.indexOf(it) } ?: -1
            if (idx >= 0) {
                parent.children.add(idx, newChild)
            } else {
                parent.children.add(newChild)
            }
        } else {
            parent.children.add(newChild)
        }
        return newChildId
    }

    private fun elementReplaceChild(parentId: String, newChildId: String, oldChildId: String): String {
        val parent = getNode(parentId) ?: return "null"
        val newChild = getNode(newChildId) ?: return "null"
        val oldChild = getNode(oldChildId) ?: return "null"
        val idx = parent.children.indexOf(oldChild)
        if (idx >= 0) {
            newChild.parent?.children?.remove(newChild)
            newChild.parent = parent
            parent.children[idx] = newChild
            oldChild.parent = null
        }
        return newChildId
    }

    private fun elementCloneNode(id: String, deep: Boolean): String {
        val node = getNode(id) ?: return "null"
        val clone = cloneNode(node, deep)
        return nodeId(clone)
    }

    private fun elementGetElementsByClassName(id: String, name: String): String {
        val node = getNode(id) ?: return "[]"
        val results = mutableListOf<String>()
        findAllByClassName(node, name, results)
        return JSONArray(results).toString()
    }

    private fun elementGetElementsByTagName(id: String, name: String): String {
        val node = getNode(id) ?: return "[]"
        val results = mutableListOf<String>()
        findAllByTagName(node, name, results)
        return JSONArray(results).toString()
    }

    private fun elementQuerySelector(id: String, selector: String): String {
        val node = getNode(id) ?: return "null"
        return findElementBySelector(node, selector)?.let { nodeId(it) } ?: "null"
    }

    private fun elementQuerySelectorAll(id: String, selector: String): String {
        val node = getNode(id) ?: return "[]"
        val results = mutableListOf<String>()
        findAllBySelector(node, selector, results)
        return JSONArray(results).toString()
    }

    private fun elementNodeType(id: String): String {
        val node = getNode(id) ?: return "0"
        return when (node.kind) {
            NodeKind.Element -> "1"
            NodeKind.Text -> "3"
            NodeKind.Document -> "9"
        }
    }

    private fun elementNodeName(id: String): String {
        val node = getNode(id) ?: return ""
        return when (node.kind) {
            NodeKind.Element -> node.tagName()?.uppercase() ?: ""
            NodeKind.Text -> "#text"
            NodeKind.Document -> "#document"
        }
    }

    // ─── 工具方法 ───

    /**
     * 获取节点 ID，如果未注册则先注册
     */
    private fun nodeId(node: Node): String {
        return ensureNodeId(node)
    }

    /**
     * 递归查找元素 ID
     */
    private fun findElementById(node: Node, id: String): Node? {
        if (node.isElement() && node.elementData?.id() == id) return node
        for (child in node.children) {
            val result = findElementById(child, id)
            if (result != null) return result
        }
        return null
    }

    /**
     * 递归查找标签
     */
    private fun findElementByTag(node: Node, tag: String): Node? {
        if (node.isElement() && node.tagName() == tag) return node
        for (child in node.children) {
            val result = findElementByTag(child, tag)
            if (result != null) return result
        }
        return null
    }

    /**
     * 按类名查找所有元素
     */
    private fun findAllByClassName(node: Node, name: String, results: MutableList<String>) {
        if (node.isElement()) {
            val classes = node.elementData?.classes() ?: emptyList()
            if (classes.contains(name)) {
                results.add(ensureNodeId(node))
            }
        }
        for (child in node.children) {
            findAllByClassName(child, name, results)
        }
    }

    /**
     * 按标签名查找所有元素
     */
    private fun findAllByTagName(node: Node, name: String, results: MutableList<String>) {
        if (node.isElement() && (name == "*" || node.tagName() == name)) {
            results.add(ensureNodeId(node))
        }
        for (child in node.children) {
            findAllByTagName(child, name, results)
        }
    }

    /**
     * 简单选择器查询（仅支持 tag, .class, #id）
     */
    private fun findElementBySelector(node: Node, selector: String): Node? {
        val sel = selector.trim()
        return when {
            sel.startsWith("#") -> {
                // #id
                findElementById(node, sel.substring(1))
            }
            sel.startsWith(".") -> {
                // .class
                val className = sel.substring(1)
                for (child in node.children) {
                    val result = findFirstByClass(child, className)
                    if (result != null) return result
                }
                null
            }
            else -> {
                // tag
                findElementByTag(node, sel.lowercase())
            }
        }
    }

    private fun findFirstByClass(node: Node, className: String): Node? {
        if (node.isElement()) {
            val classes = node.elementData?.classes() ?: emptyList()
            if (classes.contains(className)) return node
        }
        for (child in node.children) {
            val result = findFirstByClass(child, className)
            if (result != null) return result
        }
        return null
    }

    /**
     * 简单选择器查找所有
     */
    private fun findAllBySelector(node: Node, selector: String, results: MutableList<String>) {
        val sel = selector.trim()
        when {
            sel.startsWith("#") -> {
                findElementById(node, sel.substring(1))?.let {
                    results.add(ensureNodeId(it))
                }
            }
            sel.startsWith(".") -> {
                val className = sel.substring(1)
                findAllByClassName(node, className, results)
            }
            else -> {
                findAllByTagName(node, sel.lowercase(), results)
            }
        }
    }

    /**
     * 序列化内部 HTML
     */
    private fun serializeInnerHtml(node: Node, sb: StringBuilder) {
        for (child in node.children) {
            when (child.kind) {
                NodeKind.Element -> {
                    val data = child.elementData ?: continue
                    sb.append("<${data.tag}")
                    for ((k, v) in data.attrs) {
                        sb.append(" $k=\"${escapeHtml(v)}\"")
                    }
                    sb.append(">")
                    serializeInnerHtml(child, sb)
                    sb.append("</${data.tag}>")
                }
                NodeKind.Text -> {
                    sb.append(escapeHtml(child.text ?: ""))
                }
                NodeKind.Document -> {
                    serializeInnerHtml(child, sb)
                }
            }
        }
    }

    /**
     * 收集文本内容
     */
    private fun collectTextContent(node: Node, sb: StringBuilder) {
        when (node.kind) {
            NodeKind.Text -> sb.append(node.text ?: "")
            NodeKind.Element -> {
                for (child in node.children) {
                    collectTextContent(child, sb)
                }
            }
            NodeKind.Document -> {
                for (child in node.children) {
                    collectTextContent(child, sb)
                }
            }
        }
    }

    /**
     * 克隆节点
     */
    private fun cloneNode(node: Node, deep: Boolean): Node {
        val clone = when (node.kind) {
            NodeKind.Element -> {
                val data = node.elementData ?: return Node.createText("")
                Node.createElement(data.tag, data.attrs.toMap())
            }
            NodeKind.Text -> Node.createText(node.text ?: "")
            NodeKind.Document -> Node.createDocument()
        }
        if (deep) {
            for (child in node.children) {
                val childClone = cloneNode(child, true)
                childClone.parent = clone
                clone.children.add(childClone)
            }
        }
        return clone
    }

    /**
     * HTML 转义
     */
    private fun escapeHtml(s: String): String {
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;")
    }
}