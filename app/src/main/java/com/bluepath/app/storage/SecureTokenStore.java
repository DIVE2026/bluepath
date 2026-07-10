package com.bluepath.app.storage;

import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Stores the cloud access token encrypted with a non-exportable Android Keystore key. */
final class SecureTokenStore {
    private static final String KEY_ALIAS = "bluepath_cloud_session_v1";
    private static final String KEYSTORE = "AndroidKeyStore";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final String PREF_CIPHER = "accessTokenCipher";
    private static final String PREF_IV = "accessTokenIv";
    private final SharedPreferences prefs;

    SecureTokenStore(SharedPreferences prefs) {
        this.prefs = prefs;
    }

    void put(String token) {
        if (token == null || token.isEmpty()) {
            clear();
            return;
        }
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey());
            byte[] encrypted = cipher.doFinal(token.getBytes(StandardCharsets.UTF_8));
            prefs.edit()
                    .putString(PREF_CIPHER, Base64.encodeToString(encrypted, Base64.NO_WRAP))
                    .putString(PREF_IV, Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP))
                    .remove("accessToken")
                    .apply();
        } catch (Exception exception) {
            clear();
            throw new IllegalStateException("기기 보안 저장소에 로그인 정보를 저장하지 못했습니다.", exception);
        }
    }

    String get() {
        String cipherText = prefs.getString(PREF_CIPHER, "");
        String ivText = prefs.getString(PREF_IV, "");
        if (cipherText == null || cipherText.isEmpty() || ivText == null || ivText.isEmpty()) {
            return migrateLegacyToken();
        }
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(
                    Cipher.DECRYPT_MODE,
                    getOrCreateKey(),
                    new GCMParameterSpec(128, Base64.decode(ivText, Base64.NO_WRAP))
            );
            byte[] decrypted = cipher.doFinal(Base64.decode(cipherText, Base64.NO_WRAP));
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (Exception exception) {
            clear();
            return "";
        }
    }

    void clear() {
        prefs.edit().remove(PREF_CIPHER).remove(PREF_IV).remove("accessToken").apply();
    }

    private String migrateLegacyToken() {
        String legacy = prefs.getString("accessToken", "");
        if (legacy == null || legacy.isEmpty()) return "";
        put(legacy);
        return legacy;
    }

    private SecretKey getOrCreateKey() throws Exception {
        KeyStore keyStore = KeyStore.getInstance(KEYSTORE);
        keyStore.load(null);
        java.security.Key existing = keyStore.getKey(KEY_ALIAS, null);
        if (existing instanceof SecretKey) return (SecretKey) existing;

        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE);
        generator.init(new KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build());
        return generator.generateKey();
    }
}
