package com.example.downloadmanagerlab7;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class DownloadService extends Service {

    private static final String CHANNEL_ID = "download_channel";
    private boolean isPaused = false, isCancelled = false;
    private int progress = 0;
    private final String fileName = "file_downloaded.bin";

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel(); // tạo channel trước khi startForeground
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent.getAction() != null) {
            handleAction(intent.getAction());
            return START_STICKY;
        }

        String url = intent.getStringExtra("url");
        if (url == null || url.isEmpty()) {
            stopSelf();
            return START_NOT_STICKY;
        }

        // ⚙️ Tạo thông báo đầu tiên
        Notification startNotification = buildNotification("Chuẩn bị tải...", 0);
        startForeground(1, startNotification);

        // ⚙️ Chạy thread tải file
        new Thread(() -> downloadFile(url)).start();

        return START_STICKY;
    }

    private void downloadFile(String fileUrl) {
        try {
            URL url = new URL(fileUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.connect();

            if (conn.getResponseCode() != HttpURLConnection.HTTP_OK) {
                updateNotification("Lỗi kết nối server!", progress);
                stopSelf();
                return;
            }

            int length = conn.getContentLength();
            InputStream in = new BufferedInputStream(conn.getInputStream());
            File outFile = new File(getFilesDir(), fileName);
            OutputStream out = new FileOutputStream(outFile);

            byte[] buffer = new byte[4096];
            int count;
            long total = 0;
            int lastProgress = 0;

            while ((count = in.read(buffer)) != -1) {
                if (isCancelled) break;
                if (isPaused) {
                    Thread.sleep(300);
                    continue;
                }

                total += count;
                out.write(buffer, 0, count);

                // Tính phần trăm tải
                int newProgress = (int) (total * 100 / length);

                // ⚙️ Chỉ update khi tiến trình thay đổi ≥ 1%
                if (newProgress - lastProgress >= 1) {
                    lastProgress = newProgress;
                    progress = newProgress;
                    updateNotification("Đang tải... (" + progress + "%)", progress);
                }
            }

            out.flush();
            out.close();
            in.close();

            if (!isCancelled) updateNotification("✅ Tải hoàn tất! (100%)", 100);
            stopSelf();

        } catch (Exception e) {
            e.printStackTrace();
            updateNotification("❌ Lỗi khi tải!", progress);
            stopSelf();
        }
    }

    // 🔔 Xây Notification có progress bar & text %
    private Notification buildNotification(String text, int progress) {
        Intent pauseIntent = new Intent(this, NotificationReceiver.class).setAction("PAUSE");
        Intent resumeIntent = new Intent(this, NotificationReceiver.class).setAction("RESUME");
        Intent cancelIntent = new Intent(this, NotificationReceiver.class).setAction("CANCEL");

        PendingIntent pPause = PendingIntent.getBroadcast(this, 0, pauseIntent, PendingIntent.FLAG_IMMUTABLE);
        PendingIntent pResume = PendingIntent.getBroadcast(this, 1, resumeIntent, PendingIntent.FLAG_IMMUTABLE);
        PendingIntent pCancel = PendingIntent.getBroadcast(this, 2, cancelIntent, PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Download Manager")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setProgress(100, progress, false) // ⚙️ thanh tiến trình
                .addAction(android.R.drawable.ic_media_pause, "Pause", pPause)
                .addAction(android.R.drawable.ic_media_play, "Resume", pResume)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancel", pCancel)
                .setOnlyAlertOnce(true) // không rung mỗi lần update
                .setOngoing(!text.contains("hoàn tất") && !text.contains("Lỗi") && !text.contains("hủy"))
                .build();
    }

    // 🔁 Cập nhật thông báo (hiển thị progress mới)
    private void updateNotification(String text, int progress) {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm != null) nm.notify(1, buildNotification(text, progress));
    }

    // 🔧 Tạo Notification Channel cho Android 8+
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Download Channel",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Hiển thị tiến trình tải file");
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }

    // ▶ Xử lý các nút Pause / Resume / Cancel
    private void handleAction(String action) {
        switch (action) {
            case "PAUSE":
                isPaused = true;
                updateNotification("⏸️ Tạm dừng...", progress);
                break;
            case "RESUME":
                isPaused = false;
                updateNotification("▶️ Tiếp tục tải...", progress);
                break;
            case "CANCEL":
                isCancelled = true;
                updateNotification("🛑 Đã hủy tải!", progress);
                stopSelf();
                break;
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
