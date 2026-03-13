package com.example.newstart.core.model

data class ApiEnvelope<T>(
    val code: Int,
    val message: String,
    val data: T?,
    val traceId: String,
)
