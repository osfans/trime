// SPDX-License-Identifier: GPL-3.0-or-later
package com.osfans.trime;

import android.app.Instrumentation;
import android.os.Bundle;

/** Separate Java entry point compatible with the optimized published old Release. */
public final class UserDictionaryUpgradeInstrumentation extends Instrumentation {
    private String phase;

    @Override
    public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        phase = arguments == null ? null : arguments.getString("t10Upgrade");
        start();
    }

    @Override
    public void onStart() {
        Bundle identity = new Bundle();
        identity.putInt("audit_pid", android.os.Process.myPid());
        sendStatus(0, identity);
        waitForIdleSync();
        UserDictionaryNativeUpgradeProbe.run(this, phase);
    }
}
