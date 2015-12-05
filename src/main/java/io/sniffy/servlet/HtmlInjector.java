package io.sniffy.servlet;

import java.io.IOException;
import java.nio.charset.Charset;

class HtmlInjector {

    private final Buffer buffer;
    private final String characterEncoding;

    public HtmlInjector(Buffer buffer) {
        this(buffer, Charset.defaultCharset().name());
    }

    public HtmlInjector(Buffer buffer, String characterEncoding) {
        this.buffer = buffer;
        this.characterEncoding = characterEncoding;
    }

    /**
     * @param content to be inserted
     * @throws IOException
     */
    public void injectAtTheEnd(String content) throws IOException {

        String str = new String(buffer.trailingBytes(16 * 1024), characterEncoding).toLowerCase();
        StringBuilder sb = new StringBuilder(str);

        int htmlLIOf = sb.lastIndexOf("</html");
        int bodyLIOf = sb.lastIndexOf("</body");

        int i;

        if (-1 != bodyLIOf && (-1 == htmlLIOf || bodyLIOf < htmlLIOf)) {
            i = bodyLIOf;
        } else if (-1 != htmlLIOf) {
            i = htmlLIOf;
        } else {
            i = -1;
        }

        if (i == -1) {
            buffer.write(content.getBytes(characterEncoding));
        } else {
            int offset = str.substring(i).getBytes(characterEncoding).length;
            buffer.insertAt(buffer.size() - offset, content.getBytes(characterEncoding));
        }

    }

    /**
     * @param content to be inserted
     * @throws IOException
     */
    public void injectAtTheBeginning(String content) throws IOException {

        String str = new String(buffer.leadingBytes(16 * 1024), characterEncoding).toLowerCase();
        StringBuilder sb = new StringBuilder(str);

        int afterHtml = sb.indexOf("<html");
        if (afterHtml != -1) {
            afterHtml = sb.indexOf(">", afterHtml) + 1;
            if (0 == afterHtml) afterHtml = -1;
        }

        int afterHead = sb.indexOf("<head");
        if (afterHead != -1) {
            afterHead = sb.indexOf(">", afterHead) + 1;
            if (0 == afterHead) afterHead = -1;
        }

        int afterDocType = sb.indexOf("<!doctype");
        if (afterDocType != -1) {
            afterDocType = sb.indexOf(">", afterDocType) + 1;
            if (0 == afterDocType) afterDocType = -1;
        }

        int beforeScript = sb.indexOf("<script");

        // Find last meta tag bebfore first script tag

        String contentBeforeScript = -1 == beforeScript ? sb.toString() : sb.substring(0, beforeScript);

        int lastMetaOpeningTag = contentBeforeScript.lastIndexOf("<meta");
        int lastMetaClosingTag = contentBeforeScript.lastIndexOf("</meta");

        int afterLastMeta = -1;
        if (lastMetaOpeningTag != -1 || lastMetaClosingTag != -1) {
            afterLastMeta = contentBeforeScript.indexOf(">", Math.max(lastMetaOpeningTag, lastMetaClosingTag)) + 1;
            if (0 == afterLastMeta) afterLastMeta = -1;
        }

        int i;

        if (-1 != afterLastMeta) {
            i = afterLastMeta;
        } else if (-1 != afterHead && (-1 == beforeScript || afterHead <= beforeScript)) {
            i = afterHead;
        } else if (-1 != afterHtml && (-1 == beforeScript || afterHtml <= beforeScript)) {
            i = afterHtml;
        } else if (-1 != afterDocType && (-1 == beforeScript || afterDocType < beforeScript)) {
            i = afterDocType;
        } else if (-1 != beforeScript) {
            i = beforeScript;
        } else {
            i = 0;
        }

        int offset = str.substring(0, i).getBytes(characterEncoding).length;
        buffer.insertAt(offset, content.getBytes(characterEncoding));

    }

}
