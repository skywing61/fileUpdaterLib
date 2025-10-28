package tw.com.innolux.file_updater_lib;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.util.ArrayList;

public class MountBroadcast extends BroadcastReceiver {

    private Context context;
    private final String TAG = "MountBroadcast";
    private IntentFilter filter;
    private UpdateFile updateFile;
    private HasNewFilePathCallback filePathCallback;

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        Log.i(TAG, "Receiver: " + action);

        assert action != null;
        if (action.equals(Intent.ACTION_MEDIA_MOUNTED)) {
            updateExternalStorageState();
        } else if (action.equals(Intent.ACTION_MEDIA_BAD_REMOVAL)
                || action.equals(Intent.ACTION_MEDIA_UNMOUNTED)
                || action.equals(Intent.ACTION_MEDIA_REMOVED)
                || action.equals(Intent.ACTION_MEDIA_EJECT)) {

            updateExternalStorageState();
        }
    }

    public MountBroadcast(Context context) {
        this.context = context;
        this.filePathCallback = (HasNewFilePathCallback) context;

        filter = new IntentFilter();
        filter.addAction(Intent.ACTION_MEDIA_MOUNTED);
        filter.addAction(Intent.ACTION_MEDIA_UNMOUNTED);
        filter.addAction(Intent.ACTION_MEDIA_BAD_REMOVAL);
        filter.addAction(Intent.ACTION_MEDIA_REMOVED);
        filter.addAction(Intent.ACTION_MEDIA_EJECT);
        filter.addDataScheme("file");

        registerReceiver();
        updateExternalStorageState();
    }

    public void registerReceiver() {
        updateFile = new UpdateFile(context, filePathCallback);
        context.registerReceiver(this, filter);
    }

    public void unregisterReceiver() {
        if (updateFile != null)
            updateFile.stopTimer();

        updateFile = null;
        context.unregisterReceiver(this);
    }

    public String getThumbnailPath() {
        return updateFile.getThumbnailPath();
    }

    public ArrayList<File> getFileList() {
        return updateFile.getFileList();
    }

    public ArrayList<File> getInfoList() {
        return updateFile.getInfoList();
    }

    public ArrayList<File> getThumbList() {
        return updateFile.getThumbList();
    }

    public ArrayList<File> getThumbList_L() {
        return updateFile.getThumbList_L();
    }

    public ArrayList<File> getThumbList_P() {
        return updateFile.getThumbList_P();
    }

    private void updateExternalStorageState() {
        String state = Environment.getExternalStorageState();
        if (Environment.MEDIA_MOUNTED.equals(state)) {
            Log.d(TAG, "Update path");
            // SD card is unavailable or removed
            updateFile.stopTimer();
            if (filePathCallback != null) {
                filePathCallback.onStorageRemoved();
            }

            updateFile.updatePath();
        } else if (Environment.MEDIA_MOUNTED_READ_ONLY.equals(state)) {
            Log.i(TAG, "SD card mount, but read only");
            // SD card is mounted, but it is read only
        } else {
            Log.i(TAG, "SD card unavailable");
            // SD card is unavailable or removed
            updateFile.stopTimer();
            if (filePathCallback != null) {
                filePathCallback.onStorageRemoved();
            }
        }
    }
}
