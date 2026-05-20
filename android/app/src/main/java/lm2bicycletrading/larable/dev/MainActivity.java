package lm2bicycletrading.larable.dev;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.webkit.WebView;

import com.getcapacitor.BridgeActivity;
import com.onesignal.OneSignal;
import com.onesignal.Continue;
import com.onesignal.user.subscriptions.IPushSubscriptionObserver;
import com.onesignal.user.subscriptions.PushSubscriptionChangedState;
import com.onesignal.user.subscriptions.PushSubscriptionState;

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
}
