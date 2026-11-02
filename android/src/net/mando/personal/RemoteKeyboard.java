package net.mando.personal;

import android.content.Context;
import android.text.Editable;
import android.text.InputType;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.inputmethod.BaseInputConnection;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputConnectionWrapper;
import android.widget.EditText;

/** Native IME editor. Only committed text is sent; composing text stays local. */
final class RemoteKeyboard extends EditText {
    interface Sink {
        void text(String value);
        void key(String value);
    }
    private final Sink sink;
    private boolean clearing;
    private int batchDepth;

    RemoteKeyboard(Context context, Sink sink) {
        super(context);
        this.sink=sink;
        setSingleLine(true);
        setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        setImeOptions(EditorInfo.IME_ACTION_SEND | EditorInfo.IME_FLAG_NO_EXTRACT_UI |
                EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING);
        setHint("Escribe directamente en el PC…");
        setContentDescription("Escribir en el PC con el teclado Android");
        setTextColor(0xffeff7f2);setHintTextColor(0xffb1c7c0);
        setBackgroundColor(0xff223c3b);setTextSize(16);
        setSaveEnabled(false);setImportantForAutofill(IMPORTANT_FOR_AUTOFILL_NO);
        setFilters(new InputFilter[]{new InputFilter.LengthFilter(500)});
        addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s,int start,int count,int after) { }
            public void onTextChanged(CharSequence s,int start,int before,int count) { }
            public void afterTextChanged(Editable value) {
                if(!clearing) post(() -> flushCommitted());
            }
        });
        setOnEditorActionListener((view,action,event) -> {
            if(action==EditorInfo.IME_ACTION_SEND || (event!=null && event.getKeyCode()==KeyEvent.KEYCODE_ENTER)) {
                if(event==null || event.getAction()==KeyEvent.ACTION_DOWN) {
                    finishComposition();sink.key("ENTER");
                }
                return true;
            }
            return false;
        });
    }

    private void flushCommitted() {
        if(clearing || batchDepth>0) return;
        Editable value=getText();
        if(value==null || value.length()==0 || BaseInputConnection.getComposingSpanStart(value)>=0) return;
        String committed=value.toString();
        clearing=true;
        value.clear();
        clearing=false;
        sink.text(committed);
    }

    private void finishComposition() {
        Editable value=getText();
        if(value!=null) BaseInputConnection.removeComposingSpans(value);
        flushCommitted();
    }

    void discard() {
        clearing=true;
        if(getText()!=null) getText().clear();
        clearing=false;batchDepth=0;
    }

    void finishAndSend() {
        batchDepth=0;
        finishComposition();
    }

    @Override public boolean onKeyDown(int code,KeyEvent event) {
        if(code==KeyEvent.KEYCODE_ENTER) {finishComposition();sink.key("ENTER");return true;}
        if((code==KeyEvent.KEYCODE_DEL || code==KeyEvent.KEYCODE_FORWARD_DEL) && getText().length()==0) {
            sink.key(code==KeyEvent.KEYCODE_DEL?"BACKSPACE":"DELETE");return true;
        }
        return super.onKeyDown(code,event);
    }

    @Override public InputConnection onCreateInputConnection(EditorInfo info) {
        InputConnection original=super.onCreateInputConnection(info);
        if(original==null)return null;
        return new InputConnectionWrapper(original,false) {
            @Override public boolean beginBatchEdit() {
                batchDepth++;return super.beginBatchEdit();
            }
            @Override public boolean endBatchEdit() {
                boolean result=super.endBatchEdit();batchDepth=Math.max(0,batchDepth-1);
                post(() -> flushCommitted());return result;
            }
            @Override public boolean commitText(CharSequence text,int newCursorPosition) {
                boolean result=super.commitText(text,newCursorPosition);
                flushCommitted();return result;
            }
            @Override public boolean finishComposingText() {
                boolean result=super.finishComposingText();
                flushCommitted();return result;
            }
            private boolean deleteEmpty(int before,int after) {
                if(getText()==null || getText().length()!=0 || before<0 || after<0 || before>50 || after>50) return false;
                for(int i=0;i<before;i++)sink.key("BACKSPACE");
                for(int i=0;i<after;i++)sink.key("DELETE");
                return true;
            }
            @Override public boolean deleteSurroundingText(int before,int after) {
                return deleteEmpty(before,after) || super.deleteSurroundingText(before,after);
            }
            @Override public boolean deleteSurroundingTextInCodePoints(int before,int after) {
                return deleteEmpty(before,after) || super.deleteSurroundingTextInCodePoints(before,after);
            }
            @Override public boolean sendKeyEvent(KeyEvent event) {
                int key=event.getKeyCode();
                if((key==KeyEvent.KEYCODE_DEL || key==KeyEvent.KEYCODE_FORWARD_DEL) && getText().length()==0) {
                    if(event.getAction()==KeyEvent.ACTION_DOWN)sink.key(key==KeyEvent.KEYCODE_DEL?"BACKSPACE":"DELETE");
                    return true;
                }
                if(key==KeyEvent.KEYCODE_ENTER) {
                    if(event.getAction()==KeyEvent.ACTION_DOWN){finishComposition();sink.key("ENTER");}
                    return true;
                }
                return super.sendKeyEvent(event);
            }
        };
    }
}
