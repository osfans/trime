package com.osfans.trime.ime.t9

object T9PinYin {

    private val t9KeyArray = CharArray(128) { idx -> idx.toChar() }

    init {
        val rangeToKey = listOf(
            'a'..'c' to '2',
            'd'..'f' to '3',
            'g'..'i' to '4',
            'j'..'l' to '5',
            'm'..'o' to '6',
            'p'..'s' to '7',
            't'..'v' to '8',
            'w'..'z' to '9',
        )
        for ((range, key) in rangeToKey) {
            for (c in range) {
                t9KeyArray[c.code] = key
                t9KeyArray[c.uppercaseChar().code] = key
            }
        }
        for (d in '2'..'9') {
            t9KeyArray[d.code] = d
        }
    }

    private val allPinyin = setOf(
        "a", "ai", "an", "ang", "ao",
        "b", "ba", "bai", "ban", "bang", "bao", "bei", "ben", "beng", "bi",
        "bian", "biao", "bie", "bin", "bing", "bo", "bu", "biang",
        "c", "ca", "cai", "can", "cang", "cao", "ce", "cen", "ceng",
        "cha", "chai", "chan", "chang", "chao", "che", "chen", "cheng", "chi",
        "chong", "chou", "chu", "chua", "chuai", "chuan", "chuang", "chui", "chun", "chuo",
        "ci", "cong", "cou", "cu", "cuan", "cui", "cun", "cuo",
        "d", "da", "dai", "dan", "dang", "dao", "de", "dei", "den", "deng",
        "di", "dia", "dian", "diao", "die", "ding", "diu", "dong", "dou", "du",
        "duan", "dui", "dun", "duo",
        "e", "ei", "en", "eng", "er",
        "f", "fa", "fan", "fang", "fei", "fen", "feng", "fo", "fou", "fu",
        "g", "ga", "gai", "gan", "gang", "gao", "ge", "gei", "gen", "geng",
        "gong", "gou", "gu", "gua", "guai", "guan", "guang", "gui", "gun", "guo",
        "h", "ha", "hai", "han", "hang", "hao", "he", "hei", "hen", "heng",
        "hong", "hou", "hu", "hua", "huai", "huan", "huang", "hui", "hun", "huo",
        "i",
        "j", "ji", "jia", "jian", "jiang", "jiao", "jie", "jin", "jing",
        "jiong", "jiu", "ju", "juan", "jue", "jun",
        "k", "ka", "kai", "kan", "kang", "kao", "ke", "ken", "keng",
        "kong", "kou", "ku", "kua", "kuai", "kuan", "kuang", "kui", "kun", "kuo",
        "l", "la", "lai", "lan", "lang", "lao", "le", "lei", "leng",
        "li", "lia", "lian", "liang", "liao", "lie", "lin", "ling",
        "liu", "long", "lou", "lu", "luan", "lun", "luo", "lv", "lo", "lve",
        "m", "ma", "mai", "man", "mang", "mao", "me", "mei", "men", "meng",
        "mi", "mian", "miao", "mie", "min", "ming", "miu", "mo", "mou", "mu",
        "n", "na", "nai", "nan", "nang", "nao", "ne", "nei", "nen", "neng",
        "ni", "nian", "niang", "niao", "nie", "nin", "ning", "niu",
        "nong", "nou", "nu", "nuan", "nuo", "nv", "nve",
        "o", "ou",
        "p", "pa", "pai", "pan", "pang", "pao", "pei", "pen", "peng",
        "pi", "pian", "piao", "pie", "pin", "ping", "po", "pou", "pu",
        "q", "qi", "qia", "qian", "qiang", "qiao", "qie", "qin", "qing",
        "qiong", "qiu", "qu", "quan", "que", "qun",
        "r", "ran", "rang", "rao", "re", "ren", "reng", "ri", "rong",
        "rou", "ru", "rua", "ruan", "rui", "run", "ruo",
        "s", "sa", "sai", "san", "sang", "sao", "se", "sen", "seng",
        "sha", "shai", "shan", "shang", "shao", "she", "shei", "shen", "sheng", "shi",
        "shou", "shu", "shua", "shuai", "shuan", "shuang", "shui", "shun", "shuo",
        "si", "song", "sou", "su", "suan", "sui", "sun", "suo",
        "t", "ta", "tai", "tan", "tang", "tao", "te", "teng",
        "ti", "tian", "tiao", "tie", "ting", "tong", "tou", "tu",
        "tuan", "tui", "tun", "tuo",
        "u", "v",
        "w", "wa", "wai", "wan", "wang", "wei", "wen", "weng", "wo", "wu",
        "x", "xi", "xia", "xian", "xiang", "xiao", "xie", "xin", "xing",
        "xiong", "xiu", "xu", "xuan", "xue", "xun",
        "y", "ya", "yan", "yang", "yao", "ye", "yi", "yin", "ying",
        "yo", "yong", "you", "yu", "yuan", "yue", "yun",
        "z", "za", "zai", "zan", "zang", "zao", "ze", "zei", "zen", "zeng",
        "zha", "zhai", "zhan", "zhang", "zhao", "zhe", "zhen", "zheng", "zhi",
        "zhong", "zhou", "zhu", "zhua", "zhuai", "zhuan", "zhuang", "zhui", "zhun", "zhuo",
        "zi", "zong", "zou", "zu", "zuan", "zui", "zun", "zuo",
    )

    private val pinyinMap: Map<String, List<String>> = buildMap<String, MutableList<String>> {
        for (pinyin in allPinyin) {
            val chars = CharArray(pinyin.length)
            for (i in pinyin.indices) {
                chars[i] = t9KeyArray[pinyin[i].code]
            }
            val key = String(chars)
            getOrPut(key) { mutableListOf() }.add(pinyin)
        }
    }

    private val searchCache = object : LinkedHashMap<String, List<String>>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<String>>): Boolean = size > 32
    }

    fun possibleCombinations(sequence: String?): List<String> {
        if (sequence.isNullOrBlank()) return emptyList()

        searchCache[sequence]?.let { return it }

        val len = minOf(sequence.length, 6)
        val numChars = CharArray(len)
        for (i in 0 until len) {
            val c = sequence[i]
            numChars[i] = if (c.code < 128) t9KeyArray[c.code] else c
        }
        val numString = String(numChars)

        val result = mutableListOf<String>()
        for (length in numString.length downTo 1) {
            pinyinMap[numString.substring(0, length)]?.let { result.addAll(it) }
        }

        searchCache[sequence] = result
        return result
    }
}