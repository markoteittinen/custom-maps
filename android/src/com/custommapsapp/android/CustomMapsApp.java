package com.custommapsapp.android;

import com.custommapsapp.android.storage.PreferenceStore;

import android.app.Application;
import android.content.res.Configuration;
import android.util.Log;

import java.util.Locale;

public class CustomMapsApp extends Application {
  private Locale preferredLocale = null;
  private Locale defaultLocale = null;

  @Override
  public void onCreate() {
    super.onCreate();

    defaultLocale = Locale.getDefault();
    PreferenceStore prefStore = PreferenceStore.instance(getApplicationContext());
    String language = prefStore.getLanguage();
    if (language != null) {
      changeLanguage(language);
    }
  }

  @Override
  public void onConfigurationChanged(Configuration newConfig) {
    super.onConfigurationChanged(newConfig);

    if (preferredLocale != null &&
        !preferredLocale.getLanguage().equals(newConfig.locale.getLanguage())) {
      Configuration config = new Configuration();
      config.locale = preferredLocale;
      Locale.setDefault(preferredLocale);
      getBaseContext().getResources().updateConfiguration(config, null);
    }
  }

  public void changeLanguage(String languageCode) {
    Configuration config = getBaseContext().getResources().getConfiguration();
    if (languageCode.equals(config.locale.getLanguage())) {
      return;
    }
    Log.i(CustomMaps.LOG_TAG, String.format("Changing lang: preferred %s, config %s",
        languageCode, config.locale.getLanguage()));

    if (languageCode.equals("default")) {
      preferredLocale = defaultLocale;
    } else {
      preferredLocale = new Locale(languageCode);
    }
    config = new Configuration();
    config.locale = preferredLocale;
    Locale.setDefault(preferredLocale);
    getBaseContext().getResources().updateConfiguration(config, null);
  }
}
