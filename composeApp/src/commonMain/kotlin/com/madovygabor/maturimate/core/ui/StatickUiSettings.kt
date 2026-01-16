import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

// 1. Definiáljuk a modellt (id-val kiegészítve)
data class Subject(
    val id: String,
    val name: String,
    val icon: ImageVector,
    val color: Color,
    val level: String? = null
)

object SubjectStaticData {
    // 2. A Map, ami tárolja a fix adatokat
    private val configs = mapOf(
        "ENGLISH" to Subject(
            id = "ENGLISH",
            name = "Angol nyelv",
            icon = Icons.Outlined.Language,
            color = Color(0xFF4CA16A),
            level = "B2"
        ),
        "SLOVAK" to Subject(
            id = "SLOVAK",
            name = "Szlovák nyelv és irodalom",
            icon = Icons.Outlined.DriveFileRenameOutline,
            color = Color(0xFFEA5D56)
        ),
        "HUNGARIAN" to Subject(
            id = "HUNGARIAN",
            name = "Magyar nyelv és irodalom",
            icon = Icons.Outlined.MenuBook,
            color = Color(0xFFFFB74D)
        ),
        "MATH" to Subject(
            id = "MATH",
            name = "Matematika",
            icon = Icons.Outlined.Calculate,
            color = Color(0xFF4B88E0)
        )
    )

    // 3. Ezzel éred el egyenként az ID alapján
    fun getById(id: String): Subject {
        // Ha elírod az ID-t, ad egy alapértelmezett "Ismeretlen" tantárgyat, hogy ne szálljon el az app
        return configs[id] ?: Subject("UNKNOWN", "Ismeretlen", Icons.Outlined.MenuBook, Color.Gray)
    }

    // 4. Ezzel le tudod kérni az összeset egyszerre (pl. egy listához a főképernyőn)
    fun getAll(): List<Subject> = configs.values.toList()
}