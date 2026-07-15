package de.julien.flightradius;

import android.app.Application;

public class MARApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        AppPreferences.get(this).edit()
                .putString(AppPreferences.KEY_AIRCRAFT_HISTORY_JSON, "[]")
                .apply();
    }
}
