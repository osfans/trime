/*
 * Adapted from https://github.com/florisboard/florisboard/blob/master/app/src/main/java/dev/patrickgold/florisboard/util/AndroidVersion.kt
 */

package com.osfans.trime.util

import android.os.Build

@Suppress("unused")
object AndroidVersion {
    /** Android 4.4 **/
    inline val ATLEAST_KITKAT get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT
    inline val ATMOST_KITKAT get() = Build.VERSION.SDK_INT <= Build.VERSION_CODES.KITKAT

    /** Android 5.0 **/
    inline val ATLEAST_LOLLIPOP get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP
    inline val ATMOST_LOLLIPOP get() = Build.VERSION.SDK_INT <= Build.VERSION_CODES.LOLLIPOP

    /** Android 5.1 **/
    inline val ATLEAST_LOLLIPOP_MR1 get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1
    inline val ATMOST_LOLLIPOP_MR1 get() = Build.VERSION.SDK_INT <= Build.VERSION_CODES.LOLLIPOP_MR1

    /** Android 6 **/
    inline val ATLEAST_M get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
    inline val ATMOST_M get() = Build.VERSION.SDK_INT <= Build.VERSION_CODES.M

    /** Android 7 **/
    inline val ATLEAST_N get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
    inline val ATMOST_N get() = Build.VERSION.SDK_INT <= Build.VERSION_CODES.N

    /** Android 7.1 **/
    inline val ATLEAST_N_MR1 get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1
    inline val ATMOST_N_MR1 get() = Build.VERSION.SDK_INT <= Build.VERSION_CODES.N_MR1

    /** Android 8 **/
    inline val ATLEAST_O get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
    inline val ATMOST_O get() = Build.VERSION.SDK_INT <= Build.VERSION_CODES.O

    /** Android 8.1 **/
    inline val ATLEAST_O_MR1 get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1
    inline val ATMOST_O_MR1 get() = Build.VERSION.SDK_INT <= Build.VERSION_CODES.O_MR1

    /** Android 9 **/
    inline val ATLEAST_P get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
    inline val ATMOST_P get() = Build.VERSION.SDK_INT <= Build.VERSION_CODES.P

    /** Android 10 **/
    inline val ATLEAST_Q get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
    inline val ATMOST_Q get() = Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q

    /** Android 11 **/
    inline val ATLEAST_R get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
    inline val ATMOST_R get() = Build.VERSION.SDK_INT <= Build.VERSION_CODES.R
}
