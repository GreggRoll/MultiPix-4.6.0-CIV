package com.atakmap.android.multipix.plugin;

import com.atak.plugins.impl.AbstractPlugin;
import com.atak.plugins.impl.PluginContextProvider;
import com.atakmap.android.multipix.MultiPixMapComponent;

import gov.tak.api.plugin.IServiceController;

public class MultiPixLifecycle extends AbstractPlugin {

    public MultiPixLifecycle(IServiceController serviceController) {
        super(serviceController,
                new MultiPixTool(serviceController
                        .getService(PluginContextProvider.class)
                        .getPluginContext()),
                new MultiPixMapComponent());
        PluginNativeLoader.init(serviceController
                .getService(PluginContextProvider.class)
                .getPluginContext());
    }
}
