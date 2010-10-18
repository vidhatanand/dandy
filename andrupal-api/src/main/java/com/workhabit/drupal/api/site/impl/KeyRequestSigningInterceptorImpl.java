package com.workhabit.drupal.api.site.impl;

import android.util.Log;
import com.workhabit.drupal.api.site.RequestSigningInterceptor;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.Charset;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Calendar;
import java.util.Map;

/**
 * Copyright 2009 - WorkHabit, Inc. - acs
 * Date: Oct 14, 2010, 12:07:13 PM
 */
public class KeyRequestSigningInterceptorImpl implements RequestSigningInterceptor {
    private String drupalDomain;
    private String privateKey;
    private Mac apiKeyMac;
    private final Charset asciiCs = Charset.forName("US-ASCII");

    public void setPrivateKey(String privateKey) {
        this.privateKey = privateKey;
    }

    public void setDrupalDomain(String drupalDomain) {
        this.drupalDomain = drupalDomain;
    }

    public void sign(String path, String method, Map<String, Object> data) throws Exception {
        if (apiKeyMac == null) {
            SecretKeySpec keySpec = new javax.crypto.spec.SecretKeySpec(asciiCs.encode(this.privateKey).array(), "HmacSHA256");
            try {
                apiKeyMac = Mac.getInstance("HmacSHA256");
                apiKeyMac.init(keySpec);
            } catch (NoSuchAlgorithmException e) {
                Log.e("crypto", e.getMessage(), e);
            } catch (InvalidKeyException e) {
                Log.e("crypto", e.getMessage(), e);
            }
        }
        Long timestamp = Calendar.getInstance().getTimeInMillis();
        MessageDigest digest = MessageDigest.getInstance("MD5");
        digest.update(Double.toString(System.currentTimeMillis() + Math.random()).getBytes());
        StringBuffer hexString = new StringBuffer();
        byte messageDigest[] = digest.digest();
        for (byte aMessageDigest : messageDigest) {
            hexString.append(Integer.toHexString(0xFF & aMessageDigest));
        }

        String nonce = hexString.toString();
        //();
        String hash = generateHmacHash(timestamp, drupalDomain, nonce, method);

        // add params for hash
        data.put("hash", hash);
        data.put("timestamp", Long.toString(timestamp));
        data.put("nonce", nonce);
    }

    private String generateHmacHash(Long timestamp, String serviceDomain, String nonce, String operation) throws Exception {
        String hashString = String.format("%s;%s;%s;%s", Long.toString(timestamp), serviceDomain, nonce, operation);
        byte[] hash = apiKeyMac.doFinal(asciiCs.encode(hashString).array());

        String result = "";
        for (byte aHash : hash) {
            // FYI: I don't understand why this is here..
            result += Integer.toString((aHash & 0xff) + 0x100, 16)
                    .substring(1);
        }
        return result;
    }

}