// SPDX-License-Identifier: GPL-3.0-or-later
package org.x2m7.trime.editorfixture;

import android.app.Activity;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.view.WindowInsets;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

/** An independent process: killing Trime must not kill or rewrite this editor. */
public final class EditorActivity extends Activity {
    private EditText editor;
    private String mode;

    @SuppressWarnings("deprecation")
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        mode = getIntent().getStringExtra("mode");
        if (mode == null) mode = "chat";
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            view.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                    insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets;
        });
        TextView title = new TextView(this);
        title.setId(R.id.mode);
        title.setText(mode);
        title.setTextSize(18);
        editor = new EditText(this);
        editor.setId(R.id.editor);
        editor.setHint(mode);
        int type = InputType.TYPE_CLASS_TEXT;
        int action = EditorInfo.IME_ACTION_SEND;
        switch (mode) {
            case "search": action = EditorInfo.IME_ACTION_SEARCH; break;
            case "address": type |= InputType.TYPE_TEXT_VARIATION_URI; action = EditorInfo.IME_ACTION_GO; break;
            case "email": type |= InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS; action = EditorInfo.IME_ACTION_NEXT; break;
            case "password": type |= InputType.TYPE_TEXT_VARIATION_PASSWORD; action = EditorInfo.IME_ACTION_DONE; break;
            case "phone": type = InputType.TYPE_CLASS_PHONE; action = EditorInfo.IME_ACTION_DONE; break;
            case "integer": type = InputType.TYPE_CLASS_NUMBER; action = EditorInfo.IME_ACTION_DONE; break;
            case "decimal": type = InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED; action = EditorInfo.IME_ACTION_DONE; break;
            case "multiline": type |= InputType.TYPE_TEXT_FLAG_MULTI_LINE; action = EditorInfo.IME_ACTION_NONE | EditorInfo.IME_FLAG_NO_ENTER_ACTION; break;
        }
        editor.setInputType(type);
        editor.setImeOptions(action);
        editor.setText(state != null ? state.getString("text", "") : getIntent().getStringExtra("text"));
        editor.setSelection(editor.length());
        root.addView(editor, new LinearLayout.LayoutParams(-1, dp(48)));
        root.addView(title, new LinearLayout.LayoutParams(-1, dp(24)));
        TextView result = new TextView(this);
        result.setId(R.id.action_result);
        result.setText("action: none");
        root.addView(result, new LinearLayout.LayoutParams(-1, dp(24)));
        editor.setOnEditorActionListener((view, id, event) -> {
            result.setText("action: " + id);
            return id != EditorInfo.IME_NULL;
        });
        setContentView(root);
        editor.requestFocus();
        editor.postDelayed(() -> ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE)).showSoftInput(editor, 0), 300);
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        state.putString("text", editor.getText().toString());
        super.onSaveInstanceState(state);
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
