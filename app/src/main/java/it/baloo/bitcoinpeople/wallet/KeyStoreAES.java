package it.baloo.bitcoinpeople.wallet;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyPermanentlyInvalidatedException;
import android.security.keystore.KeyProperties;
import android.security.keystore.UserNotAuthenticatedException;
import android.util.Base64;

import com.greenaddress.greenapi.CryptoHelper;
import it.baloo.bitcoinpeople.wallet.ui.R;
import it.baloo.bitcoinpeople.wallet.ui.onboarding.PinSaveActivity;

import java.io.IOException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.security.spec.InvalidParameterSpecException;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.KeyGenerator;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;

public class KeyStoreAES {

    public static final String KEYSTORE_KEY = "NativeAndroidAuth";
    private static final int SECONDS_AUTH_VALID = 5;
    public static final int ACTIVITY_REQUEST_CODE = 1;

    private static String getKeyName(final String network, final boolean temporary) {
        return KEYSTORE_KEY + "_" + network + (temporary ? "temp" : "");
    }
    public static String getKeyName(final String network) {
        return getKeyName(network, false);
    }

    @TargetApi(Build.VERSION_CODES.M)
    public static void createKey(final boolean deleteImmediately, final String network) {

        KeyStore keyStore = null;
        try {
            keyStore = KeyStore.getInstance("AndroidKeyStore");
            keyStore.load(null);
            final KeyGenerator keyGenerator = KeyGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");

            keyGenerator.init(new KeyGenParameterSpec.Builder(getKeyName(network, deleteImmediately),
                    KeyProperties.PURPOSE_ENCRYPT
                            | KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_CBC)
                    .setUserAuthenticationRequired(true)
                    .setUserAuthenticationValidityDurationSeconds(SECONDS_AUTH_VALID)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_PKCS7)
                    .build());
            keyGenerator.generateKey();


        } catch (final NoSuchAlgorithmException | NoSuchProviderException
                | InvalidAlgorithmParameterException | KeyStoreException
                | CertificateException | IOException e) {
            throw new RuntimeException(e);
        } finally {
            if (deleteImmediately && keyStore != null) {
                try {
                    keyStore.deleteEntry(getKeyName(network, true));
                } catch (final KeyStoreException e) {
                }
            }
        }
    }

    public static class  RequiresAuthenticationScreen extends RuntimeException {}
    public static class  KeyInvalidated extends RuntimeException {}

    @TargetApi(Build.VERSION_CODES.M)
    public static String tryEncrypt(final String network, final SharedPreferences preferences) {
        createKey(false, network);
        final byte[] fakePin = CryptoHelper.randomBytes(32);
        try {
            final KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
            keyStore.load(null);
            final SecretKey secretKey = (SecretKey) keyStore.getKey(getKeyName(network), null);
            final Cipher cipher = Cipher.getInstance(
                    android.security.keystore.KeyProperties.KEY_ALGORITHM_AES + '/'
                            + android.security.keystore.KeyProperties.BLOCK_MODE_CBC + '/'
                            + android.security.keystore.KeyProperties.ENCRYPTION_PADDING_PKCS7);

            cipher.init(Cipher.ENCRYPT_MODE, secretKey);

            final byte[] encryptedPIN = cipher.doFinal(fakePin);
            final byte[] iv = cipher.getParameters().getParameterSpec(IvParameterSpec.class).getIV();
            setPINConfig(preferences, Base64.encodeToString(encryptedPIN, Base64.NO_WRAP),
                         Base64.encodeToString(iv, Base64.NO_WRAP));
            return Base64.encodeToString(fakePin, Base64.NO_WRAP).substring(0, 15);
        } catch (final UserNotAuthenticatedException e) {
            throw new RequiresAuthenticationScreen();
        } catch (final KeyPermanentlyInvalidatedException e) {
            throw new KeyInvalidated();
        } catch (final InvalidParameterSpecException | BadPaddingException | IllegalBlockSizeException | KeyStoreException |
                CertificateException | UnrecoverableKeyException | IOException
                | NoSuchPaddingException | NoSuchAlgorithmException | InvalidKeyException e) {
            throw new RuntimeException(e);
        }
    }

    private static void setPINConfig(final SharedPreferences preferences,
                                     final String encryptedPIN, final String iv) {
        preferences.edit()
                 .putString("native", encryptedPIN)
                 .putString("nativeiv", iv)
                 .apply();
    }

    public static void wipePIN(final SharedPreferences preferences, final String network) {
        try {
            final KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
            keyStore.load(null);
            keyStore.deleteEntry(getKeyName(network, false));
        } catch (final CertificateException | IOException | NoSuchAlgorithmException | KeyStoreException e) {
            e.printStackTrace();
        } finally {
            setPINConfig(preferences, "", "");
        }
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public static void showAuthenticationScreen(final Activity act, final String network) throws RuntimeException {
        final KeyguardManager keyguardManager = (KeyguardManager) act.getSystemService(Context.KEYGUARD_SERVICE);
        final boolean isSaveActivity = (act instanceof PinSaveActivity);
        final String authTitle = !isSaveActivity ? act.getString(R.string.id_blockstream_green) : "";
        final String authDesc = !isSaveActivity ? act.getString(R.string.id_log_in_into_your_s_wallet, network) : "";
        final Intent intent = keyguardManager.createConfirmDeviceCredentialIntent(authTitle, authDesc);
        if (intent == null)
            throw new RuntimeException();
        act.startActivityForResult(intent, ACTIVITY_REQUEST_CODE);
    }
}
