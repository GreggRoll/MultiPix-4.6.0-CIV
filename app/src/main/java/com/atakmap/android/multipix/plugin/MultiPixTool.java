package com.atakmap.android.multipix.plugin;

import android.content.Context;

import com.atak.plugins.impl.AbstractPluginTool;
import com.atakmap.android.multipix.MultiPixDropDownReceiver;

import gov.tak.api.util.Disposable;

public class MultiPixTool extends AbstractPluginTool implements Disposable {

    public MultiPixTool(Context context) {
        super(context,
                context.getString(R.string.app_name),
                context.getString(R.string.app_name),
                context.getResources().getDrawable(R.drawable.ic_launcher),
                MultiPixDropDownReceiver.SHOW_PLUGIN);
    }

    @Override
    public void dispose() {
    }
}
