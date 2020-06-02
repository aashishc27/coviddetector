package utilities;


import android.os.Build;
import android.util.Base64;

import androidx.annotation.RequiresApi;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SignatureException;
import java.security.UnrecoverableEntryException;
import java.security.cert.CertificateException;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;

import models.EncryptedInfo;


/**
 * @author Niharika.Arora
 */
public class EncryptionUtil extends SecureUtil {

    private byte[] iv;

    private static final EncryptionUtil instance = new EncryptionUtil();

    private Key secretKey;

    public static EncryptionUtil getInstance() {
        return instance;
    }

    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    public String encryptText(final String textToEncrypt)
            throws UnrecoverableEntryException, NoSuchAlgorithmException, KeyStoreException,
            NoSuchPaddingException, InvalidKeyException, IOException,
            InvalidAlgorithmParameterException, SignatureException, BadPaddingException,
            IllegalBlockSizeException, NoSuchProviderException, CertificateException {
        final Cipher cipher = getCipher();

        iv = cipher.getIV();

        byte[] encryption = cipher.doFinal(textToEncrypt.getBytes(StandardCharsets.UTF_8));
        return Base64.encodeToString(encryption, Base64.DEFAULT);
    }

    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    public EncryptedInfo encrypt(final String textToEncrypt)
            throws UnrecoverableEntryException, NoSuchAlgorithmException, KeyStoreException,
            NoSuchPaddingException, InvalidKeyException, IOException,
            InvalidAlgorithmParameterException, SignatureException, BadPaddingException,
            IllegalBlockSizeException, NoSuchProviderException, CertificateException {
        final Cipher cipher = getCipher();

        iv = cipher.getIV();

        byte[] encryption = cipher.doFinal(textToEncrypt.getBytes(StandardCharsets.UTF_8));
        final String data = Base64.encodeToString(encryption, Base64.DEFAULT);
        final EncryptedInfo encryptedInfo = new EncryptedInfo();
        encryptedInfo.setData(data);
        encryptedInfo.setIv(iv);
        return encryptedInfo;
    }

    private Cipher getCipher() throws NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeyException, NoSuchProviderException, InvalidAlgorithmParameterException, KeyStoreException, CertificateException, IOException, UnrecoverableEntryException, BadPaddingException, IllegalBlockSizeException {
        Cipher cipher= null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, getSecretKey());
        } else {
           // generateKeysForAPILessThanM();
            cipher = initCipherForLessThanM(getKey(),true);
        }
        return cipher;
    }

    private Key getKey() throws IOException, CertificateException, NoSuchAlgorithmException, InvalidKeyException, UnrecoverableEntryException, NoSuchPaddingException, NoSuchProviderException, BadPaddingException, KeyStoreException, IllegalBlockSizeException, InvalidAlgorithmParameterException {
        if (secretKey == null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                 secretKey = getSecretKey();
            } else {
                 secretKey = getSecretKeyAPILessThanM();
            }
        }
        return secretKey;
    }

    public byte[] getIv() {
        return iv;
    }
}