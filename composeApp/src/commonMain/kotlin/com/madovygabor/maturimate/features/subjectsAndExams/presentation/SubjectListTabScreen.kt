package com.madovygabor.maturimate.features.subjectsAndExams.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.madovygabor.maturimate.core.presentation.theme.AppTheme
import org.jetbrains.compose.ui.tooling.preview.Preview


private val subjects = listOf(
    Subject("MATH","Matematika", Icons.Outlined.Calculate, Color(0xFF4B88E0)),
    Subject("ENGLISH","Angol nyelv", Icons.Outlined.Language, Color(0xFF4CA16A), level = "B2"),
    Subject("HUNGARIAN","Magyar nyelv és irodalom", Icons.AutoMirrored.Outlined.MenuBook, Color(0xFFFFB74D)),
    Subject("SLOVAK","Szlovák nyelv és irodalom", Icons.Outlined.DriveFileRenameOutline, Color(0xFFEA5D56))
    //Icons.Outlined.DriveFileRenameOutline, Icons.Outlined.EditNote
)

@Composable
fun SubjectListTabScreen(
    onSubjectClick: (String) -> Unit
) {

   Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
       )
   {

       Text(
           text = "Tantárgyak",
           style = MaterialTheme.typography.headlineMedium,
           color = MaterialTheme.colorScheme.onBackground,
           fontWeight = FontWeight.Bold,
           modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp)
       )

        subjects.forEach { subject ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(bottom = 8.dp)
                    .clickable { onSubjectClick(subject.id) }
            ){
                SubjectCard(
                    subject = subject,
                )
            }

        }
   }
}

data class Subject(
    val id: String,
    val name: String,
    val icon: ImageVector,
    val color: Color,
    val level: String? = null //For English levels like A1, B2, etc.
)

@Composable
fun SubjectCard(
    subject: Subject,
) {

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {

            // Upper colored bar per the subject color
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .fillMaxWidth(0.4f)
                    .height(12.dp)
                    .background(
                        subject.color,
                        shape = RoundedCornerShape(bottomStart = 8.dp)
                    )
            )

            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Ikon doboza
                Box(
                    modifier = Modifier
                        .padding(end = 16.dp)
                        .size(80.dp)
                        .background(subject.color.copy(alpha = 0.8f), RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = subject.icon, // Itt bármilyen ikont használhatsz
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.inverseSurface,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                    )
                }

                BasicText(
                    text = subject.name,
                    style = TextStyle(
                        fontStyle = MaterialTheme.typography.titleMedium.fontStyle,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                    maxLines = 2,
                    autoSize = TextAutoSize.StepBased(
                        minFontSize = 12.sp,
                        maxFontSize = 20.sp
                    )
                )

                Spacer(modifier = Modifier.weight(1f))

                // Szint jelző (B2), ha van
                if (subject.level != null){
                    Box(
                        modifier = Modifier
                            .background(
                                color = subject.color.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(8.dp)
                            )
                            .align(Alignment.Top),
                    ){

                        Text(
                            text = subject.level,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 18.sp
                        )
                    }
                }

            }
        }
    }
}

@Preview(showBackground = true)
@Preview(showBackground = true, widthDp = 1210, heightDp = 720)
@Composable
private fun ScreenPreview() {
    AppTheme(darkTheme = true) {
       SubjectListTabScreen(onSubjectClick = {})
    }
}