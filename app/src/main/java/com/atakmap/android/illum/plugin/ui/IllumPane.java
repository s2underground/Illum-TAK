package com.atakmap.android.illum.plugin.ui;

import android.app.DatePickerDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.PointF;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.DatePicker;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;

import com.atak.plugins.impl.PluginLayoutInflater;
import com.atakmap.android.illum.plugin.R;
import com.atakmap.android.illum.plugin.core.CivilDate;
import com.atakmap.android.illum.plugin.core.DisplayZone;
import com.atakmap.android.illum.plugin.core.Formats;
import com.atakmap.android.illum.plugin.core.IllumPrefs;
import com.atakmap.android.illum.plugin.core.LightStandard;
import com.atakmap.android.illum.plugin.core.NightCalculator;
import com.atakmap.android.illum.plugin.core.NightReport;
import com.atakmap.android.illum.plugin.core.PlanSettings;
import com.atakmap.android.maps.MapEvent;
import com.atakmap.android.maps.MapEventDispatcher;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.Marker;
import com.atakmap.android.maps.PointMapItem;
import com.atakmap.android.toolbar.widgets.TextContainer;
import com.atakmap.android.util.ATAKUtilities;
import com.atakmap.app.SettingsActivity;
import com.atakmap.coremap.conversions.CoordinateFormat;
import com.atakmap.coremap.conversions.CoordinateFormatUtilities;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The Illum pane: location, night card, timeline and the planning grid.
 * All astronomy runs on one background thread; the UI only renders
 * immutable results. UI-thread only unless noted.
 */
public final class IllumPane implements SharedPreferences.OnSharedPreferenceChangeListener {

    private static final String TAG = "IllumPane";
    public static final String TOOL_KEY = "illum_preferences";

    static final int GREEN = 0xFF2E7D32, AMBER = 0xFFF9A825, RED = 0xFFC62828,
            GRAY = 0xFF616161;

    private final Context plugin;
    private final MapView mapView;
    private final SharedPreferences prefs;
    private final View root;
    private final ScrollView scroll;
    private final TextView locName, locCoord, dateLabel, bannerTitle, bannerSub, notes,
            planHeader, standardRef;
    private final LinearLayout banner, planRows;
    private final TableLayout timesTable;
    private final TimelineView timeline;
    private final MoonPhaseView bannerMoon;
    private final TextView glossaryToggle, glossary;
    private final Button pickBtn;

    private final ExecutorService exec = Executors.newSingleThreadExecutor();
    private final Handler ui = new Handler(Looper.getMainLooper());
    private int generation;
    private boolean disposed;

    private double lat = Double.NaN, lon = Double.NaN;
    private String source = "self";
    private String placeName = "";
    private long selectedDay = Long.MIN_VALUE;
    private boolean followTonight = true;

    private boolean picking;
    private MapEventDispatcher.MapEventDispatchListener pickListener;

    /** Immutable inputs for one computation. */
    static final class Inputs {
        double lat, lon;
        PlanSettings settings;
        DisplayZone zone;
        long firstDay;
        int nights;
        long selectedDay;
        boolean showZulu, allTwilight;
    }

    /** Immutable results. */
    static final class Result {
        Inputs in;
        List<NightReport> plan = Collections.emptyList();
        NightReport selected;
        String error;
    }

    private Result last;

    private final Runnable debouncedRecompute = new Runnable() {
        @Override
        public void run() {
            recompute();
        }
    };

    public IllumPane(Context pluginContext, MapView mapView) {
        this.plugin = pluginContext;
        this.mapView = mapView;
        this.prefs = PreferenceManager.getDefaultSharedPreferences(mapView.getContext());

        root = PluginLayoutInflater.inflate(pluginContext, R.layout.illum_pane, null);
        scroll = root.findViewById(R.id.illum_scroll);
        locName = root.findViewById(R.id.loc_name);
        locCoord = root.findViewById(R.id.loc_coord);
        dateLabel = root.findViewById(R.id.date_label);
        banner = root.findViewById(R.id.banner);
        bannerTitle = root.findViewById(R.id.banner_title);
        bannerSub = root.findViewById(R.id.banner_sub);
        timesTable = root.findViewById(R.id.times_table);
        notes = root.findViewById(R.id.notes);
        planHeader = root.findViewById(R.id.plan_header);
        planRows = root.findViewById(R.id.plan_rows);
        standardRef = root.findViewById(R.id.standard_ref);
        pickBtn = root.findViewById(R.id.btn_pick);

        timeline = new TimelineView(pluginContext);
        FrameLayout holder = root.findViewById(R.id.timeline_holder);
        holder.addView(timeline, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        bannerMoon = new MoonPhaseView(pluginContext);
        FrameLayout moonHolder = root.findViewById(R.id.moon_holder);
        moonHolder.addView(bannerMoon, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        glossaryToggle = root.findViewById(R.id.glossary_toggle);
        glossary = root.findViewById(R.id.glossary);
        glossaryToggle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean show = glossary.getVisibility() != View.VISIBLE;
                glossary.setVisibility(show ? View.VISIBLE : View.GONE);
                glossaryToggle.setText(show ? R.string.glossary_hide : R.string.glossary_show);
            }
        });

        root.findViewById(R.id.btn_self).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopPick();
                useSelf(true);
            }
        });
        root.findViewById(R.id.btn_center).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopPick();
                useCenter();
            }
        });
        pickBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (picking)
                    stopPick();
                else
                    startPick();
            }
        });
        root.findViewById(R.id.btn_prev).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                shiftDay(-1);
            }
        });
        root.findViewById(R.id.btn_next).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                shiftDay(1);
            }
        });
        root.findViewById(R.id.btn_today).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                followTonight = true;
                recompute();
            }
        });
        dateLabel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pickDate();
            }
        });
        root.findViewById(R.id.btn_settings).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                SettingsActivity.start(TOOL_KEY, null);
            }
        });

        restoreLocation();
        prefs.registerOnSharedPreferenceChangeListener(this);
    }

    public View getView() {
        return root;
    }

    /** Called each time the pane is shown. */
    public void onShow() {
        if ("self".equals(source))
            useSelf(false);
        else
            recompute();
    }

    public void dispose() {
        disposed = true;
        stopPick();
        ui.removeCallbacks(debouncedRecompute);
        prefs.unregisterOnSharedPreferenceChangeListener(this);
        exec.shutdownNow();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sp, String key) {
        if (key == null || !key.startsWith(IllumPrefs.PREFIX) || key.startsWith("illum_last_"))
            return;
        ui.removeCallbacks(debouncedRecompute);
        ui.postDelayed(debouncedRecompute, 300);
    }

    // ------------------------------------------------------------------
    // Location
    // ------------------------------------------------------------------

    private void restoreLocation() {
        double la = parse(prefs.getString(IllumPrefs.LAST_LAT, null));
        double lo = parse(prefs.getString(IllumPrefs.LAST_LON, null));
        if (!Double.isNaN(la) && !Double.isNaN(lo)) {
            lat = la;
            lon = lo;
            source = prefs.getString(IllumPrefs.LAST_SOURCE, "pick");
            placeName = prefs.getString(IllumPrefs.LAST_NAME, "");
        } else {
            source = "self";
        }
    }

    private static double parse(String s) {
        if (s == null)
            return Double.NaN;
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }

    private void useSelf(boolean userAsked) {
        Marker self = ATAKUtilities.findSelf(mapView);
        GeoPoint gp = self != null ? self.getPoint() : null;
        if (gp != null && gp.isValid() && !(gp.getLatitude() == 0 && gp.getLongitude() == 0)) {
            setLocation(gp.getLatitude(), gp.getLongitude(), "self",
                    plugin.getString(R.string.loc_self));
            return;
        }
        if (userAsked || Double.isNaN(lat)) {
            if (userAsked)
                Toast.makeText(mapView.getContext(), plugin.getString(R.string.no_self),
                        Toast.LENGTH_SHORT).show();
            useCenter();
        } else {
            recompute(); // keep the last known place
        }
    }

    private void useCenter() {
        GeoPointMetaData c = mapView.getCenterPoint();
        GeoPoint gp = c != null ? c.get() : null;
        if (gp != null && gp.isValid())
            setLocation(gp.getLatitude(), gp.getLongitude(), "center",
                    plugin.getString(R.string.loc_center));
    }

    private void setLocation(double la, double lo, String src, String name) {
        lat = la;
        lon = lo;
        source = src;
        placeName = name == null ? "" : name;
        prefs.edit()
                .putString(IllumPrefs.LAST_LAT, Double.toString(la))
                .putString(IllumPrefs.LAST_LON, Double.toString(lo))
                .putString(IllumPrefs.LAST_SOURCE, src)
                .putString(IllumPrefs.LAST_NAME, placeName)
                .apply();
        recompute();
    }

    private void startPick() {
        final MapEventDispatcher d = mapView.getMapEventDispatcher();
        d.pushListeners();
        d.clearListeners(MapEvent.MAP_CLICK);
        d.clearListeners(MapEvent.ITEM_CLICK);
        d.clearListeners(MapEvent.MAP_LONG_PRESS);
        pickListener = new MapEventDispatcher.MapEventDispatchListener() {
            @Override
            public void onMapEvent(MapEvent event) {
                onPicked(event);
            }
        };
        d.addMapEventListener(MapEvent.MAP_CLICK, pickListener);
        d.addMapEventListener(MapEvent.ITEM_CLICK, pickListener);
        d.addMapEventListener(MapEvent.MAP_LONG_PRESS, pickListener);
        picking = true;
        pickBtn.setText(R.string.cancel);
        TextContainer.getInstance().displayPrompt(plugin.getString(R.string.pick_prompt));
    }

    private void onPicked(MapEvent event) {
        GeoPoint gp = null;
        String name = "Map point";
        MapItem item = event.getItem();
        if (MapEvent.ITEM_CLICK.equals(event.getType()) && item instanceof PointMapItem) {
            gp = ((PointMapItem) item).getPoint();
            String n = ATAKUtilities.getDisplayName(item);
            if (n != null && !n.isEmpty())
                name = n;
        } else {
            PointF p = event.getPointF();
            if (p != null) {
                GeoPointMetaData m = mapView.inverseWithElevation(p.x, p.y);
                if (m != null)
                    gp = m.get();
            }
        }
        stopPick();
        if (gp != null && gp.isValid())
            setLocation(gp.getLatitude(), gp.getLongitude(), "pick", name);
    }

    private void stopPick() {
        if (!picking)
            return;
        picking = false;
        MapEventDispatcher d = mapView.getMapEventDispatcher();
        if (pickListener != null) {
            d.removeMapEventListener(MapEvent.MAP_CLICK, pickListener);
            d.removeMapEventListener(MapEvent.ITEM_CLICK, pickListener);
            d.removeMapEventListener(MapEvent.MAP_LONG_PRESS, pickListener);
            pickListener = null;
        }
        d.popListeners();
        TextContainer.getInstance().closePrompt();
        pickBtn.setText(R.string.loc_pick);
    }

    // ------------------------------------------------------------------
    // Dates
    // ------------------------------------------------------------------

    private static long tonight(DisplayZone zone) {
        // the night whose noon-to-noon span contains "now"
        return CivilDate.epochDayOf(System.currentTimeMillis() - 12 * 3600000L, zone);
    }

    private void shiftDay(int delta) {
        if (last == null || last.in == null)
            return;
        selectedDay = last.in.selectedDay + delta;
        followTonight = false;
        recompute();
    }

    private void pickDate() {
        if (last == null || last.in == null)
            return;
        int[] c = CivilDate.toCivil(last.in.selectedDay);
        DatePickerDialog dlg = new DatePickerDialog(mapView.getContext(),
                new DatePickerDialog.OnDateSetListener() {
                    @Override
                    public void onDateSet(DatePicker view, int y, int m, int d) {
                        selectedDay = CivilDate.fromCivil(y, m + 1, d);
                        followTonight = false;
                        recompute();
                    }
                }, c[0], c[1] - 1, c[2]);
        dlg.show();
    }

    // ------------------------------------------------------------------
    // Compute
    // ------------------------------------------------------------------

    private void recompute() {
        if (disposed)
            return;
        if (Double.isNaN(lat) || Double.isNaN(lon)) {
            // no place yet: self, else map center; either one calls back here
            useSelf(false);
            return;
        }
        Map<String, ?> all = prefs.getAll();
        final Inputs in = new Inputs();
        in.lat = lat;
        in.lon = lon;
        in.settings = IllumPrefs.settings(all);
        in.zone = IllumPrefs.zone(all, lon, TimeZone.getDefault());
        in.nights = IllumPrefs.planNights(all);
        in.showZulu = IllumPrefs.showZulu(all);
        in.allTwilight = IllumPrefs.allTwilight(all);
        in.firstDay = tonight(in.zone);
        if (followTonight || selectedDay == Long.MIN_VALUE) {
            selectedDay = in.firstDay;
            followTonight = true;
        }
        in.selectedDay = selectedDay;

        showLocation(in);
        dateLabel.setText(plugin.getString(R.string.night_of, CivilDate.label(in.selectedDay)));
        bannerSub.setText(R.string.computing);

        final int gen = ++generation;
        try {
            exec.execute(new Runnable() {
                @Override
                public void run() {
                    final Result r = compute(in);
                    ui.post(new Runnable() {
                        @Override
                        public void run() {
                            if (gen == generation && !disposed)
                                render(r);
                        }
                    });
                }
            });
        } catch (java.util.concurrent.RejectedExecutionException e) {
            Log.w(TAG, "compute rejected (pane disposed)");
        }
    }

    /** Background thread. */
    static Result compute(Inputs in) {
        Result r = new Result();
        r.in = in;
        try {
            r.plan = NightCalculator.range(in.lat, in.lon, in.firstDay, in.nights, in.zone,
                    in.settings);
            long idx = in.selectedDay - in.firstDay;
            if (idx >= 0 && idx < r.plan.size())
                r.selected = r.plan.get((int) idx);
            else
                r.selected = NightCalculator.compute(in.lat, in.lon, in.selectedDay, in.zone,
                        in.settings);
        } catch (RuntimeException e) {
            Log.e(TAG, "illumination computation failed", e);
            r.error = "Could not compute light data for this location.";
        }
        return r;
    }

    // ------------------------------------------------------------------
    // Render
    // ------------------------------------------------------------------

    private void showLocation(Inputs in) {
        locName.setText(placeName.isEmpty() ? "Location" : placeName);
        String coord;
        try {
            CoordinateFormat fmt = CoordinateFormat.find(prefs);
            coord = CoordinateFormatUtilities.formatToString(new GeoPoint(in.lat, in.lon), fmt);
        } catch (RuntimeException e) {
            coord = null;
        }
        if (coord == null || coord.isEmpty())
            coord = String.format(Locale.US, "%.5f, %.5f", in.lat, in.lon);
        locCoord.setText(coord);
    }

    private void render(Result r) {
        last = r;
        Inputs in = r.in;
        if (r.error != null || r.selected == null) {
            setBanner(GRAY, "NO DATA", r.error != null ? r.error : "", Color.WHITE);
            return;
        }
        NightReport n = r.selected;
        PlanSettings ps = in.settings;
        DisplayZone z = in.zone;

        dateLabel.setText(plugin.getString(n.epochDay == in.firstDay ? R.string.night_of_tonight
                : R.string.night_of, CivilDate.label(n.epochDay)));

        // banner
        String mission = ps.mission == PlanSettings.Mission.NVG ? "NVG ops" : "Concealment";
        if (n.rating == NightReport.Rating.NONE) {
            String why = n.noNauticalDarkness && ps.windowMode != PlanSettings.WindowMode.CUSTOM
                    ? "No nautical darkness tonight (sun never 12° below the horizon)"
                    : "No rated window tonight";
            setBanner(GRAY, "NO RATING", why + " · " + ps.standard.label + " · "
                    + mission, Color.WHITE);
        } else {
            int color = colorFor(n.rating);
            String title = n.rating.name() + " · " + levelText(n.level);
            String sub = Formats.percent(n.hllFraction) + " of " + n.windowLabel + " ("
                    + Formats.time(n.windowStart, z) + "–" + Formats.time(n.windowEnd, z)
                    + ") is HLL · " + ps.standard.label + " · " + mission;
            setBanner(color, title, sub, n.rating == NightReport.Rating.AMBER ? Color.BLACK
                    : Color.WHITE);
        }

        bannerMoon.setPhase(n.illumPercent / 100.0, n.waxing, in.lat < 0);
        renderTimes(n, in);
        timeline.setData(n, z, ps);
        renderNotes(n, in);
        renderPlan(r);
        standardRef.setText(ps.standard.reference
                + (ps.sky != PlanSettings.Sky.CLEAR ? " Sky: " + ps.sky.label + "." : "")
                + (ps.includeNightSky ? " Includes 0.5 mlux starlight/airglow." : ""));
    }

    private void setBanner(int color, String title, String sub, int textColor) {
        banner.setBackgroundColor(color);
        bannerTitle.setText(title);
        bannerTitle.setTextColor(textColor);
        bannerSub.setText(sub);
        bannerSub.setTextColor(textColor);
    }

    static int colorFor(NightReport.Rating r) {
        switch (r) {
            case GREEN:
                return GREEN;
            case AMBER:
                return AMBER;
            case RED:
                return RED;
            default:
                return GRAY;
        }
    }

    static String levelText(NightReport.LightLevel l) {
        switch (l) {
            case HLL:
                return "HLL";
            case LLL:
                return "LLL";
            case MIXED:
                return "MIXED";
            default:
                return "N/A";
        }
    }

    private void renderTimes(NightReport n, Inputs in) {
        timesTable.removeAllViews();
        DisplayZone z = in.zone;
        boolean zu = in.showZulu;
        addRow("Sunset", time(n.sunset, z, zu), "EENT", time(n.eent, z, zu));
        addRow("BMNT", time(n.bmnt, z, zu), "Sunrise", time(n.sunrise, z, zu));
        if (in.allTwilight) {
            addRow("EECT", time(n.eect, z, zu), "BMCT", time(n.bmct, z, zu));
            addRow("EEAT", time(n.eeat, z, zu), "BMAT", time(n.bmat, z, zu));
        }
        addRow("Moonrise", moon(n, true, z), "Moonset", moon(n, false, z));
        addRow("Illum", new SpannableStringBuilder(String.format(Locale.US, "%.0f%% %s",
                n.illumPercent, n.waxing ? "↑" : "↓"))
                .append(small("\n" + n.phaseName)),
                "Moon max",
                Double.isNaN(n.moonMaxAltDeg) ? new SpannableStringBuilder("--")
                        : new SpannableStringBuilder(Formats.altitude(n.moonMaxAltDeg))
                                .append(small("\n@ " + Formats.time(n.moonMaxAltTime, z))));
        addRow("Moonlight",
                Double.isNaN(n.peakMoonLux) || n.peakMoonLux <= 0
                        ? new SpannableStringBuilder("none")
                        : new SpannableStringBuilder(Formats.lux(n.peakMoonLux))
                                .append(small("\npeak @ " + Formats.time(n.peakMoonLuxTime, z))),
                "Window",
                n.hasWindow() ? new SpannableStringBuilder(Formats.time(n.windowStart, z)
                        + "–" + Formats.time(n.windowEnd, z))
                                .append(small("\n" + n.windowLabel))
                        : new SpannableStringBuilder("none"));
    }

    private static CharSequence time(long t, DisplayZone z, boolean zulu) {
        SpannableStringBuilder sb = new SpannableStringBuilder(Formats.time(t, z));
        if (zulu && !z.isUtc() && t != NightReport.NONE)
            sb.append(small("\n" + Formats.time(t, DisplayZone.ZULU)));
        return sb;
    }

    private static CharSequence moon(NightReport n, boolean rise, DisplayZone z) {
        SpannableStringBuilder sb = new SpannableStringBuilder();
        for (NightReport.MoonEvent e : n.moonEvents) {
            if (e.rise != rise)
                continue;
            if (sb.length() > 0)
                sb.append("\n");
            sb.append(Formats.time(e.time, z));
            sb.append(small(" @" + Formats.degrees(e.azimuthDeg)));
        }
        if (sb.length() == 0) {
            if (n.moonUpAllSpan)
                sb.append(rise ? "up all night" : "--");
            else if (n.moonDownAllSpan)
                sb.append(rise ? "down all night" : "--");
            else
                sb.append("--");
        }
        return sb;
    }

    private static CharSequence small(String s) {
        SpannableStringBuilder sb = new SpannableStringBuilder(s);
        sb.setSpan(new RelativeSizeSpan(0.8f), 0, s.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        sb.setSpan(new ForegroundColorSpan(0xFFAAAAAA), 0, s.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return sb;
    }

    private void addRow(String l1, CharSequence v1, String l2, CharSequence v2) {
        TableRow row = new TableRow(plugin);
        row.setPadding(0, dp(2), 0, dp(2));
        row.addView(label(l1));
        row.addView(value(v1));
        row.addView(label(l2));
        row.addView(value(v2));
        timesTable.addView(row);
    }

    private TextView label(String s) {
        TextView t = new TextView(plugin);
        t.setText(s);
        t.setTextColor(0xFF9E9E9E);
        t.setTextSize(12);
        t.setPadding(0, 0, dp(6), 0);
        return t;
    }

    private TextView value(CharSequence s) {
        TextView t = new TextView(plugin);
        t.setText(s);
        t.setTextColor(Color.WHITE);
        t.setTextSize(14);
        t.setPadding(0, 0, dp(8), 0);
        return t;
    }

    private int dp(int v) {
        return Math.round(v * plugin.getResources().getDisplayMetrics().density);
    }

    private void renderNotes(NightReport n, Inputs in) {
        StringBuilder sb = new StringBuilder();
        DisplayZone z = in.zone;
        PlanSettings ps = in.settings;
        if (n.noNauticalDarkness)
            sb.append("• The sun never gets 12° below the horizon tonight: no EENT/BMNT.\n");
        else if (n.allNauticalDark)
            sb.append("• Polar night: nautical darkness all day.\n");
        if (n.level != NightReport.LightLevel.HLL && !n.hllPeriods.isEmpty()) {
            sb.append("\u2022 HLL: ");
            for (int i = 0; i < n.hllPeriods.size(); i++) {
                NightReport.HllPeriod p = n.hllPeriods.get(i);
                if (i > 0)
                    sb.append(", ");
                sb.append(Formats.time(p.start, z)).append("\u2013")
                        .append(Formats.time(p.end, z));
                if (p.twilightOnly)
                    sb.append(" (twilight)");
            }
            sb.append(". The rest of the window is LLL.\n");
        }
        if (!Double.isNaN(n.lowMoonFraction) && n.lowMoonFraction >= 0.10
                && n.level != NightReport.LightLevel.LLL)
            sb.append("• Moon below ")
                    .append(String.format(Locale.US, "%.0f", ps.standard.minMoonAltDeg))
                    .append("° for ").append(Formats.percent(n.lowMoonFraction))
                    .append(" of the window: long shadows; NVGs degain looking toward the moon.\n");
        if (ps.standard.method == LightStandard.Method.MOON_RULE)
            sb.append("• Rated by moon illumination and elevation (")
                    .append(String.format(Locale.US, "≥%.0f%%, ≥%.0f°",
                            ps.standard.minIllumPercent, ps.standard.minMoonAltDeg))
                    .append("); the lux curve is shown for reference.\n");
        if (sb.length() == 0) {
            notes.setVisibility(View.GONE);
        } else {
            notes.setText(sb.toString().trim());
            notes.setVisibility(View.VISIBLE);
        }
    }

    private void renderPlan(final Result r) {
        Inputs in = r.in;
        planHeader.setText(plugin.getString(R.string.plan_header, r.plan.size()) + " · "
                + in.zone.describe(System.currentTimeMillis()));
        planRows.removeAllViews();

        // column header
        View head = PluginLayoutInflater.inflate(plugin, R.layout.illum_plan_row, null);
        setRow(head, plugin.getString(R.string.col_date), plugin.getString(R.string.col_rating),
                Color.TRANSPARENT, Color.LTGRAY, plugin.getString(R.string.col_illum),
                plugin.getString(R.string.col_moon), plugin.getString(R.string.col_hll));
        planRows.addView(head);

        for (final NightReport n : r.plan) {
            View row = PluginLayoutInflater.inflate(plugin, R.layout.illum_plan_row, null);
            int color = colorFor(n.rating);
            String chip = n.rating == NightReport.Rating.NONE ? "N/A" : levelChip(n.level);
            setRow(row, CivilDate.weekday(n.epochDay) + " " + CivilDate.shortLabel(n.epochDay),
                    chip, color,
                    n.rating == NightReport.Rating.AMBER ? Color.BLACK : Color.WHITE,
                    String.format(Locale.US, "%.0f%%%s", n.illumPercent,
                            n.waxing ? "↑" : "↓"),
                    moonSummary(n, in.zone),
                    Double.isNaN(n.hllFraction) ? "--" : Formats.percent(n.hllFraction));
            MoonPhaseView mini = new MoonPhaseView(plugin);
            mini.setPhase(n.illumPercent / 100.0, n.waxing, in.lat < 0);
            ((FrameLayout) row.findViewById(R.id.row_moon_holder)).addView(mini,
                    new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT));
            if (r.selected != null && n.epochDay == r.selected.epochDay)
                row.setBackgroundColor(0x33FFFFFF);
            row.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    selectedDay = n.epochDay;
                    followTonight = n.epochDay == r.in.firstDay;
                    recompute();
                    scroll.smoothScrollTo(0, 0);
                }
            });
            planRows.addView(row);
        }
    }

    private static String levelChip(NightReport.LightLevel l) {
        return l == NightReport.LightLevel.MIXED ? "MIX" : levelText(l);
    }

    private static String moonSummary(NightReport n, DisplayZone z) {
        String rise = "--", set = "--";
        for (NightReport.MoonEvent e : n.moonEvents) {
            if (e.rise && "--".equals(rise))
                rise = Formats.hhmm(e.time, z);
            if (!e.rise && "--".equals(set))
                set = Formats.hhmm(e.time, z);
        }
        if (n.moonEvents.isEmpty())
            return n.moonUpAllSpan ? "up all night" : "down all night";
        return "R " + rise + " / S " + set;
    }

    private static void setRow(View row, String date, String chip, int chipColor, int chipText,
            String illum, String moon, String hll) {
        ((TextView) row.findViewById(R.id.row_date)).setText(date);
        TextView c = row.findViewById(R.id.row_chip);
        c.setText(chip);
        c.setBackgroundColor(chipColor);
        c.setTextColor(chipText);
        c.setGravity(Gravity.CENTER);
        ((TextView) row.findViewById(R.id.row_illum)).setText(illum);
        ((TextView) row.findViewById(R.id.row_moon)).setText(moon);
        ((TextView) row.findViewById(R.id.row_hll)).setText(hll);
    }
}
