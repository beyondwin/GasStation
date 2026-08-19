package com.gasstation.core.model

public enum class Brand {
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

    public companion object {
        private val BY_NAME = entries.associateBy(Brand::name)

        public fun fromCode(code: String): Brand = BY_NAME[code] ?: ETC
    }
}
