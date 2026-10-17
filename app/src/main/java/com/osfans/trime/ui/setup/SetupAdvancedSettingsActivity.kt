/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.osfans.trime.ui.setup

import com.osfans.trime.ui.main.MainActivity

/** Standard internal settings host keeps the unfinished wizard on the task stack. */
class SetupAdvancedSettingsActivity : MainActivity() {
    protected override val shouldCheckSetup: Boolean get() = false
}
