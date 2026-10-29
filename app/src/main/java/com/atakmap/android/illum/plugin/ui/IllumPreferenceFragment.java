package com.atakmap.android.illum.plugin.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.Preference;

import com.atakmap.android.illum.plugin.R;
import com.atakmap.android.preference.PluginPreferenceFragment;

/**
 * Illum settings, reachable from Settings > Tool Preferences > Specific Tool
 * Preferences > Illum Preferences (or the Settings button in the pane).
 */
public class IllumPreferenceFragment extends PluginPreferenceFragment {

    private static Context staticPluginContext;

    private static final String[] EDIT_KEYS = {
            "illum_custom_mlux", "illum_custom_min_illum", "illum_custom_min_alt",
            "illum_window_start", "illum_window_end", "illum_manual_offset"
    };

    /** Required by Android to re-create the fragment. */
    public IllumPreferenceFragment() {
        super(staticPluginContext, R.xml.illum_preferences);
    }

    @SuppressLint("ValidFragment")
    public IllumPreferenceFragment(Context pluginContext) {
        super(pluginContext, R.xml.illum_preferences);
        staticPluginContext = pluginContext;
    }

    @Override
    public String getSubTitle() {
        return getSubTitle("Tool Preferences", "Illum Preferences");
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        for (String key : EDIT_KEYS)
            bindSummary(findPreference(key), null);
        Preference tol = findPreference("illum_tolerance");
        if (tol != null && staticPluginContext != null)
            bindSummary(tol, staticPluginContext.getString(R.string.pref_tolerance_summary));
    }

    /** Show the current value of an edit-text preference as its summary. */
    private static void bindSummary(Preference p, final String format) {
        if (!(p instanceof EditTextPreference))
            return;
        final EditTextPreference e = (EditTextPreference) p;
        e.setSummary(summary(format, e.getText()));
        e.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                preference.setSummary(summary(format, String.valueOf(newValue)));
                return true;
            }
        });
    }

    private static String summary(String format, String value) {
        String v = value == null ? "" : value;
        return format == null ? v : String.format(format, v);
    }
}
