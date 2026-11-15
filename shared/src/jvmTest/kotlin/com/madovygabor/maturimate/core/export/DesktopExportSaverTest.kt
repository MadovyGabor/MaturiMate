package com.madovygabor.maturimate.core.export

import com.madovygabor.maturimate.features.auth.domain.ExportSaver
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class DesktopExportSaverTest {

    private val dir = kotlin.io.path.createTempDirectory("export-test").toFile()

    @Test
    fun theTextIsWrittenToTheChosenFileAndThePathIsReturned() = runBlocking {
        var suggested: String? = null
        val saver = DesktopExportSaver { name -> suggested = name; File(dir, "mine.json") }

        val where = saver.save("maturimate-adataim.json", "{\n  \"a\": 1\n}")

        assertEquals("maturimate-adataim.json", suggested)
        assertEquals(File(dir, "mine.json").absolutePath, where)
        assertEquals("{\n  \"a\": 1\n}", File(dir, "mine.json").readText())
    }

    @Test
    fun aClosedDialogWritesNothing() = runBlocking {
        val saver = DesktopExportSaver { null }

        assertEquals(ExportSaver.CANCELLED, saver.save("x.json", "secret"))
        assertFalse(dir.listFiles().orEmpty().any())
    }

    @Test
    fun aFileThatCannotBeWrittenIsReportedAsFailed() = runBlocking {
        val saver = DesktopExportSaver { File(dir, "no-such-folder/x.json") }

        assertNull(saver.save("x.json", "secret"))
    }
}
