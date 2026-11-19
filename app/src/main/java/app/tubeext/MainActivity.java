package app.tubeext;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Toast;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import org.mozilla.geckoview.AllowOrDeny;
import org.mozilla.geckoview.GeckoResult;
import org.mozilla.geckoview.GeckoRuntime;
import org.mozilla.geckoview.GeckoSession;
import org.mozilla.geckoview.GeckoView;
import org.mozilla.geckoview.WebExtension;
import org.mozilla.geckoview.WebExtensionController;

/** Dedicated YouTube GeckoView client with genuine Mozilla WebExtensions support. */
public final class MainActivity extends Activity {
    private static final int PICK_XPI = 301;
    private static final String HOME = "https://m.youtube.com/";
    private static final String UBO = "https://addons.mozilla.org/firefox/downloads/latest/ublock-origin/latest.xpi";
    private static final String TRANSLATE = "https://addons.mozilla.org/firefox/downloads/latest/immersive-translate/latest.xpi";
    private static GeckoRuntime sharedRuntime;
    private GeckoRuntime runtime;
    private GeckoSession session;
    private GeckoView pageView;
    private LinearLayout toolbar, footer;
    private boolean canBack, canForward, fullscreen;
    private final Map<String, WebExtension.Action> actions = new HashMap<>();

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(17,17,17));
        getWindow().setNavigationBarColor(Color.rgb(17,17,17));
        if (sharedRuntime == null) sharedRuntime = GeckoRuntime.create(getApplicationContext());
        runtime = sharedRuntime;
        runtime.getWebExtensionController().setPromptDelegate(new ExtensionPrompts());

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.BLACK);
        setContentView(root);
        toolbar = new LinearLayout(this);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setBackgroundColor(Color.rgb(29,29,29));
        root.addView(toolbar, new LinearLayout.LayoutParams(-1, dp(49)));
        button(toolbar, "←", this::back);
        button(toolbar, "→", () -> { if (canForward) session.goForward(); });
        button(toolbar, "刷新", () -> session.reload());
        button(toolbar, "YouTube", () -> load(HOME));
        button(toolbar, "插件", this::extensionsMenu);

        pageView = new GeckoView(this);
        root.addView(pageView, new LinearLayout.LayoutParams(-1, 0, 1));
        session = new GeckoSession();
        session.setContentDelegate(new GeckoSession.ContentDelegate() {
            @Override public void onFullScreen(GeckoSession s, boolean active) {
                runOnUiThread(() -> showFullscreen(active));
            }
        });
        session.setNavigationDelegate(new GeckoSession.NavigationDelegate() {
            @Override public void onCanGoBack(GeckoSession s, boolean yes) { canBack = yes; }
            @Override public void onCanGoForward(GeckoSession s, boolean yes) { canForward = yes; }
        });
        session.open(runtime);
        pageView.setSession(session);

        footer = new LinearLayout(this);
        footer.setGravity(Gravity.CENTER);
        footer.setBackgroundColor(Color.rgb(29,29,29));
        root.addView(footer, new LinearLayout.LayoutParams(-1, dp(52)));
        button(footer, "首页", () -> load(HOME));
        button(footer, "Shorts", () -> load("https://m.youtube.com/shorts"));
        button(footer, "订阅", () -> load("https://m.youtube.com/feed/subscriptions"));
        button(footer, "我的", () -> load("https://m.youtube.com/feed/you"));
        runtime.getWebExtensionController().list().accept(
                list -> { for (WebExtension ext : list) attach(ext); },
                err -> toast("读取插件失败：" + err.getMessage()));
        openIntent(getIntent());
    }

    private Button button(LinearLayout row, String label, Runnable callback) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(12);
        b.setTextColor(Color.WHITE);
        b.setPadding(dp(1), 0, dp(1), 0);
        b.setBackgroundColor(Color.TRANSPARENT);
        row.addView(b, new LinearLayout.LayoutParams(0, -1, 1));
        b.setOnClickListener(v -> callback.run());
        return b;
    }

    private void load(String url) { if (session != null) session.loadUri(url); }
    private void openIntent(Intent intent) {
        String url = intent != null ? intent.getDataString() : null;
        load(url != null && url.startsWith("https://") ? url : HOME);
    }
    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (intent != null && intent.getDataString() != null) openIntent(intent);
    }
    @Override public void onBackPressed() { back(); }
    private void back() {
        if (fullscreen) { showFullscreen(false); return; }
        if (canBack) session.goBack();
        else super.onBackPressed();
    }
    private void showFullscreen(boolean yes) {
        fullscreen = yes;
        toolbar.setVisibility(yes ? View.GONE : View.VISIBLE);
        footer.setVisibility(yes ? View.GONE : View.VISIBLE);
        int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE;
        if (yes) flags |= View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
        getWindow().getDecorView().setSystemUiVisibility(flags);
    }
    private void extensionsMenu() {
        String[] menu = {"安装 uBlock Origin", "安装沉浸式翻译", "输入 Firefox XPI 链接",
            "导入本地已签名 XPI", "管理已安装插件", "Firefox 插件目录"};
        new AlertDialog.Builder(this).setTitle("Firefox WebExtensions")
            .setItems(menu, (dlg, index) -> {
                switch (index) {
                    case 0: install(UBO); break;
                    case 1: install(TRANSLATE); break;
                    case 2: requestUrl(); break;
                    case 3: chooseXpi(); break;
                    case 4: listInstalled(); break;
                    case 5: load("https://addons.mozilla.org/android/"); break;
                }
            }).setNegativeButton("关闭", null).show();
    }
    private void requestUrl() {
        EditText edit = new EditText(this);
        edit.setSingleLine(true);
        edit.setHint("https://example.org/extension.xpi");
        new AlertDialog.Builder(this).setTitle("安装 Mozilla 签名 XPI")
            .setView(edit).setPositiveButton("安装", (d,w) -> {
                String url = edit.getText().toString().trim();
                if (url.startsWith("https://")) install(url);
                else toast("请使用 HTTPS 地址");
            }).setNegativeButton("取消", null).show();
    }
    private void chooseXpi() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(intent, PICK_XPI);
    }
    @Override protected void onActivityResult(int request, int result, Intent intent) {
        super.onActivityResult(request, result, intent);
        if (request != PICK_XPI || result != RESULT_OK || intent == null || intent.getData() == null) return;
        try (InputStream input = getContentResolver().openInputStream(intent.getData())) {
            if (input == null) throw new IllegalStateException("文件不可读");
            File output = new File(getCacheDir(), "selected-addon.xpi");
            try (FileOutputStream sink = new FileOutputStream(output)) {
                byte[] chunk = new byte[8192];
                int count;
                while ((count = input.read(chunk)) != -1) sink.write(chunk, 0, count);
            }
            install(Uri.fromFile(output).toString());
        } catch (Exception err) { toast("读取 XPI 失败：" + err.getMessage()); }
    }
    private void install(String uri) {
        toast("正在检查签名与权限...");
        runtime.getWebExtensionController().install(uri).accept(
            ext -> { attach(ext); toast("安装完成：" + title(ext)); },
            err -> toast("安装失败：" + err.getMessage()));
    }
    private String title(WebExtension ext) {
        return ext.metaData != null && ext.metaData.name != null
                ? ext.metaData.name : ext.id;
    }
    private void attach(WebExtension ext) {
        ext.setActionDelegate(new WebExtension.ActionDelegate() {
            @Override public void onBrowserAction(WebExtension e, GeckoSession s, WebExtension.Action action) {
                if (s == null || s == session) actions.put(e.id, action);
            }
            @Override public void onPageAction(WebExtension e, GeckoSession s, WebExtension.Action action) {
                if (s == null || s == session) actions.put(e.id, action);
            }
            @Override public GeckoResult<GeckoSession> onOpenPopup(WebExtension e, WebExtension.Action action) {
                return extensionPopup();
            }
            @Override public GeckoResult<GeckoSession> onTogglePopup(WebExtension e, WebExtension.Action action) {
                return extensionPopup();
            }
        });
    }
    private GeckoResult<GeckoSession> extensionPopup() {
        GeckoSession popupSession = new GeckoSession();
        popupSession.open(runtime);
        GeckoView popupView = new GeckoView(this);
        popupView.setSession(popupSession);
        FrameLayout box = new FrameLayout(this);
        box.addView(popupView, new FrameLayout.LayoutParams(-1, dp(440)));
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("插件窗口")
            .setView(box).setPositiveButton("关闭", (d,w) -> {}).create();
        dialog.setOnDismissListener(d -> {
            popupView.releaseSession();
            popupSession.close();
        });
        dialog.show();
        return GeckoResult.fromValue(popupSession);
    }
    private void listInstalled() {
        runtime.getWebExtensionController().list().accept(exts -> {
            if (exts.isEmpty()) { toast("尚未安装插件"); return; }
            ArrayList<String> names = new ArrayList<>();
            for (WebExtension ext : exts)
                names.add(title(ext) + (ext.metaData != null && ext.metaData.enabled ? " ✓" : " (停用)"));
            new AlertDialog.Builder(this).setTitle("插件管理")
                .setItems(names.toArray(new String[0]), (d,i) -> manage(exts.get(i)))
                .setNegativeButton("关闭",null).show();
        }, err -> toast("读取失败：" + err.getMessage()));
    }
    private void manage(WebExtension ext) {
        String[] menu = {"打开插件面板", "插件设置", "启用 / 停用", "卸载"};
        new AlertDialog.Builder(this).setTitle(title(ext)).setItems(menu, (d,index) -> {
            WebExtensionController c = runtime.getWebExtensionController();
            switch (index) {
                case 0:
                    WebExtension.Action a = actions.get(ext.id);
                    if (a == null) toast("插件没有可用操作按钮");
                    else a.click();
                    break;
                case 1:
                    if (ext.metaData != null && ext.metaData.optionsPageUrl != null)
                        load(ext.metaData.optionsPageUrl);
                    else toast("插件没有独立设置页面");
                    break;
                case 2:
                    if (ext.metaData == null) { toast("无法读取插件状态"); break; }
                    GeckoResult<WebExtension> result = ext.metaData.enabled
                        ? c.disable(ext, WebExtensionController.EnableSource.USER)
                        : c.enable(ext, WebExtensionController.EnableSource.USER);
                    result.accept(newExt -> {
                        attach(newExt); toast("插件状态已更新");
                    }, err -> toast("修改失败：" + err.getMessage()));
                    break;
                case 3:
                    new AlertDialog.Builder(this).setTitle("卸载 " + title(ext) + "？")
                        .setPositiveButton("卸载", (dd,w) ->
                            c.uninstall(ext).accept(v -> {
                                actions.remove(ext.id); toast("已卸载");
                            }, err -> toast("卸载失败：" + err.getMessage())))
                        .setNegativeButton("取消",null).show();
                    break;
            }
        }).setNegativeButton("关闭",null).show();
    }
    private final class ExtensionPrompts implements WebExtensionController.PromptDelegate {
        @Override public GeckoResult<WebExtension.PermissionPromptResponse> onInstallPromptRequest(
                WebExtension ext, String[] permissions, String[] origins, String[] collection) {
            GeckoResult<WebExtension.PermissionPromptResponse> response = new GeckoResult<>();
            new AlertDialog.Builder(MainActivity.this).setTitle("安装插件：" + title(ext))
                .setMessage(permissionMessage(permissions, origins, collection))
                .setPositiveButton("允许", (d,w) -> response.complete(
                    new WebExtension.PermissionPromptResponse(true,false,false)))
                .setNegativeButton("拒绝", (d,w) -> response.complete(
                    new WebExtension.PermissionPromptResponse(false,false,false)))
                .setOnCancelListener(d -> response.complete(
                    new WebExtension.PermissionPromptResponse(false,false,false)))
                .show();
            return response;
        }
        @Override public GeckoResult<AllowOrDeny> onOptionalPrompt(
                WebExtension ext, String[] permissions, String[] origins, String[] collection) {
            GeckoResult<AllowOrDeny> response = new GeckoResult<>();
            new AlertDialog.Builder(MainActivity.this).setTitle(title(ext) + " 请求权限")
                .setMessage(permissionMessage(permissions, origins, collection))
                .setPositiveButton("允许", (d,w) -> response.complete(AllowOrDeny.ALLOW))
                .setNegativeButton("拒绝", (d,w) -> response.complete(AllowOrDeny.DENY))
                .setOnCancelListener(d -> response.complete(AllowOrDeny.DENY)).show();
            return response;
        }
        @Override public GeckoResult<AllowOrDeny> onUpdatePrompt(
                WebExtension ext, String[] permissions, String[] origins, String[] collection) {
            return onOptionalPrompt(ext, permissions, origins, collection);
        }
        private String permissionMessage(String[] api, String[] origins, String[] collection) {
            return "插件权限：" + String.join(", ", api) +
                "\n访问站点：" + String.join(", ", origins) +
                "\n数据收集：" + String.join(", ", collection) +
                "\n\n仅允许你信任的插件。";
        }
    }
    private int dp(int value) {
        return (int)(value * getResources().getDisplayMetrics().density + 0.5f);
    }
    private void toast(String value) {
        runOnUiThread(() -> Toast.makeText(this, value, Toast.LENGTH_LONG).show());
    }
    @Override protected void onDestroy() {
        if (pageView != null) pageView.releaseSession();
        if (session != null && session.isOpen()) session.close();
        super.onDestroy();
    }
}
