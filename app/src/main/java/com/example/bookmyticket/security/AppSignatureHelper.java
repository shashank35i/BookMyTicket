package com.example.bookmyticket.security;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.util.Base64;
import android.util.Log;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;

public class AppSignatureHelper extends ContextWrapper {
    private static final String TAG = "AppSignatureHelper";
    private static final String HASH_TYPE = "SHA-256";
    private static final int NUM_HASHED_BYTES = 9;
    private static final int NUM_BASE64_CHAR = 11;

    public AppSignatureHelper(Context context) {
        super(context);
    }

    public ArrayList<String> getAppSignatures() {
        ArrayList<String> appSignatures = new ArrayList<>();
        try {
            String packageName = getPackageName();
            PackageManager pm = getPackageManager();
            Signature[] signatures = pm.getPackageInfo(packageName, PackageManager.GET_SIGNATURES).signatures;
            for (Signature signature : signatures) {
                String hash = hashPackage(packageName, signature);
                if (hash != null) {
                    appSignatures.add(hash);
                }
            }
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "Unable to find package to obtain hash.", e);
        }
        return appSignatures;
    }

    private String hashPackage(String packageName, Signature signature) {
        try {
            MessageDigest md = MessageDigest.getInstance(HASH_TYPE);
            md.update(signature.toByteArray());
            byte[] digest = md.digest();
            byte[] hashedBytes = Arrays.copyOfRange(digest, 0, NUM_HASHED_BYTES);
            String base64 = Base64.encodeToString(hashedBytes, Base64.NO_WRAP | Base64.NO_PADDING | Base64.URL_SAFE);
            return base64.substring(0, NUM_BASE64_CHAR);
        } catch (NoSuchAlgorithmException e) {
            Log.e(TAG, "NoSuchAlgorithmException", e);
            return null;
        }
    }
}
