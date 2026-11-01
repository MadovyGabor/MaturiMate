package com.madovygabor.maturimate.features.appOnboarding.presentation.profileSetup.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.madovygabor.maturimate.features.userProfile.domain.TeachingLanguage

@Composable
fun LanguageSelector(
    selectedLanguage: TeachingLanguage,
    onLanguageSelected: (TeachingLanguage) -> Unit
) {

    Row(
        modifier = Modifier
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.Center
    ){

        LanguageCard(
            name = "Magyar",
            flag = "🇭🇺", //TODO: Use image not emoji
            isSelected = selectedLanguage == TeachingLanguage.HU,
            onClick = {
                onLanguageSelected(TeachingLanguage.HU)
            },
            modifier = Modifier
                .weight(1f)
                .padding(end = 8.dp)
        )

        LanguageCard(
            name = "Szlovák",
            flag = "🇸🇰", //TODO: Use image not emoji
            isSelected = selectedLanguage == TeachingLanguage.SK,
            onClick = { onLanguageSelected(TeachingLanguage.SK) },
            modifier = Modifier
                .weight(1f)
                .padding(start = 8.dp)
        )
    }

}