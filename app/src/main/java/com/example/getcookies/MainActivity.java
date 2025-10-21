package com.example.getcookies;

import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.Set;
public class MainActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "AppPrefs";
    private static final String KEY_ACCOUNTS = "accounts";
    private static final String KEY_SELECTED_ACCOUNT = "selected_account";
    private static final String KEY_WEBVIEW_URL_PREFIX = "webview_url_";
    private static final String DEFAULT_LOGIN_URL = "https://sso.weidian.com/login/index.php?redirect=https://weidian.com/weidian-h5/user/index.html";

    private View accountPanel;
    private View pagePanel;
    private FrameLayout webViewContainer;
    private ListView accountListView;
    private Button addAccountButton;
    private Button deleteAccountButton;
    private Button getCookiesButton;
    private Button clearCookiesButton;
    private Button refreshButton;
    private Button showAccountsButton;
    private Button showPageButton;
    private TextView cookiesTextView;

    private final ArrayList<String> accounts = new ArrayList<>();
    private final ArrayList<WebView> webViews = new ArrayList<>();
    private ArrayAdapter<String> accountAdapter;
    private int currentSelectedIndex = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().setStatusBarColor(getResources().getColor(R.color.statusBarColor));
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        }

        setContentView(R.layout.activity_main);

        accountPanel = findViewById(R.id.account_panel);
        pagePanel = findViewById(R.id.page_panel);
        webViewContainer = findViewById(R.id.webview_container);
        accountListView = findViewById(R.id.account_list);
        addAccountButton = findViewById(R.id.add_account_button);
        deleteAccountButton = findViewById(R.id.delete_account_button);
        getCookiesButton = findViewById(R.id.get_cookies_button);
        clearCookiesButton = findViewById(R.id.clear_cookies_button);
        refreshButton = findViewById(R.id.refresh_button);
        showAccountsButton = findViewById(R.id.show_accounts_button);
        showPageButton = findViewById(R.id.show_page_button);
        cookiesTextView = findViewById(R.id.cookiesTextView);

        loadAccounts();
        setupAccountList();
        createWebViewsForAccounts();
        setupButtons();
        setupPanelToggles();
        showAccountPanel();
        updateDeleteButtonState();

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                WebView currentWebView = getCurrentWebView();
                if (pagePanel.getVisibility() == View.VISIBLE && currentWebView != null && currentWebView.canGoBack()) {
                    currentWebView.goBack();
                } else if (pagePanel.getVisibility() == View.VISIBLE) {
                    showAccountPanel();
                } else {
                    finish();
                }
            }
        });
    }

    private void setupAccountList() {
        accountAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_activated_1, accounts);
        accountListView.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
        accountListView.setAdapter(accountAdapter);
        accountListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                showWebViewAt(position);
                showPagePanel();
            }
        });
    }

    private void setupButtons() {
        addAccountButton.setOnClickListener(view -> promptForNewAccount());

        deleteAccountButton.setOnClickListener(view -> {
            int position = currentSelectedIndex >= 0 ? currentSelectedIndex : accountListView.getCheckedItemPosition();
            if (position >= 0 && position < accounts.size()) {
                confirmDeleteAccount(position);
            }
        });

        getCookiesButton.setOnClickListener(view -> {
            String cookies = CookieManager.getInstance().getCookie("https://sso.weidian.com");
            if (cookies != null && !cookies.isEmpty()) {
                Log.d("Cookies", "Retrieved Cookies: " + cookies);
                copyToClipboard(cookies);
                cookiesTextView.setText(cookies);
                Toast.makeText(MainActivity.this, "Cookies copied to clipboard", Toast.LENGTH_SHORT).show();
            } else {
                Log.d("Cookies", "Failed to retrieve cookies.");
                cookiesTextView.setText("Failed to retrieve cookies.");
                Toast.makeText(MainActivity.this, "Failed to retrieve cookies", Toast.LENGTH_SHORT).show();
            }
        });

        clearCookiesButton.setOnClickListener(view -> new AlertDialog.Builder(MainActivity.this)
                .setTitle("Clear Cookies")
                .setMessage("Are you sure you want to clear all cookies?")
                .setPositiveButton("Yes", (dialog, which) -> CookieManager.getInstance().removeAllCookies(value -> {
                    if (value) {
                        Toast.makeText(MainActivity.this, "Cookies cleared successfully!", Toast.LENGTH_SHORT).show();
                        cookiesTextView.setText("");
                    } else {
                        Toast.makeText(MainActivity.this, "Failed to clear cookies", Toast.LENGTH_SHORT).show();
                    }
                }))
                .setNegativeButton("No", null)
                .show());

        refreshButton.setOnClickListener(view -> {
            WebView selectedWebView = getCurrentWebView();
            if (selectedWebView != null) {
                selectedWebView.reload();
                Toast.makeText(MainActivity.this, "Page Refreshed", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void setupPanelToggles() {
        showAccountsButton.setOnClickListener(v -> showAccountPanel());
        showPageButton.setOnClickListener(v -> showPagePanel());
    }

    private void showAccountPanel() {
        accountPanel.setVisibility(View.VISIBLE);
        pagePanel.setVisibility(View.GONE);
        showAccountsButton.setEnabled(false);
        showPageButton.setEnabled(true);
    }

    private void showPagePanel() {
        accountPanel.setVisibility(View.GONE);
        pagePanel.setVisibility(View.VISIBLE);
        showAccountsButton.setEnabled(true);
        showPageButton.setEnabled(false);
    }

    private void promptForNewAccount() {
        final EditText input = new EditText(MainActivity.this);
        input.setHint("Enter account nickname");

        new AlertDialog.Builder(MainActivity.this)
                .setTitle("Add Account")
                .setMessage("Enter a nickname for the new account:")
                .setView(input)
                .setPositiveButton("OK", (dialog, which) -> {
                    String accountName = input.getText().toString().trim();
                    if (accountName.isEmpty()) {
                        Toast.makeText(MainActivity.this, "Account nickname cannot be empty", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (accounts.contains(accountName)) {
                        Toast.makeText(MainActivity.this, "Account already exists", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    accounts.add(accountName);
                    accountAdapter.notifyDataSetChanged();
                    WebView newWebView = createConfiguredWebView();
                    newWebView.loadUrl(DEFAULT_LOGIN_URL);
                    newWebView.setVisibility(View.GONE);
                    webViewContainer.addView(newWebView);
                    webViews.add(newWebView);
                    saveAccounts();
                    showWebViewAt(accounts.size() - 1);
                    updateDeleteButtonState();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void confirmDeleteAccount(int position) {
        new AlertDialog.Builder(MainActivity.this)
                .setTitle("Confirm Delete Account")
                .setMessage("Are you sure you want to delete the account: " + accounts.get(position) + "?")
                .setPositiveButton("Yes", (dialog, which) -> deleteAccount(position))
                .setNegativeButton("No", null)
                .show();
    }

    private void deleteAccount(int position) {
        String accountName = accounts.remove(position);
        WebView removedWebView = webViews.remove(position);
        webViewContainer.removeView(removedWebView);
        removedWebView.destroy();
        removeWebViewState(accountName);
        saveAccounts();
        accountAdapter.notifyDataSetChanged();

        if (accounts.isEmpty()) {
            currentSelectedIndex = -1;
            accountListView.clearChoices();
            cookiesTextView.setText("");
            getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .remove(KEY_SELECTED_ACCOUNT)
                    .apply();
        } else {
            int newIndex = Math.min(position, accounts.size() - 1);
            showWebViewAt(newIndex);
        }
        updateDeleteButtonState();
    }

    private void createWebViewsForAccounts() {
        webViewContainer.removeAllViews();
        webViews.clear();

        SharedPreferences sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String selectedAccount = sharedPreferences.getString(KEY_SELECTED_ACCOUNT, null);
        int selectedIndex = -1;

        for (int i = 0; i < accounts.size(); i++) {
            String account = accounts.get(i);
            WebView newWebView = createConfiguredWebView();
            String savedUrl = loadWebViewState(account);
            if (savedUrl != null) {
                newWebView.loadUrl(savedUrl);
            } else {
                newWebView.loadUrl(DEFAULT_LOGIN_URL);
            }
            newWebView.setVisibility(View.GONE);
            webViewContainer.addView(newWebView);
            webViews.add(newWebView);

            if (selectedAccount != null && selectedAccount.equals(account)) {
                selectedIndex = i;
            }
        }

        if (!accounts.isEmpty()) {
            if (selectedIndex == -1) {
                selectedIndex = 0;
            }
            showWebViewAt(selectedIndex);
        }
    }

    private WebView createConfiguredWebView() {
        WebView webView = new WebView(MainActivity.this);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT);
        webView.setLayoutParams(params);
        webView.setWebViewClient(new WebViewClient());
        webView.setWebChromeClient(new WebChromeClient());
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setCacheMode(WebSettings.LOAD_NO_CACHE);
        return webView;
    }

    private void showWebViewAt(int position) {
        if (position < 0 || position >= webViews.size()) {
            currentSelectedIndex = -1;
            accountListView.clearChoices();
            accountAdapter.notifyDataSetChanged();
            return;
        }

        for (int i = 0; i < webViews.size(); i++) {
            webViews.get(i).setVisibility(i == position ? View.VISIBLE : View.GONE);
        }
        currentSelectedIndex = position;
        accountListView.setItemChecked(position, true);
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_SELECTED_ACCOUNT, accounts.get(position))
                .apply();
    }

    private WebView getCurrentWebView() {
        if (currentSelectedIndex >= 0 && currentSelectedIndex < webViews.size()) {
            return webViews.get(currentSelectedIndex);
        }
        return null;
    }

    private void updateDeleteButtonState() {
        deleteAccountButton.setEnabled(!accounts.isEmpty());
    }

    private void saveAccounts() {
        SharedPreferences sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        JSONArray jsonArray = new JSONArray();
        for (String account : accounts) {
            jsonArray.put(account);
        }
        if (accounts.isEmpty()) {
            editor.remove(KEY_ACCOUNTS);
        } else {
            editor.putString(KEY_ACCOUNTS, jsonArray.toString());
        }
        editor.apply();
    }

    private void loadAccounts() {
        SharedPreferences sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        accounts.clear();

        boolean loaded = false;
        try {
            String json = sharedPreferences.getString(KEY_ACCOUNTS, null);
            if (json != null) {
                loaded = parseAccountsFromJson(json);
            }
        } catch (ClassCastException e) {
            Log.w("MainActivity", "Detected legacy account storage format", e);
        }

        if (!loaded) {
            Set<String> legacyAccounts = sharedPreferences.getStringSet(KEY_ACCOUNTS, null);
            if (legacyAccounts != null) {
                for (String account : legacyAccounts) {
                    addAccountIfValid(account);
                }
                if (!accounts.isEmpty()) {
                    saveAccounts();
                }
            }
        }
    }

    private boolean parseAccountsFromJson(String json) {
        try {
            JSONArray jsonArray = new JSONArray(json);
            boolean addedAny = false;
            for (int i = 0; i < jsonArray.length(); i++) {
                String account = jsonArray.optString(i);
                if (addAccountIfValid(account)) {
                    addedAny = true;
                }
            }
            return addedAny;
        } catch (JSONException e) {
            Log.e("MainActivity", "Failed to parse accounts", e);
            return false;
        }
    }

    private boolean addAccountIfValid(String accountName) {
        if (accountName == null) {
            return false;
        }
        String trimmed = accountName.trim();
        if (trimmed.isEmpty() || accounts.contains(trimmed)) {
            return false;
        }
        accounts.add(trimmed);
        return true;
    }

    private void saveWebViewState(String accountName, WebView webView) {
        if (webView == null) {
            return;
        }
        SharedPreferences sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(KEY_WEBVIEW_URL_PREFIX + accountName, webView.getUrl());
        editor.apply();
    }

    private String loadWebViewState(String accountName) {
        SharedPreferences sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return sharedPreferences.getString(KEY_WEBVIEW_URL_PREFIX + accountName, null);
    }

    private void removeWebViewState(String accountName) {
        SharedPreferences sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.remove(KEY_WEBVIEW_URL_PREFIX + accountName);
        editor.apply();
    }

    @Override
    protected void onPause() {
        super.onPause();
        saveAccounts();
        for (int i = 0; i < accounts.size() && i < webViews.size(); i++) {
            saveWebViewState(accounts.get(i), webViews.get(i));
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        for (int i = 0; i < accounts.size() && i < webViews.size(); i++) {
            saveWebViewState(accounts.get(i), webViews.get(i));
        }
    }

    private void copyToClipboard(String cookies) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            android.content.ClipData clip = android.content.ClipData.newPlainText("Cookies", cookies);
            clipboard.setPrimaryClip(clip);
        }
    }
}
