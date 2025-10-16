package com.example.downloadmanagerlab7;

import android.content.*;
import android.os.Build;
import android.widget.Toast;

public class NotificationReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        Toast.makeText(context, "Đã chọn: " + action, Toast.LENGTH_SHORT).show();

        Intent serviceIntent = new Intent(context, DownloadService.class);
        serviceIntent.setAction(action);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent);
        } else {
            context.startService(serviceIntent);
        }
    }
}
