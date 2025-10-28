package tw.com.innolux.file_updater_lib;

import android.util.Log;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStreamReader;

public class SuCommand {
    private static final String LOG_TAG = SuCommand.class.getSimpleName();

    /**
     * 以 root 權限執行 shell 命令並回傳
     *
     * @param commands 要執行的命令陣列
     * @return 包含 stdout 和 stderr 的命令執行結果字串
     */
    public static String suExecute(String[] commands) {
        Process shell = null;
        DataOutputStream out = null;
        BufferedReader in = null;
        BufferedReader err = null; // <-- 用於讀取標準錯誤 (stderr)
        StringBuilder sb = new StringBuilder(); // <-- 在頂層宣告，用於收集所有輸出

        try {
            // 獲取 root 權限
            Log.i(LOG_TAG, "Starting exec of su");
            shell = Runtime.getRuntime().exec("su");

            // 建立輸入/輸出流
            out = new DataOutputStream(shell.getOutputStream());
            in = new BufferedReader(new InputStreamReader(shell.getInputStream()));
            err = new BufferedReader(new InputStreamReader(shell.getErrorStream())); // <-- 初始化 stderr 讀取器

            // 執行命令
            Log.i(LOG_TAG, "Executing commands...");
            for (String command : commands) {
                Log.i(LOG_TAG, "Executing: " + command);
                out.writeBytes(command + "\n");
                out.flush();
            }

            out.writeBytes("exit\n"); // 結束 su shell
            out.flush();

            String line;

            // 讀取標準輸出 (stdout)
            while ((line = in.readLine()) != null) {
                sb.append(line).append("\n");
            }

            // 讀取標準錯誤 (stderr)
            while ((line = err.readLine()) != null) {
                Log.e(LOG_TAG, "suError: " + line);
                sb.append("Error: ").append(line).append("\n");
            }

            // 等待行程結束
            shell.waitFor();
            Log.i(LOG_TAG, "su command finished.");

        } catch (Exception e) {
            Log.d(LOG_TAG, "ShellRoot#suExecute() finished with error", e);
            // 將異常訊息也加入到回傳結果中
            sb.append("Exception: ").append(e.getMessage());
        } finally {
            try {
                // 關閉所有流
                if (out != null) {
                    out.close();
                }
                if (in != null) {
                    in.close();
                }
                if (err != null) { // <-- 關閉 stderr 讀取器
                    err.close();
                }
            } catch (Exception e) {
                // 無能為力
                Log.e(LOG_TAG, "Failed to close streams", e);
            }
        }

        // 回傳收集到的所有輸出
        return sb.toString();
    }
}