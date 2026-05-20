package lm2bicycletrading.larable.dev;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintManager;
import android.util.Log;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebView;

import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;

import com.getcapacitor.BridgeActivity;
import com.getcapacitor.BridgeWebChromeClient;
import com.onesignal.OneSignal;
import com.onesignal.Continue;
import com.onesignal.user.subscriptions.IPushSubscriptionObserver;
import com.onesignal.user.subscriptions.PushSubscriptionChangedState;
import com.onesignal.user.subscriptions.PushSubscriptionState;

import java.util.Collections;

public class MainActivity extends BridgeActivity {

    private static final String TAG = "MainActivity";
    private static final String ONESIGNAL_APP_ID = "3a65a187-1237-4d54-b760-2bfd94b50a6c";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private String lastInjectedPlayerId = null;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Handle back button for WebView navigation
        getOnBackPressedDispatcher().addCallback(this, new androidx.activity.OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                WebView webView = getBridge().getWebView();
                if (webView != null && webView.canGoBack()) {
                    webView.goBack();
                } else {
                    // Disable callback temporarily and trigger standard back action (exit)
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                    setEnabled(true);
                }
            }
        });

        // Initialize custom WebView printing and custom multi-photo file chooser
        WebView webView = getBridge().getWebView();
        if (webView != null) {
            // Register JavaScript Interface for Native Android Printing
            webView.addJavascriptInterface(new AndroidPrintInterface(), "AndroidPrint");
            Log.i(TAG, "Registered JavaScript Interface 'AndroidPrint'");

            // Enable document start script to override window.print
            try {
                if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
                    WebViewCompat.addDocumentStartJavaScript(
                        webView,
                        "window.print = function() { if (window.AndroidPrint) { window.AndroidPrint.print(); } };",
                        Collections.singleton("*")
                    );
                    Log.i(TAG, "Document start script for printing registered successfully.");
                } else {
                    Log.w(TAG, "DOCUMENT_START_SCRIPT is not supported on this device. Fallback to direct window.AndroidPrint.print() calls.");
                }
            } catch (Exception e) {
                Log.e(TAG, "Error registering document start script: " + e.getMessage());
            }

            // Set custom WebChromeClient that overrides file/photo choosing behavior to force multiple selection
            webView.setWebChromeClient(new BridgeWebChromeClient(getBridge()) {
                @Override
                public boolean onShowFileChooser(
                    WebView webView,
                    ValueCallback<Uri[]> filePathCallback,
                    FileChooserParams fileChooserParams
                ) {
                    Log.d(TAG, "onShowFileChooser called");
                    
                    boolean isImage = false;
                    String[] acceptTypes = fileChooserParams.getAcceptTypes();
                    if (acceptTypes != null) {
                        for (String type : acceptTypes) {
                            if (type != null && (type.contains("image/") || type.contains("img") || type.isEmpty())) {
                                isImage = true;
                                break;
                            }
                        }
                    }

                    if (isImage) {
                        Log.i(TAG, "Forcing multiple selection for image/photo selector");
                        FileChooserParams customParams = new FileChooserParams() {
                            @Override
                            public int getMode() {
                                return MODE_OPEN_MULTIPLE;
                            }

                            @Override
                            public String[] getAcceptTypes() {
                                return fileChooserParams.getAcceptTypes();
                            }

                            @Override
                            public boolean isCaptureEnabled() {
                                return fileChooserParams.isCaptureEnabled();
                            }

                            @Override
                            public CharSequence getTitle() {
                                return fileChooserParams.getTitle();
                            }

                            @Override
                            public String getFilenameHint() {
                                return fileChooserParams.getFilenameHint();
                            }

                            @Override
                            public Intent createIntent() {
                                Intent intent = fileChooserParams.createIntent();
                                intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                                return intent;
                            }
                        };
                        return super.onShowFileChooser(webView, filePathCallback, customParams);
                    }

                    return super.onShowFileChooser(webView, filePathCallback, fileChooserParams);
                }
            });
            Log.i(TAG, "Custom BridgeWebChromeClient configured for multi-photo selection.");
        }

        // Step 1: Initialize OneSignal SDK
        OneSignal.initWithContext(this, ONESIGNAL_APP_ID);
        Log.i(TAG, "OneSignal initialized with App ID: " + ONESIGNAL_APP_ID);

        // Step 2: Observe push subscription changes to capture the Player ID
        OneSignal.getUser().getPushSubscription().addObserver(new IPushSubscriptionObserver() {
            @Override
            public void onPushSubscriptionChange(PushSubscriptionChangedState state) {
                PushSubscriptionState current = state.getCurrent();
                String newId = current.getId();
                boolean isOptedIn = current.getOptedIn();
                Log.i(TAG, "Push subscription changed. ID: " + newId + ", OptedIn: " + isOptedIn);
                if (newId != null && !newId.isEmpty()) {
                    handler.postDelayed(() -> injectPlayerIdIntoWebView(newId), 2000);
                }
            }
        });

        // Step 3: Request permission with a small delay to let SDK initialize
        handler.postDelayed(() -> {
            Log.i(TAG, "Requesting notification permission...");
            OneSignal.getNotifications().requestPermission(true, Continue.with(result -> {
                Log.i(TAG, "Notification permission result: " + result);

                // Step 4: After permission granted, explicitly opt in to push
                boolean hasPermission = OneSignal.getNotifications().getPermission();
                Log.i(TAG, "Has notification permission: " + hasPermission);

                if (hasPermission) {
                    // Opt in to push notifications
                    OneSignal.getUser().getPushSubscription().optIn();
                    Log.i(TAG, "Opted in to push notifications");

                    // Try to get the player ID after opting in
                    handler.postDelayed(() -> injectCurrentPlayerId(), 2000);
                }
            }));
        }, 1500);
    }

    @Override
    public void onResume() {
        super.onResume();
        // Re-inject player ID on resume (handles returning from background)
        handler.postDelayed(this::injectCurrentPlayerId, 3000);
    }

    /**
     * Check for current player ID and inject it into the WebView.
     */
    private void injectCurrentPlayerId() {
        try {
            String playerId = OneSignal.getUser().getPushSubscription().getId();
            boolean isOptedIn = OneSignal.getUser().getPushSubscription().getOptedIn();
            Log.i(TAG, "Current Player ID: " + playerId + ", OptedIn: " + isOptedIn);

            if (playerId != null && !playerId.isEmpty()) {
                injectPlayerIdIntoWebView(playerId);
            } else {
                // Retry after a delay if not yet available
                Log.i(TAG, "Player ID not available yet, retrying...");
                handler.postDelayed(this::injectCurrentPlayerId, 5000);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting player ID: " + e.getMessage());
        }
    }

    /**
     * Inject the OneSignal Player ID into the WebView via JavaScript.
     * Sets a global variable and dispatches a custom event so the web app can pick it up.
     */
    private void injectPlayerIdIntoWebView(String playerId) {
        if (playerId.equals(lastInjectedPlayerId)) {
            return; // Already injected this ID
        }
        lastInjectedPlayerId = playerId;

        runOnUiThread(() -> {
            try {
                WebView webView = getBridge().getWebView();
                if (webView != null) {
                    // Escape the player ID for safe JS injection
                    String safeId = playerId.replace("'", "\\'");
                    String js =
                        "window.__onesignal_player_id = '" + safeId + "';" +
                        "window.__is_capacitor_wrapper = true;" +
                        "window.dispatchEvent(new CustomEvent('onesignal-ready', " +
                        "{ detail: { playerId: '" + safeId + "' } }));";
                    webView.evaluateJavascript(js, null);
                    Log.i(TAG, "Injected OneSignal Player ID into WebView: " + playerId);
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to inject player ID: " + e.getMessage());
            }
        });
    }

    /**
     * JavaScript Interface to bridge window.print() on the website to native Android PrintManager.
     */
    public class AndroidPrintInterface {
        @JavascriptInterface
        public void print() {
            Log.i(TAG, "AndroidPrintInterface.print() invoked from JS");
            runOnUiThread(() -> {
                try {
                    WebView webView = getBridge().getWebView();
                    if (webView != null) {
                        PrintManager printManager = (PrintManager) getSystemService(Context.PRINT_SERVICE);
                        if (printManager != null) {
                            String jobName = (webView.getTitle() != null ? webView.getTitle() : "LM2 Bicycle Trading Document");
                            PrintDocumentAdapter printAdapter = webView.createPrintDocumentAdapter(jobName);
                            printManager.print(jobName, printAdapter, new PrintAttributes.Builder().build());
                            Log.i(TAG, "Print job sent to system spooler.");
                        } else {
                            Log.e(TAG, "System PrintManager not available.");
                        }
                    } else {
                        Log.e(TAG, "WebView is null, cannot trigger print.");
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error executing native print job: " + e.getMessage(), e);
                }
            });
        }
    }
}
