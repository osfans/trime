package com.osfans.trime.ime.enums

import android.view.KeyEvent
import com.osfans.trime.ime.keyboard.Key
import timber.log.Timber
import java.util.EnumMap
import kotlin.collections.HashMap

@Suppress("ktlint:standard:enum-entry-name-case")
enum class Keycode {
    // 与原trime.yaml主题android_key/name小节相比，差异如下：
    // 1. 数字开头的keyName添加了下划线(在init阶段已经修复)，受到影响的按键有： 0-12，3D_MODE
    //
    // 符号英文名称-图形对应关系可以参考 https://www.mianfeiziti.com/font_glyph-172795.htm

    VoidSymbol,
    SOFT_LEFT,
    SOFT_RIGHT,
    HOME,
    BACK,
    CALL,
    ENDCALL,
    _0,
    _1,
    _2,
    _3,
    _4,
    _5,
    _6,
    _7,
    _8,
    _9,
    asterisk,
    numbersign,
    Up,
    Down,
    Left,
    Right,
    KP_Begin,
    VOLUME_UP,
    VOLUME_DOWN,
    POWER,
    CAMERA,
    Clear,
    a,
    b,
    c,
    d,
    e,
    f,
    g,
    h,
    i,
    j,
    k,
    l,
    m,
    n,
    o,
    p,
    q,
    r,
    s,
    t,
    u,
    v,
    w,
    x,
    y,
    z,
    comma,
    period,
    Alt_L,
    Alt_R,
    Shift_L,
    Shift_R,
    Tab,
    space,
    SYM,
    EXPLORER,
    ENVELOPE,
    Return,
    BackSpace,
    grave,
    minus,
    equal,
    bracketleft,
    bracketright,
    backslash,
    semicolon,
    apostrophe,
    slash,
    at,
    NUM,
    HEADSETHOOK,
    FOCUS,
    plus,
    Menu,
    NOTIFICATION,
    Find,
    MEDIA_PLAY_PAUSE,
    MEDIA_STOP,
    MEDIA_NEXT,
    MEDIA_PREVIOUS,
    MEDIA_REWIND,
    MEDIA_FAST_FORWARD,
    MUTE,
    Page_Up,
    Page_Down,
    PICTSYMBOLS,
    Mode_switch,
    BUTTON_A,
    BUTTON_B,
    BUTTON_C,
    BUTTON_X,
    BUTTON_Y,
    BUTTON_Z,
    BUTTON_L1,
    BUTTON_R1,
    BUTTON_L2,
    BUTTON_R2,
    BUTTON_THUMBL,
    BUTTON_THUMBR,
    BUTTON_START,
    BUTTON_SELECT,
    BUTTON_MODE,
    Escape,
    Delete,
    Control_L,
    Control_R,
    Caps_Lock,
    Scroll_Lock,
    Meta_L,
    Meta_R,
    function,
    Sys_Req,
    Pause,
    Home,
    End,
    Insert,
    Next,
    MEDIA_PLAY,
    MEDIA_PAUSE,
    MEDIA_CLOSE,
    MEDIA_EJECT,
    MEDIA_RECORD,
    F1,
    F2,
    F3,
    F4,
    F5,
    F6,
    F7,
    F8,
    F9,
    F10,
    F11,
    F12,
    Num_Lock,
    KP_0,
    KP_1,
    KP_2,
    KP_3,
    KP_4,
    KP_5,
    KP_6,
    KP_7,
    KP_8,
    KP_9,
    KP_Divide,
    KP_Multiply,
    KP_Subtract,
    KP_Add,
    KP_Decimal,
    KP_Separator,
    KP_Enter,
    KP_Equal,
    parenleft,
    parenright,
    VOLUME_MUTE,
    INFO,
    CHANNEL_UP,
    CHANNEL_DOWN,
    ZOOM_IN,
    ZOOM_OUT,
    TV,
    WINDOW,
    GUIDE,
    DVR,
    BOOKMARK,
    CAPTIONS,
    SETTINGS,
    TV_POWER,
    TV_INPUT,
    STB_POWER,
    STB_INPUT,
    AVR_POWER,
    AVR_INPUT,
    PROG_RED,
    PROG_GREEN,
    PROG_YELLOW,
    PROG_BLUE,
    APP_SWITCH,
    BUTTON_1,
    BUTTON_2,
    BUTTON_3,
    BUTTON_4,
    BUTTON_5,
    BUTTON_6,
    BUTTON_7,
    BUTTON_8,
    BUTTON_9,
    BUTTON_10,
    BUTTON_11,
    BUTTON_12,
    BUTTON_13,
    BUTTON_14,
    BUTTON_15,
    BUTTON_16,
    LANGUAGE_SWITCH,
    MANNER_MODE,
    _3D_MODE,
    CONTACTS,
    CALENDAR,
    MUSIC,
    CALCULATOR,
    Zenkaku_Hankaku,
    Eisu_toggle,
    Muhenkan,
    Henkan,
    Hiragana_Katakana,
    yen,
    RO,
    Kana_Lock,
    ASSIST,
    BRIGHTNESS_DOWN,
    BRIGHTNESS_UP,
    MEDIA_AUDIO_TRACK,
    SLEEP,
    WAKEUP,
    PAIRING,
    MEDIA_TOP_MENU,
    _11,
    _12,
    LAST_CHANNEL,
    TV_DATA_SERVICE,
    VOICE_ASSIST,
    TV_RADIO_SERVICE,
    TV_TELETEXT,
    TV_NUMBER_ENTRY,
    TV_TERRESTRIAL_ANALOG,
    TV_TERRESTRIAL_DIGITAL,
    TV_SATELLITE,
    TV_SATELLITE_BS,
    TV_SATELLITE_CS,
    TV_SATELLITE_SERVICE,
    TV_NETWORK,
    TV_ANTENNA_CABLE,
    TV_INPUT_HDMI_1,
    TV_INPUT_HDMI_2,
    TV_INPUT_HDMI_3,
    TV_INPUT_HDMI_4,
    TV_INPUT_COMPOSITE_1,
    TV_INPUT_COMPOSITE_2,
    TV_INPUT_COMPONENT_1,
    TV_INPUT_COMPONENT_2,
    TV_INPUT_VGA_1,
    TV_AUDIO_DESCRIPTION,
    TV_AUDIO_DESCRIPTION_MIX_UP,
    TV_AUDIO_DESCRIPTION_MIX_DOWN,
    TV_ZOOM_MODE,
    TV_CONTENTS_MENU,
    TV_MEDIA_CONTEXT_MENU,
    TV_TIMER_PROGRAMMING,
    Help,
    NAVIGATE_PREVIOUS,
    NAVIGATE_NEXT,
    NAVIGATE_IN,
    NAVIGATE_OUT,
    STEM_PRIMARY,
    STEM_1,
    STEM_2,
    STEM_3,
    Pointer_UpLeft,
    Pointer_DownLeft,
    Pointer_UpRight,
    Pointer_DownRight,
    MEDIA_SKIP_FORWARD,
    MEDIA_SKIP_BACKWARD,
    MEDIA_STEP_FORWARD,
    MEDIA_STEP_BACKWARD,
    SOFT_SLEEP,
    CUT,
    COPY,
    PASTE,
    SYSTEM_NAVIGATION_UP,
    SYSTEM_NAVIGATION_DOWN,
    SYSTEM_NAVIGATION_LEFT,
    SYSTEM_NAVIGATION_RIGHT,
    ALL_APPS,
    REFRESH,
    THUMBS_UP,
    THUMBS_DOWN,
    PROFILE_SWITCH,
    A,
    B,
    C,
    D,
    E,
    F,
    G,
    H,
    I,
    J,
    K,
    L,
    M,
    N,
    O,
    P,
    Q,
    R,
    S,
    T,
    U,
    V,
    W,
    X,
    Y,
    Z,
    exclam,
    quotedbl,
    dollar,
    percent,
    ampersand,
    colon,
    less,
    greater,
    question,
    asciicircum,
    underscore,
    braceleft,
    bar,
    braceright,
    asciitilde,
    ;

    companion object {
        // librime keyname (x11) - trime keycode (兼容Android)
        private val convertMap: HashMap<String, Keycode> = hashMapOf()

        // 部分符号的 trime keycode (兼容Android) - key label
        private val reverseMap: EnumMap<Keycode, String> = EnumMap(Keycode::class.java)

        init {
            for (type in values()) {
                convertMap[type.toString()] = type
            }

            // android keycode 包含的数字开头的按键
            reverseMap[_3D_MODE] = "3D_MODE"
            reverseMap[_0] = "0"
            reverseMap[_1] = "1"
            reverseMap[_2] = "2"
            reverseMap[_3] = "3"
            reverseMap[_4] = "4"
            reverseMap[_5] = "5"
            reverseMap[_6] = "6"
            reverseMap[_7] = "7"
            reverseMap[_8] = "8"
            reverseMap[_9] = "9"
            reverseMap[_8] = "8"
            reverseMap[_9] = "9"

            // android keycode 已包含的符号
            reverseMap[grave] = "`"
            reverseMap[at] = "@"
            reverseMap[numbersign] = "#"
            reverseMap[asterisk] = "*"
            reverseMap[parenleft] = "("
            reverseMap[parenright] = ")"
            reverseMap[minus] = "-"
            reverseMap[equal] = "="
            reverseMap[plus] = "+"
            reverseMap[bracketleft] = "["
            reverseMap[bracketright] = "]"
            reverseMap[backslash] = "\\"
            reverseMap[semicolon] = ";"
            reverseMap[apostrophe] = "'"
            reverseMap[comma] = ","
            reverseMap[period] = "."
            reverseMap[slash] = "/"

            // android keycode未包含的符号
            reverseMap[exclam] = "!"
            reverseMap[quotedbl] = "\""
            reverseMap[dollar] = "$"
            reverseMap[percent] = "%"
            reverseMap[ampersand] = "&"
            reverseMap[colon] = ":"
            reverseMap[less] = "<"
            reverseMap[greater] = ">"
            reverseMap[question] = "?"
            reverseMap[asciicircum] = "^"
            reverseMap[underscore] = "_"
            reverseMap[braceleft] = "{"
            reverseMap[bar] = "|"
            reverseMap[braceright] = "}"
            reverseMap[asciitilde] = "~"

            reverseMap.forEach {
                convertMap[it.value] = it.key
            }

            // android keycode 包含的小键盘，仅用于输出label，不用于label转按键
            reverseMap[KP_0] = "0"
            reverseMap[KP_1] = "1"
            reverseMap[KP_2] = "2"
            reverseMap[KP_3] = "3"
            reverseMap[KP_4] = "4"
            reverseMap[KP_5] = "5"
            reverseMap[KP_6] = "6"
            reverseMap[KP_7] = "7"
            reverseMap[KP_8] = "8"
            reverseMap[KP_9] = "9"
            reverseMap[KP_8] = "8"
            reverseMap[KP_9] = "9"
        }

        fun isStdKey(keycode: Int): Boolean {
            return keycode in SOFT_LEFT.ordinal..PROFILE_SWITCH.ordinal
        }

        fun toStdKeyEvent(
            keycode: Int,
            mask: Int = 0,
        ): IntArray {
            val event = IntArray(2)
            if (keycode !in values().indices) return event
            if (keycode < A.ordinal) {
                event[0] = keycode
                event[1] = mask
            } else {
                if (keycode <= Z.ordinal) {
                    event[0] = keycode - A.ordinal + a.ordinal
                } else {
                    event[0] =
                        when (keycode) {
                            exclam.ordinal -> _1.ordinal
                            dollar.ordinal -> _4.ordinal
                            percent.ordinal -> _5.ordinal
                            asciicircum.ordinal -> _6.ordinal
                            ampersand.ordinal -> _7.ordinal
                            quotedbl.ordinal -> apostrophe.ordinal
                            colon.ordinal -> semicolon.ordinal
                            less.ordinal -> comma.ordinal
                            greater.ordinal -> period.ordinal
                            question.ordinal -> slash.ordinal
                            underscore.ordinal -> minus.ordinal
                            braceleft.ordinal -> bracketleft.ordinal
                            braceright.ordinal -> bracketright.ordinal
                            asciitilde.ordinal -> grave.ordinal
                            bar.ordinal -> backslash.ordinal
                            else -> 0
                        }
                }
                event[1] = mask or KeyEvent.META_SHIFT_ON
            }
            return event
        }

        private fun hasSymbolLabel(keycode: Int): Boolean {
            if (keycode !in values().indices) return false
            return keycode >= A.ordinal || reverseMap.containsKey(values()[keycode])
        }

        fun getSymbolLabel(keycode: Keycode): String {
            return reverseMap[keycode] ?: ""
        }

        fun getDisplayLabel(
            keyCode: Int,
            mask: Int,
        ): String =
            if (isStdKey(keyCode)) {
                // Android keycode区域
                if (Key.getKcm().isPrintingKey(keyCode)) {
                    val event = KeyEvent(0, 0, KeyEvent.ACTION_DOWN, keyCode, 0, mask)
                    val charCode = event.getUnicodeChar(mask)
                    Timber.d("getDisplayLabel(): keycode=$keyCode, mask=$mask, charCode=$charCode")
                    if (charCode > 0) {
                        charCode.toChar().toString()
                    } else {
                        Key.getKcm().getDisplayLabel(keyCode).lowercase()
                    }
                } else {
                    keyNameOf(keyCode)
                }
            } else if (hasSymbolLabel(keyCode)) { // 可見符號
                getSymbolLabel(valueOf(keyCode))
            } else {
                ""
            }

        private val masks =
            hashMapOf(
                "Shift" to KeyEvent.META_SHIFT_ON,
                "Control" to KeyEvent.META_CTRL_ON,
                "Alt" to KeyEvent.META_ALT_ON,
                "Meta" to KeyEvent.META_META_ON,
                "SYM" to KeyEvent.META_SYM_ON,
            )

        @JvmStatic
        fun fromString(s: String): Keycode {
            return convertMap[s] ?: VoidSymbol
        }

        @JvmStatic
        fun valueOf(ordinal: Int): Keycode {
            return runCatching {
                values()[ordinal]
            }.getOrDefault(VoidSymbol)
        }

        @JvmStatic
        fun keyNameOf(ordinal: Int): String {
            return valueOf(ordinal).toString().substringAfter('_')
        }

        @JvmStatic
        fun keyCodeOf(name: String): Int {
            Timber.d("keyCodeOf(): name=$name")
            return fromString(name).ordinal
        }

        @JvmStatic
        fun parseSend(s: String): IntArray {
            val sends = IntArray(2)
            if (s.isEmpty()) return sends
            val keys = s.split('+')
            sends[0] = fromString(keys.last()).ordinal
            for (key in keys) {
                if (masks.containsKey(key)) {
                    sends[1] = sends[1] or (masks[key] ?: 0)
                }
            }
            return sends
        }
    }
}
