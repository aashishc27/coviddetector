package views;

import android.bluetooth.BluetoothAdapter;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentSender;
import android.graphics.Bitmap;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.ActionMode;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import com.example.coviddetector.BuildConfig;
import com.example.coviddetector.R;
import com.example.coviddetector.background_.CoronaApplication;
import com.example.coviddetector.background_.FirebaseRemoteConfigUtil;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.play.core.appupdate.AppUpdateInfo;
import com.google.android.play.core.appupdate.AppUpdateManager;
import com.google.android.play.core.appupdate.AppUpdateManagerFactory;
import com.google.android.play.core.install.InstallState;
import com.google.android.play.core.install.InstallStateUpdatedListener;
import com.google.android.play.core.install.model.AppUpdateType;
import com.google.android.play.core.install.model.InstallStatus;
import com.google.android.play.core.install.model.UpdateAvailability;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.Stack;

import db.FightCovidDB;
import db.dao.BluetoothDataDao;
import prefs.SharedPref;
import prefs.SharedPrefsConstants;
import utilities.Constants;
import utilities.CorUtility;
import utilities.CorUtilityKt;
import utilities.ExecutorHelper;
import utilities.Logger;
import utilities.UploadDataUtil;

import static utilities.LocalizationUtil.getLocalisedString;


/**
 * @author Chandrapal Yadav
 * @author Niharika.Arora
 */
public class HomeActivity extends AppCompatActivity implements NoNetworkDialog.Retry, NoBluetoothDialog.BluetoothActionListener,View.OnClickListener,
        InstallStateUpdatedListener, SyncDataDialog.SyncDataModeListener, SyncDataConsentDialog.ConfirmationListener, SyncDataStateDialog.SyncListener,UploadDataUtil.UploadListener {
    private static final String TAG = HomeActivity.class.getSimpleName();

    public static final String FRAG_NO_BT_DIALOG = "frag_no_bt_dialog";
    public static final String SYNC_DATA_DIALOG = "sync_data_dialog";
    public static final String SYNC_DATA_CONSENT_DIALOG = "sync_data_consent_dialog";
    public static final String SYNCING_DIALOG = "syncing_dialog";
    public static final String EXTRA_ASK_PERMISSION = "need_permissions";
    public static final String DO_NOT_SHOW_BACK = "do_not_show_back";

    public static final Integer NO_NETWORK = 1000;

    private static final int REQUEST_CODE_PERMISSION = 642;
    private static final int REQUEST_CODE_FLEXIBLE_UPDATE = 1734;
    private static final int REQUEST_CODE_IMMEDIATE_UPDATE = 1736;

    private static AppUpdateManager appUpdateManager;
    private ActionMode mActionMode;
   // private UploadDataUtil mUploadDataUtil;
    private final Stack<String> webPageStack = new Stack<>();
    private View menu, menuIntro, back;
    private boolean doNotShowBack;
    private View progressBar;
    private NoNetworkDialog networkDialog;
    private WebView webView;
    private HomeNavigationView homeNavigationView;
    private UploadDataUtil mUploadDataUtil;
    private FullScreenVideoWebChromeClient fullScreenVideoWebChromeClient;
   BluetoothDataDao bluetoothDataDao;



    private void disableScreenShot() {
        try {
            getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
        } catch (Exception e) {
            Logger.d(TAG, e.getLocalizedMessage());
        }
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            setContentView(R.layout.activity_home);
        } catch (Exception e) {
            //Android OS internal bug - When view is being inflated, the webview package is being updated by the os and therefore it can't find the webview package for those few moments.
            if (e.getMessage() != null && e.getMessage().contains("webview")) {
                Toast.makeText(getApplicationContext(), Constants.WEB_NOT_SUPPORTED, Toast.LENGTH_LONG).show();
            }
            mBluetoothStatusChangeReceiver = null;
            finish();
            return;
        }
        if (!BuildConfig.DEBUG) {
            disableScreenShot();
        }


        webView = findViewById(R.id.webView);

        back = findViewById(R.id.back);
        menu = findViewById(R.id.menu);

        View languageChange = findViewById(R.id.language_change);
        languageChange.setOnClickListener(v -> showLanguageSelectionDialog());

        back.setOnClickListener(v -> handleBack());

        IntentFilter filter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
        registerReceiver(mBluetoothStatusChangeReceiver, filter);
        handleShare();
        checkForUpdates();

        doNotShowBack = getIntent().getBooleanExtra(HomeActivity.DO_NOT_SHOW_BACK, false);

        setupNavigationMenu();

//        if (!CorUtility.isNetworkAvailable(HomeActivity.this)) {
//            showRetryDialog(getCurrentBaseURL());
//        } else {
//            loadUrl(getCurrentBaseURL());
//        }

        final boolean needPermissions = getIntent().getBooleanExtra(EXTRA_ASK_PERMISSION, false);
        if (needPermissions) {
            if (!CorUtility.arePermissionsGranted(this)) {
                CorUtility.requestPermissions(this, REQUEST_CODE_PERMISSION);
            } else {
                if (!CorUtility.isLocationOn(this)) {
                    CorUtility.enableLocation(this);
                }
            }
        }
       // AnalyticsUtils.sendEvent(EventNames.EVENT_OPEN_WEB_VIEW);
        CorUtility.startService(this);
        checkForDataUpload();

        if (!SharedPref.hasKey(this, SharedPrefsConstants.APPLICATION_INSTALL_TIME)) {
            SharedPref.setStringParams(this, SharedPrefsConstants.APPLICATION_INSTALL_TIME, String.valueOf(System.currentTimeMillis()));
        }

        checkOldData();
    }

    private void checkOldData() {
        ExecutorHelper.getThreadPoolExecutor().execute(CorUtility::remove30DaysOldData);
    }

    private void setupNavigationMenu() {
        final DrawerLayout drawerLayout = findViewById(R.id.drawer);

        // Hide and show hamburger menu and menu action
        menuIntro = findViewById(R.id.hamburger_menu_intro);
        int menuIntroCount = Integer.parseInt(SharedPref.getStringParams(this, SharedPrefsConstants.MENU_INTRO_COUNT, SharedPrefsConstants.DEFAULT_MENU_INTRO_COUNT)) + 1;
        if (menuIntroCount > Constants.MAX_INTRO_VIEWS) {
            menuIntro.setVisibility(View.GONE);
        } else {
            SharedPref.setStringParams(this, SharedPrefsConstants.MENU_INTRO_COUNT, "" + menuIntroCount);
            findViewById(R.id.close_menu_intro).setOnClickListener(v -> menuIntro.setVisibility(View.GONE));
        }

        // Setup navigation drawer UI
        homeNavigationView = findViewById(R.id.navigation_drawer);
        homeNavigationView.inflate(v -> {
            drawerLayout.closeDrawer(GravityCompat.START);
            HomeActivity.this.onClick(v);
        });
        menu.setOnClickListener(v -> drawerLayout.openDrawer(GravityCompat.START));

        //Hide hamburger intro when drawer opens
//        int lockMode = AuthUtility.INSTANCE.isSignedIn() ? DrawerLayout.LOCK_MODE_UNLOCKED : DrawerLayout.LOCK_MODE_LOCKED_CLOSED;
//        drawerLayout.setDrawerLockMode(lockMode);
//        drawerLayout.addDrawerListener(new DrawerLayout.SimpleDrawerListener() {
//            @Override
//            public void onDrawerOpened(View drawerView) {
//                super.onDrawerOpened(drawerView);
//                menuIntro.setVisibility(View.GONE);
//                SharedPref.setStringParams(HomeActivity.this, SharedPrefsConstants.MENU_INTRO_COUNT, "" + Constants.MAX_INTRO_VIEWS);
//            }
//        });
    }


    private void openDefaultBrowser(String url) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            startActivity(intent);
            if (webPageStack.empty()) {
                finish();
            }
        } catch (Exception e) {
            if (!isFinishing()) {
                Toast.makeText(HomeActivity.this,
                        Constants.Errors.SELECT_OTHER_APP, Toast.LENGTH_LONG).show();
            }
        }
    }

    private boolean updateToken() {
        try {
           // AuthUtility.updateToken();
        } catch (Exception ee) {
            return false;
        }
        return true;
    }


    /**
     * This method is used to share the application with other user.
     */
    private void handleShare() {
        View share = findViewById(R.id.share);
        share.setOnClickListener(v -> {
            String appUrl = CorUtility.getShareText(this);
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("text/plain");
            intent.putExtra(Intent.EXTRA_TEXT, appUrl);
            startActivity(Intent.createChooser(intent, ""));
           // AnalyticsUtils.sendBasicEvent(EventNames.EVENT_SHARE_CLICKED, ScreenNames.SCREEN_DASHBOARD);
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (!CorUtility.isLocationOn(this))
            enableLocation();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateNavigationDrawer();
        if (CorUtility.arePermissionsGranted(this)) {
            checkBluetooth();
        } else {
            showPermissionAlert();
        }
        if (CorUtility.isForceUpgradeRequired()) {
            appUpdateManager.getAppUpdateInfo().addOnSuccessListener(appUpdateInfo -> {
                if (appUpdateInfo.updateAvailability() == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS) {
                    if (appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)) {
                        requestHardUpdate(appUpdateInfo);
                    }
                }
            });
        }

    }

    private void updateNavigationDrawer() {
        if (homeNavigationView != null) {
            homeNavigationView.setDetail();
        }
    }

    /**
     * When activity is paused, make sure action mode is ended properly.
     * This check would feel better to have in onDestroy(), but that seems to be
     * too late down the life cycle and the crash keeps on occurring. The drawback
     * of this solution is that the action mode is finished when app is minimized etc.
     */
    @Override
    protected void onPause() {
        super.onPause();
        endActionMode();
        hideNoBluetoothDialog();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mBluetoothStatusChangeReceiver != null) {
            unregisterReceiver(mBluetoothStatusChangeReceiver);
        }
        try {
            if (appUpdateManager != null)
                appUpdateManager.unregisterListener(this);
        } catch (Exception ignored) {
            //do nothing
        }
    }

    @Override
    public void onClick(View v) {
//        switch (v.getId()) {
//            case R.id.qr:
//                openQrScreen();
//                break;
//            case R.id.verify_installed_app:
//                loadUrl(BuildConfig.VERIFY_APP_URL);
//                break;
//            case R.id.share_data:
//                onUploadDataClicked();
//                break;
//            case R.id.call:
//                loadUrl(BuildConfig.CALL_US_URL);
//                break;
//            case R.id.faq:
//                loadUrl(BuildConfig.FAQ_URL);
//                break;
//            case R.id.privacy_policy:
//                loadUrl(BuildConfig.PRIVACY_POLICY_URL);
//                break;
//            case R.id.terms:
//                loadUrl(BuildConfig.TNC_URL);
//                break;
//            default:
//                break;
//        }
    }

    private void openQrScreen() {
     //   Intent qrItent = new Intent(HomeActivity.this, QrActivity.class);
     //   startActivity(qrItent);
    }

    /**
     * Store action mode if it is started
     *
     * @param mode: Mode that needs to be stored. It is of type android.view.ActionMode
     */
    @Override
    public void onActionModeStarted(ActionMode mode) {
        super.onActionModeStarted(mode);
        mActionMode = mode;
    }

    @Override
    public void onActionModeFinished(ActionMode mode) {
        super.onActionModeFinished(mode);
        mActionMode = null;
    }

    /**
     * Makes sure action mode is ended
     */
    private void endActionMode() {
        if (mActionMode != null) {
            mActionMode.finish(); /* immediately calls {@link #onActionModeFinished(ActionMode)} */
        }
    }

    private void showRetryDialog(String retryUrl) {
        networkDialog = new NoNetworkDialog();
        networkDialog.setRetryUrl(retryUrl);
        showDialog(networkDialog, networkDialog.getTag());
    }

    private boolean isTopUrlSame(String urlS) {
        try {
            if (webPageStack.isEmpty()) {
                return false;
            }
            URL url = new URL(urlS);
            String currUrl = url.getProtocol() + Constants.DOUBLE_SLASH + url.getHost() + url.getPath();
            URL stackUrl = new URL(webPageStack.peek());
            String stack = stackUrl.getProtocol() + Constants.DOUBLE_SLASH + stackUrl.getHost() + stackUrl.getPath();
            return currUrl.equalsIgnoreCase(stack);
        } catch (MalformedURLException e) {
            //do nothing
        }
        return true;
    }

    private void handleBack() {
        if (this.fullScreenVideoWebChromeClient != null &&
                this.fullScreenVideoWebChromeClient.onBackPressed()) {
            return;
        }
        if (!webPageStack.isEmpty())
            webPageStack.pop();
        if (webPageStack.isEmpty()) {
            finish();
        } else {
            try {
                loadUrl(getChangedUrl(webPageStack.peek()));
            } catch (MalformedURLException e) {
                loadUrl(webPageStack.peek());
            }
        }
    }

    public static Map<String, String> getQueryMap(String query) {
        Map<String, String> map = new HashMap<>();
        if (TextUtils.isEmpty(query)) {
            return map;
        }
        String[] params = query.split("&");

        for (String param : params) {
            String value = "", name;
            String[] splitArray = param.split("=");
            if (splitArray.length > 0) {
                name = splitArray[0];
                if (splitArray.length > 1) {
                    value = splitArray[1];
                }
                map.put(name, value);
            }
        }
        return map;
    }

    private String getCurrentBaseURL() {
        String url = "";
        if (getIntent().getData() != null) {
            Uri uri = this.getIntent().getData();
            url += uri.getScheme() + "://" + uri.getHost() + uri.getPath();
        } else {
            url = getIntent().getStringExtra(Constants.URL);
        }

        url += "?locale=" + SharedPref.getStringParams(CoronaApplication.instance, SharedPrefsConstants.USER_SELECTED_LANGUAGE_CODE, "en");

        return url;
    }


    private void loadUrl(String url) {
//        if (BuildConfig.WEB_HOST.equals(Uri.parse(url).getHost())) {
//            Map<String, String> headers = new HashMap<>();
//            headers.put(Constants.AUTH, AuthUtility.getToken());
//            headers.put(Constants.VERSION, String.valueOf(BuildConfig.VERSION_CODE));
//            headers.put(Constants.PLATFORM, BuildConfig.PLATFORM_KEY);
//            headers.put(Constants.UNIQUE_ID, SharedPref.getStringParams(
//                    CoronaApplication.getInstance(),
//                    SharedPrefsConstants.UNIQUE_ID,
//                    ""
//            ));
//
//            webView.loadUrl(url, headers);
//        } else {
//            openDefaultBrowser(url);
//        }
    }

    public void showLanguageSelectionDialog() {
        SelectLanguageFragment.showDialog(getSupportFragmentManager(), true);
    }

    public static Intent getLaunchIntent(@NotNull String url, @NotNull String title, @NotNull Context context) {
        Intent intent = new Intent(context, HomeActivity.class);
        intent.putExtra(Constants.URL, url);
        intent.putExtra(Constants.TITLE, title);
        return intent;
    }

    /**
     * This method is used to change language of the pages with selected language
     *
     * @param urlToChange: This variable is the original URL that need to be changed
     * @return The URL with selected language
     * @throws MalformedURLException: This exception is received when format of original URL is incorrect
     *                                or there are some invalid data in the URl.
     */
    public String getChangedUrl(String urlToChange) throws MalformedURLException {
        URL url = new URL(urlToChange);
        Map<String, String> map = getQueryMap(url.getQuery());
        if (map.containsKey(Constants.LOCALE)) {
            map.put(Constants.LOCALE, SharedPref.getStringParams(CoronaApplication.instance, SharedPrefsConstants.USER_SELECTED_LANGUAGE_CODE, "en"));
            map.put(Constants.LANG, SharedPref.getStringParams(CoronaApplication.instance, SharedPrefsConstants.USER_SELECTED_LANGUAGE_CODE, "en"));
        }
        if (map.containsKey(Constants.LANG)) {
            map.put(Constants.LOCALE, SharedPref.getStringParams(CoronaApplication.instance, SharedPrefsConstants.USER_SELECTED_LANGUAGE_CODE, "en"));
            map.put(Constants.LANG, SharedPref.getStringParams(CoronaApplication.instance, SharedPrefsConstants.USER_SELECTED_LANGUAGE_CODE, "en"));
        }
        String fquery = "";
        if (map.keySet().size() > 0) {
            StringBuilder query = new StringBuilder("?");
            for (String key : map.keySet()) {
                query.append(key).append("=").append(map.get(key)).append("&");
            }
            fquery = query.toString().substring(0, query.toString().length() - 1);
        }
        return url.getProtocol() + Constants.DOUBLE_SLASH + url.getHost() + url.getPath() + fquery;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            if (keyCode == KeyEvent.KEYCODE_BACK) {
                handleBack();
                return true;
            }

        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public void retry(String retryUrl) {

        if (CorUtility.isNetworkAvailable(HomeActivity.this)) {
            if (!TextUtils.isEmpty(retryUrl))
                loadUrl(retryUrl);
        } else {
            showRetryDialog(retryUrl);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == NO_NETWORK) {
            if (networkDialog != null && networkDialog.isAdded() && !networkDialog.isDetached()) {
                if (CorUtility.isNetworkAvailable(HomeActivity.this)) {
                    networkDialog.dismiss();
                    if (!TextUtils.isEmpty(networkDialog.getRetryUrl()))
                        retry(networkDialog.getRetryUrl());
                }

            }
        }  else if (requestCode == REQUEST_CODE_IMMEDIATE_UPDATE) {
            finish();
        } else if (requestCode == REQUEST_CODE_FLEXIBLE_UPDATE) {
            //todo handle this accordingly for Immediate when user cancelled
            switch (resultCode) {
                case RESULT_CANCELED:

                case RESULT_OK:
                case com.google.android.play.core.install.model.ActivityResult.RESULT_IN_APP_UPDATE_FAILED:
            }
        }
    }

    private void checkBluetooth() {
        showNoBluetoothDialog();
    }

    private BroadcastReceiver mBluetoothStatusChangeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)) {
                final int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE,
                        BluetoothAdapter.ERROR);
                switch (state) {
                    case BluetoothAdapter.STATE_OFF:
                        showNoBluetoothDialog();
                        break;
                    case BluetoothAdapter.STATE_ON:
                        hideNoBluetoothDialog();
                        break;

                }
            }
        }
    };

    private void showNoBluetoothDialog() {
        if (!CorUtility.isBluetoothAvailable()) {
            showDialog(new NoBluetoothDialog(), FRAG_NO_BT_DIALOG);
        }
    }

    private void hideNoBluetoothDialog() {
        Fragment btDialog = getSupportFragmentManager().findFragmentByTag(FRAG_NO_BT_DIALOG);
        if (btDialog != null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .remove(btDialog)
                    .commitAllowingStateLoss();
        }
    }

    @Override
    public void onBluetoothRequested() {
        CorUtility.enableBluetooth();
        new Handler().postDelayed(() -> {
            if (!isFinishing() && !CorUtility.isBluetoothAvailable()) {
                Intent enableBT = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBT, 123);
            }
        }, 2000);
    }

    private void enableLocation() {
        CorUtility.enableLocation(this, aBoolean -> null);
    }


    private void notifyUserForFail() {
        Snackbar.make(webView, Constants.DOWNLOAD_FAIL, Snackbar.LENGTH_INDEFINITE).setAction(R.string.message_failed_tap_to_retry, v -> checkForUpdates()).show();
    }

    private void notifyUser() {
        Snackbar.make(webView, Constants.RESTART_TO_UPDATE, Snackbar.LENGTH_INDEFINITE).setAction(R.string.restart, v -> {
            if (appUpdateManager != null) {
                appUpdateManager.completeUpdate();
                appUpdateManager.unregisterListener(this);
                appUpdateManager = null;
            }
        }).show();
    }

    /**
     * This method is used to check for app update on the play store and start update if required.
     */
    private void checkForUpdates() {
        appUpdateManager = AppUpdateManagerFactory.create(this);
        appUpdateManager.registerListener(this);
        appUpdateManager.getAppUpdateInfo().addOnSuccessListener(appUpdateInfo -> {
            if (appUpdateInfo.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE) {
                if (CorUtility.isForceUpgradeRequired()) {
                    requestHardUpdate(appUpdateInfo);
                } else {
                    requestSoftUpdate(appUpdateInfo);
                }
            }
        });
    }

    private void requestSoftUpdate(AppUpdateInfo appUpdateInfo) {
        try {

            appUpdateManager.startUpdateFlowForResult(
                    appUpdateInfo,
                    AppUpdateType.FLEXIBLE, //  HERE specify the type of update flow you want
                    this,   //  the instance of an activity
                    REQUEST_CODE_FLEXIBLE_UPDATE);
        } catch (IntentSender.SendIntentException e) {
            //do nothing
        }
    }

    private void requestHardUpdate(AppUpdateInfo appUpdateInfo) {
        try {
            if (CorUtility.isForceUpgradeRequired()) {
                appUpdateManager.startUpdateFlowForResult(
                        appUpdateInfo,
                        AppUpdateType.IMMEDIATE, //  HERE specify the type of update flow you want
                        this,   //  the instance of an activity
                        REQUEST_CODE_IMMEDIATE_UPDATE
                );
            }
        } catch (IntentSender.SendIntentException e) {
            //do nothing
        }
    }

    @Override
    public void onStateUpdate(InstallState installState) {
        if (installState.installStatus() == InstallStatus.DOWNLOADED) {
            notifyUser();
        } else if (installState.installStatus() == InstallStatus.FAILED) {
            notifyUserForFail();
        }
    }

    private void showPermissionAlert() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(getLocalisedString(this, R.string.permission_alert_message)).setCancelable(false)
                .setPositiveButton(Constants.ACTION_GOTO_SETTINGS, (dialog, which) -> openAppSettings()).setNegativeButton(Constants.ACTION_REMIND_LATER, (dialog, which) -> dialog.cancel());
        AlertDialog alertDialog = builder.create();
        if (!isFinishing()) {
            alertDialog.show();
        }

    }

    private void openAppSettings() {
        Intent intent = new Intent();
        intent.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        Uri uri = Uri.fromParts(Constants.PACKAGE, getPackageName(), null);
        intent.setData(uri);
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException ignored) {
        }
    }

    private void showSyncDataDialog() {
        showDialog(new SyncDataDialog(), SYNC_DATA_DIALOG);
    }

    private void showSyncDataConsentDialog(String uploadType) {
        showDialog(SyncDataConsentDialog.newInstance(uploadType), SYNC_DATA_CONSENT_DIALOG);
    }

    private void showSyncingDataDialog(SyncDataStateDialog.State state, String uploadType) {
        showDialog(SyncDataStateDialog.newInstance(state, uploadType), SYNCING_DIALOG);
    }

    private void showDialog(DialogFragment dialog, String dialogType) {
        DialogFragment previousDialog = (DialogFragment) getSupportFragmentManager()
                .findFragmentByTag(dialogType);
        if (previousDialog != null) {
            previousDialog.dismissAllowingStateLoss();
        }
        dialog.setCancelable(false);
        FragmentTransaction fragmentTransaction = getSupportFragmentManager().beginTransaction();
        fragmentTransaction.add(dialog, dialogType);
        fragmentTransaction.commitAllowingStateLoss();

    }

    @Override
    public void syncDataWith(@NotNull String mode) {
        showSyncDataConsentDialog(mode);
    }

    @Override
    public void proceedSyncing(@NotNull String uploadType) {
        showSyncingDataDialog(SyncDataStateDialog.State.SYNCING, uploadType);
        startUploading(uploadType);
    }

    @Override
    public void cancelSyncing() {

    }

    @Override
    public void retrySyncing(@NotNull String uploadType) {
        showSyncingDataDialog(SyncDataStateDialog.State.SYNCING, uploadType);
        startUploading(uploadType);
    }

//    @Override
//    public void onUploadSuccess() {
//        showSyncingDataDialog(SyncDataStateDialog.State.SUCCESS, null);
//        mUploadDataUtil = null;
//    }
//
//    @Override
//    public void onUploadFailure(@NotNull String uploadType) {
//        showSyncingDataDialog(SyncDataStateDialog.State.FAILURE, uploadType);
//        mUploadDataUtil = null;
//    }

    private void checkForDataUpload() {

        String uploadType = "Sync";
        showSyncDataConsentDialog(uploadType);

//        if (!FirebaseRemoteConfigUtil.getINSTANCE().isUploadEnabled() || SharedPref.getBooleanParams(getBaseContext(), Constants.PUSH_COVID_POSTIVE_P) ) // Put status and condition here if possible
//        {
//            homeNavigationView.hideShareData();
//        }
//
//        if (getIntent() != null && getIntent().hasExtra(Constants.PUSH) && getIntent().hasExtra(Constants.UPLOAD_TYPE)) {
//            String uploadType = getIntent().getStringExtra(Constants.UPLOAD_TYPE);
//            showSyncDataConsentDialog(uploadType);
//        }
    }

    private void onUploadDataClicked() {
        if (FirebaseRemoteConfigUtil.getINSTANCE().disableSyncChoice()) {
            showSyncDataConsentDialog(Constants.UPLOAD_TYPES.SELF_CONSENT);
        } else {
            showSyncDataDialog();
        }

        //AnalyticsUtils.sendBasicEvent(EventNames.EVENT_OPEN_UPLOAD_CONSENT_SCREEN, EventNames.EVENT_OPEN_WEB_VIEW);
    }

    private void startUploading(String uploadType) {
        mUploadDataUtil = new UploadDataUtil(uploadType, this);
        mUploadDataUtil.startInBackground();
    }

    @Override
    public void onUploadSuccess() {
        showSyncingDataDialog(SyncDataStateDialog.State.SUCCESS, null);
        mUploadDataUtil = null;
    }

    @Override
    public void onUploadFailure(@NotNull String uploadType) {
        showSyncingDataDialog(SyncDataStateDialog.State.FAILURE, uploadType);
        mUploadDataUtil = null;
    }

    private class FullScreenVideoWebChromeClient extends WebChromeClient {
        private CustomViewCallback mCustomViewCallback;
        private int mOriginalOrientation;
        private int mOriginalSystemUiVisibility;
        private View mCustomView;

        @Override
        public void onShowCustomView(View paramView, CustomViewCallback callback) {
            if (this.mCustomView != null) {
                onHideCustomView();
                return;
            }
            this.mCustomView = paramView;
            this.mOriginalSystemUiVisibility = getWindow().getDecorView().getSystemUiVisibility();
            this.mOriginalOrientation = getRequestedOrientation();
            this.mCustomViewCallback = callback;
            ((FrameLayout) getWindow().getDecorView()).addView(this.mCustomView, new FrameLayout.LayoutParams(-1, -1));
            getWindow().getDecorView().setSystemUiVisibility(3846 | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);

        }

        @Override
        public void onHideCustomView() {
            ((FrameLayout) getWindow().getDecorView()).removeView(this.mCustomView);
            this.mCustomView = null;
            getWindow().getDecorView().setSystemUiVisibility(this.mOriginalSystemUiVisibility);
            setRequestedOrientation(this.mOriginalOrientation);
            this.mCustomViewCallback.onCustomViewHidden();
            this.mCustomViewCallback = null;
        }

        public boolean onBackPressed() {
            if (this.mCustomView != null) {
                onHideCustomView();
                return true;
            }
            return false;
        }
    }
}
