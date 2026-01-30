package com.madovygabor.maturimate.features.appOnboarding.presentation.profileSetup.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.madovygabor.maturimate.features.appOnboarding.domain.MaturitaLanguage

@Composable
fun LanguageSelector(
    selectedLanguage: MaturitaLanguage,
    onLanguageSelected: (MaturitaLanguage) -> Unit
) {

    Row(
        modifier = Modifier
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.Center
    ){

        LanguageCard(
            name = "Magyar",
            flag = "🇭🇺", //TODO: Use image not emoji
            isSelected = selectedLanguage == MaturitaLanguage.HU,
            onClick = {
                onLanguageSelected(MaturitaLanguage.HU)
            },
            modifier = Modifier
                .weight(1f)
                .padding(end = 8.dp)
        )

        LanguageCard(
            name = "Szlovák",
            flag = "🇸🇰", //TODO: Use image not emoji
            isSelected = selectedLanguage == MaturitaLanguage.SK,
            onClick = { onLanguageSelected(MaturitaLanguage.SK) },
            modifier = Modifier
                .weight(1f)
                .padding(start = 8.dp)
        )
    }

}