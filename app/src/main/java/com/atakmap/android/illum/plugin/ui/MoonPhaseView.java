package com.atakmap.android.illum.plugin.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

import java.util.Locale;

/**
 * The moon's phase as seen from the observer: a dark disk with the lit part
 * drawn from the illuminated fraction. The terminator is an ellipse whose
 * half-width is r x |2k - 1|. Waxing is lit on the right as seen from the
 * northern hemisphere; the image is mirrored for waning and for the southern
 * hemisphere (where the moon appears upside down).
 */
public class MoonPhaseView extends View {

    static final int LIT = 0xFFF2EEDB;
    static final int DARK = 0xFF262B33;
    static final int RIM = 0xFF6B7380;

    private double fraction = 0.5;
    private boolean waxing = true;
    private boolean southern;

    private final Paint lit = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dark = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF disk = new RectF();
    private final RectF term = new RectF();

    public MoonPhaseView(Context context) {
        super(context);
        lit.setColor(LIT);
        lit.setStyle(Paint.Style.FILL);
        dark.setColor(DARK);
        dark.setStyle(Paint.Style.FILL);
        rim.setColor(RIM);
        rim.setStyle(Paint.Style.STROKE);
        rim.setStrokeWidth(Math.max(1f, context.getResources().getDisplayMetrics().density));
    }

    /**
     * @param illumFraction illuminated fraction 0..1
     * @param waxing true while the moon is waxing
     * @param southernHemisphere observer latitude below 0
     */
    public void setPhase(double illumFraction, boolean waxing, boolean southernHemisphere) {
        this.fraction = Math.max(0, Math.min(1, illumFraction));
        this.waxing = waxing;
        this.southern = southernHemisphere;
        setContentDescription(String.format(Locale.US, "Moon %.0f%% illuminated, %s",
                fraction * 100, waxing ? "waxing" : "waning"));
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        super.onDraw(c);
        float w = getWidth(), h = getHeight();
        float r = Math.min(w, h) / 2f - rim.getStrokeWidth();
        if (r <= 1)
            return;
        float cx = w / 2f, cy = h / 2f;
        disk.set(cx - r, cy - r, cx + r, cy + r);
        c.drawOval(disk, dark);

        if (fraction > 0.005) {
            c.save();
            // Base drawing: lit limb on the right (waxing, northern hemisphere).
            if (waxing == southern)
                c.scale(-1f, 1f, cx, cy);
            if (fraction >= 0.995) {
                c.drawOval(disk, lit);
            } else {
                float e = (float) (r * Math.abs(2 * fraction - 1));
                term.set(cx - e, cy - r, cx + e, cy + r);
                path.reset();
                // right half of the disk, top to bottom
                path.arcTo(disk, -90f, 180f, true);
                // terminator back from bottom to top: bulging left (gibbous)
                // or right (crescent)
                path.arcTo(term, 90f, fraction > 0.5 ? 180f : -180f, false);
                path.close();
                c.drawPath(path, lit);
            }
            c.restore();
        }
        c.drawOval(disk, rim);
    }
}
