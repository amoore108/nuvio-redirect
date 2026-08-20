package com.codex.nuvio.redirector;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

final class TvUi {
    private TvUi() {}

    static LinearLayout scrollableColumn(Activity activity) {
        ScrollView scroll = new ScrollView(activity);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(activity.getColor(R.color.background));

        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        int horizontal = dp(activity, 72);
        int vertical = dp(activity, 42);
        content.setPadding(horizontal, vertical, horizontal, vertical);
        scroll.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        activity.setContentView(scroll);
        return content;
    }

    static TextView title(Activity activity, String text) {
        TextView view = text(activity, text, 30, R.color.text_primary);
        view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        view.setPadding(0, 0, 0, dp(activity, 12));
        return view;
    }

    static TextView heading(Activity activity, String text) {
        TextView view = text(activity, text, 20, R.color.text_primary);
        view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        view.setPadding(0, dp(activity, 24), 0, dp(activity, 8));
        return view;
    }

    static TextView body(Activity activity, String text) {
        TextView view = text(activity, text, 16, R.color.text_secondary);
        view.setLineSpacing(0f, 1.15f);
        view.setPadding(0, 0, 0, dp(activity, 10));
        return view;
    }

    static TextView status(Activity activity, String text) {
        TextView view = text(activity, text, 17, R.color.text_primary);
        view.setBackgroundColor(activity.getColor(R.color.surface));
        view.setPadding(dp(activity, 18), dp(activity, 14), dp(activity, 18), dp(activity, 14));
        return view;
    }

    static Button button(Activity activity, String text) {
        Button button = new Button(activity);
        button.setText(text);
        button.setTextColor(Color.WHITE);
        button.setTextSize(17f);
        button.setAllCaps(false);
        button.setGravity(android.view.Gravity.START | android.view.Gravity.CENTER_VERTICAL);
        button.setBackgroundResource(R.drawable.button_background);
        button.setFocusable(true);
        button.setMinHeight(dp(activity, 58));
        button.setPadding(dp(activity, 18), dp(activity, 10), dp(activity, 18), dp(activity, 10));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, dp(activity, 6), 0, dp(activity, 6));
        button.setLayoutParams(params);
        return button;
    }

    static EditText edit(Activity activity, String hint) {
        EditText edit = new EditText(activity);
        edit.setHint(hint);
        edit.setHintTextColor(activity.getColor(R.color.text_secondary));
        edit.setTextColor(activity.getColor(R.color.text_primary));
        edit.setTextSize(17f);
        edit.setSingleLine(true);
        edit.setBackgroundResource(R.drawable.edit_background);
        edit.setSelectAllOnFocus(false);
        edit.setFocusable(true);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, dp(activity, 6), 0, dp(activity, 6));
        edit.setLayoutParams(params);
        return edit;
    }

    static View spacer(Activity activity, int heightDp) {
        View view = new View(activity);
        view.setLayoutParams(new LinearLayout.LayoutParams(1, dp(activity, heightDp)));
        return view;
    }

    static int dp(Activity activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }

    private static TextView text(Activity activity, String text, int sizeSp, int color) {
        TextView view = new TextView(activity);
        view.setText(text);
        view.setTextSize(sizeSp);
        view.setTextColor(activity.getColor(color));
        return view;
    }
}
