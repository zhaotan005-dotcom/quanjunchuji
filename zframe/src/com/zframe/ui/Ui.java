package com.zframe.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;

/** 统一的暗色霓虹风格控件（框架 App 和游戏内面板共用，不依赖任何资源文件）。 */
public class Ui {
    public static final int BG = 0xFF070B1E, CARD = 0xFF121833, CARD2 = 0xFF1A2244, TEXT = 0xFFE8ECFF,
            SUB = 0xFF8C96C8, CYAN = 0xFF2EE6F0, PURPLE = 0xFF9B5CFF, GREEN = 0xFF3BE38A, RED = 0xFFFF5C7A;

    public final Context c;
    public Ui(Context c) { this.c = c; }

    public int dp(float v) { return (int) (v * c.getResources().getDisplayMetrics().density + 0.5f); }

    public GradientDrawable round(int color, float r) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(r));
        return g;
    }

    public GradientDrawable grad(float r) {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{CYAN, PURPLE});
        g.setCornerRadius(dp(r));
        return g;
    }

    public LinearLayout col() {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    public LinearLayout row() {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    public LinearLayout card(ViewGroup parent) {
        LinearLayout l = col();
        l.setBackground(round(CARD, 16));
        int p = dp(14);
        l.setPadding(p, p, p, p);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(12);
        parent.addView(l, lp);
        return l;
    }

    public TextView text(ViewGroup parent, CharSequence s, float size, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(size);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setLineSpacing(0, 1.15f);
        t.setPadding(0, dp(2), 0, dp(2));
        if (parent != null) parent.addView(t);
        return t;
    }

    public TextView title(ViewGroup p, String s) { return text(p, s, 17, TEXT, true); }
    public TextView sub(ViewGroup p, CharSequence s) { return text(p, s, 13, SUB, false); }

    public TextView chip(ViewGroup p, String s, int color) {
        TextView t = text(null, s, 12, color, true);
        GradientDrawable g = round(0x00000000, 20);
        g.setStroke(dp(1), color);
        t.setBackground(g);
        t.setPadding(dp(10), dp(3), dp(10), dp(3));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.rightMargin = dp(6);
        p.addView(t, lp);
        return t;
    }

    private TextView btnBase(ViewGroup parent, String s, View.OnClickListener l) {
        TextView b = new TextView(c);
        b.setText(s);
        b.setGravity(Gravity.CENTER);
        b.setTextSize(14);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setPadding(dp(10), dp(11), dp(10), dp(11));
        b.setClickable(true);
        b.setOnClickListener(v -> { v.animate().scaleX(0.96f).scaleY(0.96f).setDuration(60).withEndAction(() ->
                v.animate().scaleX(1f).scaleY(1f).setDuration(60).start()).start(); l.onClick(v); });
        LinearLayout.LayoutParams lp = parent instanceof LinearLayout && ((LinearLayout) parent).getOrientation() == LinearLayout.HORIZONTAL
                ? new LinearLayout.LayoutParams(0, -2, 1) : new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(dp(3), dp(4), dp(3), dp(4));
        parent.addView(b, lp);
        return b;
    }

    public TextView primary(ViewGroup p, String s, View.OnClickListener l) {
        TextView b = btnBase(p, s, l);
        b.setBackground(grad(12));
        b.setTextColor(0xFF0A0E24);
        return b;
    }

    public TextView ghost(ViewGroup p, String s, View.OnClickListener l) {
        TextView b = btnBase(p, s, l);
        GradientDrawable g = round(CARD2, 12);
        g.setStroke(dp(1), 0x334A5BFF);
        b.setBackground(g);
        b.setTextColor(TEXT);
        return b;
    }

    public TextView danger(ViewGroup p, String s, View.OnClickListener l) {
        TextView b = ghost(p, s, l);
        b.setTextColor(RED);
        return b;
    }

    public EditText input(ViewGroup p, String hint, boolean number) {
        EditText e = new EditText(c);
        e.setHint(hint);
        e.setHintTextColor(0xFF5A6496);
        e.setTextColor(TEXT);
        e.setTextSize(15);
        e.setSingleLine();
        e.setBackground(round(CARD2, 12));
        e.setPadding(dp(14), dp(11), dp(14), dp(11));
        if (number) e.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        LinearLayout.LayoutParams lp = p instanceof LinearLayout && ((LinearLayout) p).getOrientation() == LinearLayout.HORIZONTAL
                ? new LinearLayout.LayoutParams(0, -2, 1) : new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(dp(3), dp(4), dp(3), dp(4));
        p.addView(e, lp);
        return e;
    }

    /** 分段选择器，返回当前下标数组 [0] */
    public int[] segmented(ViewGroup p, String[] items, int sel, Runnable onChange) {
        final int[] cur = {sel};
        LinearLayout r = row();
        r.setBackground(round(CARD2, 12));
        r.setPadding(dp(3), dp(3), dp(3), dp(3));
        final TextView[] tvs = new TextView[items.length];
        for (int i = 0; i < items.length; i++) {
            final int idx = i;
            TextView t = new TextView(c);
            t.setText(items[i]);
            t.setGravity(Gravity.CENTER);
            t.setTextSize(12);
            t.setTypeface(Typeface.DEFAULT_BOLD);
            t.setPadding(0, dp(8), 0, dp(8));
            t.setOnClickListener(v -> {
                cur[0] = idx;
                for (int j = 0; j < tvs.length; j++) style(tvs[j], j == idx);
                if (onChange != null) onChange.run();
            });
            tvs[i] = t;
            style(t, i == sel);
            r.addView(t, new LinearLayout.LayoutParams(0, -2, 1));
        }
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(dp(3), dp(4), dp(3), dp(4));
        p.addView(r, lp);
        return cur;
    }

    public void style(TextView t, boolean on) {
        t.setBackground(on ? grad(10) : null);
        t.setTextColor(on ? 0xFF0A0E24 : SUB);
    }

    public View divider(ViewGroup p) {
        View v = new View(c);
        v.setBackgroundColor(0x22FFFFFF);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(1));
        lp.setMargins(0, dp(8), 0, dp(8));
        p.addView(v, lp);
        return v;
    }

    public static int alpha(int color, int a) { return (color & 0x00FFFFFF) | (a << 24); }
    public static int rgb(int r, int g, int b) { return Color.rgb(r, g, b); }
}
