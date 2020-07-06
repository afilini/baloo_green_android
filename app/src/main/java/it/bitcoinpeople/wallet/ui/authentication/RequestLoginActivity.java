package it.bitcoinpeople.wallet.ui.authentication;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.afollestad.materialdialogs.MaterialDialog;
import com.btchip.BTChipConstants;
import com.btchip.BTChipDongle;
import com.btchip.BTChipDongle.BTChipFirmware;
import com.btchip.BTChipException;
import com.btchip.comm.BTChipTransport;
import com.btchip.comm.android.BTChipTransportAndroid;
import com.google.common.util.concurrent.SettableFuture;
import com.greenaddress.gdk.GDKSession;
import com.greenaddress.greenapi.HWWallet;
import com.greenaddress.greenapi.data.HWDeviceData;
import com.greenaddress.greenapi.data.NetworkData;
import it.bitcoinpeople.wallet.AuthenticationHandler;
import it.bitcoinpeople.wallet.ui.BuildConfig;
import it.bitcoinpeople.wallet.ui.LoginActivity;
import it.bitcoinpeople.wallet.ui.R;
import it.bitcoinpeople.wallet.ui.UI;
import it.bitcoinpeople.wallet.wallets.BTChipHWWallet;
import it.bitcoinpeople.wallet.wallets.HardwareCodeResolver;
import it.bitcoinpeople.wallet.wallets.TrezorHWWallet;
import com.satoshilabs.trezor.Trezor;

import java.util.List;

import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

import static com.greenaddress.greenapi.Session.getSession;

public class RequestLoginActivity extends LoginActivity {

    private static final String TAG = RequestLoginActivity.class.getSimpleName();

    private static final int VENDOR_BTCHIP    = 0x2581;
    private static final int VENDOR_LEDGER    = 0x2c97;
    private static final int VENDOR_TREZOR    = 0x534c;
    private static final int VENDOR_TREZOR_V2 = 0x1209;

    private UsbManager mUsbManager;
    private UsbDevice mUsb;

    private TextView mInstructionsText;
    private Dialog mPinDialog;
    private String mPin;
    private Integer mVendorId;
    private Boolean mInLedgerDashboard;

    private HWWallet mHwWallet;
    private TextView mActiveNetwork;
    private NetworkData networkData;
    private Disposable loginDisposable;

    @Override
    protected int getMainViewId() { return R.layout.activity_first_login_requested; }

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mInLedgerDashboard = false;
        mUsbManager = (UsbManager) getSystemService(Context.USB_SERVICE);

        mInstructionsText = UI.find(this, R.id.first_login_instructions);
        mActiveNetwork = UI.find(this, R.id.activeNetwork);
        networkData = getNetwork();
    }

    @Override
    protected void onNewIntent(final Intent intent) {
        Log.d(TAG, "onNewIntent");
        setIntent(intent);
    }

    private void onUsbAttach(final UsbDevice usb) {
        Log.d(TAG, "onUsbAttach");
        if (mUsb != null && mUsb == usb) {
            Log.d(TAG, "onUsbAttach with existing USB");
            return;
        }
        mUsb = usb;
        mInLedgerDashboard = false;
        if (usb == null)
            return;

        final ImageView hardwareIcon = UI.find(this, R.id.hardwareIcon);
        mVendorId = usb.getVendorId();
        Log.d(TAG, "Vendor: " + mVendorId + " Product: " + usb.getProductId());

        switch (mVendorId) {
        case VENDOR_TREZOR:
        case VENDOR_TREZOR_V2:
            hardwareIcon.setImageResource(R.drawable.ic_trezor);
            onTrezor();
            return;
        case VENDOR_BTCHIP:
        case VENDOR_LEDGER:
            hardwareIcon.setImageResource(R.drawable.ic_ledger);
            if (BTChipTransportAndroid.isLedgerWithScreen(usb)) {
                // User entered PIN on-device
                onLedger(true);
            } else {
                // Prompt for PIN to unlock device before setting it up
                showPinDialog();
            }
        }
    }

    private void onTrezor() {
        final Trezor t;
        t = Trezor.getDevice(this);

        if (networkData.getLiquid()) {
            showInstructions(R.string.id_hardware_wallet_support_for);
            return;
        }
        if (t == null)
            return;

        final List<Integer> version = t.getFirmwareVersion();
        final int vendorId = t.getVendorId();
        Log.d(TAG,"Trezor Version: " + version + " vendorid:" + vendorId + " productid:" + t.getProductId());

        final boolean isFirmwareOutdated = version.get(0) < 1 ||
                                           (version.get(0) == 1 && version.get(1) < 6) ||
                                           (version.get(0) == 1 && version.get(1) == 6 && version.get(2) < 0) ||
                                           (version.get(0) == 2 && version.get(1) < 1);

        if (!isFirmwareOutdated) {
            onTrezorConnected(t);
            return;
        }

        showFirmwareOutdated(R.string.id_outdated_hardware_wallet,
                             () -> onTrezorConnected(t));
    }

    private void onTrezorConnected(final Trezor t) {

        Log.d(TAG, "Creating Trezor HW wallet");
        final HWDeviceData hwDeviceData = new HWDeviceData("Trezor", false, false,
                                                           HWDeviceData.HWDeviceDataLiquidSupport.None);
        mHwWallet = new TrezorHWWallet(t, networkData, hwDeviceData);

        doLogin(this);
    }

    private void showPinDialog() {
        mPinDialog = UI.dismiss(this, mPinDialog);

        final View v = UI.inflateDialog(this, R.layout.dialog_btchip_pin);

        mPinDialog = UI.popup(this, R.string.id_pin)
                     .customView(v, true)
                     .backgroundColor(getResources().getColor(R.color.buttonJungleGreen))
                     .onPositive((dialog, which) -> {
            mPin = UI.getText(v, R.id.btchipPINValue);
            Observable.just(getSession())
            .observeOn(Schedulers.computation())
            .subscribe((session) -> {
                onLedger(false);
            });
        })
                     .onNegative((dialog, which) -> {
            UI.toast(this, R.string.id_no_pin_provided_exiting, Toast.LENGTH_LONG);
            finish();
        }).build();

        UI.mapEnterToPositive(mPinDialog, R.id.btchipPINValue);
        UI.showDialog(mPinDialog);
    }

    private void onLedger(final boolean hasScreen) {
        showInstructions(R.string.id_logging_in);
        final String pin = mPin;
        mPin = null;

        BTChipTransport transport = null;
        if (mUsb != null) {
            try {
                transport = BTChipTransportAndroid.open(mUsbManager, mUsb);
            } catch (final Exception e) {
                e.printStackTrace();
            }
            if (transport == null) {
                showInstructions(R.string.id_please_reconnect_your_hardware);
                return;
            }
        }

        transport.setDebug(BuildConfig.DEBUG);
        try {
            final BTChipDongle dongle = new BTChipDongle(transport, hasScreen);
            final BTChipFirmware fw = dongle.getFirmwareVersion();
            try {
                // This should only be supported by the Nano X
                final BTChipDongle.BTChipApplication application = dongle.getApplication();

                final boolean netMainnet = networkData.getMainnet();
                final boolean netLiquid = networkData.getLiquid();
                final boolean hwMainnet = !application.getName().contains("Test");
                final boolean hwLiquid = application.getName().contains("Liquid");

                Log.d(TAG, "Ledger application:" + application.getName() + " network is mainnet:"+ netMainnet);

                if (netMainnet != hwMainnet || netLiquid != hwLiquid) {
                    // We using the wrong app, prompt the user to open the right app.
                    mUsb = null;
                    mInLedgerDashboard = true;
                    showInstructions(R.string.id_the_network_selected_on_the);
                    return;
                }
            } catch (BTChipException ignored) { }

            final int major = fw.getMajor(), minor = fw.getMinor(), patch = fw.getPatch();

            Log.d(TAG, "BTChip/Ledger firmware version " + fw.toString() + '(' +
                  major + '.' + minor + '.' + patch + ')');

            boolean isFirmwareOutdated = false;
            if (mVendorId == VENDOR_BTCHIP) {
                isFirmwareOutdated = major < 0x2001 ||
                                     (major == 0x2001 && minor < 0) || // Just for consistency in checking code
                                     (major == 0x2001 && minor == 0 && patch < 4);
            } else if (mVendorId == VENDOR_LEDGER) {
                isFirmwareOutdated = major < 0x3001 ||
                                     (major == 0x3001 && minor < 3) ||
                                     (major == 0x3001 && minor == 3 && patch < 7);
            }

            if (!isFirmwareOutdated) {
                onLedgerConnected(dongle, pin);
                return;
            }

            showFirmwareOutdated(R.string.id_outdated_hardware_wallet,
                                 () -> onLedgerConnected(dongle, pin));
        } catch (final BTChipException e) {
            if (e.getSW() != BTChipConstants.SW_INS_NOT_SUPPORTED)
                e.printStackTrace();
            if (e.getSW() == 0x6faa) {
                showInstructions(R.string.id_please_disconnect_your_ledger);
                return;
            }
            try {
                transport.close();
            } catch (final BTChipException bte) {}
            // We are in dashboard mode, prompt the user to open the btcoin app.
            mUsb = null;
            mInLedgerDashboard = true;
            showInstructions(R.string.id_ledger_dashboard_detected);
        }
    }

    private void onLedgerConnected(final BTChipDongle dongle, final String pin) {
        final SettableFuture<Integer> pinCB = SettableFuture.create();

        final boolean havePin = !TextUtils.isEmpty(pin);
        Log.d(TAG, "Creating Ledger HW wallet" + (havePin ? " with PIN" : ""));
        final HWDeviceData hwDeviceData = new HWDeviceData("Ledger", false, true,
                                                           HWDeviceData.HWDeviceDataLiquidSupport.Lite);
        mHwWallet = new BTChipHWWallet(dongle, havePin ? pin : null, pinCB, networkData, hwDeviceData);

        doLogin(this);
    }

    private void doLogin(final Activity parent) {
        loginDisposable = Observable.just(getSession())
                          .observeOn(Schedulers.computation())
                          .map((session) -> {
            session.disconnect();
            connect();
            session.registerUser(mHwWallet.getHWDeviceData(), "").resolve(null,
                                                                          new HardwareCodeResolver(this, mHwWallet));
            session.login(mHwWallet.getHWDeviceData(), "", "").resolve(null,
                                                                       new HardwareCodeResolver(this, mHwWallet));
            session.setHWWallet(mHwWallet);
            return session;
        })
                          .observeOn(AndroidSchedulers.mainThread())
                          .subscribe((session) -> {
            onPostLogin();
            stopLoading();
            onLoggedIn();
        }, (final Throwable e) -> {
            stopLoading();
            GDKSession.get().disconnect();
            UI.toast(this, R.string.id_error_logging_in_with_hardware, Toast.LENGTH_LONG);
            showInstructions(R.string.id_please_reconnect_your_hardware);
        });
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mPinDialog = UI.dismiss(this, mPinDialog);
        if (loginDisposable != null)
            loginDisposable.dispose();
    }

    @Override
    public void onResume() {
        super.onResume();
        mActiveNetwork.setText(getString(R.string.id_s_network, networkData.getName()));

        final Intent intent = getIntent();

        if (ACTION_USB_ATTACHED.equalsIgnoreCase(intent.getAction())) {
            onUsbAttach(intent.getParcelableExtra(UsbManager.EXTRA_DEVICE));
        }

        if (mUsb != null || mInLedgerDashboard) {
            // Continue displaying instructions until the user opens the
            // correct wallet app, or log in completes/errors out
            return;
        }

        // No hardware wallet, jump to PIN or 1st screen entry
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        if (AuthenticationHandler.hasPin(this))
            startActivityForResult(new Intent(this, PinActivity.class), 0);
        else
            startActivityForResult(new Intent(this, FirstScreenActivity.class), 0);
    }

    private void showInstructions(final int resId) {
        runOnUiThread(() -> {
            mInstructionsText.setText(resId);
            UI.show(mInstructionsText);
        });
    }

    private void showFirmwareOutdated(final int resId, final Runnable onContinue) {
        // FIXME: Close and set mUsb to null for ledger in onNegative/onCancel

        if (!BuildConfig.DEBUG) {
            // Only allow the user to skip firmware checks in debug builds.
            showInstructions(resId);
            return;
        }

        runOnUiThread(() -> {
            final MaterialDialog d;
            d = UI.popup(RequestLoginActivity.this, R.string.id_warning, R.string.id_continue, R.string.id_cancel)
                .content(resId)
                .onPositive((dialog, which) -> onContinue.run()).build();
            UI.setDialogCloseHandler(d, this::finishOnUiThread);
            d.show();
        });
    }
}
