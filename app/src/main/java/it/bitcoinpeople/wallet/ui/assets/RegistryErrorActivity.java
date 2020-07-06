package it.bitcoinpeople.wallet.ui.assets;

import android.app.Activity;
import android.os.Bundle;

import androidx.annotation.Nullable;

import it.bitcoinpeople.wallet.ui.R;
import it.bitcoinpeople.wallet.ui.UI;

public class RegistryErrorActivity extends Activity {
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // hard-coded colors to liquid since the registry is only used on liquid
        UI.popup(this, R.string.id_warning, android.R.string.ok)
        .content(R.string.id_the_asset_registry_is_currently)
        .onAny((dialog, which) -> finish())
        .canceledOnTouchOutside(false)
        .positiveColor(getResources().getColor(R.color.liquidDark))
        .negativeColor(getResources().getColor(R.color.liquidDark))
        .build()
        .show();
    }
}
