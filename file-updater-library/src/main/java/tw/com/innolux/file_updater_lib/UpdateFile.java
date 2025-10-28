package tw.com.innolux.file_updater_lib;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.ThumbnailUtils;
import android.provider.MediaStore;
import android.util.Log;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Timer;
import java.util.TimerTask;

/**
 * 更新並管理 USB 裝置上圖片與縮圖的檔案清單，
 * 將內容複製到 App 私有快取，以避免卸載時崩潰
 */
public class UpdateFile {
    private static final String TAG = "UpdateFile";
    private static final String FOLDER_NAME = "GraphicFile";
    private static final String THUMB_FOLDER_NAME = ".thumbnail";
    public static final String[] ALLOWED_IMG_FORMAT = {".jpg", ".JPG", ".png", ".PNG", ".bmp", ".BMP"};
    public static final String[] ALLOWED_VIDEO_FORMAT = {".mp4", ".MP4"};

    private final Context context;
    private final HasNewFilePathCallback hasNewFilePathCallback;

    private final SharedPreferences pref;

    private Timer timer;
    private String mountFilePath = "";       // 原始 USB 掛載路徑
    private File usbCacheFolder = null;        // 複製到 App 快取的 USB 資料夾
    private String thumbnailPath = "";
    private String mountDevice = "";
    private String pref_mountDevice = "emulated";

    private String[] oldIds = {};
    private boolean firstStart = true;
    private boolean isUsbMounted = false;

    private final ArrayList<File> fileList = new ArrayList<>();
    private final ArrayList<File> thumbList = new ArrayList<>();
    private final ArrayList<File> thumbList_L = new ArrayList<>();
    private final ArrayList<File> thumbList_P = new ArrayList<>();
    private final ArrayList<File> infoList = new ArrayList<>();

    public UpdateFile(Context context, HasNewFilePathCallback callback) {
        this.context = context.getApplicationContext();
        this.hasNewFilePathCallback = callback;

        // 取得SharedPreferences
        pref = context.getSharedPreferences("Gallery", Context.MODE_PRIVATE);
        pref_mountDevice = pref.getString("mountDevice", pref_mountDevice);
    }

    /**
     * 啟動更新流程：延遲 2 秒後檢測掛載變化
     */
    public void updatePath() {
        stopTimer();
        timer = new Timer();
        timer.schedule(new UpdateTask(), 2000);
    }

    /**
     * 停止任何延遲任務，並清空列表
     */
    public void stopTimer() {
        if (timer != null) {
            timer.cancel();
            timer = null;
        }
        fileList.clear();
        thumbList.clear();
        thumbList_L.clear();
        thumbList_P.clear();
        infoList.clear();
    }

    /**
     * 取得當前可用路徑：若 USB 掛載，使用 cache；否則使用原始掛載
     */
    public String getActivePath() {
        return (isUsbMounted && usbCacheFolder != null)
                ? usbCacheFolder.getAbsolutePath()
                : mountFilePath;
    }

    // Getter for lists and thumbnail path
    public ArrayList<File> getFileList() {
        return fileList;
    }

    public ArrayList<File> getThumbList() {
        return thumbList;
    }

    public ArrayList<File> getThumbList_L() {
        return thumbList_L;
    }

    public ArrayList<File> getThumbList_P() {
        return thumbList_P;
    }

    public ArrayList<File> getInfoList() {
        return infoList;
    }

    public String getThumbnailPath() {
        return thumbnailPath;
    }

    private int calcMountFileNum(File srcFolder) {
        Log.d(TAG, "srcFolder: " + srcFolder.getAbsolutePath());
        int fileNum = 0;
        File[] children = srcFolder.listFiles();

        if (children != null) {
            for (File child : children) {
                if (child.isFile()) {
                    fileNum++;
                }
            }
        }
        return fileNum;
    }

    /**
     * 計算實際掛載路徑，但不立即覆寫 mountFilePath
     */
    private String calcMountPath() throws IOException {
//        String[] ids = new SystemHelper()
//                .executeCommand("ls /storage").split("\n");

        // 使用 SuCommand 的新程式碼:
        String rawOutput = SuCommand.suExecute(new String[]{"ls /storage"});
        // 必須謹慎解析 SuCommand 的輸出，過濾掉空行和錯誤訊息
        ArrayList<String> idList = new ArrayList<>();
        if (!rawOutput.isEmpty()) {
            String[] lines = rawOutput.split("\n");
            for (String line : lines) {
                String trimmedLine = line.trim();

                // 過濾掉空行以及 SuCommand 可能回傳的錯誤/異常
                if (!trimmedLine.isEmpty() &&
                        !trimmedLine.startsWith("Error:") &&
                        !trimmedLine.startsWith("Exception:")) {

                    idList.add(trimmedLine);
                }
            }
        }

        // 將乾淨的列表轉換回 String[] 陣列
        String[] ids = idList.toArray(new String[0]);

        mountDevice = ids.length > 0 ? ids[0] : "emulated";
        if (!firstStart) {
            for (String id : ids) {
                if (!Arrays.asList(oldIds).contains(id)) {
                    mountDevice = id;
                    break;
                }
            }
        }
        oldIds = ids;

        Log.d(TAG, "device: " + mountDevice);

        if ("emulated".equals(mountDevice)) {
            isUsbMounted = false;
            return "/storage/" + mountDevice + "/0/" + FOLDER_NAME;
        } else {
            isUsbMounted = true;
            return "/storage/" + mountDevice + "/" + FOLDER_NAME;
        }
    }

    /**
     * 複製整個資料夾內容到 App cache
     */
    private File copyFolderToCache(File srcFolder) throws IOException {
        File dst = new File(context.getCacheDir(), srcFolder.getName());
        if (dst.exists()) {
            deleteFolderRecursive(dst);
        }
        dst.mkdirs();

        File[] children = srcFolder.listFiles();
        if (children != null) {
            int fileNum = children.length;
            int progress = 0;
            for (File child : children) {
                progress++;
                hasNewFilePathCallback.onCopyFiles(progress + "/" + fileNum);
                if (!child.isFile()) continue;
                File out = new File(dst, child.getName());
                try (FileInputStream in = new FileInputStream(child);
                     FileOutputStream outStream = new FileOutputStream(out)) {
                    byte[] buf = new byte[8192];
                    int len;
                    while ((len = in.read(buf)) > 0) {
                        outStream.write(buf, 0, len);
                    }
                } catch (IOException e) {
                    Log.e(TAG, "Failed to copy file", e);
                }
            }
        }
        Log.i(TAG, "Copied USB folder to cache finish: " + dst.getAbsolutePath());
        return dst;
    }

    /**
     * 遞迴刪除資料夾
     */
    private void deleteFolderRecursive(File folder) {
        if (folder == null || !folder.exists()) return;

        File[] files = folder.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isDirectory()) deleteFolderRecursive(f);
                else f.delete();
            }
        }
        folder.delete();
    }

    /**
     * 列出指定路徑的圖片與影片檔案
     */
    private void listFile(String path) {
        File dir = new File(path);
        if (!dir.exists()) {
            fileList.clear();
            return;
        }
        FilenameFilter filter = (f, name) -> {
            // 搜索圖片
            for (String ext : ALLOWED_IMG_FORMAT) if (name.endsWith(ext)) return true;
            // 搜索影片
            for (String ext : ALLOWED_VIDEO_FORMAT) if (name.endsWith(ext)) return true;
            return false;
        };
        File[] files = dir.listFiles(filter);
        fileList.clear();
        if (files != null) fileList.addAll(Arrays.asList(files));
        fileList.sort(Comparator.naturalOrder());
    }

    /**
     * 列出縮圖
     */
    private void listThumbnail() {
        String base = getActivePath();
        thumbnailPath = base + "/" + THUMB_FOLDER_NAME;
        File thumbsDir = new File(thumbnailPath);
        if (!thumbsDir.exists()) thumbsDir.mkdirs();
        File[] thumbs = thumbsDir.listFiles((d, n) -> n.endsWith(".png"));
        thumbList.clear();
        if (thumbs != null) thumbList.addAll(Arrays.asList(thumbs));
        thumbList.sort(Comparator.naturalOrder());
    }

    /**
     * 分離橫/直式縮圖
     */
    private void separateThumbOrientation() {
        thumbList_L.clear();
        thumbList_P.clear();
        for (File f : thumbList) {
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(f.getAbsolutePath(), opts);
            if (opts.outWidth >= opts.outHeight) thumbList_L.add(f);
            else thumbList_P.add(f);
        }
        thumbList_L.sort(Comparator.naturalOrder());
        thumbList_P.sort(Comparator.naturalOrder());
    }

    /**
     * 列出資訊檔案
     */
    private void listInfo() {
        File infoDir = new File(thumbnailPath);
        File[] infos = infoDir.listFiles((d, n) -> n.endsWith(".txt"));
        infoList.clear();
        if (infos != null) infoList.addAll(Arrays.asList(infos));
        infoList.sort(Comparator.naturalOrder());
    }

    /**
     * 建立縮圖
     */
    private void createThumbnail(ArrayList<File> files) {
        String base = getActivePath();
        thumbnailPath = base + "/" + THUMB_FOLDER_NAME;
        File thumbsDir = new File(thumbnailPath);
        if (!thumbsDir.exists()) thumbsDir.mkdirs();

        // 取得目前應該保留的縮圖檔名集合（不含副檔名）
        HashSet<String> validThumbNames = new HashSet<>();
        for (File f : files) {
            String nameNoExt = f.getName().substring(0, f.getName().lastIndexOf('.'));
            validThumbNames.add(nameNoExt.toLowerCase()); // 統一小寫處理
        }

        // 刪除不是對應 files 的縮圖檔案
        File[] existingThumbs = thumbsDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".png"));
        if (existingThumbs != null) {
            for (File thumb : existingThumbs) {
                String nameNoExt = thumb.getName().substring(0, thumb.getName().lastIndexOf('.')).toLowerCase();
                if (!validThumbNames.contains(nameNoExt)) {
                    thumb.delete();
                }
            }
        }

        // 建立縮圖
        try {
            int fileNum = files.size();
            int progress = 0;
            for (File f : files) {
                progress++;
                hasNewFilePathCallback.onCreateThumbnail(progress + "/" + fileNum);

                String nameNoExt = f.getName().substring(0, f.getName().lastIndexOf('.'));
                File out = new File(thumbsDir, nameNoExt + ".png");

                if (out.exists()) continue; // 如果縮圖檔名存在不重新建立

                String ext = f.getName().substring(f.getName().lastIndexOf('.')).toLowerCase();
                Log.d(TAG, "file name: " + f.getName());
                Bitmap bmp = null;
                if (Arrays.asList(ALLOWED_VIDEO_FORMAT).contains(ext)) {
                    bmp = ThumbnailUtils.createVideoThumbnail(f.getAbsolutePath(), MediaStore.Images.Thumbnails.MINI_KIND);
                } else if (Arrays.asList(ALLOWED_IMG_FORMAT).contains(ext)) {
                    BitmapFactory.Options opts = new BitmapFactory.Options();
                    opts.inSampleSize = 6;
                    bmp = BitmapFactory.decodeFile(f.getAbsolutePath(), opts);
                }
                if (bmp != null) {
                    try (BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(out))) {
                        bmp.compress(Bitmap.CompressFormat.PNG, 80, bos);
                    }
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "Failed to create thumbnail", e);
        }
    }

    /**
     * TimerTask：檢測掛載變化並執行更新
     */
    private class UpdateTask extends TimerTask {
        @SuppressLint("CommitPrefEdits")
        @Override
        public void run() {
            try {
                String newPath = calcMountPath();
                Log.d(TAG, "newPath: " + newPath);
                if (firstStart || !newPath.equals(mountFilePath)) {
                    firstStart = false;
                    mountFilePath = newPath;

                    hasNewFilePathCallback.hasNewDevice();

                    if (isUsbMounted) {
                        File newFiles = new File(newPath);
                        int fileNum = calcMountFileNum(newFiles);

                        // 取得SharedPreferences
                        pref_mountDevice = pref.getString("mountDevice", pref_mountDevice);

                        File candidate = new File(context.getCacheDir(), new File(newPath).getName());
                        int cacheFileNum = 0;
                        if (candidate.exists()) {
                            // 計算 cache folder 中的檔案數量
                            cacheFileNum = calcMountFileNum(candidate);
                            usbCacheFolder = candidate;
                        }

                        Log.d(TAG, "file num: " + fileNum);
                        Log.d(TAG, "cache fileNum: " + cacheFileNum);
                        Log.d(TAG, "mountDevice: " + mountDevice);
                        Log.d(TAG, "pref mountDevice: " + pref_mountDevice);

                        if (!mountDevice.equals(pref_mountDevice) || fileNum != cacheFileNum) {
                            pref.edit().putString("mountDevice", mountDevice).apply(); // 將USB/SD裝置存到app紀錄

                            if (usbCacheFolder != null) {
                                Log.d(TAG, "clear cache");
                                deleteFolderRecursive(usbCacheFolder);
                                usbCacheFolder = null;
                            }

                            usbCacheFolder = copyFolderToCache(newFiles);
                        }
                    }

                    // 不論有無拷貝，都要確保資料夾存在、掃一次列表
                    createFolder(getActivePath());
                    listFile(getActivePath());
                    createThumbnail(fileList);
                    listThumbnail();
                    separateThumbOrientation();
                    listInfo();
                    hasNewFilePathCallback.updateThumb();
                }
            } catch (IOException e) {
                Log.e(TAG, "Failed to update path", e);
            }
        }
    }

    /**
     * 建立資料夾
     */
    private void createFolder(String path) {
        File dir = new File(path);
        if (!dir.exists()) dir.mkdirs();
    }
}
