package tw.com.innolux.fileupdaterlib;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.util.Log;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import tw.com.innolux.file_updater_lib.HasNewFilePathCallback;
import tw.com.innolux.file_updater_lib.MountBroadcast;

public class MainActivity extends AppCompatActivity implements HasNewFilePathCallback {

    private final String TAG = "MainActivity";

    private ActivityResultLauncher<Intent> settingsLauncher;

    private void checkAndRequestStoragePermission() {
        // Android 11 (API 30) 或更高
        if (!Environment.isExternalStorageManager()) {
            try {
                // 引導使用者到設定頁面
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                intent.setData(Uri.parse("package:" + getPackageName()));
                // 使用啟動器 (settingsLauncher) 來啟動 Intent
                settingsLauncher.launch(intent);
            } catch (Exception e) {
                Log.e(TAG, "Failed to open settings for MANAGE_EXTERNAL_STORAGE", e);
            }
        } else {
            Log.d(TAG, "權限已存在 (Permission already exists)");
            init();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        // 這必須在 Activity 創建時完成
        settingsLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    // 這裡是使用者從設定頁面返回時會執行的地方
                    Log.d(TAG, "從設定頁面返回 (Returned from settings)");

                    // 重新檢查權限
                    if (Environment.isExternalStorageManager()) {
                        Log.d(TAG, "權限已授予！(Permission Granted!)");
                        // 權限已授予，執行您需要的操作 (例如初始化 UpdateFile)
                        init();
                    } else {
                        Log.w(TAG, "權限仍被拒絕 (Permission Denied)，關閉APP");
                        finish();
                    }
                }
        );

        // 首次啟動時檢查權限
        checkAndRequestStoragePermission();
    }

    private MountBroadcast mountBroadcast;

    private void init() {
        mountBroadcast = new MountBroadcast(this);
    }

    @Override
    public void hasNewDevice() {

    }

    @Override
    public void updateThumb() {

    }

    @Override
    public void onCopyFiles(String progress) {

    }

    @Override
    public void onCreateThumbnail(String progress) {

    }

    @Override
    public void onStorageRemoved() {

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        mountBroadcast.unregisterReceiver();
    }
}