package com.futo.platformplayer.builders

import java.io.StringWriter

open class XMLBuilder {
    protected val writer = StringWriter();
    private var _indentation = 0;

    fun writeXmlHeader(version: String = "1.0", encoding: String = "UTF-8") {
        writer.write("<?xml version=\"${version}\" encoding=\"${encoding}\"?>\n");
    }
    fun tagClosed(tagName: String, vararg parameters: Pair<String, String>) { tagClosed(tagName, parameters.toMap()) }
    fun tagClosed(tagName: String, parameters: Map<String, String>) {
        writeTag(tagName, parameters, true);
    }
    fun tag(tagName: String, parameters: Map<String, String>, fill: (XMLBuilder)->Unit) {
        writeTag(tagName, parameters, false);
        fill(this);
        writeCloseTag(tagName);
    }
    fun valueTag(tagName: String, value: String){ valueTag(tagName, mapOf(), value); }
    fun valueTag(tagName: String, parameters: Map<String, String>, value: String) {
        writeTag(tagName, parameters, false, false);
        writer.write(value);
        writeCloseTag(tagName, false);
    }
    fun value(value: String) {
        writeIndentation(_indentation);
        writer.write(value + "\n");
    }

    protected fun writeTag(tagName: String, parameters: Map<String, String> = mapOf(), closed: Boolean = true, withNewLine: Boolean = true) {
        writeIndentation(_indentation)
        writer.write("<${tagName}");
        for(parameter in parameters)
            writer.write(" ${parameter.key}=\"${parameter.value}\"");

        if(closed)
            writer.write("/>");
        else {
            writer.write(">");
            _indentation++;
        }
        if(withNewLine)
            writer.write("\n");
    }
    protected fun writeCloseTag(tagName: String, withIndentation: Boolean = true) {
        _indentation--;
        if(withIndentation)
            writeIndentation(_indentation);
        writer.write("</${tagName}>\n");
    }
    protected fun writeIndentation(level: Int) {
        writer.write("".padStart(level * 3, ' '));
    }
    open fun build() : String {
        return writer.toString();
    }
}