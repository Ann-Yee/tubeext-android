package app.tubeext;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
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
import java.util.UUID;
import org.mozilla.geckoview.AllowOrDeny;
import org.mozilla.geckoview.GeckoResult;
import org.mozilla.geckoview.GeckoRuntime;
import org.mozilla.geckoview.GeckoSession;
import org.mozilla.geckoview.GeckoView;
import org.mozilla.geckoview.WebExtension;
import org.mozilla.geckoview.WebExtensionController;

/**
 * TubeExt 0.2: YouTube stays in the main GeckoSession; the entire app UI
 * (including extension settings and AMO store) is reachable from bottom controls.
 */
public final class MainActivity extends Activity {
    private static final int PICK_XPI = 301;
    private static final String HOME = "https://m.youtube.com/";
    private static final String UBO = "https://addons.mozilla.org/firefox/downloads/latest/ublock-origin/latest.xpi";
    private static final String TRANSLATE = "https://addons.mozilla.org/firefox/downloads/latest/immersive-translate/latest.xpi";
    private static GeckoRuntime sharedRuntime;
    private GeckoRuntime runtime;
    private GeckoSession session;
    private GeckoView pageView;
    private GeckoSession overlaySession;
    private GeckoView overlayView;
    private LinearLayout overlayFrame, root, navigation, tools;
    private FrameLayout browserFrame;
    private boolean canBack, canForward, fullscreen;
    private String waitingOptionsId;
    private final Map<String, WebExtension.Action> actions = new HashMap<>();
    private final Map<String, WebExtension> extensions = new HashMap<>();

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(18,18,18));
        getWindow().setNavigationBarColor(Color.rgb(18,18,18));
        if (sharedRuntime == null) sharedRuntime = GeckoRuntime.create(getApplicationContext());
        runtime = sharedRuntime;
        WebExtensionController manager = runtime.getWebExtensionController();
        manager.setPromptDelegate(new ExtensionPrompts());
        manager.setAddonManagerDelegate(new WebExtensionController.AddonManagerDelegate() {
            @Override public void onReady(WebExtension extension) {
                registerExtension(extension);
                if (extension.id.equals(waitingOptionsId)) {
                    waitingOptionsId = null;
                    openOptions(extension);
                }
            }
            @Override public void onEnabled(WebExtension extension) { registerExtension(extension); }
            @Override public void onUninstalled(WebExtension extension) {
                extensions.remove(extension.id);
                actions.remove(extension.id);
            }
        });

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(10,10,10));
        setContentView(root);
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets safe = insets.getInsets(
                    WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                root.setPadding(safe.left, fullscreen ? 0 : safe.top,
                    safe.right, fullscreen ? 0 : safe.bottom);
            }
            return insets;
        });

        browserFrame = new FrameLayout(this);
        root.addView(browserFrame, new LinearLayout.LayoutParams(-1, 0, 1));
        pageView = new GeckoView(this);
        browserFrame.addView(pageView, new FrameLayout.LayoutParams(-1, -1));
        session = new GeckoSession();
        session.setContentDelegate(new GeckoSession.ContentDelegate() {
            @Override public void onFullScreen(GeckoSession source, boolean active) {
                runOnUiThread(() -> showFullscreen(active));
            }
        });
        session.setNavigationDelegate(new GeckoSession.NavigationDelegate() {
            @Override public void onCanGoBack(GeckoSession source, boolean value) { canBack = value; }
            @Override public void onCanGoForward(GeckoSession source, boolean value) { canForward = value; }
        });
        session.open(runtime);
        pageView.setSession(session);

        // Both navigation bars are explicitly below the web page; no toolbar
        // competes with the Android status-bar clock, battery or camera cutout.
        navigation = new LinearLayout(this);
        navigation.setGravity(Gravity.CENTER_VERTICAL);
        navigation.setBackgroundColor(Color.rgb(27,27,27));
        root.addView(navigation, new LinearLayout.LayoutParams(-1, dp(49)));
        button(navigation, "首页", () -> load(HOME));
        button(navigation, "Shorts", () -> load("https://m.youtube.com/shorts"));
        button(navigation, "订阅", () -> load("https://m.youtube.com/feed/subscriptions"));
        button(navigation, "我的", () -> load("https://m.youtube.com/feed/you"));

        tools = new LinearLayout(this);
        tools.setGravity(Gravity.CENTER_VERTICAL);
        tools.setBackgroundColor(Color.rgb(40,40,40));
        root.addView(tools, new LinearLayout.LayoutParams(-1, dp(51)));
        button(tools, "←", this::back);
        button(tools, "→", () -> { if (canForward) session.goForward(); });
        button(tools, "刷新", () -> session.reload());
        button(tools, "市场", this::openMarketplace);
        button(tools, "插件", this::extensionsMenu);

        manager.list().accept(
            items -> { for (WebExtension ext : items) registerExtension(ext); },
            err -> toast("读取插件失败：" + err.getMessage()));
        openIntent(getIntent());
        root.requestApplyInsets();
    }

    private Button button(LinearLayout row, String label, Runnable callback) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(13);
        b.setTextColor(Color.WHITE);
        b.setPadding(0, 0, 0, 0);
        b.setBackgroundColor(Color.TRANSPARENT);
        row.addView(b, new LinearLayout.LayoutParams(0, -1, 1));
        b.setOnClickListener(v -> callback.run());
        return b;
    }
    private void load(String url) { if (session != null) session.loadUri(url); }
    private void openIntent(Intent intent) {
        if (intent != null && intent.getData() != null &&
                "content".equals(intent.getData().getScheme())) {
            importDocument(intent.getData());
            if (session != null) load(HOME);
            return;
        }
        String url = intent == null ? null : intent.getDataString();
        if (url != null && url.startsWith("https://")) load(url);
        else load(HOME);
    }
    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (intent != null) openIntent(intent);
    }
    @Override public void onBackPressed() { back(); }
    private void back() {
        if (overlayFrame != null) { closeOverlay(); return; }
        if (fullscreen) { showFullscreen(false); return; }
        if (canBack) session.goBack();
        else super.onBackPressed();
    }
    private void showFullscreen(boolean show) {
        fullscreen = show;
        navigation.setVisibility(show ? View.GONE : View.VISIBLE);
        tools.setVisibility(show ? View.GONE : View.VISIBLE);
        int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE;
        if (show) flags |= View.SYSTEM_UI_FLAG_FULLSCREEN
              | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
        getWindow().getDecorView().setSystemUiVisibility(flags);
        root.requestApplyInsets();
    }
    private void openMarketplace() {
        AddonStore.open(this, this::install);
    }
    private void extensionsMenu() {
        String[] items = {"插件市场（搜索并一键安装）", "管理已安装插件",
            "安装 uBlock Origin", "安装沉浸式翻译", "从本地文件导入 XPI",
            "通过 HTTPS / XPI 链接安装", "查看 Mozilla 插件官网"};
        new AlertDialog.Builder(this).setTitle("插件中心 · WebExtensions")
            .setItems(items, (dlg, index) -> {
                switch (index) {
                    case 0: openMarketplace(); break;
                    case 1: listInstalled(); break;
                    case 2: install(UBO); break;
                    case 3: install(TRANSLATE); break;
                    case 4: chooseXpi(); break;
                    case 5: requestUrl(); break;
                    case 6: openOverlayPage("https://addons.mozilla.org/android/", "Mozilla 插件官网"); break;
                }
            }).setNegativeButton("关闭", null).show();
    }
    private void requestUrl() {
        EditText edit = new EditText(this);
        edit.setSingleLine(true);
        edit.setHint("https://example.org/signed-extension.xpi");
        LinearLayout wrapper = new LinearLayout(this);
        wrapper.setPadding(dp(18), dp(8), dp(18), 0);
        wrapper.addView(edit, new LinearLayout.LayoutParams(-1, -2));
        new AlertDialog.Builder(this).setTitle("安装自定义签名插件")
            .setMessage("支持 Mozilla 签名的 Firefox XPI。Chrome CRX 与未签名 XPI 不适用。")
            .setView(wrapper).setPositiveButton("安装", (d,w) -> {
                String value = edit.getText().toString().trim();
                if (value.startsWith("https://")) install(value);
                else toast("需要 HTTPS 插件下载链接");
            }).setNegativeButton("取消", null).show();
    }
    private void chooseXpi() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivityForResult(intent, PICK_XPI);
    }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == PICK_XPI && result == RESULT_OK && data != null &&
                data.getData() != null) importDocument(data.getData());
    }
    private void importDocument(Uri contentUri) {
        toast("正在读取本地插件...");
        // SAF content:// cannot be passed to Gecko's XPI installer directly;
        // copy to private app storage and use the supported file:// URI.
        new Thread(() -> {
            File output = new File(getCacheDir(), "addon-" + UUID.randomUUID() + ".xpi");
            try (InputStream in = getContentResolver().openInputStream(contentUri)) {
                if (in == null) throw new IllegalArgumentException("无法读取所选文件");
                try (FileOutputStream out = new FileOutputStream(output)) {
                    byte[] bytes = new byte[16384];
                    int amount;
                    long total = 0;
                    while ((amount = in.read(bytes)) != -1) {
                        total += amount;
                        if (total > 100 * 1024 * 1024) throw new IllegalArgumentException("文件超过 100MB");
                        out.write(bytes, 0, amount);
                    }
                    if (total < 4) throw new IllegalArgumentException("插件文件为空");
                }
                try (InputStream check = new java.io.FileInputStream(output)) {
                    if (check.read() != 0x50 || check.read() != 0x4b)
                        throw new IllegalArgumentException("请选择有效的 XPI/ZIP 插件文件");
                }
                runOnUiThread(() -> install(Uri.fromFile(output).toString(), output));
            } catch (Exception ex) {
                output.delete();
                toast("导入失败：" + ex.getMessage());
            }
        }, "xpi-import").start();
    }
    private void install(String uri) { install(uri, null); }
    private void install(String uri, File tempFile) {
        toast("正在验证签名和插件权限...");
        runtime.getWebExtensionController().install(uri).accept(
            ext -> {
                if (tempFile != null) tempFile.delete();
                registerExtension(ext);
                toast("已安装：" + title(ext));
            },
            err -> {
                if (tempFile != null) tempFile.delete();
                toast("安装失败：" + err.getMessage());
            });
    }
    private String title(WebExtension ext) {
        return ext.metaData != null && ext.metaData.name != null
                ? ext.metaData.name : ext.id;
    }
    private void registerExtension(WebExtension ext) {
        extensions.put(ext.id, ext);
        WebExtension.ActionDelegate actionDelegate = new WebExtension.ActionDelegate() {
            @Override public void onBrowserAction(WebExtension e, GeckoSession s, WebExtension.Action a) {
                if (s == null || s == session) actions.put(e.id, a);
            }
            @Override public void onPageAction(WebExtension e, GeckoSession s, WebExtension.Action a) {
                if (s == null || s == session) actions.put(e.id, a);
            }
            @Override public GeckoResult<GeckoSession> onOpenPopup(WebExtension e, WebExtension.Action a) {
                return createPopup();
            }
            @Override public GeckoResult<GeckoSession> onTogglePopup(WebExtension e, WebExtension.Action a) {
                return createPopup();
            }
        };
        ext.setActionDelegate(actionDelegate);
        session.getWebExtensionController().setActionDelegate(ext, actionDelegate);
        ext.setTabDelegate(new WebExtension.TabDelegate() {
            @Override public void onOpenOptionsPage(WebExtension source) {
                openOptions(source);
            }
            @Override public GeckoResult<GeckoSession> onNewTab(
                    WebExtension source, WebExtension.CreateTabDetails details) {
                return GeckoResult.fromValue(openOverlaySession(null, "插件标签页"));
            }
        });
    }
    private GeckoResult<GeckoSession> createPopup() {
        GeckoSession popup = new GeckoSession();
        popup.open(runtime);
        GeckoView view = new GeckoView(this);
        view.setSession(popup);
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.addView(view, new LinearLayout.LayoutParams(-1, dp(440)));
        AlertDialog dialog = new AlertDialog.Builder(this)
            .setTitle("插件操作面板")
            .setView(panel).setPositiveButton("关闭", (d,w) -> {}).create();
        dialog.setOnDismissListener(d -> {
            view.releaseSession();
            if (popup.isOpen()) popup.close();
        });
        dialog.show();
        if (dialog.getWindow() != null)
            dialog.getWindow().setLayout(
                getResources().getDisplayMetrics().widthPixels - dp(20), -2);
        return GeckoResult.fromValue(popup);
    }
    private void openOptions(WebExtension raw) {
        WebExtension ext = extensions.getOrDefault(raw.id, raw);
        if (ext.metaData == null || ext.metaData.optionsPageUrl == null ||
                ext.metaData.optionsPageUrl.isEmpty()) {
            // Installation completion precedes extension startup; onReady
            // is the reliable moment for optionsPageUrl to become available.
            if (ext.metaData == null || ext.metaData.baseUrl == null ||
                    ext.metaData.baseUrl.isEmpty()) {
                waitingOptionsId = ext.id;
                toast("插件正在初始化，设置页就绪后自动打开");
                return;
            }
            WebExtension.Action action = actions.get(ext.id);
            if (action != null) { action.click(); return; }
            toast("此插件未提供设置页或按钮，可在网页上使用它的悬浮入口");
            return;
        }
        openOverlayPage(ext.metaData.optionsPageUrl, title(ext) + " 设置");
    }
    private void openOverlayPage(String url, String title) {
        openOverlaySession(url, title);
    }
    private GeckoSession openOverlaySession(String url, String title) {
        closeOverlay();
        GeckoSession other = new GeckoSession();
        other.setContentDelegate(new GeckoSession.ContentDelegate() {});
        other.setNavigationDelegate(new GeckoSession.NavigationDelegate() {});
        other.open(runtime);
        overlaySession = other;
        overlayView = new GeckoView(this);
        overlayView.setSession(other);
        overlayFrame = new LinearLayout(this);
        overlayFrame.setOrientation(LinearLayout.VERTICAL);
        overlayFrame.setBackgroundColor(Color.BLACK);
        overlayFrame.addView(overlayView, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout controls = new LinearLayout(this);
        controls.setBackgroundColor(Color.rgb(38,38,38));
        overlayFrame.addView(controls, new LinearLayout.LayoutParams(-1, dp(52)));
        button(controls, "返回 YouTube", this::closeOverlay);
        button(controls, "返回", () -> {
            if (overlaySession != null) overlaySession.goBack();
        });
        button(controls, "刷新", () -> {
            if (overlaySession != null) overlaySession.reload();
        });
        browserFrame.addView(overlayFrame, new FrameLayout.LayoutParams(-1, -1));
        if (url != null) other.loadUri(url);
        return other;
    }
    private void closeOverlay() {
        if (overlayFrame == null) return;
        if (overlayView != null) overlayView.releaseSession();
        if (overlaySession != null && overlaySession.isOpen()) overlaySession.close();
        browserFrame.removeView(overlayFrame);
        overlayFrame = null;
        overlayView = null;
        overlaySession = null;
    }
    private void listInstalled() {
        runtime.getWebExtensionController().list().accept(list -> {
            if (list.isEmpty()) { toast("尚未安装插件，请点击底部市场"); return; }
            ArrayList<String> labels = new ArrayList<>();
            for (WebExtension ext : list) {
                registerExtension(ext);
                labels.add(title(ext) + (ext.metaData != null && ext.metaData.enabled
                    ? "  ✓" : "  (停用)"));
            }
            new AlertDialog.Builder(this).setTitle("已安装插件")
                .setItems(labels.toArray(new String[0]), (d,i) -> manage(list.get(i)))
                .setNegativeButton("关闭",null).show();
        }, err -> toast("读取失败：" + err.getMessage()));
    }
    private void manage(WebExtension ext) {
        String[] choices = {"插件设置", "打开插件面板", "启用 / 停用", "卸载"};
        new AlertDialog.Builder(this).setTitle(title(ext)).setItems(choices, (d,index) -> {
            WebExtensionController manager = runtime.getWebExtensionController();
            switch (index) {
                case 0: openOptions(ext); break;
                case 1:
                    WebExtension.Action a = actions.get(ext.id);
                    if (a != null) a.click();
                    else {
                        // Some extensions define a page-specific action only after
                        // the page is loaded. Try an options page as a fallback.
                        openOptions(ext);
                    }
                    break;
                case 2:
                    if (ext.metaData == null) { toast("扩展正在加载"); return; }
                    GeckoResult<WebExtension> changed = ext.metaData.enabled
                        ? manager.disable(ext, WebExtensionController.EnableSource.USER)
                        : manager.enable(ext, WebExtensionController.EnableSource.USER);
                    changed.accept(updated -> {
                        registerExtension(updated);
                        toast("状态更新完成");
                    }, err -> toast("修改失败：" + err.getMessage()));
                    break;
                case 3:
                    new AlertDialog.Builder(this).setTitle("卸载 " + title(ext) + "？")
                        .setPositiveButton("卸载", (dd,w) ->
                            manager.uninstall(ext).accept(v -> {
                                actions.remove(ext.id);
                                extensions.remove(ext.id);
                                toast("已卸载");
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
            new AlertDialog.Builder(MainActivity.this).setTitle("安装：" + title(ext))
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
            new AlertDialog.Builder(MainActivity.this).setTitle(title(ext) + " 申请权限")
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
        private String permissionMessage(String[] perms, String[] origins, String[] collection) {
            return "插件权限：" + (perms == null ? "" : String.join(", ", perms)) +
                "\n访问站点：" + (origins == null ? "" : String.join(", ", origins)) +
                "\n数据收集：" + (collection == null ? "" : String.join(", ", collection)) +
                "\n\n仅允许你信任的插件。";
        }
    }
    private int dp(int value) {
        return (int)(value * getResources().getDisplayMetrics().density + 0.5f);
    }
    private void toast(String message) {
        runOnUiThread(() -> Toast.makeText(this, message, Toast.LENGTH_LONG).show());
    }
    @Override protected void onDestroy() {
        closeOverlay();
        if (pageView != null) pageView.releaseSession();
        if (session != null && session.isOpen()) session.close();
        super.onDestroy();
    }
}
