package com.openhwlab.desktop;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.Gravity;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * 原生 Windows 风格桌面 (不再用 WebView):
 *  - 渐变壁纸 + 图标网格 + 开始菜单 + 右键菜单, 全部原生 View
 *  - 应用启动走 Intent; 需要 root 的动作(返回/关闭程序/重启/关机)仍走本机 127.0.0.1:8899 的 CGI
 *  - 右上角可折叠导航栏(开始/返回/桌面/关闭/确定 + 时钟), 由原生窗口实现
 */
public class MainActivity extends Activity {
    private static final String BASE = "http://127.0.0.1:8899/";
    private static final String TRANSFER = "http://127.0.0.1:8890/";
    private static final String RUN  = BASE + "cgi-bin/run?app=";
    private static final String DOCS = "/storage/emulated/0/documents";
    private static final String MIME_DOC = "application/msword";
    private static final String MIME_XLS = "application/vnd.ms-excel";
    private static final String MIME_PPT = "application/vnd.openxmlformats-officedocument.presentationml.presentation";

    private FrameLayout root, iconLayer;
    private LinearLayout startMenu, ctxMenu;
    private WindowManager wm;
    private ViewGroup navBar;
    private WindowManager.LayoutParams navLp;
    private TextView navHandle, clock;
    private boolean barExpanded = true;
    private String navApplied = null;
    private int hoverX = 460, hoverY = 260;   // 鼠标光标位置(右键菜单用)
    private final Handler h = new Handler();
    private SharedPreferences sp;
    private boolean draggingIcon = false;     // 拖动中(抑制 hover 高亮)
    private String lastTapLabel = null;       // 双击检测
    private long lastTapTime = 0;
    // 图标网格(拖动吸附用): 列起点/步长, 行起点/步长 (dp)
    private static final int GX0 = 14, GDX = 144, GY0 = 14, GDY = 96;

    private final Runnable tick = new Runnable() { public void run() {
        if (clock != null) clock.setText(new SimpleDateFormat("HH:mm  MM-dd").format(new Date()));
        h.postDelayed(this, 15000);
    }};

    private long lastNavCheck = 0;
    private long lastHeal = 0;
    private final Runnable navWatch = new Runnable() { public void run() {
        long now = System.currentTimeMillis();
        if (now - lastNavCheck > 2000) { lastNavCheck = now; applyNavMode(); }
        if (now - lastHeal > 30000) { lastHeal = now; cgi("dnsfix"); }
        h.postDelayed(this, 400);
    }};
    private final Runnable autoCollapse = new Runnable() { public void run() { if (!"always".equals(navApplied)) setBar(false); }};

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        sp = getSharedPreferences("kiosk", 0);

        root = new FrameLayout(this);
        root.addView(new WallpaperView(this), new FrameLayout.LayoutParams(-1, -1));

        iconLayer = new FrameLayout(this);
        root.addView(iconLayer, new FrameLayout.LayoutParams(-1, -1));
        buildIcons();
        // 空白处右键/长按 -> 右键菜单
        iconLayer.setLongClickable(true);
        iconLayer.setOnLongClickListener(new View.OnLongClickListener() {
            public boolean onLongClick(View v) {
                showCtx(dp(60), dp(60));
                return true;
            }
        });
        buildStartMenu();
        buildCtxMenu();

        setContentView(root);
        // 注意: 不要在这些窗口上设 SYSTEM_UI_FLAG_HIDE_NAVIGATION —— 该 ROM 会把所有触摸事件吞掉(要点两下)
        makeNavBar();
        h.post(tick);
        h.post(navWatch);

        // U盘 插拔提示
        try {
            android.content.BroadcastReceiver rx = new android.content.BroadcastReceiver() {
                public void onReceive(android.content.Context c, Intent i) {
                    String a = i.getAction();
                    if (Intent.ACTION_MEDIA_MOUNTED.equals(a)) showTip("\uD83D\uDCBE 检测到外接U盘\n\n点桌面「外接U盘」打开");
                    else if (Intent.ACTION_MEDIA_UNMOUNTED.equals(a) || Intent.ACTION_MEDIA_EJECT.equals(a)) showTip("外接U盘已拔出");
                }
            };
            android.content.IntentFilter f = new android.content.IntentFilter();
            f.addAction(Intent.ACTION_MEDIA_MOUNTED);
            f.addAction(Intent.ACTION_MEDIA_UNMOUNTED);
            f.addAction(Intent.ACTION_MEDIA_EJECT);
            f.addDataScheme("file");
            registerReceiver(rx, f);
        } catch (Throwable e) { Log.i("kiosk", "usbRx " + e); }
        usbHintOnStart();
    }

    private void showTip(String txt) {
        showLoading(txt);
        h.postDelayed(new Runnable() { public void run() { hideLoading(); } }, 6000);
    }

    private void usbHintOnStart() {
        String[] ds = {"/storage/sda", "/storage/sdb", "/storage/sdc"};
        for (String d : ds) {
            String[] ls = new File(d).list();
            if (ls != null && ls.length > 0) {
                showTip("\uD83D\uDCBE 检测到外接U盘\n\n点桌面「外接U盘」打开");
                return;
            }
        }
    }

    // ---------- 壁纸 ----------
    private class WallpaperView extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        WallpaperView(android.content.Context c) { super(c); }
        protected void onDraw(Canvas cv) {
            int w = getWidth(), hh = getHeight();
            if (w == 0 || hh == 0) return;
            float cx = w * 0.28f, cy = hh * 0.18f;
            float r = (float) Math.hypot(Math.max(cx, w - cx), Math.max(cy, hh - cy));
            p.setShader(new RadialGradient(cx, cy, r,
                    new int[]{0xFF3A7FC4, 0xFF12406F, 0xFF07182F}, new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP));
            cv.drawRect(0, 0, w, hh, p);
            p.setShader(new LinearGradient(0, 0, w, hh, 0x1AFFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
            cv.drawRect(0, 0, w, hh, p);
            p.setShader(null);
            // 品牌字标 "Open HW Lab" (右下角水印)
            p.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
            p.setTextSize(dp(48));
            String a = "Open ", b = "HW Lab";
            float base = hh - dp(48), right = w - dp(48);
            float wa = p.measureText(a), wb = p.measureText(b);
            p.setColor(0xC0FFFFFF);
            cv.drawText(a, right - wa - wb, base, p);
            p.setColor(0xCC7FD0FF);
            cv.drawText(b, right - wb, base, p);
            // 中文 slogan（在字母上方）
            p.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
            p.setTextSize(dp(26));
            String s = "激发探索的勇气";
            p.setColor(0xB09FB3CC);
            cv.drawText(s, right - p.measureText(s), base - dp(44), p);
        }
    }

    // ---------- 桌面图标 ----------
    private void buildIcons() {
        // 核心
        addIcon("浏览器", 14, 14, "browser", actUrl("https://www.baidu.com/"));
        addIcon("外接U盘", 14, 110, "usb", actWeb(TRANSFER + "usb", "外接U盘"));
        // 媒体
        addIcon("音乐", 158, 14, "bili", actMusic());
        // 应用 / 媒体（双击打开）
        addIcon("当贝市场", 302, 14, "store", actPkg("com.dangbeimarket"));
        addIcon("当贝投屏", 158, 110, "cam", actPkg("com.dangbei.screencast"));
        addIcon("电视直播", 302, 110, "tv", actPkg("com.jerry.live.dszb"));

        // 系统工具
        addIcon("回收站", 14, 206, "recycle", actAct(RecycleBinActivity.class));
        addIcon("程序和功能", 158, 206, "apps", actAct(ProgramsActivity.class));
        addIcon("系统清理", 302, 206, "clean", actAct(CleanActivity.class));
        addIcon("显示设置", 14, 302, "disp", actAct(SettingsActivity.class));
        addIcon("设置", 158, 302, "gear", actPkg("com.android.settings"));
        // 局域网互传入口 (手机<->盒子)
        addIcon("传文件", 302, 302, "transfer", actWeb(TRANSFER, "传文件"));
        // 办公
        addIcon("WPS文档", 14, 398, "wps", actComp("cn.wps.moffice_eng", "cn.wps.moffice.main.local.HomeRootActivity"));
    }

    /** 整理图标: 清掉用户拖动保存的位置, 回到默认网格布局 */
    private void resetIcons() {
        if (sp != null) sp.edit().clear().apply();
        iconLayer.removeAllViews();
        buildIcons();
    }

    private Runnable actUrl(final String url) {
        return new Runnable() { public void run() { openUrl(url); } };
    }
    private Runnable actHome() {
        return new Runnable() { public void run() {
            dismissMenus();
            try {
                Intent i = new Intent(Intent.ACTION_MAIN);
                i.addCategory(Intent.CATEGORY_HOME);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(i);
            } catch (Throwable e) { Log.i("kiosk", "actHome " + e); }
        } };
    }

    private Runnable actPkg(final String pkg) {
        return new Runnable() { public void run() { launchPkg(pkg); } };
    }
    private Runnable actComp(final String pkg, final String cls) {
        return new Runnable() { public void run() { launchComp(pkg, cls); } };
    }
    private Runnable actNew(final String name, final String ext, final String mime) {
        return new Runnable() { public void run() { newDoc(name, ext, mime); } };
    }
    private Runnable actAct(final Class<?> cls) {
        return new Runnable() { public void run() { openAct(cls); } };
    }
    private Runnable actMusic() {
        return new Runnable() { public void run() { openMusic(); } };
    }
    private Runnable actCgi(final String app) {
        return new Runnable() { public void run() { cgi(app); } };
    }
    private Runnable actConfirm(final String app, final String msg) {
        return new Runnable() { public void run() { confirmAction(msg, app); } };
    }
    private Runnable actWeb(final String url, final String title) {
        return new Runnable() { public void run() {
            dismissMenus();
            try {
                Intent i = new Intent(MainActivity.this, WebActivity.class);
                i.putExtra("url", url);
                i.putExtra("title", title);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(i);
            } catch (Throwable e) { Log.i("kiosk", "actWeb " + e); }
        } };
    }

    private void addIcon(final String label, int left, int top, String asset, final Runnable act) {
        final LinearLayout it = new LinearLayout(this);
        it.setOrientation(LinearLayout.VERTICAL);
        it.setGravity(Gravity.CENTER_HORIZONTAL);
        it.setPadding(0, dp(8), 0, dp(8));

        final GradientDrawable bg = new GradientDrawable();
        bg.setColor(0x00000000);
        bg.setCornerRadius(dp(4));
        it.setBackground(bg);

        ImageView im = new ImageView(this);
        im.setImageBitmap(loadAsset("icons/" + asset + ".png"));
        im.setScaleType(ImageView.ScaleType.FIT_CENTER);
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(dp(40), dp(40));
        ilp.bottomMargin = dp(5);
        it.addView(im, ilp);

        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextColor(0xFFFFFFFF);
        tv.setTextSize(12.5f);
        tv.setGravity(Gravity.CENTER);
        tv.setSingleLine(true);
        tv.setShadowLayer(2, 0, 1, 0xFF000000);
        it.addView(tv);

        int[] pos = loadIconPos(label, left, top);   // 读用户保存的位置(无则用默认)
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(dp(128), ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = dp(pos[0]);
        lp.topMargin = dp(pos[1]);
        it.setLayoutParams(lp);
        it.setClickable(true);
        it.setOnHoverListener(new View.OnHoverListener() {
            public boolean onHover(View v, MotionEvent e) {
                if (draggingIcon) return true;
                int a = e.getActionMasked();
                if (a == MotionEvent.ACTION_HOVER_ENTER) { bg.setColor(0x00000000); bg.setStroke(0, 0x00000000); }
                else if (a == MotionEvent.ACTION_HOVER_EXIT) { bg.setColor(0x00000000); bg.setStroke(0, 0x00000000); }
                return false;
            }
        });
        // 交互: 拖动(超过阈值)→ 移动图标, 抬起吸附网格并存位置; 单击 → 启动; 长按 → 右键菜单
        it.setOnTouchListener(new View.OnTouchListener() {
            float sx, sy; int sl, st; boolean dragging, longFired;
            final Runnable lpRun = new Runnable() { public void run() {
                longFired = true; dragging = false;
                FrameLayout.LayoutParams p = (FrameLayout.LayoutParams) it.getLayoutParams();
                showCtx(p.leftMargin + dp(64), p.topMargin + dp(60));
            }};
            public boolean onTouch(View v, MotionEvent e) {
                int a = e.getActionMasked();
                if (a == MotionEvent.ACTION_DOWN) {
                    sx = e.getRawX(); sy = e.getRawY();
                    FrameLayout.LayoutParams p = (FrameLayout.LayoutParams) v.getLayoutParams();
                    sl = p.leftMargin; st = p.topMargin;
                    dragging = false; longFired = false;
                    h.postDelayed(lpRun, ViewConfiguration.getLongPressTimeout());
                    return true;
                } else if (a == MotionEvent.ACTION_MOVE) {
                    float dx = e.getRawX() - sx, dy = e.getRawY() - sy;
                    if (!dragging && !longFired && Math.hypot(dx, dy) > dp(8)) {
                        dragging = true; draggingIcon = true;
                        h.removeCallbacks(lpRun);
                        bg.setColor(0x00000000); bg.setStroke(0, 0x00000000);
                        v.bringToFront();
                    }
                    if (dragging) {
                        FrameLayout.LayoutParams p = (FrameLayout.LayoutParams) v.getLayoutParams();
                        p.leftMargin = clampX((int) (sl + dx));
                        p.topMargin = clampY((int) (st + dy), v.getHeight());
                        v.setLayoutParams(p);
                    }
                    return true;
                } else if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) {
                    h.removeCallbacks(lpRun);
                    if (dragging) {
                        FrameLayout.LayoutParams p = (FrameLayout.LayoutParams) v.getLayoutParams();
                        int[] g = snapGrid(p.leftMargin, p.topMargin, v.getHeight());
                        p.leftMargin = g[0]; p.topMargin = g[1];
                        v.setLayoutParams(p);
                        saveIconPos(label, g[0], g[1]);
                        bg.setColor(0x00000000); bg.setStroke(0, 0x00000000);
                    } else if (a == MotionEvent.ACTION_UP && !longFired) {
                        long now = System.currentTimeMillis();
                        if (label.equals(lastTapLabel) && now - lastTapTime < 450) {
                            lastTapLabel = null; lastTapTime = 0; act.run();   // 双击打开
                        } else { lastTapLabel = label; lastTapTime = now; }
                    }
                    dragging = false; draggingIcon = false;
                    return true;
                }
                return false;
            }
        });
        iconLayer.addView(it);
    }

    // ---------- 图标位置(拖动 / 持久化) ----------
    private int[] loadIconPos(String label, int dl, int dt) {
        String s = (sp == null) ? null : sp.getString("pos_" + label, null);
        if (s != null) {
            try {
                String[] a = s.split(",");
                return new int[]{ Integer.parseInt(a[0].trim()), Integer.parseInt(a[1].trim()) };
            } catch (Throwable t) { }
        }
        return new int[]{ dl, dt };
    }

    private void saveIconPos(String label, int lpx, int tpx) {
        if (sp == null) return;
        float d = getResources().getDisplayMetrics().density;
        sp.edit().putString("pos_" + label, Math.round(lpx / d) + "," + Math.round(tpx / d)).apply();
    }

    private int clampX(int x) {
        int max = getResources().getDisplayMetrics().widthPixels - dp(128);
        if (max < 0) max = 0;
        if (x < 0) x = 0;
        if (x > max) x = max;
        return x;
    }

    private int clampY(int y, int vh) {
        if (vh <= 0) vh = dp(90);
        int max = getResources().getDisplayMetrics().heightPixels - vh;
        if (max < 0) max = 0;
        if (y < 0) y = 0;
        if (y > max) y = max;
        return y;
    }

    private int[] snapGrid(int l, int t, int vh) {
        int col = Math.round((l - dp(GX0)) / (float) dp(GDX));
        int row = Math.round((t - dp(GY0)) / (float) dp(GDY));
        if (col < 0) col = 0;
        if (row < 0) row = 0;
        int nl = clampX(dp(GX0) + col * dp(GDX));
        int nt = clampY(dp(GY0) + row * dp(GDY), vh);
        return new int[]{ nl, nt };
    }

    private Bitmap loadAsset(String path) {
        try {
            InputStream is = getAssets().open(path);
            Bitmap bm = BitmapFactory.decodeStream(is);
            is.close();
            return bm;
        } catch (Throwable e) { Log.i("kiosk", "asset " + path + " " + e); return null; }
    }

    // ---------- 开始菜单 ----------
    private void buildStartMenu() {
        startMenu = new LinearLayout(this);
        startMenu.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xF2182030);
        bg.setStroke(dp(1), 0x24FFFFFF);
        bg.setCornerRadii(new float[]{0, 0, dp(10), dp(10), 0, 0, 0, 0});
        startMenu.setBackground(bg);
        startMenu.setPadding(dp(12), dp(10), dp(12), dp(12));

        TextView hd = new TextView(this);
        hd.setText("所有程序");
        hd.setTextColor(0xFFC8D4E4);
        hd.setTextSize(13);
        hd.setPadding(dp(6), 0, 0, dp(6));
        startMenu.addView(hd);

        menuRow(new String[][]{{"浏览器", "browser"}, {"传文件", "transfer"}});

        menuRow(new String[][]{{"音乐", "music"}, {"设置", "gear"}, {"系统清理", "clean"}});
        menuRow(new String[][]{{"回收站", "recycle"}});

        LinearLayout f = new LinearLayout(this);
        f.setOrientation(LinearLayout.HORIZONTAL);
        f.setPadding(0, dp(10), 0, 0);
        f.addView(pill("重启", "reboot"));
        f.addView(pill("关机", "poweroff"));
        f.addView(pill("主页", "home"));
        startMenu.addView(f);

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(dp(430), ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.BOTTOM | Gravity.LEFT;
        startMenu.setLayoutParams(lp);
        startMenu.setVisibility(View.GONE);
        root.addView(startMenu);
    }

    private void menuRow(String[][] items) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        for (final String[] it : items) {
            LinearLayout t = new LinearLayout(this);
            t.setOrientation(LinearLayout.VERTICAL);
            t.setGravity(Gravity.CENTER_HORIZONTAL);
            t.setPadding(0, dp(12), 0, dp(12));
            final GradientDrawable tbg = new GradientDrawable();
            tbg.setColor(0x00000000);
            tbg.setCornerRadius(dp(4));
            t.setBackground(tbg);
            ImageView im = new ImageView(this);
            im.setImageBitmap(loadAsset("icons/" + it[1] + ".png"));
            im.setScaleType(ImageView.ScaleType.FIT_CENTER);
            LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(dp(34), dp(34));
            ilp.bottomMargin = dp(6);
            t.addView(im, ilp);
            TextView tv = new TextView(this);
            tv.setText(it[0]);
            tv.setTextColor(0xFFFFFFFF);
            tv.setTextSize(12.5f);
            tv.setGravity(Gravity.CENTER);
            t.addView(tv);
            LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(dp(120), dp(98));
            tlp.rightMargin = dp(12);
            t.setLayoutParams(tlp);
            t.setClickable(true);
            t.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { menuAct(it[1]); } });
            t.setOnHoverListener(new View.OnHoverListener() {
                public boolean onHover(View v, MotionEvent e) {
                    int a = e.getActionMasked();
                    if (a == MotionEvent.ACTION_HOVER_ENTER) tbg.setColor(0x26FFFFFF);
                    else if (a == MotionEvent.ACTION_HOVER_EXIT) tbg.setColor(0x00000000);
                    return false;
                }
            });
            row.addView(t);
        }
        startMenu.addView(row);
    }

    private TextView pill(final String label, final String app) {
        TextView t = new TextView(this);
        t.setText(label);
        t.setTextColor(0xFFFFFFFF);
        t.setTextSize(13);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(16), dp(7), dp(16), dp(7));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0x1AFFFFFF);
        bg.setCornerRadius(dp(4));
        t.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = dp(8);
        t.setLayoutParams(lp);
        t.setClickable(true);
        t.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
            dismissMenus();
            if ("poweroff".equals(app)) confirmAction("确定要关机吗？", app);
            else if ("reboot".equals(app)) confirmAction("确定要重启吗？", app);
            else cgi(app);
        } });
        return t;
    }

    private void menuAct(String key) {
        if ("browser".equals(key)) openUrl("https://www.baidu.com/");
        else if ("transfer".equals(key)) openUrl(TRANSFER);
        else if ("music".equals(key)) openMusic();
        else if ("files".equals(key)) launchPkg("com.tv.filemanager");
        else if ("store".equals(key)) launchPkg("hb.tv.appstore");
        else if ("wps".equals(key)) launchComp("cn.wps.moffice_eng", "cn.wps.moffice.main.local.HomeRootActivity");
        else if ("newdoc".equals(key)) newDoc("文档", "doc", MIME_DOC);
        else if ("xls".equals(key)) newDoc("表格", "xls", MIME_XLS);
        else if ("ppt".equals(key)) newDoc("演示", "pptx", MIME_PPT);
        else if ("photo".equals(key)) launchComp("com.hisilicon.android.gallery3d", "com.hisilicon.android.gallery3d.app.Gallery");
        else if ("tv".equals(key)) launchPkg("com.ktcp.video");
        else if ("gear".equals(key)) launchPkg("com.android.settings");
        else if ("clean".equals(key)) openAct(CleanActivity.class);
        else if ("recycle".equals(key)) openAct(RecycleBinActivity.class);
        else if ("prog".equals(key)) openAct(ProgramsActivity.class);
        else if ("disp".equals(key)) openAct(SettingsActivity.class);
    }

    private void toggleStartMenu() {
        Log.i("kiosk", "toggleStartMenu vis=" + startMenu.getVisibility());
        dismissCtx();
        startMenu.setVisibility(startMenu.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
    }
    private void dismissMenus() { startMenu.setVisibility(View.GONE); ctxMenu.setVisibility(View.GONE); cgi("dnsfix"); }
    private void dismissCtx() { ctxMenu.setVisibility(View.GONE); }

    // ---------- 右键菜单 ----------
    private void buildCtxMenu() {
        ctxMenu = new LinearLayout(this);
        ctxMenu.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFA1A2232);
        bg.setStroke(dp(1), 0x2EFFFFFF);
        bg.setCornerRadius(dp(4));
        ctxMenu.setBackground(bg);
        ctxRow("刷新桌面", new Runnable() { public void run() { dismissMenus(); } });
        ctxRow("整理图标", new Runnable() { public void run() { resetIcons(); } });
        ctxRow("返回", new Runnable() { public void run() { cgi("back"); } });
        ctxRow("任务切换(最近)", new Runnable() { public void run() { cgi("tasks"); } });
        ctxRow("关闭当前程序", new Runnable() { public void run() { cgi("close"); } });
        ctxRow("程序和功能", new Runnable() { public void run() { openAct(ProgramsActivity.class); } });
        ctxRow("显示设置", new Runnable() { public void run() { openAct(SettingsActivity.class); } });
        ctxRow("系统清理", new Runnable() { public void run() { openAct(CleanActivity.class); } });
        ctxRow("回收站", new Runnable() { public void run() { openAct(RecycleBinActivity.class); } });
        ctxRow("重启电脑", new Runnable() { public void run() { cgi("reboot"); } });
        ctxMenu.setVisibility(View.GONE);
        root.addView(ctxMenu);
    }

    private void ctxRow(String label, final Runnable act) {
        TextView t = new TextView(this);
        t.setText(label);
        t.setTextColor(0xFFFFFFFF);
        t.setTextSize(13);
        t.setPadding(dp(14), dp(9), dp(14), dp(9));
        t.setClickable(true);
        t.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { dismissMenus(); act.run(); } });
        ctxMenu.addView(t);
    }

    private void showCtx(int x, int y) {
        dismissMenus();
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(dp(178), ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.TOP | Gravity.LEFT;
        int sw = getResources().getDisplayMetrics().widthPixels;
        int sh = getResources().getDisplayMetrics().heightPixels;
        lp.leftMargin = Math.min(x, sw - dp(180));
        lp.topMargin = Math.min(y, sh - dp(300));
        ctxMenu.setLayoutParams(lp);
        ctxMenu.setVisibility(View.VISIBLE);
    }

    private void toggleCtx(int x, int y) {
        if (ctxMenu != null && ctxMenu.getVisibility() == View.VISIBLE) { dismissCtx(); return; }
        showCtx(x, y);
    }

    /** 鼠标光标位置(用于右键菜单定位) */
    @Override public boolean dispatchGenericMotionEvent(MotionEvent e) {
        int a = e.getActionMasked();
        if (a == MotionEvent.ACTION_HOVER_MOVE || a == MotionEvent.ACTION_HOVER_ENTER) {
            hoverX = (int) e.getX();
            hoverY = (int) e.getY();
        }
        return super.dispatchGenericMotionEvent(e);
    }

    /** 这个 ROM 把鼠标右键映射成了 KEYCODE_BACK —— 把「来自鼠标的 BACK」当作右键 -> 弹菜单 */
    private boolean isMouseKey(KeyEvent e) {
        try {
            InputDevice d = InputDevice.getDevice(e.getDeviceId());
            return d != null && (d.getSources() & InputDevice.SOURCE_MOUSE) != 0;
        } catch (Throwable t) { return false; }
    }

    @Override public boolean dispatchKeyEvent(KeyEvent e) {
        // 这个 ROM 把鼠标右键映射成 KEYCODE_BACK。以前右击会弹右键菜单，容易被误触/残留；
        // 现改为直接吃掉：右键在桌面不做任何事，也不再弹菜单
        if (e.getKeyCode() == KeyEvent.KEYCODE_BACK && isMouseKey(e)) {
            return true;
        }
        return super.dispatchKeyEvent(e);
    }

    // ---------- 动作 ----------
    private void openUrl(String url) {
        dismissMenus();
        try {
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } catch (Throwable e) { Log.i("kiosk", "openUrl " + e); }
    }

    private void launchPkg(String pkg) {
        dismissMenus();
        if ("cn.wps.moffice_eng".equals(pkg)) { launchWps(); return; }
        if ("tv.danmaku.bili".equals(pkg)) biliPrep();
        try {
            Intent i = getPackageManager().getLaunchIntentForPackage(pkg);
            if (i != null) { i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(i); }
        } catch (Throwable e) { Log.i("kiosk", "launchPkg " + e); }
    }

    private void launchComp(String pkg, String cls) {
        dismissMenus();
        if ("cn.wps.moffice_eng".equals(pkg)) { launchWps(); return; }
        try {
            Intent i = new Intent();
            i.setComponent(new ComponentName(pkg, cls));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } catch (Throwable e) { Log.i("kiosk", "launchComp " + e); }
    }

    /** WPS 主页活动(HomeRootActivity)未导出 → 普通 App 无权 start; 只能由 root 的 CGI(am start) 拉起。 */
    private void launchWps() {
        wpsPrep();
        cgi("wps");
    }

    /** 音乐: 打开 /sdcard/Music 里第一首歌的 VIEW 意图 -> 进 ZTE 播放器音乐库 */
    private void openMusic() {
        dismissMenus();
        File[] dirs = { new File("/sdcard/Music"), new File("/sdcard/transfer"), new File("/sdcard/Download") };
        File pick = null;
        for (File dir : dirs) {
            File[] fs = dir.listFiles();
            if (fs == null) continue;
            for (File f : fs) {
                String n = f.getName().toLowerCase();
                if (n.endsWith(".mp3") || n.endsWith(".wav") || n.endsWith(".m4a")
                        || n.endsWith(".flac") || n.endsWith(".aac") || n.endsWith(".ogg")
                        || n.endsWith(".wma")) { pick = f; break; }
            }
            if (pick != null) break;
        }
        if (pick == null) {
            showLoading("\uD83C\uDFB5 音乐\n\n/sdcard/Music 里还没有歌曲。\n用桌面「传文件」上传, 或放进 /sdcard/Music。");
            h.postDelayed(new Runnable() { public void run() { hideLoading(); } }, 5000);
            return;
        }
        try {
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setDataAndType(Uri.fromFile(pick), "audio/*");
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } catch (Throwable e) { Log.i("kiosk", "openMusic " + e); }
    }

    private void openAct(Class<?> cls) {
        dismissMenus();
        try {
            Intent i = new Intent(this, cls);
            startActivity(i);
        } catch (Throwable e) { Log.i("kiosk", "openAct " + e); }
    }

    private void newDoc(String name, String ext, String mime) {
        dismissMenus();
        wpsPrep();
        try {
            File dir = new File(DOCS);
            if (!dir.exists()) dir.mkdirs();
            String tpl = "doc".equals(ext) ? ".blank_template.doc" : ("xls".equals(ext) ? ".blank_sheet.xls" : ".blank_slide.pptx");
            File src = new File(dir, tpl);
            File dst = new File(dir, name + "_" + new SimpleDateFormat("MMdd-HHmmss").format(new Date()) + "." + ext);
            copyFile(src, dst);
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setDataAndType(Uri.fromFile(dst), mime);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } catch (Throwable e) { Log.i("kiosk", "newDoc " + e); }
    }

    private void copyFile(File src, File dst) {
        try {
            InputStream in = new FileInputStream(src);
            OutputStream out = new FileOutputStream(dst);
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            in.close();
            out.close();
            try { dst.setReadable(true, false); dst.setWritable(true, false); } catch (Throwable e) { }
        } catch (Throwable e) { Log.i("kiosk", "copyFile " + e); }
    }

    /** 需要 root 的动作走本机 CGI (httpd 以 root 跑) */
    // ---- WPS “正在加载”遮罩 ----
    // 这个 ROM 里 WPS 加载文档时主线程被 dex 加载卡住; 此时鼠标一但把输入派发给它,
    // 5s 派发超时 -> 系统判无响应(用户遇到的就是这个)。遮罩是 SYSTEM_ALERT(盖在应用之上),
    // 加载期间手势落在我方这个响应正常的窗口上, WPS 就不会被判 ANR; 也顺便提示“请稍候”。
    private View loadPanel;

    private void showLoading(String txt) {
        hideLoading();
        try {
            WindowManager wm = getWindowManager();
            WindowManager.LayoutParams lp = new WindowManager.LayoutParams(-1, -1,
                    WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    android.graphics.PixelFormat.TRANSLUCENT);
            LinearLayout box = new LinearLayout(this);
            box.setOrientation(LinearLayout.VERTICAL);
            box.setGravity(Gravity.CENTER);
            box.setBackground(new android.graphics.drawable.ColorDrawable(0xCC101820));
            TextView t = new TextView(this);
            t.setText(txt);
            t.setTextColor(0xFFFFFFFF);
            t.setTextSize(22);
            t.setGravity(Gravity.CENTER);
            t.setLineSpacing(dp(8), 1f);
            box.addView(t);
            box.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { hideLoading(); } });
            loadPanel = box;
            wm.addView(loadPanel, lp);
            h.postDelayed(new Runnable() { public void run() { hideLoading(); } }, 45000);
        } catch (Throwable e) { Log.i("kiosk", "showLoading " + e); }
    }

    private void hideLoading() {
        if (loadPanel == null) return;
        try { getWindowManager().removeView(loadPanel); } catch (Throwable t) { }
        loadPanel = null;
    }

    // ---- 危险操作确认弹窗 (防误触) ----
    private View confirmPanel;

    private TextView makeBtn(String s, int color) {
        TextView b = new TextView(this);
        b.setText(s); b.setTextColor(0xFFFFFFFF); b.setTextSize(18); b.setGravity(Gravity.CENTER);
        b.setPadding(dp(30), dp(11), dp(30), dp(11));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(color); bg.setCornerRadius(dp(6));
        b.setBackground(bg); b.setClickable(true);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.leftMargin = dp(10); lp.rightMargin = dp(10);
        b.setLayoutParams(lp);
        return b;
    }

    private void confirmAction(String msg, final String app) {
        hideConfirm();
        try {
            WindowManager wm = getWindowManager();
            WindowManager.LayoutParams lp = new WindowManager.LayoutParams(-1, -1,
                    WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    android.graphics.PixelFormat.TRANSLUCENT);
            LinearLayout box = new LinearLayout(this);
            box.setOrientation(LinearLayout.VERTICAL);
            box.setGravity(Gravity.CENTER);
            box.setBackground(new android.graphics.drawable.ColorDrawable(0xD9101820));
            TextView t = new TextView(this);
            t.setText("\u26A0  " + msg);
            t.setTextColor(0xFFFFFFFF);
            t.setTextSize(24);
            t.setGravity(Gravity.CENTER);
            box.addView(t);
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER);
            row.setPadding(0, dp(22), 0, 0);
            TextView no = makeBtn("取消", 0xFF3A4560);
            TextView ok = makeBtn("确定", 0xFFB03030);
            no.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { hideConfirm(); } });
            ok.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { hideConfirm(); cgi(app); } });
            row.addView(no); row.addView(ok);
            box.addView(row);
            box.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { hideConfirm(); } });
            confirmPanel = box;
            wm.addView(confirmPanel, lp);
            h.postDelayed(new Runnable() { public void run() { hideConfirm(); } }, 15000);
        } catch (Throwable e) { Log.i("kiosk", "confirm " + e); cgi(app); }
    }

    private void hideConfirm() {
        if (confirmPanel == null) return;
        try { getWindowManager().removeView(confirmPanel); } catch (Throwable t) { }
        confirmPanel = null;
    }

    /** 打开 WPS 前: 弹遮罩(吃掉加载期间的点按, 防 ANR) + 让 CGI 预热 dex/给 WPS 进程提权 */
    private void wpsPrep() {
        showLoading("\u23F3 正在打开 WPS…\n首次打开较慢, 请稍候(不要连续点击)");
        cgi("dnsfix");
        h.postDelayed(new Runnable() { public void run() { cgi("wpsgo"); } }, 8000);
    }

    /** 打开哔哩哔哩前: 弹遮罩(这个 App 冷启动要 1 分钟上下, 遮罩吃掉加载期的点按, 也做提示) */
    private void biliPrep() {
        showLoading("\u23F3 正在打开哔哩哔哩…\n首次打开较慢, 请稍候(不要连续点击)");
        cgi("dnsfix");
    }

    /** WPS 拿到前台(本页退到后台)后再等 3s 收遮罩: 即保护了加载期, 又在文档出来后就消失 */
    @Override protected void onPause() {
        super.onPause();
        if (loadPanel != null) h.postDelayed(new Runnable() { public void run() { hideLoading(); } }, 3000);
    }

    private void cgi(final String app) {
        new Thread(new Runnable() {
            public void run() {
                try {
                    java.net.HttpURLConnection c = (java.net.HttpURLConnection)
                            new java.net.URL(RUN + app).openConnection();
                    c.setConnectTimeout(3000);
                    c.setReadTimeout(3000);
                    c.getInputStream().close();
                } catch (Throwable e) { }
            }
        }).start();
    }

    private int dp(float v) {
        return (int) (getResources().getDisplayMetrics().density * v + 0.5f);
    }

    // ---------- 右上角可折叠导航栏 ----------
    private String readNavMode() {
        String m = "auto";
        try {
            java.io.BufferedReader br = new java.io.BufferedReader(new java.io.FileReader("/data/dispset"));
            String line;
            while ((line = br.readLine()) != null) {
                if (line.startsWith("nav=")) {
                    String t = line.trim().substring(4).trim();
                    if (t.length() > 0) m = t;
                }
            }
            br.close();
        } catch (Throwable e) { }
        return m;
    }

    private void applyNavMode() {
        try {
            if (navBar == null || navLp == null || wm == null) return;
            String m = readNavMode();
            if (m.equals(navApplied)) return;
            navApplied = m;
            h.removeCallbacks(autoCollapse);
            try { wm.removeView(navBar); } catch (Throwable e) { }
            if (m.equals("off")) { barExpanded = false; return; }
            wm.addView(navBar, navLp);
            setBar(true);
            if (!m.equals("always")) h.postDelayed(autoCollapse, 6000);
        } catch (Throwable e) { Log.i("kiosk", "applyNavMode " + e); }
    }

    private void setBar(boolean expanded) {
        try {
            if (navBar == null || navLp == null || wm == null) return;
            barExpanded = expanded;
            if (navHandle != null) navHandle.setText(expanded ? "\u25B2" : "\u25BC");
            int n = navBar.getChildCount();
            for (int i = 0; i < n; i++) {
                View c = navBar.getChildAt(i);
                if (i == 0) c.setVisibility(View.VISIBLE);
                else c.setVisibility(expanded ? View.VISIBLE : View.GONE);
            }
            navLp.width = expanded ? dp(800) : dp(52);
            navBar.setAlpha(expanded ? 1f : 0.45f);
            wm.updateViewLayout(navBar, navLp);
        } catch (Throwable e) { Log.i("kiosk", "setBar " + e); }
    }

    private void barTouch() {
        if ("always".equals(navApplied)) return;
        h.removeCallbacks(autoCollapse);
        setBar(true);
        h.postDelayed(autoCollapse, 6000);
    }

    private void makeNavBar() {
        if (clock != null) return;
        try {
            wm = (WindowManager) getSystemService(WINDOW_SERVICE);
            LinearLayout bar = new LinearLayout(this);
            bar.setOrientation(LinearLayout.HORIZONTAL);
            bar.setBackgroundColor(0xE6141A26);
            bar.setGravity(Gravity.CENTER_VERTICAL);

            navHandle = btn("\u25BC", new Runnable() { public void run() {
                h.removeCallbacks(autoCollapse); setBar(!barExpanded);
                if (barExpanded) h.postDelayed(autoCollapse, 6000);
            }});
            bar.addView(navHandle);
            bar.addView(btn("\u2630  开始", new Runnable() { public void run() { Log.i("kiosk", "navbtn start"); toggleStartMenu(); } }));
            bar.addView(btn("\u25A6  任务", new Runnable() { public void run() { dismissMenus(); cgi("tasks"); } }));
            bar.addView(sep());
            bar.addView(btn("\u25C0  返回", new Runnable() { public void run() { dismissMenus(); cgi("back"); } }));
            bar.addView(btn("\u2302  桌面", new Runnable() { public void run() { dismissMenus(); cgi("home"); } }));
            bar.addView(btn("\u2715  关闭", new Runnable() { public void run() { dismissMenus(); cgi("close"); } }));
            bar.addView(sep());
            bar.addView(btn("\u21B5  确定", new Runnable() { public void run() { dismissMenus(); cgi("enter"); } }));
            bar.addView(sep());
            bar.addView(btn("\u23FB  关机", new Runnable() { public void run() { dismissMenus(); confirmAction("确定要关机吗？", "poweroff"); } }));
            bar.addView(btn("\u263E  休眠", new Runnable() { public void run() { dismissMenus(); confirmAction("进入待机(关屏)? 任意键唤醒", "suspend"); } }));

            View sp = new View(this);
            bar.addView(sp, new LinearLayout.LayoutParams(0, 1, 1f));

            clock = new TextView(this);
            clock.setTextColor(0xFFDCE6F5);
            clock.setTextSize(13);
            clock.setPadding(dp(14), 0, dp(14), 0);
            clock.setGravity(Gravity.CENTER_VERTICAL);
            bar.addView(clock);

            WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                    dp(800), dp(34),
                    WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT);
            lp.gravity = Gravity.TOP | Gravity.RIGHT;
            lp.x = dp(4);
            lp.y = dp(4);

            navBar = bar;
            navLp = lp;
            View.OnHoverListener hv = new View.OnHoverListener() {
                public boolean onHover(View v, MotionEvent e) { barTouch(); return false; }
            };
            for (int i = 0; i < bar.getChildCount(); i++) bar.getChildAt(i).setOnHoverListener(hv);
            bar.setOnTouchListener(new View.OnTouchListener() {
                public boolean onTouch(View v, MotionEvent e) { barTouch(); return false; }
            });

            applyNavMode();
        } catch (Throwable e) {
            Log.i("kiosk", "nav FAIL " + e);
            clock = null;
        }
    }

    private View sep() {
        View v = new View(this);
        v.setBackgroundColor(0x33FFFFFF);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(dp(1), dp(20));
        p.leftMargin = dp(4);
        p.rightMargin = dp(4);
        v.setLayoutParams(p);
        return v;
    }

    private TextView btn(String text, final Runnable action) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextColor(0xFFFFFFFF);
        t.setTextSize(13);
        t.setPadding(dp(12), dp(6), dp(12), dp(6));
        t.setGravity(Gravity.CENTER);
        t.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { action.run(); } });
        return t;
    }

    private void hideBars() {
        View d = getWindow().getDecorView();
        d.setSystemUiVisibility(View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }

    @Override public void onWindowFocusChanged(boolean f) {
        super.onWindowFocusChanged(f);
    }

    @Override public boolean dispatchTouchEvent(MotionEvent e) {
        try {
            if (e.getActionMasked() == MotionEvent.ACTION_DOWN
                    && (e.getButtonState() & MotionEvent.BUTTON_SECONDARY) != 0) {
                toggleCtx((int) e.getX(), (int) e.getY());
                return true;
            }
        } catch (Throwable x) { }
        return super.dispatchTouchEvent(e);
    }

    @Override public boolean onKeyDown(int keyCode, KeyEvent e) {
        if (keyCode == KeyEvent.KEYCODE_ESCAPE) { cgi("back"); return true; }
        return super.onKeyDown(keyCode, e);
    }

    @Override public void onBackPressed() {
        if (startMenu != null && startMenu.getVisibility() == View.VISIBLE) { dismissMenus(); return; }
        if (ctxMenu != null && ctxMenu.getVisibility() == View.VISIBLE) { dismissMenus(); return; }
        // 桌面按下返回: 不退出
    }

    @Override protected void onResume() {
        super.onResume();
        dismissMenus();
        if (clock == null) makeNavBar();
    }
}
