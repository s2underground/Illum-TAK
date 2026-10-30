package com.atakmap.android.illum.plugin.ui;

import com.atakmap.android.illum.plugin.core.HorizonProfile;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.map.elevation.ElevationData;
import com.atakmap.map.elevation.ElevationManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Reads bare-terrain elevations (DTED, SRTM and other terrain models loaded
 * in ATAK) through ElevationManager. Surface models such as DSMs are
 * excluded so buildings and trees do not count as skyline. Missing data
 * stays NaN. Call from a background thread.
 */
final class AtakElevationSampler implements HorizonProfile.ElevationSampler {

    private static final String TAG = "IllumElevation";
    /** Points per native query: bounds memory use and keeps queries quick. */
    private static final int CHUNK = 1024;

    @Override
    public void sample(double[] lat, double[] lon, double[] out) {
        ElevationManager.QueryParameters params = new ElevationManager.QueryParameters();
        params.elevationModel = ElevationData.MODEL_TERRAIN;
        int n = lat.length;
        for (int start = 0; start < n; start += CHUNK) {
            if (Thread.currentThread().isInterrupted())
                return;
            int end = Math.min(n, start + CHUNK);
            List<GeoPoint> pts = new ArrayList<>(end - start);
            for (int i = start; i < end; i++)
                pts.add(new GeoPoint(lat[i], lon[i]));
            double[] chunk = new double[end - start];
            try {
                ElevationManager.getElevation(pts.iterator(), chunk, params, null);
                System.arraycopy(chunk, 0, out, start, chunk.length);
            } catch (RuntimeException e) {
                // leave this chunk as NaN; the profile reports reduced coverage
                Log.w(TAG, "elevation query failed", e);
            }
        }
    }
}
