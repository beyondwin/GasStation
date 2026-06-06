package com.gasstation.core.model

enum class Brand {
    SKE,
    GSC,
    HDO,
    SOL,
    RTO,
    RTX,
    NHO,
    ETC,
    E1G,
    SKG,
    ;

    companion object {
        private val BY_NAME = entries.associateBy(Brand::name)

        fun fromCode(code: String): Brand = BY_NAME[code] ?: ETC
    }
}
