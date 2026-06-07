package com.example.data

import kotlinx.coroutines.flow.Flow

class HostRuleRepository(private val dao: HostRuleDao) {
    val allRules: Flow<List<HostRule>> = dao.getAllRulesFlow()

    suspend fun getActiveRules(): List<HostRule> = dao.getActiveRules()

    suspend fun insert(rule: HostRule) = dao.insertRule(rule)

    suspend fun update(rule: HostRule) = dao.updateRule(rule)

    suspend fun delete(rule: HostRule) = dao.deleteRule(rule)

    suspend fun deleteById(id: Int) = dao.deleteRuleById(id)
}
