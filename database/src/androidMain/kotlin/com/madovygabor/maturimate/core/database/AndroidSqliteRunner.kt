package com.madovygabor.maturimate.core.database

import android.database.sqlite.SQLiteDatabase

class AndroidSqliteRunner : SqliteRunner {
    override fun execute(path: String, statements: List<String>) {
        val db = SQLiteDatabase.openDatabase(path, null, SQLiteDatabase.OPEN_READWRITE)
        try {
            statements.forEach { db.execSQL(it) }
        } finally {
            db.close()
        }
    }

    override fun queryStrings(path: String, sql: String): List<String> {
        val db = SQLiteDatabase.openDatabase(path, null, SQLiteDatabase.OPEN_READONLY)
        try {
            db.rawQuery(sql, null).use { cursor ->
                val result = mutableListOf<String>()
                while (cursor.moveToNext()) result += cursor.getString(0)
                return result
            }
        } finally {
            db.close()
        }
    }
}
