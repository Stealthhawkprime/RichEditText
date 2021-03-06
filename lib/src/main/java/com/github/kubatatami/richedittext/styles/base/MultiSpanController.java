package com.github.kubatatami.richedittext.styles.base;

import android.text.Editable;
import android.text.Spanned;

import com.github.kubatatami.richedittext.BaseRichEditText;
import com.github.kubatatami.richedittext.modules.StyleSelectionInfo;

import org.xml.sax.Attributes;

import java.util.List;
import java.util.Map;

public abstract class MultiSpanController<T, Z> extends SpanController<T, Z> {

    SpanInfo<Z> spanInfo;

    private Z value;

    protected MultiSpanController(Class<T> clazz, String tagName) {
        super(clazz, tagName);
    }

    protected abstract Z getValueFromSpan(T span);

    public String getDebugValueFromSpan(T span) {
        return getValueFromSpan(span).toString();
    }

    public void performSpan(T span, Editable editable, StyleSelectionInfo styleSelectionInfo) {
        perform(getValueFromSpan(span), editable, styleSelectionInfo);
    }

    protected abstract T add(Z value, Editable editable, int selectionStart, int selectionEnd, int flags);

    protected void add(Z value, Editable editable, int selectionStart, int selectionEnd) {
        add(value, editable, selectionStart, selectionEnd, defaultFlags);
    }

    public void perform(Z value, Editable editable, StyleSelectionInfo styleSelectionInfo) {
        clearStyles(editable, styleSelectionInfo);
        selectStyle(value, editable, styleSelectionInfo);
        checkValueChange(value);
    }

    boolean selectStyle(Z value, Editable editable, StyleSelectionInfo styleSelectionInfo) {
        if (styleSelectionInfo.selectionStart == styleSelectionInfo.selectionEnd) {
            spanInfo = new SpanInfo<>(styleSelectionInfo.selectionStart, styleSelectionInfo.selectionEnd, defaultFlags, value);
            return false;
        } else {
            int finalSpanStart = styleSelectionInfo.selectionStart;
            int finalSpanEnd = styleSelectionInfo.selectionEnd;
            for (Object span : filter(editable.getSpans(styleSelectionInfo.selectionStart, styleSelectionInfo.selectionEnd, getClazz()))) {
                int spanStart = editable.getSpanStart(span);
                int spanEnd = editable.getSpanEnd(span);
                if (spanStart < finalSpanStart) {
                    finalSpanStart = spanStart;
                }
                if (spanEnd > finalSpanEnd) {
                    finalSpanEnd = spanEnd;
                }
                editable.removeSpan(span);
            }
            add(value, editable, finalSpanStart, finalSpanEnd);
            return true;
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public void clearStyle(Editable editable, Object span, StyleSelectionInfo styleSelectionInfo) {
        int spanStart = editable.getSpanStart(span);
        int spanEnd = editable.getSpanEnd(span);
        if (spanStart >= styleSelectionInfo.selectionStart && spanEnd <= styleSelectionInfo.selectionEnd) {
            editable.removeSpan(span);
        } else if (spanStart < styleSelectionInfo.selectionStart && spanEnd <= styleSelectionInfo.selectionEnd) {
            editable.removeSpan(span);
            add(getValueFromSpan((T) span), editable, spanStart, styleSelectionInfo.selectionStart);
        } else if (spanStart >= styleSelectionInfo.selectionStart && spanEnd > styleSelectionInfo.selectionEnd) {
            editable.removeSpan(span);
            add(getValueFromSpan((T) span), editable, styleSelectionInfo.selectionEnd, spanEnd);
        } else {
            editable.removeSpan(span);
            add(getValueFromSpan((T) span), editable, spanStart, styleSelectionInfo.selectionStart);
            add(getValueFromSpan((T) span), editable, styleSelectionInfo.selectionEnd, spanEnd);
        }
    }

    @Override
    public void clearStyles(Editable editable, StyleSelectionInfo styleSelectionInfo) {
        if (styleSelectionInfo.selectionStart != styleSelectionInfo.selectionEnd) {
            for (T span : filter(editable.getSpans(styleSelectionInfo.selectionStart, styleSelectionInfo.selectionEnd, getClazz()))) {
                clearStyle(editable, span, styleSelectionInfo);
            }
        }
    }

    @Override
    public void checkAfterChange(BaseRichEditText editText, StyleSelectionInfo styleSelectionInfo, boolean passive) {
        Z newValue = getCurrentValue(editText, styleSelectionInfo);
        checkValueChange(newValue);
        if (!passive && spanInfo != null) {
            add(spanInfo.span, editText.getText(), spanInfo.start, Math.min(spanInfo.end, editText.getText().length()), spanInfo.flags);
        }
        spanInfo = null;
    }

    protected void checkValueChange(Z newValue) {
        if ((newValue == null && value != null) || (newValue != null && value == null) || (value != null && !newValue.equals(value))) {
            value = newValue;
            invokeListeners(value);
        }
    }

    @SuppressWarnings("unchecked")
    public Z getCurrentValue(BaseRichEditText editText, StyleSelectionInfo styleSelectionInfo) {
        T[] spans = editText.getText().getSpans(styleSelectionInfo.realSelectionStart, styleSelectionInfo.realSelectionEnd, getClazz());
        spans = (T[]) filter(spans).toArray();
        Z newValue = spans.length > 0 ? getValueFromSpan(spans[0]) : getDefaultValue(editText);
        if (spans.length > 1 && styleSelectionInfo.realSelectionStart == styleSelectionInfo.realSelectionEnd) {
            if (editText.getText().getSpanFlags(spans[0]) == Spanned.SPAN_INCLUSIVE_EXCLUSIVE) {
                newValue = getValueFromSpan(spans[1]);
            } else {
                newValue = getValueFromSpan(spans[0]);
            }
        } else if (spans.length > 1) {
            for (T span : spans) {
                Z value = getValueFromSpan(span);
                if (!value.equals(newValue)) {
                    newValue = getMultiValue();
                    break;
                }
            }
        } else if (spans.length == 1 && !getValueFromSpan(spans[0]).equals(getDefaultValue(editText))) {
            int spanStart = editText.getText().getSpanStart(spans[0]);
            int spanEnd = editText.getText().getSpanEnd(spans[0]);
            if (spanStart > styleSelectionInfo.realSelectionStart || spanEnd < styleSelectionInfo.realSelectionEnd) {
                newValue = getMultiValue();
            }
        }
        return newValue;
    }

    @Override
    public void checkBeforeChange(final Editable editable, StyleSelectionInfo styleSelectionInfo, boolean added) {
        if (spanInfo != null && styleSelectionInfo.selectionStart == styleSelectionInfo.selectionEnd
                && spanInfo.start == styleSelectionInfo.selectionStart) {
            List<T> spans = filter(editable.getSpans(styleSelectionInfo.selectionStart, styleSelectionInfo.selectionEnd, getClazz()));
            add(spanInfo.span, editable, spanInfo.start, spanInfo.end, spanInfo.flags);

            if (spans.size() > 0) {
                final T span = spans.get(0);
                int spanStart = editable.getSpanStart(span);
                int spanEnd = editable.getSpanEnd(span);
                editable.removeSpan(span);
                if (spanInfo.start > spanStart && spanInfo.start < spanEnd) {
                    add(getValueFromSpan(span), editable, spanInfo.start, spanEnd, Spanned.SPAN_EXCLUSIVE_INCLUSIVE);
                    spanEnd = spanInfo.start;
                }
                if (styleSelectionInfo.selectionStart != 0 && styleSelectionInfo.selectionEnd == styleSelectionInfo.realSelectionEnd) {
                    spanInfo = new SpanInfo<>(spanStart, spanEnd, Spanned.SPAN_INCLUSIVE_EXCLUSIVE, getValueFromSpan(span));
                } else {
                    spanInfo = null;
                }
            } else {
                spanInfo = null;
            }
        }
    }

    public T createSpanFromTag(String tag, Map<String, String> styleMap, Attributes attributes) {
        if (tag.equals(tagName)) {
            return createSpan(styleMap, attributes);
        }
        return null;
    }

    protected abstract T createSpan(Map<String, String> styleMap, Attributes attributes);

    protected abstract Z getDefaultValue(BaseRichEditText editText);

    protected abstract Z getMultiValue();

}
