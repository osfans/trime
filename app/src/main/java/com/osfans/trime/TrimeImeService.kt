package com.osfans.trime

import com.osfans.trime.ime.core.Trime

/**
 * This class only exists to prevent accidental IME deactivation after an update
 * of Trime to a new version when the location of the Trime class has
 * changed. The Android Framework uses the service class path as the IME id,
 * using this extension here makes sure it won't change ever again for the system.
 *
 * Important: DO NOT PUT ANY LOGIC INTO THIS CLASS. Make the necessary changes
 *  within the Trime class instead.
 */
class TrimeImeService : Trime()
