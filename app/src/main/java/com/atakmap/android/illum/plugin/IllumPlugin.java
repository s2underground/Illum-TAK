package com.atakmap.android.illum.plugin;

import android.content.Context;
import android.graphics.drawable.Drawable;

import com.atak.plugins.impl.PluginContextProvider;
import com.atakmap.android.illum.plugin.ui.IllumPane;
import com.atakmap.android.illum.plugin.ui.IllumPreferenceFragment;
import com.atakmap.android.maps.MapView;
import com.atakmap.app.preferences.ToolsPreferenceFragment;
import com.atakmap.coremap.log.Log;

import gov.tak.api.plugin.IPlugin;
import gov.tak.api.plugin.IServiceController;
import gov.tak.api.ui.IHostUIService;
import gov.tak.api.ui.Pane;
import gov.tak.api.ui.PaneBuilder;
import gov.tak.api.ui.ToolbarItem;
import gov.tak.api.ui.ToolbarItemAdapter;
import gov.tak.platform.marshal.MarshalManager;

/**
 * Illum: night light planning (BMNT/EENT, sun and moon times, moon
 * illumination and an HLL/LLL stoplight). Everything is computed on the
 * device, so it works with no network at all.
 */
public class IllumPlugin implements IPlugin {

    private static final String TAG = "IllumPlugin";

    private final IServiceController serviceController;
    private Context pluginContext;
    private final IHostUIService uiService;
    private final ToolbarItem toolbarItem;
    private Pane pane;
    private IllumPane controller;
    private boolean prefsRegistered;

    public IllumPlugin(IServiceController serviceController) {
        this.serviceController = serviceController;
        final PluginContextProvider ctxProvider = serviceController
                .getService(PluginContextProvider.class);
        if (ctxProvider != null) {
            pluginContext = ctxProvider.getPluginContext();
            pluginContext.setTheme(R.style.ATAKPluginTheme);
        }
        uiService = serviceController.getService(IHostUIService.class);

        toolbarItem = new ToolbarItem.Builder(
                pluginContext.getString(R.string.app_name),
                MarshalManager.marshal(
                        icon(),
                        android.graphics.drawable.Drawable.class,
                        gov.tak.api.commons.graphics.Bitmap.class))
                .setListener(new ToolbarItemAdapter() {
                    @Override
                    public void onClick(ToolbarItem item) {
                        showPane();
                    }
                }).setIdentifier(pluginContext.getPackageName())
                .build();
    }

    private Drawable icon() {
        return pluginContext.getResources().getDrawable(R.drawable.ic_launcher,
                pluginContext.getTheme());
    }

    @Override
    public void onStart() {
        if (uiService != null)
            uiService.addToolbarItem(toolbarItem);
        try {
            ToolsPreferenceFragment.register(new ToolsPreferenceFragment.ToolPreference(
                    pluginContext.getString(R.string.pref_title),
                    pluginContext.getString(R.string.pref_summary),
                    IllumPane.TOOL_KEY,
                    icon(),
                    new IllumPreferenceFragment(pluginContext)));
            prefsRegistered = true;
        } catch (RuntimeException e) {
            Log.e(TAG, "could not register tool preferences", e);
        }
    }

    @Override
    public void onStop() {
        if (uiService != null) {
            if (pane != null && uiService.isPaneVisible(pane))
                uiService.closePane(pane);
            uiService.removeToolbarItem(toolbarItem);
        }
        if (prefsRegistered) {
            ToolsPreferenceFragment.unregister(IllumPane.TOOL_KEY);
            prefsRegistered = false;
        }
        if (controller != null) {
            controller.dispose();
            controller = null;
            pane = null;
        }
    }

    private void showPane() {
        if (uiService == null)
            return;
        if (pane == null) {
            controller = new IllumPane(pluginContext, MapView.getMapView());
            pane = new PaneBuilder(controller.getView())
                    .setMetaValue(Pane.RELATIVE_LOCATION, Pane.Location.Default)
                    .setMetaValue(Pane.PREFERRED_WIDTH_RATIO, 0.45D)
                    .setMetaValue(Pane.PREFERRED_HEIGHT_RATIO, 0.6D)
                    .build();
        }
        controller.onShow();
        if (!uiService.isPaneVisible(pane))
            uiService.showPane(pane, null);
    }
}
