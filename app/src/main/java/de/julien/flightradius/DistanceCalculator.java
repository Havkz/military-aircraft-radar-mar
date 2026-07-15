package de.julien.flightradius;

final class DistanceCalculator {
    private static final double EARTH_RADIUS_KM = 6371.0088;

    private DistanceCalculator() { }

    static double kilometers(double userLatitude, double userLongitude,
                              double aircraftLatitude, double aircraftLongitude) {
        if (!validCoordinate(userLatitude, userLongitude)
                || !validCoordinate(aircraftLatitude, aircraftLongitude)) {
            return Double.NaN;
        }

        double latitudeDelta = Math.toRadians(aircraftLatitude - userLatitude);
        double longitudeDelta = Math.toRadians(aircraftLongitude - userLongitude);
        double userLatitudeRadians = Math.toRadians(userLatitude);
        double aircraftLatitudeRadians = Math.toRadians(aircraftLatitude);
        double sinLatitude = Math.sin(latitudeDelta / 2d);
        double sinLongitude = Math.sin(longitudeDelta / 2d);
        double haversine = sinLatitude * sinLatitude
                + Math.cos(userLatitudeRadians) * Math.cos(aircraftLatitudeRadians)
                * sinLongitude * sinLongitude;
        double boundedHaversine = Math.max(0d, Math.min(1d, haversine));
        double arc = 2d * Math.atan2(
                Math.sqrt(boundedHaversine), Math.sqrt(1d - boundedHaversine));
        return EARTH_RADIUS_KM * arc;
    }

    private static boolean validCoordinate(double latitude, double longitude) {
        return !Double.isNaN(latitude) && !Double.isInfinite(latitude)
                && !Double.isNaN(longitude) && !Double.isInfinite(longitude)
                && latitude >= -90d && latitude <= 90d
                && longitude >= -180d && longitude <= 180d;
    }
}
