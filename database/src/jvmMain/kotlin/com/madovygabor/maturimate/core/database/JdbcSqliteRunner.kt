package com.madovygabor.maturimate.core.database

import java.sql.DriverManager

class JdbcSqliteRunner : SqliteRunner {
    override fun execute(path: String, statements: List<String>) {
        DriverManager.getConnection("jdbc:sqlite:$path").use { c ->
            c.createStatement().use { s -> statements.forEach { s.execute(it) } }
        }
    }

    override fun queryStrings(path: String, sql: String): List<String> =
        DriverManager.getConnection("jdbc:sqlite:$path").use { c ->
            c.createStatement().use { s ->
                s.executeQuery(sql).use { rows ->
                    generateSequence { if (rows.next()) rows.getString(1) else null }.toList()
                }
            }
        }
}
