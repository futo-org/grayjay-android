package com.futo.platformplayer.engine.packages

import com.caoccao.javet.annotations.V8Convert
import com.caoccao.javet.annotations.V8Function
import com.caoccao.javet.annotations.V8Property
import com.caoccao.javet.enums.V8ConversionMode
import com.caoccao.javet.enums.V8ProxyMode
import com.caoccao.javet.values.reference.V8ValueObject
import com.futo.platformplayer.engine.V8Plugin
import com.futo.platformplayer.engine.internal.V8BindObject
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jsoup.Jsoup
import org.jsoup.nodes.Element


class PackageDOMParser : V8Package {
    override val name: String get() = "DOMParser";
    override val variableName: String = "domParser";

    constructor(v8Plugin: V8Plugin): super(v8Plugin) {

    }

    @V8Function
    fun parseFromString(html: String): DOMNode {
        val dom = DOMNode.parse(this, html);
        return dom;
    }

    @V8Convert(mode = V8ConversionMode.AllowOnly, proxyMode = V8ProxyMode.Class)
    class DOMNode: V8BindObject {
        @Transient
        private val _children: ArrayList<DOMNode> = arrayListOf();

        @Transient
        private val _element: Element;
        @Transient
        private val _package: PackageDOMParser;

        @V8Property
        fun nodeType(): String = _element.tagName();
        @V8Property
        fun childNodes(): List<DOMNode> {
            val results = _element.children().map { DOMNode(_package, it) }.toList();
            _children.addAll(results);
            return results;
        }
        @V8Property
        fun firstChild(): DOMNode? {
            val result = _element.firstElementChild()?.let { DOMNode(_package, it) };
            if(result != null)
                _children.add(result);
            return result;
        }
        @V8Property
        fun lastChild(): DOMNode? {
            val result = _element.firstElementChild()?.let { DOMNode(_package, it) };
            if(result != null)
                _children.add(result);
            return result;
        }
        @V8Property
        fun parentNode(): DOMNode? {
            val result = _element.parent()?.let { DOMNode(_package, it) };
            if(result != null)
                _children.add(result);
            return result;
        }
        @V8Property
        fun attributes(): Map<String, String> = _element.attributes().associate { Pair(it.key, it.value) }
        @V8Property
        fun innerHTML(): String = _element.html();
        @V8Property
        fun outerHTML(): String = _element.outerHtml();
        @V8Property
        fun textContent(): String = _element.text();
        @V8Property
        fun text(): String = _element.text().ifEmpty { data() };
        @V8Property
        fun data(): String = _element.data();

        @V8Property
        fun classList(): List<String> = _element.classNames().toList()

        @V8Property
        fun className(): String = _element.className();


        constructor(parser: PackageDOMParser, element: Element) {
            _package = parser;
            _element = element;
        }

        @V8Function
        fun getAttribute(key: String): String {
            return _element.attr(key);
        }
        @V8Function
        fun getElementById(id: String): DOMNode? {
            val node = _element.getElementById(id)?.let { DOMNode(_package, it) };
            if(node != null)
                _children.add(node);
            return node;
        }
        @V8Function
        fun getElementsByClassName(className: String): List<DOMNode> {
            val results = _element.getElementsByClass(className).map { DOMNode(_package, it) }.toList();
            _children.addAll(results);
            return results;
        }
        @V8Function
        fun getElementsByTagName(tagName: String): List<DOMNode> {
            val results = _element.getElementsByTag(tagName).map { DOMNode(_package, it) }.toList();
            _children.addAll(results);
            return results;
        }
        @V8Function
        fun getElementsByName(name: String): List<DOMNode> {
            val results = _element.getElementsByAttributeValue("name", name).map { DOMNode(_package, it) }.toList();
            _children.addAll(results);
            return results;
        }

        @V8Function
        fun querySelector(query: String): DOMNode? {
            val result = _element.selectFirst(query) ?: return null;
            return DOMNode(_package, result);
        }
        @V8Function
        fun querySelectorAll(query: String): List<DOMNode> {
            val results = _element.select(query) ?: return listOf();
            return results.map { DOMNode(_package, it) };
        }

        @V8Function
        override fun dispose() {
            for(child in _children)
                child.dispose();
            _children.clear();
            super.dispose();
        }

        @V8Function
        fun toNodeTree(): SerializedNode {
            return SerializedNode(
                childNodes().map { it.toNodeTree() },
                _element.tagName(),
                _element.text(),
                attributes()
            );
        }
        @V8Function
        fun toNodeTreeJson(): String {
            return Json.encodeToString(SerializedNode.serializer(), toNodeTree());
        }

        companion object {
            fun parse(parser: PackageDOMParser, str: String): DOMNode {
                return DOMNode(parser, Jsoup.parse(str));
            }
        }

        @Serializable
        class SerializedNode(
            val children: List<SerializedNode>,
            val name: String,
            val value: String,
            val attributes: Map<String, String>
        );
    }
}