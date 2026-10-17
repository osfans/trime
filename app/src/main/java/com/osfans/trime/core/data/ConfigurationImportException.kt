// SPDX-License-Identifier: GPL-3.0-or-later
package com.osfans.trime.core.data

/** Separates a rejected import from a recovery that needs the user's backup. */
class ConfigurationImportException(
    cause: Throwable,
    var previousConfigurationRestored: Boolean,
    val recoveryDirectory: String?,
) : Exception("Configuration validation or activation failed", cause)
