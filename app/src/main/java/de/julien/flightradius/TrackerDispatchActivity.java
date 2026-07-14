package de.julien.flightradius;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.os.Bundle;

public class TrackerDispatchActivity extends Activity {
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        Intent source = getIntent();
        String callsign = source.getStringExtra("callsign");
        String hex = source.getStringExtra("hex");
        double lat = source.getDoubleExtra("lat", Double.NaN);
        double lon = source.getDoubleExtra("lon", Double.NaN);
        try {
            startActivity(TrackerLinks.selectedIntent(this, callsign, hex, lat, lon));
        } catch (ActivityNotFoundException ignored) {
            startActivity(new Intent(Intent.ACTION_VIEW,
                    TrackerLinks.selected(this, callsign, hex, lat, lon)));
        }
        finish();
        overridePendingTransition(0, 0);
    }
}
