package com.example.data

import kotlinx.coroutines.flow.Flow

class ReportRepository(private val reportDao: ReportDao) {
    val allReports: Flow<List<Report>> = reportDao.getAllReports()

    suspend fun insert(report: Report): Long {
        return reportDao.insertReport(report)
    }

    suspend fun deleteById(id: Int) {
        reportDao.deleteReportById(id)
    }

    suspend fun getById(id: Int): Report? {
        return reportDao.getReportById(id)
    }
}
