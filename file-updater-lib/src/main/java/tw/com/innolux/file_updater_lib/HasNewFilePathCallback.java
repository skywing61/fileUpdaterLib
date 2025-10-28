package tw.com.innolux.file_updater_lib;

public interface HasNewFilePathCallback {
    public void hasNewDevice();

    public void updateThumb();

    public void onCopyFiles(String progress);

    public void onCreateThumbnail(String progress);

    public void onStorageRemoved();
}
