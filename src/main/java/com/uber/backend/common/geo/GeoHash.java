package com.uber.backend.common.geo;

/**
 * Base32 geohash encoder/decoder used for surge zones and secondary spatial indexing.
 * Precision 6 ≈ 1.2km x 0.6km cells — good default for city surge buckets.
 */
public final class GeoHash {

    private static final char[] BASE32 = "0123456789bcdefghjkmnpqrstuvwxyz".toCharArray();
    private static final int[] BASE32_DECODE = buildDecodeTable();

    private GeoHash() {
    }

    public static String encode(GeoPoint point, int precision) {
        if (precision < 1 || precision > 12) {
            throw new IllegalArgumentException("precision must be in [1, 12]");
        }
        if (point == null) {
            throw new IllegalArgumentException("point must not be null");
        }

        double minLat = -90.0;
        double maxLat = 90.0;
        double minLon = -180.0;
        double maxLon = 180.0;

        boolean evenBit = true;
        int bit = 0;
        int ch = 0;
        StringBuilder hash = new StringBuilder(precision);

        while (hash.length() < precision) {
            if (evenBit) {
                double mid = (minLon + maxLon) / 2.0;
                if (point.longitude() >= mid) {
                    ch |= 1 << (4 - bit);
                    minLon = mid;
                } else {
                    maxLon = mid;
                }
            } else {
                double mid = (minLat + maxLat) / 2.0;
                if (point.latitude() >= mid) {
                    ch |= 1 << (4 - bit);
                    minLat = mid;
                } else {
                    maxLat = mid;
                }
            }
            evenBit = !evenBit;
            if (bit < 4) {
                bit++;
            } else {
                hash.append(BASE32[ch]);
                bit = 0;
                ch = 0;
            }
        }
        return hash.toString();
    }

    public static GeoPoint decodeCenter(String hash) {
        if (hash == null || hash.isBlank()) {
            throw new IllegalArgumentException("hash must not be blank");
        }
        double minLat = -90.0;
        double maxLat = 90.0;
        double minLon = -180.0;
        double maxLon = 180.0;
        boolean evenBit = true;

        for (char c : hash.toLowerCase().toCharArray()) {
            int idx = BASE32_DECODE[c];
            if (idx < 0) {
                throw new IllegalArgumentException("invalid geohash character: " + c);
            }
            for (int mask = 16; mask > 0; mask >>= 1) {
                if (evenBit) {
                    double mid = (minLon + maxLon) / 2.0;
                    if ((idx & mask) != 0) {
                        minLon = mid;
                    } else {
                        maxLon = mid;
                    }
                } else {
                    double mid = (minLat + maxLat) / 2.0;
                    if ((idx & mask) != 0) {
                        minLat = mid;
                    } else {
                        maxLat = mid;
                    }
                }
                evenBit = !evenBit;
            }
        }
        return new GeoPoint((minLat + maxLat) / 2.0, (minLon + maxLon) / 2.0);
    }

    private static int[] buildDecodeTable() {
        int[] table = new int[128];
        for (int i = 0; i < table.length; i++) {
            table[i] = -1;
        }
        for (int i = 0; i < BASE32.length; i++) {
            table[BASE32[i]] = i;
        }
        return table;
    }
}
