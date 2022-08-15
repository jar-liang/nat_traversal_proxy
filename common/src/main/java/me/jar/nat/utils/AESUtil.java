package me.jar.nat.utils;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.UnsupportedEncodingException;
import java.security.GeneralSecurityException;
import java.security.Security;

/**
 * @Description
 * @Date 2021/4/21-23:37
 */
public final class AESUtil {
    private static final Logger LOGGER = LoggerFactory.getLogger(AESUtil.class);

    /**
     * 密钥算法 AES
     */
    private static final String KEY_ALGORITHM = "AES";

    /**
     * 加解密算法/工作模式/填充方式
     * java支持PKCS5Padding、不支持PKCS7Padding
     * Bouncy Castle支持PKCS7Padding填充方式
     */
    private static final String CIPHER_ALGORITHM = "AES/CBC/PKCS7Padding";

    /**
     * 偏移量，CBC模式需要
     * 与其他语言平台使用的需一致才能通用加解密
     */
    private static final String IV = "0000000000000000";

    public static final String ENCODING = "UTF-8";

    static {
        // 是PKCS7Padding填充方式，则需要添加Bouncy Castle支持
        Security.addProvider(new BouncyCastleProvider());
    }

    private AESUtil() {
    }

    public static byte[] encrypt(byte[] sourceBytes, String password) throws GeneralSecurityException, UnsupportedEncodingException {
        checkPassword(password);
        byte[] passwordBytes = password.getBytes(ENCODING);
        Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM, "BC");
        IvParameterSpec iv = new IvParameterSpec(IV.getBytes(ENCODING));
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(passwordBytes, KEY_ALGORITHM), iv);
        return cipher.doFinal(sourceBytes);
    }

    public static byte[] decrypt(byte[] sourceBytes, String password) throws GeneralSecurityException, UnsupportedEncodingException {
        checkPassword(password);
        byte[] passwordBytes = password.getBytes(ENCODING);
        Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM, "BC");
        IvParameterSpec iv = new IvParameterSpec(IV.getBytes(ENCODING));
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(passwordBytes, KEY_ALGORITHM), iv);
        return cipher.doFinal(sourceBytes);
    }

    private static void checkPassword(String password) throws UnsupportedEncodingException {
        if (password == null || (password.length() != 16 && password.length() != 32)) {
            LOGGER.warn("===The pass code length needs to be 16 or 32.");
            throw new UnsupportedEncodingException("Wrong pass code length");
        }
    }
}
