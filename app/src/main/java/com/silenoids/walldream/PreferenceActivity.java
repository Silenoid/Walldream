package com.silenoids.walldream;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceFragmentCompat;

public class PreferenceActivity extends AppCompatActivity {

    WallpaperPreferencesFragment we = new WallpaperPreferencesFragment();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.settings);
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.settings_container, new WallpaperPreferencesFragment())
                .commit();
    }

    public static class WallpaperPreferencesFragment extends PreferenceFragmentCompat {

        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.preferences, rootKey);
        }
    }
}
