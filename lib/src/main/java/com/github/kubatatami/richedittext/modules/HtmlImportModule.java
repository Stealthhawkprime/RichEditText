package com.github.kubatatami.richedittext.modules;

import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.SpannedString;

import com.github.kubatatami.richedittext.styles.base.MultiStyleController;
import com.github.kubatatami.richedittext.styles.base.SpanController;

import org.ccil.cowan.tagsoup.HTMLSchema;
import org.ccil.cowan.tagsoup.Parser;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.InputSource;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

import java.io.IOException;
import java.io.StringReader;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by Kuba on 02/01/15.
 */
public abstract class HtmlImportModule {

    private static final HTMLSchema schema = new HTMLSchema();
    private static int tagCounter = 0;
    private static Integer tagBaseCounter = null;
    private static boolean endingMode = false;

    public static Spanned fromHtml(String source, Collection<SpanController<?>> spanControllers) throws IOException{
        if (source == null || source.length() == 0) {
            return new SpannedString("");
        }
        tagBaseCounter = null;
        tagCounter = 0;
        endingMode = false;
        Parser parser = new Parser();
        try {
            parser.setProperty(Parser.schemaProperty, schema);
        } catch (org.xml.sax.SAXNotRecognizedException | org.xml.sax.SAXNotSupportedException e) {
            throw new RuntimeException(e);
        }

        HtmlToSpannedConverter converter = new HtmlToSpannedConverter(source, parser, spanControllers);
        return converter.convert();
    }


    static class HtmlToSpannedConverter implements ContentHandler {
        private String mSource;
        private XMLReader mReader;
        private SpannableStringBuilder mSpannableStringBuilder;
        private Collection<SpanController<?>> mSpanControllers;

        public HtmlToSpannedConverter(
                String source,
                Parser parser, Collection<SpanController<?>> spanControllers) {
            mSource = source;
            mSpanControllers = spanControllers;
            mSpannableStringBuilder = new SpannableStringBuilder();
            mReader = parser;
        }

        public Spanned convert() throws IOException {

            mReader.setContentHandler(this);
            try {
                mReader.parse(new InputSource(new StringReader(mSource)));
            } catch (SAXException e) {
                throw new IOException(e.getMessage());
            }

            return mSpannableStringBuilder;
        }

        private void handleStartTag(String tag, Attributes attributes) throws SAXException {
            if (tag.equals("br")) {
                mSpannableStringBuilder.append('\n');
                return;
            }

            String styles = attributes.getValue("style");
            Map<String, String> styleMap = new HashMap<>();
            if (styles != null) {
                for (String style : styles.split(";")) {
                    if (style.length() > 0) {
                        String[] nameValue = style.split(":");
                        if (nameValue.length == 2) {
                            styleMap.put(nameValue[0].trim(), nameValue[1].trim());
                        }
                    }
                }
            }
            for (SpanController<?> spanController : mSpanControllers) {
                Object object = spanController.createSpanFromTag(tag, styleMap, attributes);
                if (object != null) {
                    start(mSpannableStringBuilder, object);
                    if (endingMode) {
                        throw new SAXException("Start new tag(" + tag + " " + attrToString(attributes) + ") before end previous");
                    }
                    tagCounter++;
                    return;
                }
            }
            if (!tag.equals("html") && !tag.equals("body") && !(tag.equals("p") && attributes.getLength() == 0)) {
                throw new SAXException("Unsupported tag: " + tag + " " + attrToString(attributes));
            }
        }

        private String attrToString(Attributes attrs){
            StringBuilder builder=new StringBuilder();
            for(int i=0;i<attrs.getLength();i++){
                builder.append(" ");
                builder.append(attrs.getLocalName(i));
                builder.append("=");
                builder.append(attrs.getValue(i));
            }
            return builder.toString();
        }

        private void handleEndTag(String tag) throws SAXException {

            for (SpanController<?> spanController : mSpanControllers) {
                Class<?> spanClass = spanController.spanFromEndTag(tag);
                if (spanClass != null) {
                    if (end(mSpannableStringBuilder, spanClass, spanController)) {
                        tagCounter--;
                        endingMode = tagCounter > tagBaseCounter;
                        return;
                    }
                }
            }
        }

        private static Object getLast(Spanned text, Class kind, SpanController<?> spanController) {
        /*
         * This knows that the last returned object from getSpans()
         * will be the most recently added.
         */
            Object[] objs = text.getSpans(0, text.length(), kind);

            if (objs.length == 0) {
                return null;
            } else {
                if(spanController instanceof MultiStyleController){
                    for (Object obj : objs) {
                        int flag = text.getSpanFlags(obj);
                        if (flag == Spannable.SPAN_MARK_MARK) {
                            return obj;
                        }
                    }
                }else {
                    for (int i = objs.length - 1; i >= 0; i--) {
                        int flag = text.getSpanFlags(objs[i]);
                        if (flag == Spannable.SPAN_MARK_MARK) {
                            return objs[i];
                        }
                    }
                }
                return null;
            }
        }

        private static void start(SpannableStringBuilder text, Object mark) {
            int len = text.length();
            text.setSpan(mark, len, len, Spannable.SPAN_MARK_MARK);
        }

        private static boolean end(SpannableStringBuilder text, Class kind, SpanController<?> spanController) {
            int len = text.length();
            Object obj = getLast(text, kind,spanController);

            if (obj == null) {
                return false;
            }

            boolean accept = spanController.acceptSpan(obj);
            if (!accept) {
                return false;
            }

            int where = text.getSpanStart(obj);

            text.removeSpan(obj);

            if (where != len && where != -1) {
                text.setSpan(obj, where, len, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                return true;
            } else {
                return false;
            }
        }


        public void setDocumentLocator(Locator locator) {
        }

        public void startDocument() throws SAXException {
        }

        public void endDocument() throws SAXException {
        }

        public void startPrefixMapping(String prefix, String uri) throws SAXException {
        }

        public void endPrefixMapping(String prefix) throws SAXException {
        }

        public void startElement(String uri, String localName, String qName, Attributes attributes)
                throws SAXException {
            handleStartTag(localName, attributes);
        }

        public void endElement(String uri, String localName, String qName) throws SAXException {
            handleEndTag(localName);
        }

        public void characters(char ch[], int start, int length) throws SAXException {
            if (tagBaseCounter == null) {
                tagBaseCounter = tagCounter;
            }
            StringBuilder sb = new StringBuilder();

            for (int i = 0; i < length; i++) {
                char c = ch[i + start];

                if (c == ' ' || c == '\n') {
                    char pred;
                    int len = sb.length();

                    if (len == 0) {
                        len = mSpannableStringBuilder.length();

                        if (len == 0) {
                            pred = '\n';
                        } else {
                            pred = mSpannableStringBuilder.charAt(len - 1);
                        }
                    } else {
                        pred = sb.charAt(len - 1);
                    }

                    if (pred != ' ' && pred != '\n') {
                        sb.append(' ');
                    }
                } else {
                    sb.append(c);
                }
            }

            mSpannableStringBuilder.append(sb);
        }

        public void ignorableWhitespace(char ch[], int start, int length) throws SAXException {
        }

        public void processingInstruction(String target, String data) throws SAXException {
        }

        public void skippedEntity(String name) throws SAXException {
        }


    }
}
