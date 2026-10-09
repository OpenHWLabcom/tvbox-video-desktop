package com.openhwlab.desktop;

import android.app.Activity;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

/** 原生设置类页面的公共骨架: 深色 + 顶部返回条 + 可滚动内容区 */
public abstract class BaseActivity extends Activity {
    protected static final String BASE = "http://127.0.0.1:8899/";
    protected LinearLayout body;
    private LinearLayout col;
    private FrameLayout rootF;
    private LinearLayout rcMenu;
    private int hoverX = 460, hoverY = 260;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setBackgroundColor(0xFF0D1726);

        LinearLayout hd = new LinearLayout(this);
        hd.setOrientation(LinearLayout.HORIZONTAL);
        hd.setGravity(Gravity.CENTER_VERTICAL);
        hd.setBackgroundColor(0xFF0F1A2B);

        TextView back = new TextView(this);
        back.setText("\u2039 返回");
        back.setTextColor(0xFFFFFFFF);
        back.setTextSize(15);
        back.setPadding(dp(14), dp(12), dp(14), dp(12));
        back.setClickable(true);
        back.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { finish(); } });
        hd.addView(back);

        TextView t = new TextView(this);
        t.setText(title());
        t.setTextColor(0xFFCFE0F5);
        t.setTextSize(17);
        t.setPadding(dp(8), 0, 0, 0);
        hd.addView(t);
        col.addView(hd, new LinearLayout.LayoutParams(-1, dp(52)));

        ScrollView sv = new ScrollView(this);
        body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(18), dp(6), dp(18), dp(18));
        sv.addView(body, new LinearLayout.LayoutParams(-1, -2));
        col.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));

        rootF = new FrameLayout(this);
        rootF.addView(col, new FrameLayout.LayoutParams(-1, -1));
        rcMenu = buildRcMenu();
        rootF.addView(rcMenu);
        setContentView(rootF);
    }

    /** 鼠标右键(这 ROM 把它变成了 BACK) -> 页面菜单, 而不是“返回” */
    private LinearLayout buildRcMenu() {
        LinearLayout m = new LinearLayout(this);
        m.setOrientation(LinearLayout.VERTICAL);
        m.setBackground(bg(0xFA1A2232, dp(4)));
        m.addView(rcItem("刷新", new Runnable() { public void run() { hideRc(); recreate(); } }));
        m.addView(rcItem("返回桌面", new Runnable() { public void run() {
            hideRc();
            Intent i = new Intent(Intent.ACTION_MAIN);
            i.addCategory(Intent.CATEGORY_HOME);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } }));
        m.addView(rcItem("关闭本页", new Runnable() { public void run() { hideRc(); finish(); } }));
        m.setVisibility(View.GONE);
        return m;
    }

    private TextView rcItem(String label, final Runnable act) {
        TextView t = new TextView(this);
        t.setText(label);
        t.setTextColor(0xFFFFFFFF);
        t.setTextSize(14);
        t.setPadding(dp(18), dp(11), dp(30), dp(11));
        t.setClickable(true);
        t.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { act.run(); } });
        return t;
    }

    private void hideRc() { if (rcMenu != null) rcMenu.setVisibility(View.GONE); }

    private void toggleRc(int x, int y) {
        if (rcMenu == null) return;
        if (rcMenu.getVisibility() == View.VISIBLE) { hideRc(); return; }
        int sw = getResources().getDisplayMetrics().widthPixels;
        int sh = getResources().getDisplayMetrics().heightPixels;
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(dp(140), -2);
        lp.gravity = Gravity.TOP | Gravity.LEFT;
        lp.leftMargin = Math.min(Math.max(x, dp(4)), sw - dp(150));
        lp.topMargin = Math.min(Math.max(y, dp(56)), sh - dp(170));
        rcMenu.setLayoutParams(lp);
        rcMenu.setVisibility(View.VISIBLE);
    }

    @Override public boolean dispatchGenericMotionEvent(MotionEvent e) {
        int a = e.getActionMasked();
        if (a == MotionEvent.ACTION_HOVER_MOVE || a == MotionEvent.ACTION_HOVER_ENTER) {
            hoverX = (int) e.getX();
            hoverY = (int) e.getY();
        }
        return super.dispatchGenericMotionEvent(e);
    }

    private boolean isMouseKey(KeyEvent e) {
        try {
            InputDevice d = InputDevice.getDevice(e.getDeviceId());
            return d != null && (d.getSources() & InputDevice.SOURCE_MOUSE) != 0;
        } catch (Throwable t) { return false; }
    }

    @Override public boolean dispatchKeyEvent(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.KEYCODE_BACK && isMouseKey(e)) {
            if (e.getAction() == KeyEvent.ACTION_UP) toggleRc(hoverX, hoverY);
            return true;
        }
        return super.dispatchKeyEvent(e);
    }

    protected abstract String title();

    protected int dp(float v) { return (int) (getResources().getDisplayMetrics().density * v + 0.5f); }

    /** 一个设置分组: 标题(带当前值) + 一排按钮; 返回按钮行容器 */
    protected LinearLayout section(String label, String cur) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(0, dp(16), 0, dp(4));
        TextView h = new TextView(this);
        h.setText(cur == null || cur.length() == 0 ? label : label + "     当前: " + cur);
        h.setTextColor(0xFFCFE0F5);
        h.setTextSize(15);
        box.addView(h);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(10), 0, 0);
        box.addView(row);
        body.addView(box);
        return row;
    }

    protected TextView chip(String text, boolean on, View.OnClickListener l) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextColor(0xFFFFFFFF);
        t.setTextSize(14);
        t.setPadding(dp(16), dp(9), dp(16), dp(9));
        t.setBackground(bg(on ? 0xFF2F7FD0 : 0x1AFFFFFF, dp(5)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.rightMargin = dp(8);
        lp.bottomMargin = dp(6);
        t.setLayoutParams(lp);
        t.setClickable(true);
        t.setOnClickListener(l);
        return t;
    }

    protected GradientDrawable bg(int color, int radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(radius);
        return g;
    }

    protected TextView text(String s, float size, int color, int padTop) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(size);
        t.setTextColor(color);
        t.setPadding(0, padTop, 0, 0);
        return t;
    }

    protected String httpGet(String url, int ms) {
        try {
            HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(ms);
            c.setReadTimeout(ms);
            BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String ln;
            while ((ln = r.readLine()) != null) sb.append(ln).append('\n');
            r.close();
            return sb.toString();
        } catch (Throwable e) {
            Log.i("kiosk", "httpGet " + url + " " + e);
            return "";
        }
    }

    protected void httpGetAsync(final String url) {
        new Thread(new Runnable() { public void run() { httpGet(url, 20000); } }).start();
    }

    /** 带回调的异步 GET (回调在主线程) */
    protected interface Cb { void done(String body); }
    protected void httpGetCb(final String url, final Cb cb) {
        new Thread(new Runnable() {
            public void run() {
                final String r = httpGet(url, 20000);
                runOnUiThread(new Runnable() { public void run() { cb.done(r); } });
            }
        }).start();
    }

    /** 路径等参数用 hex 传(盒子 busybox 没有 base64, CGI 用 awk 解 hex) */
    protected String hex(String s) {
        try {
            byte[] b = s.getBytes("UTF-8");
            StringBuilder sb = new StringBuilder(b.length * 2);
            for (int i = 0; i < b.length; i++) sb.append(String.format("%02x", b[i] & 0xff));
            return sb.toString();
        } catch (Throwable e) { return ""; }
    }

    protected String kb2mb(String kb) {
        if (kb == null || kb.length() == 0) return "?";
        try { return String.format("%.1f MB", Double.parseDouble(kb) / 1024.0); }
        catch (Throwable e) { return kb; }
    }

    /** 解析 "k=v" 行 (含 
) */
    protected java.util.HashMap<String, String> kv(String body) {
        java.util.HashMap<String, String> m = new java.util.HashMap<String, String>();
        String[] lines = body.split("\n");
        for (int i = 0; i < lines.length; i++) {
            int p = lines[i].indexOf('=');
            if (p > 0) m.put(lines[i].substring(0, p).trim(), lines[i].substring(p + 1).trim());
        }
        return m;
    }
}
