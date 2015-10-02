package com.aware.plugin.android.wear;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;

import com.aware.Aware;

/**
 * Created by denzil on 22/05/15.
 */
public class Settings extends PreferenceActivity implements SharedPreferences.OnSharedPreferenceChangeListener {

    /**
     * Activate/Deactivate Android Wear
     */
    public static final String STATUS_PLUGIN_ANDROID_WEAR = "status_plugin_android_wear";

    private static CheckBoxPreference status;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.preferences);

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        prefs.registerOnSharedPreferenceChangeListener(this);

        status = (CheckBoxPreference) findPreference(STATUS_PLUGIN_ANDROID_WEAR);
        status.setChecked(Aware.getSetting(this, STATUS_PLUGIN_ANDROID_WEAR).equals("true"));
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        Preference setting = findPreference(key);

        if( setting.getKey().equals(STATUS_PLUGIN_ANDROID_WEAR)) {
            boolean is_active = sharedPreferences.getBoolean(key, false);
            Aware.setSetting(this, key, is_active);
            status.setChecked(is_active);
            if( is_active ) {
                Aware.startPlugin(this, "com.aware.plugin.android.wear");
            } else {
                Aware.stopPlugin(this, "com.aware.plugin.android.wear");
            }
        }
    }
}
