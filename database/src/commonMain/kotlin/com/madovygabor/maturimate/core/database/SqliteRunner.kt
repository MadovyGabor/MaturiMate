package com.madovygabor.maturimate.core.database

/** Runs plain SQL on a SQLite file (`ATTACH` and the checks of the content swap); one implementation per platform. */
interface SqliteRunner {
    /** Opens the file, runs the statements in order outside any transaction, closes it. */
    fun execute(path: String, statements: List<String>)

    /** Runs a query and returns the first column of every row as text. */
    fun queryStrings(path: String, sql: String): List<String>
}
