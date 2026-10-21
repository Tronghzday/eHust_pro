package com.example.ehustpro;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.webkit.ConsoleMessage;
import android.webkit.JavascriptInterface;
import android.webkit.JsResult;
import android.webkit.PermissionRequest;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

public class MainActivity extends AppCompatActivity {

    private WebView webView;
    private static final String CHANNEL_ID = "web_notifications";
    private static final int NOTIFICATION_PERMISSION_CODE = 100;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Fullscreen
        getWindow().setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        );
        
        View decorView = getWindow().getDecorView();
        int uiOptions = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
        decorView.setSystemUiVisibility(uiOptions);
        
        setContentView(R.layout.activity_main);

        createNotificationChannel();
        requestNotificationPermission();

        webView = findViewById(R.id.webView);
        setupWebView();
        
        webView.loadUrl("file:///android_asset/index.html");
    }

    private void setupWebView() {
        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setAllowFileAccess(true);
        webSettings.setAllowContentAccess(true);
        webSettings.setDatabaseEnabled(true);
        webSettings.setJavaScriptCanOpenWindowsAutomatically(true);
        
        // Thêm JavaScript Interface để web có thể gọi
        webView.addJavascriptInterface(new WebAppInterface(), "AndroidNotification");
        
        webView.setWebViewClient(new WebViewClient());
        
        // Custom WebChromeClient để xử lý permissions và notifications
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onPermissionRequest(final PermissionRequest request) {
                runOnUiThread(() -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        String[] requestedResources = request.getResources();
                        for (String r : requestedResources) {
                            if (r.equals(PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID) ||
                                r.equals(PermissionRequest.RESOURCE_VIDEO_CAPTURE) ||
                                r.equals(PermissionRequest.RESOURCE_AUDIO_CAPTURE)) {
                                request.grant(new String[]{r});
                                return;
                            }
                        }
                        request.grant(requestedResources);
                    }
                });
            }

            @Override
            public boolean onJsAlert(WebView view, String url, String message, JsResult result) {
                // Xử lý alert từ JavaScript
                return super.onJsAlert(view, url, message, result);
            }

            @Override
            public boolean onConsoleMessage(ConsoleMessage consoleMessage) {
                // Log console để debug
                android.util.Log.d("WebView Console", consoleMessage.message());
                return true;
            }
        });

        // Inject JavaScript để override Notification API
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                injectNotificationAPI();
            }
        });
    }

    private void injectNotificationAPI() {
        String js = "(function() {" +
                "  if (typeof Notification === 'undefined') {" +
                "    window.Notification = function(title, options) {" +
                "      var body = options ? options.body || '' : '';" +
                "      AndroidNotification.showNotification(title, body);" +
                "    };" +
                "    window.Notification.permission = 'granted';" +
                "    window.Notification.requestPermission = function() {" +
                "      return Promise.resolve('granted');" +
                "    };" +
                "  } else {" +
                "    var OriginalNotification = window.Notification;" +
                "    window.Notification = function(title, options) {" +
                "      var body = options ? options.body || '' : '';" +
                "      AndroidNotification.showNotification(title, body);" +
                "      return new OriginalNotification(title, options);" +
                "    };" +
                "    window.Notification.permission = 'granted';" +
                "    window.Notification.requestPermission = function() {" +
                "      return Promise.resolve('granted');" +
                "    };" +
                "  }" +
                "})();";
        
        webView.evaluateJavascript(js, null);
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, 
                    Manifest.permission.POST_NOTIFICATIONS) 
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.POST_NOTIFICATIONS},
                    NOTIFICATION_PERMISSION_CODE);
            }
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "Web Notifications";
            String description = "Notifications from web content";
            int importance = NotificationManager.IMPORTANCE_DEFAULT;
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
            channel.setDescription(description);
            channel.enableVibration(true);
            channel.enableLights(true);
            
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }

    public class WebAppInterface {
        @JavascriptInterface
        public void showNotification(String title, String message) {
            runOnUiThread(() -> {
                NotificationCompat.Builder builder = new NotificationCompat.Builder(MainActivity.this, CHANNEL_ID)
                        .setSmallIcon(android.R.drawable.ic_dialog_info)
                        .setContentTitle(title)
                        .setContentText(message)
                        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                        .setAutoCancel(true)
                        .setVibrate(new long[]{0, 500, 200, 500});

                NotificationManagerCompat notificationManager = NotificationManagerCompat.from(MainActivity.this);
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    if (ActivityCompat.checkSelfPermission(MainActivity.this, 
                            Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                        notificationManager.notify((int) System.currentTimeMillis(), builder.build());
                    }
                } else {
                    notificationManager.notify((int) System.currentTimeMillis(), builder.build());
                }
            });
        }
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }
}