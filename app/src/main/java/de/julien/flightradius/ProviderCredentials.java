package de.julien.flightradius;

import android.content.Context;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

final class ProviderCredentials {
    private static final String ADSBX_KEY_FILE = "adsbx_api_key";

    private ProviderCredentials() { }

    static String adsbExchangeKey(Context context) {
        File file = new File(context.getNoBackupFilesDir(), ADSBX_KEY_FILE);
        if (!file.isFile()) return "";
        try (FileInputStream input = new FileInputStream(file)) {
            byte[] data = new byte[(int) Math.min(file.length(), 4096L)];
            int length = input.read(data);
            return length <= 0 ? "" : new String(data, 0, length, StandardCharsets.UTF_8).trim();
        } catch (Exception ignored) {
            return "";
        }
    }

    static boolean hasAdsbExchangeKey(Context context) {
        return !adsbExchangeKey(context).isEmpty();
    }

    static void setAdsbExchangeKey(Context context, String value) throws Exception {
        File file = new File(context.getNoBackupFilesDir(), ADSBX_KEY_FILE);
        String key = value == null ? "" : value.trim();
        if (key.isEmpty()) {
            if (file.exists() && !file.delete()) {
                throw new IllegalStateException("Could not remove API key");
            }
            return;
        }
        try (FileOutputStream output = new FileOutputStream(file, false)) {
            output.write(key.getBytes(StandardCharsets.UTF_8));
        }
    }
}
