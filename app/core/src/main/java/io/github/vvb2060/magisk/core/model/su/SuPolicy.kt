package io.github.vvb2060.magisk.core.model.su

import io.github.vvb2060.magisk.core.data.magiskdb.MagiskDB

class SuPolicy(
    val uid: Int,
    var policy: Int = QUERY,
    var remain: Long = -1L,
    var logging: Boolean = true,
    var notification: Boolean = true,
) {
    companion object {
        const val QUERY = 0
        const val DENY = 1
        const val ALLOW = 2
        const val RESTRICT = 3
    }

    fun toMap(): MutableMap<String, Any> {
        val until = if (remain <= 0) {
            0  // 永久授权使用 0，-1 会被 deleteOutdated() 删除
        } else {
            MagiskDB.Literal("(strftime(\"%s\", \"now\") + $remain)")
        }
        return mutableMapOf(
            "uid" to uid,
            "policy" to policy,
            "until" to until,
            "logging" to logging,
            "notification" to notification
        )
    }
}
