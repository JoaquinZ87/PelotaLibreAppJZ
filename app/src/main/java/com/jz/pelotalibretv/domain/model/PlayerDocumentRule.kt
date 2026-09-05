package com.jz.pelotalibretv.domain.model

data class PlayerDocumentRule(
    val parentHost: String,
    val playerHost: String,
    val playerPath: String,
    val referer: String
)
