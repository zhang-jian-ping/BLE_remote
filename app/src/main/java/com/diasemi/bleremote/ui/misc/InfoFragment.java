package com.diasemi.bleremote.ui.misc;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceGroup;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.diasemi.bleremote.BLERemoteApplication;
import com.diasemi.bleremote.R;
import com.diasemi.bleremote.Utils;

public class InfoFragment extends PreferenceFragment {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.chip_info);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_settings, container, false);
        BLERemoteApplication application = (BLERemoteApplication) getActivity().getApplication();
        PreferenceManager preferenceManager = getPreferenceManager();

        String version = "";
        try {
            version = getActivity().getPackageManager().getPackageInfo(getActivity().getPackageName(), 0).versionName;
        } catch (PackageManager.NameNotFoundException e) {}
        preferenceManager.findPreference("AppVersion").setSummary(version);

        String hidVersion = Utils.getHidDriverVersion();
        Preference hidVersionEntry = preferenceManager.findPreference("HidVersion");
        if (hidVersion.isEmpty())
            ((PreferenceGroup) preferenceManager.findPreference("Information")).removePreference(hidVersionEntry);
        else
            hidVersionEntry.setSummary(hidVersion);

        if (application.version != null)
            preferenceManager.findPreference("FirmwareVersion").setSummary(application.version);

        preferenceManager.findPreference("InfoSendMail").setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            public boolean onPreferenceClick(Preference pref) {
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                intent.setData(Uri.parse("mailto:bluetooth.support@diasemi.com?subject=Remote Controls application question"));
                getActivity().startActivity(intent);
                return true;
            }
        });

        return view;
    }
}
