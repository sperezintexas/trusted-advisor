package com.atxbogart.trustedadvisor.service

class TokenLimitExceededException(
    val role: String,
    val used: Long,
    val limit: Long
) : RuntimeException("Daily token limit reached for role $role: $used/$limit")
