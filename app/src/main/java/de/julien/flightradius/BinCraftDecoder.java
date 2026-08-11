package de.julien.flightradius;

import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Decodes the compact readsb/tar1090 aircraft format used by the ADSB.lol globe. */
final class BinCraftDecoder {
    private static final int HEADER_BYTES = 52;

    private BinCraftDecoder() { }

    static JSONArray decode(byte[] bytes) throws Exception {
        JSONArray aircraft = new JSONArray();
        if (bytes == null || bytes.length < HEADER_BYTES) return aircraft;
        ByteBuffer data = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        int stride = data.getInt(8);
        int version = data.getInt(40);
        if (stride < 108 || stride > 512 || bytes.length < stride) {
            throw new IllegalArgumentException("Invalid binCraft stride");
        }

        for (int offset = stride; offset + stride <= bytes.length; offset += stride) {
            JSONObject plane = decodeAircraft(data, offset, stride, version);
            if (plane != null) aircraft.put(plane);
        }
        return aircraft;
    }

    private static JSONObject decodeAircraft(ByteBuffer data, int offset, int stride,
                                             int version) throws Exception {
        int rawHex = data.getInt(offset);
        String hex = String.format("%06x", rawHex & 0x00ffffff);
        if ((rawHex & 0x01000000) != 0) hex = "~" + hex;

        int validity1 = unsignedByte(data, offset + 73);
        int validity2 = unsignedByte(data, offset + 74);
        int validity3 = unsignedByte(data, offset + 75);
        int validity4 = unsignedByte(data, offset + 76);
        int validity5 = unsignedByte(data, offset + 77);
        JSONObject plane = new JSONObject().put("hex", hex);

        double seen = version >= 20240218
                ? data.getInt(offset + 4) / 10d
                : unsignedShort(data, offset + 6) / 10d;
        plane.put("seen", seen);
        if ((validity1 & 64) != 0) {
            plane.put("lon", data.getInt(offset + 8) / 1_000_000d);
            plane.put("lat", data.getInt(offset + 12) / 1_000_000d);
            double seenPos = version >= 20240218 && stride >= 112
                    ? data.getInt(offset + 108) / 10d
                    : unsignedShort(data, offset + 4) / 10d;
            plane.put("seen_pos", seenPos);
        }

        int airGround = unsignedByte(data, offset + 68) & 15;
        if (airGround == 1) {
            plane.put("alt_baro", "ground");
        } else if ((validity1 & 16) != 0) {
            plane.put("alt_baro", data.getShort(offset + 20) * 25);
        }
        if ((validity1 & 32) != 0) {
            plane.put("alt_geom", data.getShort(offset + 22) * 25);
        }
        if ((validity1 & 128) != 0) plane.put("gs", data.getShort(offset + 34) / 10d);
        if ((validity2 & 8) != 0) plane.put("track", data.getShort(offset + 40) / 90d);
        if ((validity3 & 1) != 0) plane.put("baro_rate", data.getShort(offset + 16) * 8);
        if ((validity3 & 2) != 0) plane.put("geom_rate", data.getShort(offset + 18) * 8);

        String flight = ascii(data, offset + 78, Math.min(offset + 86, offset + stride));
        if ((validity1 & 8) != 0 && !flight.isEmpty()) plane.put("flight", flight);
        String aircraftType = ascii(data, offset + 88, Math.min(offset + 92, offset + stride));
        if (!aircraftType.isEmpty()) plane.put("t", aircraftType);
        String registration = ascii(data, offset + 92, Math.min(offset + 104, offset + stride));
        if (!registration.isEmpty()) plane.put("r", registration);
        plane.put("dbFlags", unsignedShort(data, offset + 86));

        int category = unsignedByte(data, offset + 64);
        if (category != 0) plane.put("category", Integer.toHexString(category).toUpperCase());
        if ((validity4 & 4) != 0) {
            plane.put("squawk", String.format("%04x", unsignedShort(data, offset + 32)));
        }
        if ((validity5 & 4) != 0) {
            JSONArray modes = new JSONArray();
            int navModes = unsignedByte(data, offset + 66);
            if ((navModes & 1) != 0) modes.put("autopilot");
            if ((navModes & 2) != 0) modes.put("vnav");
            if ((navModes & 4) != 0) modes.put("alt_hold");
            if ((navModes & 8) != 0) modes.put("approach");
            if ((navModes & 16) != 0) modes.put("lnav");
            if ((navModes & 32) != 0) modes.put("tcas");
            plane.put("nav_modes", modes);
        }
        return plane;
    }

    private static int unsignedByte(ByteBuffer data, int offset) {
        return data.get(offset) & 0xff;
    }

    private static int unsignedShort(ByteBuffer data, int offset) {
        return data.getShort(offset) & 0xffff;
    }

    private static String ascii(ByteBuffer data, int start, int end) {
        StringBuilder value = new StringBuilder();
        for (int i = start; i < end; i++) {
            int character = unsignedByte(data, i);
            if (character == 0) break;
            value.append((char) character);
        }
        return value.toString().trim();
    }
}
