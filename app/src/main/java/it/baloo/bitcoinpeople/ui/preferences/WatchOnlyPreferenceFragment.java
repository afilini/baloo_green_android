package it.baloo.bitcoinpeople.ui.preferences;

import android.os.Bundle;

import androidx.core.content.ContextCompat;
import androidx.preference.Preference;

import it.baloo.bitcoinpeople.ui.BuildConfig;
import it.baloo.bitcoinpeople.ui.LoggedActivity;
import it.baloo.bitcoinpeople.ui.R;
import it.baloo.bitcoinpeople.ui.UI;

public class WatchOnlyPreferenceFragment extends GAPreferenceFragment
    implements Preference.OnPreferenceClickListener {
    private static final String TAG = WatchOnlyPreferenceFragment.class.getSimpleName();

    @Override
    public void onCreatePreferences(final Bundle savedInstanceState, final String rootKey) {
        super.onCreatePreferences(savedInstanceState, rootKey);
        addPreferencesFromResource(R.xml.preference_watchonly);
        setHasOptionsMenu(true);

        // Network & Logout
        final Preference logout = find(PrefKeys.LOGOUT);
        logout.setTitle("Bitcoin People");
        logout.setSummary(UI.getColoredString(
                              getString(R.string.id_log_out), ContextCompat.getColor(getContext(), R.color.red)));
        logout.setOnPreferenceClickListener(preference -> {
            logout.setEnabled(false);
            logout();
            return false;
        });

        // Version
        final Preference version = find("version");
        version.setSummary(String.format("%s %s",
                                         getString(R.string.app_name),
                                         getString(R.string.id_version_1s_2s,
                                                   BuildConfig.VERSION_NAME,
                                                   BuildConfig.BUILD_TYPE)));

        // Terms of service
        final Preference termsOfUse = find(PrefKeys.TERMS_OF_USE);
        termsOfUse.setOnPreferenceClickListener(preference -> openURI("https://www.bitcoinpeople.it/terms"));

        // Privacy policy
        final Preference privacyPolicy = find(PrefKeys.PRIVACY_POLICY);
        privacyPolicy.setOnPreferenceClickListener(preference -> openURI("https://www.bitcoinpeople.it/privacy"));

        ((Preference) find("logout")).setOnPreferenceClickListener(this);
    }

    @Override
    public boolean onPreferenceClick(final Preference preference) {
        switch (preference.getKey()) {
        case "logout":
            ((LoggedActivity) getActivity()).logout();
            return true;
        }
        return false;
    }



}
