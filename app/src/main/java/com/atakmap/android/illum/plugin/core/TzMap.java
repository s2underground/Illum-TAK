package com.atakmap.android.illum.plugin.core;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.InflaterInputStream;

/**
 * Offline map of the world's time zones: latitude/longitude to an IANA zone
 * id such as "America/New_York". The device's own time zone database then
 * supplies the offset and daylight saving rules, so rule changes arrive with
 * normal Android updates.
 *
 * Built by tools/tzmap/build_tzmap.py from timezone-boundary-builder
 * (data (c) OpenStreetMap contributors, ODbL 1.0) on a 0.025 degree grid
 * (about 3 km). Format: deflated; magic "ITZ1", u16 cols, u16 rows,
 * f32 resolution, u16 zone count, zone ids (u8 length + UTF-8), then per row
 * u16 run count and runs of (u16 start column, u16 zone index).
 */
public final class TzMap {

    private static final int NONE = 0xFFFF;

    private final int cols, rows;
    private final double res;
    private final String[] zones;
    /** Index of each row's first run in starts/zoneOf; length rows + 1. */
    private final int[] rowStart;
    private final short[] starts;
    private final short[] zoneOf;

    private TzMap(int cols, int rows, double res, String[] zones, int[] rowStart,
            short[] starts, short[] zoneOf) {
        this.cols = cols;
        this.rows = rows;
        this.res = res;
        this.zones = zones;
        this.rowStart = rowStart;
        this.starts = starts;
        this.zoneOf = zoneOf;
    }

    /** Reads a map (the stream is closed). */
    public static TzMap read(InputStream raw) throws IOException {
        DataInputStream in = new DataInputStream(
                new BufferedInputStream(new InflaterInputStream(raw), 1 << 16));
        try {
            if (in.readInt() != 0x49545A31) // "ITZ1"
                throw new IOException("not a tz map");
            int cols = in.readUnsignedShort();
            int rows = in.readUnsignedShort();
            double res = in.readFloat();
            int n = in.readUnsignedShort();
            String[] zones = new String[n];
            for (int i = 0; i < n; i++) {
                byte[] b = new byte[in.readUnsignedByte()];
                in.readFully(b);
                zones[i] = new String(b, "UTF-8");
            }
            int[] rowStart = new int[rows + 1];
            short[] starts = new short[1 << 16];
            short[] zoneOf = new short[1 << 16];
            int k = 0;
            for (int r = 0; r < rows; r++) {
                rowStart[r] = k;
                int runs = in.readUnsignedShort();
                if (k + runs > starts.length) {
                    int len = Math.max(starts.length * 2, k + runs);
                    starts = copy(starts, len);
                    zoneOf = copy(zoneOf, len);
                }
                for (int i = 0; i < runs; i++) {
                    starts[k] = (short) in.readUnsignedShort();
                    zoneOf[k] = (short) in.readUnsignedShort();
                    k++;
                }
            }
            rowStart[rows] = k;
            if (!(res > 0) || Math.abs(cols * res - 360) > 1e-3
                    || Math.abs(rows * res - 180) > 1e-3)
                throw new IOException("bad tz map grid");
            return new TzMap(cols, rows, res, zones, rowStart, copy(starts, k), copy(zoneOf, k));
        } finally {
            in.close();
        }
    }

    private static short[] copy(short[] a, int len) {
        short[] b = new short[len];
        System.arraycopy(a, 0, b, 0, Math.min(a.length, len));
        return b;
    }

    /** IANA zone id at a point (oceans give "Etc/GMT+n"), or null if unknown. */
    public String zoneAt(double latDeg, double lonDeg) {
        if (Double.isNaN(latDeg) || Double.isNaN(lonDeg))
            return null;
        double lon = ((lonDeg + 180) % 360 + 360) % 360; // 0..360 from 180W
        int r = (int) Math.floor((90 - latDeg) / res);
        int c = (int) Math.floor(lon / res);
        r = Math.max(0, Math.min(rows - 1, r));
        c = Math.max(0, Math.min(cols - 1, c));
        int lo = rowStart[r], hi = rowStart[r + 1] - 1;
        if (hi < lo)
            return null;
        // last run whose start column <= c
        while (lo < hi) {
            int mid = (lo + hi + 1) >>> 1;
            if ((starts[mid] & 0xFFFF) <= c)
                lo = mid;
            else
                hi = mid - 1;
        }
        int z = zoneOf[lo] & 0xFFFF;
        return z == NONE || z >= zones.length ? null : zones[z];
    }

    public int zoneCount() {
        return zones.length;
    }
}
