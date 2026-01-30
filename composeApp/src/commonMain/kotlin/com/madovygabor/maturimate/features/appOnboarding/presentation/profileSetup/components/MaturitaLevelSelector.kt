package com.madovygabor.maturimate.features.appOnboarding.presentation.profileSetup.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.madovygabor.maturimate.features.appOnboarding.domain.MaturitaLevel
import com.madovygabor.maturimate.features.appOnboarding.presentation.profileSetup.toUiText

@Composable
fun MaturitaLevelSelector(
    selectedLevel: MaturitaLevel,
    onLevelSelected: (MaturitaLevel) -> Unit
) {
    val options = MaturitaLevel.entries

    SingleChoiceSegmentedButtonRow(
        modifier = Modifier
            .fillMaxWidth()
    ) {
        options.forEachIndexed { index, label ->
            SegmentedButton(
                shape = SegmentedButtonDefaults.itemShape(
                    index = index,
                    count = options.size
                ),
                selected = selectedLevel == label,
                onClick = {
                    onLevelSelected(label)
                },
                label = {
                    Text(label.toUiText().asString())
                }
            )
        }
    }
}