package com.osfans.trime.core

data class RimeResponse(
    val commit: RimeProto.Commit?,
    val context: RimeProto.Context?,
    val status: RimeProto.Status?,
)
