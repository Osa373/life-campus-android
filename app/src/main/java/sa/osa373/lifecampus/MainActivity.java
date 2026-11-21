package sa.osa373.lifecampus;

import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.net.Uri;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebSettings;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Button;

public class MainActivity extends Activity {
    private static final String APP_URL = "https://life-campus.osamah-arishi.workers.dev/";
    private static final String TRUSTED_HOST = "life-campus.osamah-arishi.workers.dev";
    private WebView webView;
    private LinearLayout root;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        setContentView(root);
        webView = new WebView(this);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(true);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        webView.setWebChromeClient(new WebChromeClient());
        webView.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                if ("https".equalsIgnoreCase(uri.getScheme()) && TRUSTED_HOST.equalsIgnoreCase(uri.getHost())) return false;
                try { startActivity(new Intent(Intent.ACTION_VIEW, uri)); } catch (Exception ignored) { }
                return true;
            }
            @Override public void onReceivedError(WebView view, WebResourceRequest request,
                        android.webkit.WebResourceError error) {
                if (request.isForMainFrame()) showOfflineMessage();
            }
        });
        root.addView(webView, new LinearLayout.LayoutParams(-1, -1));
        if (savedInstanceState != null) webView.restoreState(savedInstanceState);
        else webView.loadUrl(APP_URL);
    }

    private void showOfflineMessage() {
        if (isFinishing()) return;
        runOnUiThread(() -> {
            root.removeAllViews();
            LinearLayout offline = new LinearLayout(this);
            offline.setOrientation(LinearLayout.VERTICAL);
            offline.setPadding(40, 100, 40, 40);
            TextView text = new TextView(this);
            text.setText(R.string.no_connection);
            text.setTextSize(18);
            Button retry = new Button(this);
            retry.setText(R.string.retry);
            retry.setOnClickListener(v -> {
                root.removeAllViews();
                root.addView(webView, new LinearLayout.LayoutParams(-1, -1));
                webView.loadUrl(APP_URL);
            });
            offline.addView(text);
            offline.addView(retry);
            root.addView(offline);
        });
    }

    @Override public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }
    @Override protected void onSaveInstanceState(Bundle out) {
        if (webView != null) webView.saveState(out);
        super.onSaveInstanceState(out);
    }
    @Override protected void onDestroy() {
        if (webView != null) { webView.destroy(); webView = null; }
        super.onDestroy();
    }
}
