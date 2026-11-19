package app.tubeext;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;
import java.io.File;
import java.io.FileInputStream;
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

/** Native YouTube interface on top of a real, multi-tab GeckoView browser. */
public final class MainActivity extends Activity {
    private static final String TAG = "TubeExt";
    private static final int PICK_XPI = 301;
    private static final String HOME = "https://m.youtube.com/";
    private static final String AMO = "https://addons.mozilla.org/android/";
    private static final String UBO = "https://addons.mozilla.org/firefox/downloads/latest/ublock-origin/latest.xpi";
    private static final String TRANSLATE = "https://addons.mozilla.org/firefox/downloads/latest/immersive-translate/latest.xpi";
    private static GeckoRuntime sharedRuntime;

    private GeckoRuntime runtime;
    private GeckoView webView;
    private LinearLayout root, youtubeBar, addressBar, toolsBar;
    private EditText address;
    private final ArrayList<BrowserTab> tabs = new ArrayList<>();
    private final Map<String, WebExtension> extensions = new HashMap<>();
    private final Map<String, WebExtension.Action> defaultActions = new HashMap<>();
    private final Map<GeckoSession, Map<String, WebExtension.Action>> tabActions = new HashMap<>();
    private BrowserTab youtube;
    private int selected = -1;
    private boolean fullscreen;

    private static final class BrowserTab {
        GeckoSession session;
        boolean youtube, canBack, canForward;
        String url = "about:blank", title = "新标签";
        BrowserTab(GeckoSession session, boolean youtube) {
            this.session = session; this.youtube = youtube;
        }
    }

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        getWindow().setStatusBarColor(Color.rgb(17,17,17));
        getWindow().setNavigationBarColor(Color.rgb(17,17,17));
        if (sharedRuntime == null) sharedRuntime = GeckoRuntime.create(getApplicationContext());
        runtime = sharedRuntime;
        WebExtensionController addon = runtime.getWebExtensionController();
        addon.setPromptDelegate(new ExtensionPrompts());
        addon.setAddonManagerDelegate(new WebExtensionController.AddonManagerDelegate() {
            @Override public void onReady(WebExtension extension) { register(extension); }
            @Override public void onEnabled(WebExtension extension) { register(extension); }
            @Override public void onUninstalled(WebExtension extension) {
                extensions.remove(extension.id);
                defaultActions.remove(extension.id);
            }
        });

        root = new LinearLayout(this);
        root.setBackgroundColor(Color.BLACK);
        root.setOrientation(LinearLayout.VERTICAL);
        setContentView(root);
        root.setOnApplyWindowInsetsListener((v,insets) -> {
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets p = insets.getInsets(
                    WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                root.setPadding(p.left, fullscreen?0:p.top, p.right, fullscreen?0:p.bottom);
            }
            return insets;
        });

        webView = new GeckoView(this);
        root.addView(webView, new LinearLayout.LayoutParams(-1,0,1));

        addressBar = new LinearLayout(this);
        addressBar.setGravity(Gravity.CENTER_VERTICAL);
        addressBar.setBackgroundColor(Color.rgb(31,31,31));
        address = new EditText(this);
        address.setSingleLine(true);
        address.setTextSize(12);
        address.setTextColor(Color.WHITE);
        address.setHintTextColor(Color.LTGRAY);
        address.setHint("输入网址或搜索内容");
        address.setSelectAllOnFocus(true);
        address.setImeOptions(EditorInfo.IME_ACTION_GO);
        addressBar.addView(address, new LinearLayout.LayoutParams(0,dp(45),1));
        address.setOnEditorActionListener((v,id,event)->{
            enterAddress(address.getText().toString());
            address.clearFocus();
            return true;
        });
        button(addressBar,"前往",()->enterAddress(address.getText().toString()));
        root.addView(addressBar,new LinearLayout.LayoutParams(-1,dp(48)));

        youtubeBar = new LinearLayout(this);
        youtubeBar.setBackgroundColor(Color.rgb(28,28,28));
        root.addView(youtubeBar,new LinearLayout.LayoutParams(-1,dp(49)));
        button(youtubeBar,"首页",()->youtubeOpen(HOME));
        button(youtubeBar,"Shorts",()->youtubeOpen("https://m.youtube.com/shorts"));
        button(youtubeBar,"订阅",()->youtubeOpen("https://m.youtube.com/feed/subscriptions"));
        button(youtubeBar,"我的",()->youtubeOpen("https://m.youtube.com/feed/you"));

        toolsBar = new LinearLayout(this);
        toolsBar.setBackgroundColor(Color.rgb(42,42,42));
        root.addView(toolsBar,new LinearLayout.LayoutParams(-1,dp(52)));
        button(toolsBar,"←",this::back);
        button(toolsBar,"→",()->{
            BrowserTab t=current(); if(t!=null&&t.canForward)t.session.goForward();
        });
        button(toolsBar,"刷新",()->{
            BrowserTab t=current();if(t!=null)t.session.reload();
        });
        button(toolsBar,"标签",this::tabPicker);
        button(toolsBar,"浏览器",this::browserMenu);
        button(toolsBar,"插件",this::extensionMenu);

        youtube = createTab(HOME,true,true,true,"YouTube");
        addon.list().accept(list -> {
            for (WebExtension ext : list) register(ext);
        },error -> notice("读取插件失败: "+error.getMessage()));
        initialIntent(getIntent());
        root.requestApplyInsets();
    }

    private Button button(LinearLayout row,String label,Runnable onclick){
        Button b=new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextColor(Color.WHITE);
        b.setTextSize(12);
        b.setPadding(0,0,0,0);
        b.setBackgroundColor(Color.TRANSPARENT);
        row.addView(b,new LinearLayout.LayoutParams(0,-1,1));
        b.setOnClickListener(v->onclick.run());
        return b;
    }
    private int dp(int value){
        return (int)(value*getResources().getDisplayMetrics().density+0.5f);
    }
    private void notice(String message){
        runOnUiThread(()->Toast.makeText(this,message,Toast.LENGTH_LONG).show());
    }
    private BrowserTab current(){
        return selected>=0&&selected<tabs.size()?tabs.get(selected):null;
    }
    private void initialIntent(Intent intent){
        if(intent!=null && intent.getData()!=null &&
           "content".equals(intent.getData().getScheme())){
            importFile(intent.getData());return;
        }
        String url=intent!=null?intent.getDataString():null;
        if(url!=null&&url.startsWith("https://")){
            if(url.contains("youtube.com/")||url.contains("youtu.be/"))youtubeOpen(url);
            else openWeb(url);
        }
    }
    @Override protected void onNewIntent(Intent intent){
        super.onNewIntent(intent);
        setIntent(intent);
        initialIntent(intent);
    }

    /**
     * A returned WebExtensions tab or window must remain strongly referenced.
     * GeckoView navigates it after the delegate returns, so do not load twice.
     */
    private BrowserTab createTab(String uri, boolean dedicatedYoutube,
                                 boolean selectNow, boolean loadNow, String title){
        GeckoSession s=new GeckoSession();
        BrowserTab t=new BrowserTab(s,dedicatedYoutube);
        t.title=title;
        t.url=uri==null?"about:blank":uri;
        setupSession(t);
        s.open(runtime);
        tabs.add(t);
        tabActions.put(s,new HashMap<>());
        for(WebExtension e:extensions.values())attachTab(t,e);
        if(selectNow)select(t);
        if(loadNow && uri!=null)s.loadUri(uri);
        return t;
    }
    private BrowserTab createDelegatedTab(String upcoming,boolean selectNow){
        return createTab(upcoming,false,selectNow,false,"扩展标签页");
    }
    private void select(BrowserTab next){
        if(!tabs.contains(next))return;
        BrowserTab old=current();
        if(old==next)return;
        if(old!=null){
            webView.releaseSession();
            runtime.getWebExtensionController().setTabActive(old.session,false);
        }
        selected=tabs.indexOf(next);
        webView.setSession(next.session);
        runtime.getWebExtensionController().setTabActive(next.session,true);
        updateChrome();
    }
    private void updateChrome(){
        BrowserTab t=current();
        if(t==null)return;
        addressBar.setVisibility(!fullscreen&&!t.youtube?View.VISIBLE:View.GONE);
        youtubeBar.setVisibility(fullscreen?View.GONE:View.VISIBLE);
        toolsBar.setVisibility(fullscreen?View.GONE:View.VISIBLE);
        if(!address.hasFocus())address.setText(t.url);
    }
    private void setupSession(BrowserTab t){
        t.session.setNavigationDelegate(new GeckoSession.NavigationDelegate(){
            @Override public void onCanGoBack(GeckoSession s,boolean can){t.canBack=can;}
            @Override public void onCanGoForward(GeckoSession s,boolean can){t.canForward=can;}
            @Override public void onLocationChange(GeckoSession s,String url,
                java.util.List<GeckoSession.PermissionDelegate.ContentPermission> permissions,
                Boolean userGesture){
                if(url!=null)t.url=url;
                if(current()==t)updateChrome();
            }
            @Override public GeckoResult<GeckoSession> onNewSession(GeckoSession from,String url){
                return GeckoResult.fromValue(createDelegatedTab(url,true).session);
            }
            @Override public GeckoResult<AllowOrDeny> onLoadRequest(
                GeckoSession s,GeckoSession.NavigationDelegate.LoadRequest request){
                String url=request.uri;
                if(isAddonDownload(url)){
                    install(url);
                    return GeckoResult.fromValue(AllowOrDeny.DENY);
                }
                return GeckoResult.fromValue(AllowOrDeny.ALLOW);
            }
            @Override public GeckoResult<String> onLoadError(
                    GeckoSession s,String url,org.mozilla.geckoview.WebRequestError error){
                Log.e(TAG,"Navigation failed: "+url+" "+error.category+"/"+error.code);
                if(current()==t)notice("页面打开失败: "+url);
                return null;
            }
        });
        t.session.setContentDelegate(new GeckoSession.ContentDelegate(){
            @Override public void onTitleChange(GeckoSession s,String title){
                if(title!=null&&!title.isEmpty())t.title=title;
            }
            @Override public void onCloseRequest(GeckoSession s){closeTab(t);}
            @Override public void onFullScreen(GeckoSession s,boolean active){
                if(current()==t)fullScreen(active);
            }
            @Override public void onCrash(GeckoSession s){
                if(current()==t)notice("网页进程崩溃，可点击刷新");
            }
        });
    }
    private boolean isAddonDownload(String uri){
        try{
            Uri u=Uri.parse(uri);
            if(!"https".equalsIgnoreCase(u.getScheme()))return false;
            String path=u.getPath(),host=u.getHost();
            return path!=null&&(path.endsWith(".xpi") ||
                ("addons.mozilla.org".equalsIgnoreCase(host) &&
                path.startsWith("/firefox/downloads/latest/")));
        }catch(Exception ignored){return false;}
    }
    private void closeTab(BrowserTab t){
        if(t==null||!tabs.contains(t))return;
        if(t==youtube){
            if(tabs.size()==1){t.session.loadUri(HOME);return;}
            select(t);return;
        }
        boolean active=current()==t;
        if(active)select(youtube);
        tabs.remove(t);
        tabActions.remove(t.session);
        if(t.session.isOpen())t.session.close();
        if(active)updateChrome();
    }
    private void back(){
        BrowserTab t=current();
        if(fullscreen){fullScreen(false);return;}
        if(t!=null&&t.canBack)t.session.goBack();
        else if(t!=null&&t!=youtube)closeTab(t);
        else super.onBackPressed();
    }
    @Override public void onBackPressed(){back();}
    private void fullScreen(boolean yes){
        fullscreen=yes;
        int flags=View.SYSTEM_UI_FLAG_LAYOUT_STABLE;
        if(yes)flags|=View.SYSTEM_UI_FLAG_FULLSCREEN|
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
        getWindow().getDecorView().setSystemUiVisibility(flags);
        updateChrome();root.requestApplyInsets();
    }
    private void youtubeOpen(String uri){
        if(youtube==null)return;
        select(youtube);youtube.session.loadUri(uri);
    }
    private void openWeb(String uri){createTab(uri,false,true,true,"浏览器");}
    private void enterAddress(String source){
        String s=source.trim();if(s.isEmpty())return;
        String uri;
        if(s.matches("(?i)^(https?://|about:|moz-extension://).*"))uri=s;
        else if(!s.contains(" ")&&s.contains("."))uri="https://"+s;
        else uri="https://www.google.com/search?q="+Uri.encode(s);
        BrowserTab active=current();
        if(active==null||active.youtube)openWeb(uri);
        else active.session.loadUri(uri);
    }
    private void tabPicker(){
        ArrayList<String> labels=new ArrayList<>();
        for(int i=0;i<tabs.size();i++){
            BrowserTab t=tabs.get(i);
            String title=t.title==null?"新标签":t.title;
            labels.add((current()==t?"● ":"")+"["+(i+1)+"] "+
                (title.length()>45?title.substring(0,45):title));
        }
        new AlertDialog.Builder(this).setTitle("标签页（"+tabs.size()+"）")
            .setItems(labels.toArray(new String[0]),(d,i)->select(tabs.get(i)))
            .setNeutralButton("关闭当前",(d,w)->closeTab(current()))
            .setPositiveButton("新标签",(d,w)->openWeb("about:blank"))
            .setNegativeButton("取消",null).show();
    }
    private void browserMenu(){
        String[] items={"打开网址/搜索","新建标签页","返回 YouTube",
            "Firefox 插件官网","搜索并安装插件","已安装插件","当前页面信息"};
        new AlertDialog.Builder(this).setTitle("浏览器")
            .setItems(items,(d,i)->{
                switch(i){
                    case 0:addressDialog();break;
                    case 1:openWeb("about:blank");break;
                    case 2:select(youtube);break;
                    case 3:openWeb(AMO);break;
                    case 4:openStore();break;
                    case 5:installed();break;
                    case 6:debugTab();break;
                }
            }).setNegativeButton("关闭",null).show();
    }
    private void addressDialog(){
        EditText field=new EditText(this);
        field.setSingleLine(true);
        BrowserTab t=current();
        field.setText(t==null?"":t.url);
        field.selectAll();
        new AlertDialog.Builder(this).setTitle("网址或搜索")
            .setView(field).setPositiveButton("前往",(d,w)->enterAddress(field.getText().toString()))
            .setNegativeButton("取消",null).show();
    }
    private void debugTab(){
        BrowserTab t=current();if(t==null)return;
        new AlertDialog.Builder(this).setTitle("当前标签页信息")
            .setMessage("标题："+t.title+"\n地址："+t.url+
                "\n标签总数："+tabs.size()+
                "\n返回/前进："+t.canBack+"/"+t.canForward)
            .setPositiveButton("好的",null).show();
    }

    private void openStore(){AddonStore.open(this,this::install,this::openWeb);}
    private void extensionMenu(){
        String[] items={"管理已安装插件","插件市场（可安装）",
            "Firefox 插件官网（浏览器）","安装 uBlock Origin","安装沉浸式翻译",
            "从文件导入 XPI","从 HTTPS 链接安装"};
        new AlertDialog.Builder(this).setTitle("Firefox 插件")
            .setItems(items,(d,i)->{
                switch(i){
                    case 0:installed();break;
                    case 1:openStore();break;
                    case 2:openWeb(AMO);break;
                    case 3:install(UBO);break;
                    case 4:install(TRANSLATE);break;
                    case 5:chooseXpi();break;
                    case 6:installDialog();break;
                }
            }).setNegativeButton("关闭",null).show();
    }
    private void installDialog(){
        EditText input=new EditText(this);
        input.setSingleLine(true);
        input.setHint("https://example.org/addon.xpi");
        new AlertDialog.Builder(this).setTitle("安装 Mozilla 签名的 XPI")
            .setView(input).setPositiveButton("安装",(d,w)->{
                String url=input.getText().toString().trim();
                if(url.startsWith("https://"))install(url);
                else notice("请输入 HTTPS 的 XPI 链接");
            }).setNegativeButton("取消",null).show();
    }
    private void chooseXpi(){
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.setType("*/*");
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivityForResult(i,PICK_XPI);
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(request==PICK_XPI && result==RESULT_OK &&
           data!=null && data.getData()!=null)importFile(data.getData());
    }
    private void importFile(Uri uri){
        notice("正在导入插件文件...");
        new Thread(()->{
            File output=new File(getCacheDir(),"addon-"+UUID.randomUUID()+".xpi");
            try(InputStream in=getContentResolver().openInputStream(uri)){
                if(in==null)throw new IllegalArgumentException("文件不可读");
                long total=0;
                try(FileOutputStream out=new FileOutputStream(output)){
                    byte[] buf=new byte[8192];
                    int len;
                    while((len=in.read(buf))!=-1){
                        total+=len;
                        if(total>100*1024*1024)throw new IllegalArgumentException("文件超过100MB");
                        out.write(buf,0,len);
                    }
                }
                try(FileInputStream verify=new FileInputStream(output)){
                    if(verify.read()!=0x50||verify.read()!=0x4b)
                        throw new IllegalArgumentException("不是有效 XPI 文件");
                }
                runOnUiThread(()->install(Uri.fromFile(output).toString(),output));
            }catch(Exception e){
                output.delete();notice("读取失败: "+e.getMessage());
            }
        },"local-xpi").start();
    }
    private void install(String url){install(url,null);}
    private void install(String url,File temp){
        notice("安装并验证插件签名...");
        runtime.getWebExtensionController().install(url).accept(
            e->{
                if(temp!=null)temp.delete();
                register(e);
                notice("安装成功: "+title(e));
            },err->{
                if(temp!=null)temp.delete();
                notice("安装失败: "+err.getMessage());
            });
    }
    private String title(WebExtension e){
        return e.metaData!=null&&e.metaData.name!=null?e.metaData.name:e.id;
    }
    private void register(WebExtension e){
        extensions.put(e.id,e);
        e.setActionDelegate(new WebExtension.ActionDelegate(){
            @Override public void onBrowserAction(WebExtension ext,GeckoSession s,WebExtension.Action a){
                if(s==null)defaultActions.put(ext.id,a);
            }
            @Override public void onPageAction(WebExtension ext,GeckoSession s,WebExtension.Action a){
                if(s==null)defaultActions.put(ext.id,a);
            }
            @Override public GeckoResult<GeckoSession> onOpenPopup(WebExtension ext,WebExtension.Action action){
                return GeckoResult.fromValue(createDelegatedTab(null,true).session);
            }
            @Override public GeckoResult<GeckoSession> onTogglePopup(WebExtension ext,WebExtension.Action action){
                return GeckoResult.fromValue(createDelegatedTab(null,true).session);
            }
        });
        e.setTabDelegate(new WebExtension.TabDelegate(){
            @Override public GeckoResult<GeckoSession> onNewTab(
                    WebExtension ext,WebExtension.CreateTabDetails detail){
                boolean show=detail.active==null || detail.active;
                BrowserTab t=createDelegatedTab(detail.url,show);
                return GeckoResult.fromValue(t.session);
            }
            @Override public void onOpenOptionsPage(WebExtension ext){
                openOptions(ext);
            }
        });
        for(BrowserTab t:tabs)attachTab(t,e);
    }
    private void attachTab(BrowserTab tab,WebExtension e){
        tab.session.getWebExtensionController().setActionDelegate(e,new WebExtension.ActionDelegate(){
            @Override public void onBrowserAction(WebExtension ext,GeckoSession s,WebExtension.Action a){
                storeAction(tab,ext,a);
            }
            @Override public void onPageAction(WebExtension ext,GeckoSession s,WebExtension.Action a){
                storeAction(tab,ext,a);
            }
            @Override public GeckoResult<GeckoSession> onOpenPopup(WebExtension ext,WebExtension.Action a){
                return GeckoResult.fromValue(createDelegatedTab(null,true).session);
            }
            @Override public GeckoResult<GeckoSession> onTogglePopup(WebExtension ext,WebExtension.Action a){
                return GeckoResult.fromValue(createDelegatedTab(null,true).session);
            }
        });
        tab.session.getWebExtensionController().setTabDelegate(e,new WebExtension.SessionTabDelegate(){
            @Override public GeckoResult<AllowOrDeny> onCloseTab(WebExtension ext,GeckoSession s){
                if(tab==youtube)return GeckoResult.fromValue(AllowOrDeny.DENY);
                closeTab(tab);
                return GeckoResult.fromValue(AllowOrDeny.ALLOW);
            }
            @Override public GeckoResult<AllowOrDeny> onUpdateTab(
                WebExtension ext,GeckoSession s,WebExtension.UpdateTabDetails detail){
                return GeckoResult.fromValue(AllowOrDeny.ALLOW);
            }
        });
    }
    private void storeAction(BrowserTab tab,WebExtension e,WebExtension.Action a){
        Map<String,WebExtension.Action> values=tabActions.get(tab.session);
        if(values!=null)values.put(e.id,a);
    }
    private WebExtension.Action actionFor(WebExtension e){
        BrowserTab t=current();
        WebExtension.Action action=t==null?null:tabActions.get(t.session).get(e.id);
        if(action!=null)return action;
        return defaultActions.get(e.id);
    }
    private void openOptions(WebExtension e){
        WebExtension ext=extensions.containsKey(e.id)?extensions.get(e.id):e;
        String options=ext.metaData==null?null:ext.metaData.optionsPageUrl;
        if(options!=null&&!options.isEmpty()){
            openWeb(options);return;
        }
        WebExtension.Action a=actionFor(ext);
        if(a!=null){a.click();return;}
        String detail="该插件没有公开 optionsPageUrl，也尚未提供可点击操作按钮。"+
            "\n扩展 ID: "+ext.id+
            "\n扩展根目录: "+(ext.metaData==null?"未知":ext.metaData.baseUrl)+
            "\n\n请在 YouTube 页面加载后再尝试打开插件面板。";
        new AlertDialog.Builder(this).setTitle(title(ext)+" 无法打开设置")
            .setMessage(detail)
            .setPositiveButton("返回",null).show();
    }
    private void installed(){
        runtime.getWebExtensionController().list().accept(list->{
            if(list.isEmpty()){notice("尚未安装插件");return;}
            ArrayList<String> names=new ArrayList<>();
            for(WebExtension e:list){
                register(e);
                names.add(title(e)+(e.metaData!=null&&e.metaData.enabled?" ✓":" (已禁用)"));
            }
            new AlertDialog.Builder(this).setTitle("已安装插件")
                .setItems(names.toArray(new String[0]),(d,i)->manage(list.get(i)))
                .setNegativeButton("返回",null).show();
        },e->notice("读取列表失败: "+e.getMessage()));
    }
    private void manage(WebExtension e){
        String[] actions={"插件设置（浏览器标签）","打开插件面板",
            "启用/停用","查看插件信息","卸载"};
        new AlertDialog.Builder(this).setTitle(title(e)).setItems(actions,(d,i)->{
            WebExtensionController c=runtime.getWebExtensionController();
            switch(i){
                case 0:openOptions(e);break;
                case 1:{
                    WebExtension.Action a=actionFor(e);
                    if(a!=null)a.click();
                    else openOptions(e);
                    break;
                }
                case 2:{
                    if(e.metaData==null){notice("插件信息尚未加载");break;}
                    GeckoResult<WebExtension> op=e.metaData.enabled?
                        c.disable(e,WebExtensionController.EnableSource.USER):
                        c.enable(e,WebExtensionController.EnableSource.USER);
                    op.accept(updated->{register(updated);notice("状态已更改");},
                        err->notice("修改失败: "+err.getMessage()));
                    break;
                }
                case 3:new AlertDialog.Builder(this).setTitle(title(e))
                    .setMessage("ID: "+e.id+
                      "\n设置页: "+(e.metaData==null?"未知":e.metaData.optionsPageUrl)+
                      "\n扩展地址: "+(e.metaData==null?"未知":e.metaData.baseUrl))
                    .setPositiveButton("确定",null).show();break;
                case 4:new AlertDialog.Builder(this).setTitle("确定卸载？")
                    .setMessage(title(e))
                    .setPositiveButton("卸载",(dd,w)->c.uninstall(e).accept(
                        unused->{extensions.remove(e.id);notice("已卸载");},
                        err->notice("卸载失败: "+err.getMessage())))
                    .setNegativeButton("取消",null).show();break;
            }
        }).setNegativeButton("取消",null).show();
    }

    private final class ExtensionPrompts implements WebExtensionController.PromptDelegate {
        @Override public GeckoResult<WebExtension.PermissionPromptResponse> onInstallPromptRequest(
            WebExtension e,String[] permissions,String[] origins,String[] collection){
            GeckoResult<WebExtension.PermissionPromptResponse> result=new GeckoResult<>();
            new AlertDialog.Builder(MainActivity.this).setTitle("安装 "+title(e)+"？")
                .setMessage(permissionsText(permissions,origins,collection))
                .setPositiveButton("允许",(d,w)->result.complete(
                    new WebExtension.PermissionPromptResponse(true,false,false)))
                .setNegativeButton("拒绝",(d,w)->result.complete(
                    new WebExtension.PermissionPromptResponse(false,false,false)))
                .setOnCancelListener(d->result.complete(
                    new WebExtension.PermissionPromptResponse(false,false,false))).show();
            return result;
        }
        @Override public GeckoResult<AllowOrDeny> onOptionalPrompt(
            WebExtension e,String[] permissions,String[] origins,String[] collection){
            GeckoResult<AllowOrDeny> result=new GeckoResult<>();
            new AlertDialog.Builder(MainActivity.this).setTitle(title(e)+" 请求权限")
                .setMessage(permissionsText(permissions,origins,collection))
                .setPositiveButton("允许",(d,w)->result.complete(AllowOrDeny.ALLOW))
                .setNegativeButton("拒绝",(d,w)->result.complete(AllowOrDeny.DENY))
                .setOnCancelListener(d->result.complete(AllowOrDeny.DENY)).show();
            return result;
        }
        @Override public GeckoResult<AllowOrDeny> onUpdatePrompt(
            WebExtension e,String[] permissions,String[] origins,String[] collection){
            return onOptionalPrompt(e,permissions,origins,collection);
        }
    }
    private String permissionsText(String[] a,String[] b,String[] c){
        return "插件权限: "+(a==null?"":String.join(", ",a))+
            "\n可访问站点: "+(b==null?"":String.join(", ",b))+
            "\n数据收集: "+(c==null?"":String.join(", ",c))+
            "\n\n只安装信任的插件。";
    }
    @Override protected void onDestroy(){
        if(webView!=null)webView.releaseSession();
        for(BrowserTab tab:new ArrayList<>(tabs))
            if(tab.session.isOpen())tab.session.close();
        tabs.clear();
        super.onDestroy();
    }
}
