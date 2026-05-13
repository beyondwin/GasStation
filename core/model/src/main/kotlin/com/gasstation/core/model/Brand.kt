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
        fun fromCode(code: String): Brand = entries.firstOrNull { it.name == code } ?: ETC
    }
}
