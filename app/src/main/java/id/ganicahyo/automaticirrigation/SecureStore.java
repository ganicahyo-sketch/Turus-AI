package id.ganicahyo.automaticirrigation;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.nio.ByteBuffer;
import java.security.KeyStore;
import java.security.SecureRandom;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Encrypts broker passwords, command tokens and custom CA material with an Android Keystore AES key. */
final class SecureStore {
    private static final String PREFS = "irrigation_secure_values";
    private static final String ALIAS = "ganicahyo_automatic_irrigation_mqtt_key_v1";
    private final SharedPreferences prefs;
    private final Context context;

    SecureStore(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = this.context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    synchronized void put(String name, String value) throws Exception {
        if (value == null || value.isEmpty()) {
            prefs.edit().remove(name).apply();
            return;
        }
        SecretKey key = getOrCreateKey();
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key);
        byte[] iv = cipher.getIV();
        byte[] encrypted = cipher.doFinal(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        ByteBuffer buffer = ByteBuffer.allocate(4 + iv.length + encrypted.length);
        buffer.putInt(iv.length).put(iv).put(encrypted);
        prefs.edit().putString(name, Base64.encodeToString(buffer.array(), Base64.NO_WRAP)).apply();
    }

    synchronized String get(String name, String fallback) {
        String packed = prefs.getString(name, null);
        if (packed == null || packed.isEmpty()) return fallback;
        try {
            byte[] all = Base64.decode(packed, Base64.NO_WRAP);
            ByteBuffer buffer = ByteBuffer.wrap(all);
            int ivSize = buffer.getInt();
            if (ivSize < 12 || ivSize > 32 || all.length <= 4 + ivSize) return fallback;
            byte[] iv = new byte[ivSize];
            buffer.get(iv);
            byte[] encrypted = new byte[buffer.remaining()];
            buffer.get(encrypted);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), new GCMParameterSpec(128, iv));
            return new String(cipher.doFinal(encrypted), java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception ex) {
            return fallback;
        }
    }

    private SecretKey getOrCreateKey() throws Exception {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) throw new IllegalStateException("Android 6.0+ required");
        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);
        java.security.Key existing = keyStore.getKey(ALIAS, null);
        if (existing instanceof SecretKey) return (SecretKey) existing;
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build());
        return generator.generateKey();
    }
}
