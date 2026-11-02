package net.mando.personal;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import org.json.JSONObject;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Device authorization only, never a Windows password. AES-GCM key stays in Android Keystore. */
final class DeviceStore {
    private static final String ALIAS="mando-own-device-v1";
    private final SharedPreferences preferences;
    DeviceStore(Context context){preferences=context.getSharedPreferences("mando-own-device",Context.MODE_PRIVATE);}
    private SecretKey key()throws Exception {
        KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);
        if(store.containsAlias(ALIAS))return (SecretKey)store.getKey(ALIAS,null);
        KeyGenerator generator=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
            .setKeySize(256).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());
        return generator.generateKey();
    }
    JSONObject load()throws Exception {
        String encoded=preferences.getString("authorization",null);if(encoded==null)return null;
        byte[] bytes=Base64.decode(encoded,Base64.NO_WRAP);if(bytes.length<29)throw new Exception("Invalid saved authorization");
        byte[] iv=java.util.Arrays.copyOfRange(bytes,0,12),ciphertext=java.util.Arrays.copyOfRange(bytes,12,bytes.length);
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,iv));
        return new JSONObject(new String(cipher.doFinal(ciphertext),java.nio.charset.StandardCharsets.UTF_8));
    }
    boolean hasSaved(){try{return load()!=null;}catch(Exception unavailable){forget();return false;}}
    void save(JSONObject profile)throws Exception {
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key());
        byte[] iv=cipher.getIV(),encrypted=cipher.doFinal(profile.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        if(iv.length!=12)throw new Exception("Unsupported GCM nonce");
        byte[] bytes=new byte[iv.length+encrypted.length];System.arraycopy(iv,0,bytes,0,iv.length);System.arraycopy(encrypted,0,bytes,iv.length,encrypted.length);
        if(!preferences.edit().putString("authorization",Base64.encodeToString(bytes,Base64.NO_WRAP)).commit())throw new Exception("Cannot save device authorization");
    }
    void forget(){
        preferences.edit().remove("authorization").apply();
        try {KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);store.deleteEntry(ALIAS);}catch(Exception ignored){ }
    }
}
