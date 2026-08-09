package de.julien.flightradius;

import android.content.Context;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.zip.GZIPInputStream;

final class AirportDirectory {
    static final class Airport {
        final double latitude;
        final double longitude;
        final String code;
        final String city;
        final String name;
        final String country;

        Airport(double latitude, double longitude, String code, String city,
                String name, String country) {
            this.latitude = latitude;
            this.longitude = longitude;
            this.code = code;
            this.city = city;
            this.name = name;
            this.country = country;
        }
    }

    private static volatile List<Airport> airports;

    private AirportDirectory() { }

    static Airport nearest(Context context, double latitude, double longitude,
                           double maximumDistanceKm) {
        Airport nearest = null;
        double best = maximumDistanceKm;
        double longitudeWindow = .4d / Math.max(.15d,
                Math.cos(Math.toRadians(latitude)));
        for (Airport airport : load(context)) {
            double longitudeDelta = Math.abs(airport.longitude - longitude);
            longitudeDelta = Math.min(longitudeDelta, 360d - longitudeDelta);
            if (Math.abs(airport.latitude - latitude) > .4d
                    || longitudeDelta > longitudeWindow) continue;
            double distance = DistanceCalculator.kilometers(latitude, longitude,
                    airport.latitude, airport.longitude);
            if (!Double.isNaN(distance) && distance < best) {
                best = distance;
                nearest = airport;
            }
        }
        return nearest;
    }

    private static List<Airport> load(Context context) {
        List<Airport> current = airports;
        if (current != null) return current;
        synchronized (AirportDirectory.class) {
            if (airports != null) return airports;
            try {
                airports = parse(new GZIPInputStream(
                        context.getAssets().open("airports_compact.tsv.gz")));
            } catch (Exception ignored) {
                airports = Collections.emptyList();
            }
            return airports;
        }
    }

    static List<Airport> parse(InputStream stream) throws Exception {
        List<Airport> result = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, "UTF-8"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] fields = line.split("\\t", -1);
                if (fields.length < 6) continue;
                try {
                    result.add(new Airport(Double.parseDouble(fields[0]),
                            Double.parseDouble(fields[1]), fields[2], fields[3],
                            fields[4], fields[5]));
                } catch (NumberFormatException ignored) { }
            }
        }
        return result;
    }
}
