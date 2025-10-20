package com.example.getcookies;

import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ArrayAdapter;
import android.widget.AdapterView;
import android.widget.Button;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

public class MainActivity extends AppCompatActivity {

    private WebView webView;
    private Button getCookiesButton;
    private Button clearCookiesButton;
    private Button refreshButton;
    private TextView cookiesTextView;
    private Spinner spinner;
    private Button addAccountButton;
    private Button deleteAccountButton;
    private ArrayList<WebView> webViews = new ArrayList<>();
    private ArrayList<String> accounts = new ArrayList<>();
    private ArrayAdapter<String> spinnerAdapter;
    private LinearLayout webviewContainer;

    private int accountCount = 0;  // Track the number of accounts added
    private static final String PREFS_NAME = "AppPrefs";
    private static final String KEY_ACCOUNTS = "accounts";
    private static final String KEY_WEBVIEW_URL = "webview_url";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().setStatusBarColor(getResources().getColor(R.color.statusBarColor));  // Set desired status bar color
        }

        // For Android Marshmallow (API 23) and above, set light status bar icons (dark icons on the status bar)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR); // Dark icons in the status bar
        }

        setContentView(R.layout.activity_main);

        // Initialize UI components
        webviewContainer = findViewById(R.id.webview_container);
        spinner = findViewById(R.id.spinner);
        getCookiesButton = findViewById(R.id.get_cookies_button);
        clearCookiesButton = findViewById(R.id.clear_cookies_button);
        refreshButton = findViewById(R.id.refresh_button);
        cookiesTextView = findViewById(R.id.cookiesTextView);
        addAccountButton = findViewById(R.id.add_account_button);
        deleteAccountButton = findViewById(R.id.delete_account_button);

        // Load accounts from SharedPreferences
        loadAccounts();

        // Set up Spinner Adapter
        if (spinnerAdapter == null) {
            spinnerAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, accounts);
            spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            spinner.setAdapter(spinnerAdapter);
        }



        // Create WebView for each account loaded from SharedPreferences
        createWebViewsForAccounts();


        // Register a listener for the back button press
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                // Handle back press in Fragment
                int position = spinner.getSelectedItemPosition();
                WebView currentWebView = webViews.get(position);

                // If the WebView has a history, go back to the previous page
                if (currentWebView != null && currentWebView.canGoBack()) {
                    currentWebView.goBack();
                } else {
                    // If the WebView doesn't have a history, perform the default back press behavior
//                    requireActivity().onBackPressed(); // Call back to Activity's onBackPressed
                }
            }
        });


        // Set up the Refresh button
        Button refreshButton = findViewById(R.id.refresh_button);
        refreshButton.setOnClickListener(v -> {
            // Refresh and redirect to the desired URL
            String redirectUrl = "https://weidian.com/weidian-h5/user/index.html";
            webView.loadUrl(redirectUrl);  // Redirect to the new URL
        });


        // Add Account Button click listener
        addAccountButton.setOnClickListener(view -> {
            // Prompt the user for a nickname for the new account
            final EditText input = new EditText(MainActivity.this);
            input.setHint("Enter account nickname");

            // Create an AlertDialog to ask for the nickname
            new AlertDialog.Builder(MainActivity.this)
                    .setTitle("Add Account")
                    .setMessage("Enter a nickname for the new account:")
                    .setView(input)
                    .setPositiveButton("OK", (dialog, which) -> {
                        String accountName = input.getText().toString().trim();
                        if (!accountName.isEmpty()) {
                            accounts.add(accountName);
                            spinnerAdapter.notifyDataSetChanged();
                            saveAccounts(); // Save accounts to SharedPreferences

                            // Create a new WebView for the new account
                            WebView newWebView = new WebView(MainActivity.this);
                            newWebView.setLayoutParams(new LinearLayout.LayoutParams(
                                    LinearLayout.LayoutParams.MATCH_PARENT,
                                    0, 1));  // Take up equal space in the container
                            newWebView.setWebViewClient(new WebViewClient());
                            newWebView.setWebChromeClient(new WebChromeClient());
                            newWebView.getSettings().setJavaScriptEnabled(true);
                            newWebView.getSettings().setCacheMode(WebSettings.LOAD_NO_CACHE);

                            // Load a default URL for the new account
                            newWebView.loadUrl("https://sso.weidian.com/login/index.php?redirect=https://weidian.com/weidian-h5/user/index.html");

                            // Hide all existing WebViews
                            for (WebView webView : webViews) {
                                webView.setVisibility(View.GONE);
                            }

                            // Add the new WebView to the container and display it
                            webviewContainer.addView(newWebView);
                            webViews.add(newWebView);  // Track this WebView

                            // Make the newly added WebView visible
                            newWebView.setVisibility(View.VISIBLE);

                            accountCount++;

                            // Ensure spinner always syncs with the latest account
                            spinnerAdapter.notifyDataSetChanged();
                            spinner.setSelection(accountCount - 1);  // Set the newly added account as selected
                            updateDeleteButtonState();
                        } else {
                            Toast.makeText(MainActivity.this, "Account nickname cannot be empty", Toast.LENGTH_SHORT).show();
                        }
                    })
                    .setNegativeButton("Cancel", null)
                    .show();

        });

        // Delete Account Button click listener
        deleteAccountButton.setOnClickListener(view -> {
            // Get the currently selected account
            int position = spinner.getSelectedItemPosition();

            if (position >= 0 && position < accountCount) {
                // Confirmation dialog for deleting the account
                new AlertDialog.Builder(MainActivity.this)
                        .setTitle("Confirm Delete Account")
                        .setMessage("Are you sure you want to delete the account: " + accounts.get(position) + "?")
                        .setPositiveButton("Yes", (dialog, which) -> {
                            // Remove the selected account from the list
                            accounts.remove(position);
                            saveAccounts(); // Save updated accounts list

                            // Remove the corresponding WebView
                            webviewContainer.removeViewAt(position);
                            webViews.remove(position);

                            // Remove the WebView's saved URL from SharedPreferences
                            removeWebViewState(position);

                            // Update Spinner and WebView
                            spinnerAdapter.notifyDataSetChanged();
                            accountCount--;

                            // Update spinner selection after account deletion
                            if (accountCount > 0) {
                                // If there are still accounts, select the previous one
                                spinner.setSelection(position - 1);
                            } else {
                                // If no accounts remain, disable the delete button
                                deleteAccountButton.setEnabled(false);
                            }

                            // Ensure delete button state is updated
                            updateDeleteButtonState();
                        })
                        .setNegativeButton("No", null)
                        .show();
            }
        });


        // Spinner Item Selected Listener to switch WebViews
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int position, long id) {
                // Hide all WebViews first
                for (WebView webView : webViews) {
                    webView.setVisibility(View.GONE);
                }

                // Show the selected WebView
                if (position >= 0 && position < webViews.size()) {
                    webViews.get(position).setVisibility(View.VISIBLE);
                }
                SharedPreferences sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
                SharedPreferences.Editor editor = sharedPreferences.edit();
                editor.putInt("selected_position", position);
                editor.apply();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parentView) {
                // Do nothing
            }
        });

        // Get Cookies Button click listener
        getCookiesButton.setOnClickListener(view -> {
            String cookies = CookieManager.getInstance().getCookie("https://sso.weidian.com");

            if (cookies != null) {
                // Log cookies to the console
                Log.d("Cookies", "Retrieved Cookies: " + cookies);
                // Copy cookies to clipboard
                copyToClipboard(cookies);
                // Display cookies in a Toast
                Toast.makeText(MainActivity.this, "Cookies copied to clipboard", Toast.LENGTH_SHORT).show();
                // Update the TextView to show the cookies
                cookiesTextView.setText(cookies);
            } else {
                // Log and show failure message
                Log.d("Cookies", "Failed to retrieve cookies.");
                Toast.makeText(MainActivity.this, "Failed to retrieve cookies", Toast.LENGTH_SHORT).show();
                cookiesTextView.setText("Failed to retrieve cookies.");
            }
        });

        // Clear Cookies Button click listener
        clearCookiesButton.setOnClickListener(view -> {
            // 弹出确认对话框，提示用户是否确定清除 Cookies
            new AlertDialog.Builder(MainActivity.this)
                    .setTitle("Clear Cookies")
                    .setMessage("Are you sure you want to clear all cookies?")
                    .setPositiveButton("Yes", (dialog, which) -> {
                        // 清除所有 cookies
                        CookieManager.getInstance().removeAllCookies(value -> {
                            if (value) {
                                // 如果成功清除 cookies，显示成功提示
                                Toast.makeText(MainActivity.this, "Cookies cleared successfully!", Toast.LENGTH_SHORT).show();
                            } else {
                                // 如果清除失败，显示失败提示
                                Toast.makeText(MainActivity.this, "Failed to clear cookies", Toast.LENGTH_SHORT).show();
                            }
                        });
                    })
                    .setNegativeButton("No", null)  // 如果用户点击“否”，不执行任何操作
                    .show();
        });

        // Refresh Button click listener
        refreshButton.setOnClickListener(view -> {
            // Reload the current URL for the selected WebView
            WebView selectedWebView = webViews.get(spinner.getSelectedItemPosition());
            selectedWebView.reload();
            Toast.makeText(MainActivity.this, "Page Refreshed", Toast.LENGTH_SHORT).show();
        });

        SharedPreferences sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        int selectedPosition = sharedPreferences.getInt("selected_position", 0); // 默认选中第一个
        spinner.setSelection(selectedPosition); // 设置 Spinner 的选中项
    }

    // Method to update delete button state
    private void updateDeleteButtonState() {
        if (accounts.size() > 0) {
            deleteAccountButton.setEnabled(true);
        } else {
            deleteAccountButton.setEnabled(false);
        }
    }

    private void saveAccounts() {
        if (accounts != null && !accounts.isEmpty()) {
            SharedPreferences sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putStringSet(KEY_ACCOUNTS, new HashSet<>(accounts)); // Save as a Set to avoid duplicates
            editor.apply();
        }
    }

    private void loadAccounts() {
        SharedPreferences sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        Set<String> accountSet = sharedPreferences.getStringSet(KEY_ACCOUNTS, new HashSet<>());

        // Debugging output
        Log.d("MainActivity", "Loaded accounts: " + accountSet);

        // Clear any previous data and reload
        accounts.clear();
        if (accountSet != null && !accountSet.isEmpty()) {
            accounts.addAll(accountSet);
        }

        // Initialize accountCount based on the size of accounts
        accountCount = accounts.size();

        // Update the delete button state when accounts are loaded
        updateDeleteButtonState();
    }

    private void saveWebViewState(int position, WebView webView) {
        SharedPreferences sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(KEY_WEBVIEW_URL + position, webView.getUrl()); // Save URL for this WebView
        editor.apply();
    }

    private String loadWebViewState(int position) {
        SharedPreferences sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return sharedPreferences.getString(KEY_WEBVIEW_URL + position, null); // Get URL for this WebView
    }

    // Remove WebView's saved URL from SharedPreferences
    private void removeWebViewState(int position) {
        SharedPreferences sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.remove(KEY_WEBVIEW_URL + position); // Remove saved URL for this WebView
        editor.apply();
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Save the accounts whenever the activity is paused (e.g., when the app goes to the background)
        saveAccounts();
        // Save the WebView states (URLs) when the activity is paused
        for (int i = 0; i < webViews.size(); i++) {
            saveWebViewState(i, webViews.get(i));
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        // Save the WebView states (URLs) for all webViews
        for (int i = 0; i < webViews.size(); i++) {
            saveWebViewState(i, webViews.get(i));
        }
    }

//    @Override
//    public void onBackPressed() {
//        // Get the currently selected WebView
//        int position = spinner.getSelectedItemPosition();
//        WebView currentWebView = webViews.get(position);
//
//        // If the WebView has a history, go back
//        if (currentWebView.canGoBack()) {
//            currentWebView.goBack();
//        } else {
//            // Otherwise, call the default onBackPressed behavior (exit the app)
//            super.onBackPressed();
//        }
//    }

    // Copy the cookies to clipboard
    private void copyToClipboard(String cookies) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            android.content.ClipData clip = android.content.ClipData.newPlainText("Cookies", cookies);
            clipboard.setPrimaryClip(clip);
        }
    }

    // Create WebView for each account loaded from SharedPreferences
    private void createWebViewsForAccounts() {
        // Clear existing WebViews first
        webviewContainer.removeAllViews();
        webViews.clear();

        // For each account, create a new WebView and load the saved URL
        for (int i = 0; i < accounts.size(); i++) {
            String account = accounts.get(i);

            WebView newWebView = new WebView(MainActivity.this);
            newWebView.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    0, 1));  // Take up equal space in the container
            newWebView.setWebViewClient(new WebViewClient());
            newWebView.setWebChromeClient(new WebChromeClient());
            newWebView.getSettings().setJavaScriptEnabled(true);
            newWebView.getSettings().setCacheMode(WebSettings.LOAD_NO_CACHE);

            // Load URL for the WebView, or a default one
            String savedUrl = loadWebViewState(i);
            if (savedUrl != null) {
                newWebView.loadUrl(savedUrl); // Load the saved URL
            } else {
                newWebView.loadUrl("https://sso.weidian.com/login/index.php?redirect=https://weidian.com/weidian-h5/user/index.html");
            }

            webviewContainer.addView(newWebView);
            webViews.add(newWebView);  // Track this WebView
        }
    }


}
