package com.atakmap.android.illum.plugin.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

import com.atakmap.android.illum.plugin.core.DisplayZone;
import com.atakmap.android.illum.plugin.core.Formats;
import com.atakmap.android.illum.plugin.core.LightStandard;
import com.atakmap.android.illum.plugin.core.NightReport;
import com.atakmap.android.illum.plugin.core.PlanSettings;

import java.util.ArrayList;
import java.util.List;

/**
 * One night as a horizontal strip:
 * <ul>
 * <li>background shaded by sun altitude (day, civil, nautical, astronomical, night)</li>
 * <li>top strip: moon above the horizon</li>
 * <li>white curve: estimated illuminance, log scale 0.1 mlux to 1 lux; dashed line = HLL threshold</li>
 * <li>bottom strip: stoplight color per moment (HLL / LLL for the mission)</li>
 * <li>cyan brackets: the rated window; outside it is dimmed</li>
 * </ul>
 */
public class TimelineView extends View {

    static final int DAY = 0xFF4F6B85, CIVIL = 0xFF34485C, NAUT = 0xFF243241,
            ASTRO = 0xFF18212B, NIGHT = 0xFF0E1319;
    static final int MOON = 0xFFFFF59D;

    private NightReport night;
    private DisplayZone zone = DisplayZone.ZULU;
    private PlanSettings settings = PlanSettings.defaults();

    private final Paint fill = new Paint();
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dash = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final float dp;

    public TimelineView(Context context) {
        super(context);
        dp = context.getResources().getDisplayMetrics().density;
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(1.6f * dp);
        dash.setStyle(Paint.Style.STROKE);
        dash.setStrokeWidth(1f * dp);
        dash.setColor(0xCCFFFFFF);
        dash.setPathEffect(new DashPathEffect(new float[] {
                4 * dp, 3 * dp
        }, 0));
        text.setColor(Color.WHITE);
        text.setTextSize(10 * context.getResources().getDisplayMetrics().scaledDensity);
        setContentDescription("Night illumination timeline");
    }

    public void setData(NightReport n, DisplayZone z, PlanSettings s) {
        night = n;
        zone = z;
        settings = s;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        super.onDraw(c);
        int w = getWidth(), h = getHeight();
        c.drawColor(0xFF000000);
        NightReport n = night;
        if (n == null || n.sampleLux == null || w <= 0)
            return;

        // visible range: an hour either side of sunset/sunrise when they exist
        long t0 = n.sunset != NightReport.NONE
                ? Math.max(n.spanStart, n.sunset - 3600000L)
                : n.spanStart;
        long t1 = n.sunrise != NightReport.NONE
                ? Math.min(n.spanEnd, n.sunrise + 3600000L)
                : n.spanEnd;
        if (t1 <= t0) {
            t0 = n.spanStart;
            t1 = n.spanEnd;
        }

        float labelH = 13 * dp, moonH = 5 * dp, hllH = 7 * dp, axisH = 13 * dp;
        float chartTop = labelH + moonH, chartBot = h - hllH - axisH;
        if (chartBot <= chartTop)
            return;

        int count = n.sampleLux.length;
        boolean nvg = settings.mission == PlanSettings.Mission.NVG;
        int good = 0xFF2E7D32, bad = 0xFFC62828;

        // background, moon strip, stoplight strip
        for (int i = 0; i < count; i++) {
            long ta = n.sampleTime(i), tb = ta + n.sampleStep;
            if (tb < t0 || ta > t1)
                continue;
            float xa = x(ta, t0, t1, w), xb = x(tb, t0, t1, w) + 0.5f;
            fill.setColor(sky(n.sampleSunAlt[i]));
            c.drawRect(xa, labelH, xb, chartBot, fill);
            if (n.sampleMoonAlt[i] > 0) {
                fill.setColor(MOON);
                c.drawRect(xa, labelH, xb, labelH + moonH, fill);
            }
            boolean hll = n.sampleHll[i];
            fill.setColor(hll == nvg ? good : bad);
            c.drawRect(xa, chartBot, xb, chartBot + hllH, fill);
        }

        // lux curve, log scale 1e-4 .. 1 lux
        path.reset();
        boolean started = false;
        for (int i = 0; i < count; i++) {
            long t = n.sampleTime(i);
            if (t < t0 || t > t1)
                continue;
            float px = x(t, t0, t1, w);
            float py = y(n.sampleLux[i], chartTop, chartBot);
            if (!started) {
                path.moveTo(px, py);
                started = true;
            } else {
                path.lineTo(px, py);
            }
        }
        stroke.setColor(Color.WHITE);
        c.drawPath(path, stroke);

        // HLL threshold line (lux method)
        if (settings.standard.method == LightStandard.Method.LUX) {
            float ty = y(settings.standard.luxThreshold, chartTop, chartBot);
            c.drawLine(0, ty, w, ty, dash);
            text.setTextAlign(Paint.Align.RIGHT);
            text.setColor(0xCCFFFFFF);
            c.drawText(Formats.lux(settings.standard.luxThreshold), w - 2 * dp, ty - 2 * dp, text);
        }

        // dim outside the rated window, then bracket it
        if (n.hasWindow()) {
            fill.setColor(0x88000000);
            float ws = x(n.windowStart, t0, t1, w), we = x(n.windowEnd, t0, t1, w);
            if (ws > 0)
                c.drawRect(0, labelH, Math.min(ws, w), chartBot, fill);
            if (we < w)
                c.drawRect(Math.max(we, 0), labelH, w, chartBot, fill);
            stroke.setColor(0xFF4DD0E1);
            if (ws >= 0 && ws <= w)
                c.drawLine(ws, labelH, ws, chartBot + hllH, stroke);
            if (we >= 0 && we <= w)
                c.drawLine(we, labelH, we, chartBot + hllH, stroke);
        }

        // event labels along the top
        List<long[]> ev = new ArrayList<>();
        List<String> names = new ArrayList<>();
        addEvent(ev, names, n.sunset, "SS");
        addEvent(ev, names, n.eent, "EENT");
        addEvent(ev, names, n.bmnt, "BMNT");
        addEvent(ev, names, n.sunrise, "SR");
        for (NightReport.MoonEvent m : n.moonEvents)
            addEvent(ev, names, m.time, m.rise ? "MR" : "MS");
        text.setTextAlign(Paint.Align.CENTER);
        text.setColor(Color.WHITE);
        float lastX = -1000;
        // draw in time order to space labels
        for (int pass = 0; pass < ev.size(); pass++) {
            int best = -1;
            for (int i = 0; i < ev.size(); i++)
                if (ev.get(i)[1] == 0 && (best < 0 || ev.get(i)[0] < ev.get(best)[0]))
                    best = i;
            if (best < 0)
                break;
            ev.get(best)[1] = 1;
            long t = ev.get(best)[0];
            if (t < t0 || t > t1)
                continue;
            float px = x(t, t0, t1, w);
            c.drawLine(px, labelH - 2 * dp, px, labelH + moonH, text);
            if (px - lastX > 30 * dp) {
                float tx = Math.max(14 * dp, Math.min(w - 14 * dp, px));
                c.drawText(names.get(best), tx, labelH - 3 * dp, text);
                lastX = px;
            }
        }

        // hour ticks along the bottom
        text.setColor(0xFFBDBDBD);
        long hour = 3600000L;
        long off = zone.offsetMillis(t0);
        long first = ((t0 + off + hour - 1) / hour) * hour - off;
        int every = w / dp < 260 ? 3 : 2;
        for (long t = first; t <= t1; t += hour) {
            float px = x(t, t0, t1, w);
            c.drawLine(px, chartBot + hllH, px, chartBot + hllH + 3 * dp, text);
            String hh = Formats.hhmm(t, zone).substring(0, 2);
            int hv = Integer.parseInt(hh);
            if (hv % every == 0 && px > 8 * dp && px < w - 8 * dp)
                c.drawText(hh, px, h - 2 * dp, text);
        }
    }

    private static void addEvent(List<long[]> ev, List<String> names, long t, String name) {
        if (t == NightReport.NONE)
            return;
        ev.add(new long[] {
                t, 0
        });
        names.add(name);
    }

    private static float x(long t, long t0, long t1, int w) {
        return (float) ((t - t0) / (double) (t1 - t0) * w);
    }

    private static float y(double lux, float top, float bot) {
        double l = Math.log10(Math.max(1e-4, Math.min(1.0, lux)));
        double f = (l + 4.0) / 4.0; // 0 at 1e-4, 1 at 1 lux
        return (float) (bot - f * (bot - top));
    }

    static int sky(float sunAlt) {
        if (sunAlt > -0.833f)
            return DAY;
        if (sunAlt > -6f)
            return CIVIL;
        if (sunAlt > -12f)
            return NAUT;
        if (sunAlt > -18f)
            return ASTRO;
        return NIGHT;
    }
}
