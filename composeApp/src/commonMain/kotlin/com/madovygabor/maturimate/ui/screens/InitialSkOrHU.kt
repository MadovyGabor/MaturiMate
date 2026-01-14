package com.madovygabor.maturimate.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.example.compose.AppTheme
import org.jetbrains.compose.ui.tooling.preview.Preview


@Composable
fun InitialSkOrHU(
    onSkSelected: () -> Unit,
    onHuSelected: () -> Unit
) {
    AppTheme {

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(16.dp),

        ){
            Column(
                modifier = Modifier.fillMaxWidth()
            ){
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    )
                    {
                        Text(
                            text = "Udvözöljük az alkalmazásban!",
                            fontStyle = MaterialTheme.typography.headlineLarge.fontStyle,
                            color = MaterialTheme.colorScheme.onBackground

                        )
                        Text(
                            text = "Valasztja ki a tagozatát ",
                            fontStyle = MaterialTheme.typography.bodyLarge.fontStyle,
                            color = MaterialTheme.colorScheme.onBackground

                        )
                    }

                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxSize(.5f)
                        .clip(MaterialTheme.shapes.medium)
                        //.border(width = 2.dp, color = MaterialTheme.colorScheme.primary, shape = MaterialTheme.shapes.medium)
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .clickable() {
                            onHuSelected()
                        },

                    contentAlignment = Alignment.Center,
                ){
                    Text(
                        modifier = Modifier
                            .clip(MaterialTheme.shapes.small)
                            .background(MaterialTheme.colorScheme.primary)
                            .padding(16.dp),
                        text = "Magyar tagozat",
                        fontStyle =  MaterialTheme.typography.bodyLarge.fontStyle,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 8.dp)
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(MaterialTheme.shapes.medium)
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .clickable() {
                            onSkSelected()
                        },
                    contentAlignment = Alignment.Center
                ){
                    Text(
                        modifier = Modifier
                            .clip(MaterialTheme.shapes.small)
                            .background(MaterialTheme.colorScheme.primary)
                            .padding(16.dp),
                        text = "Szlovák tagozat",
                        fontStyle =  MaterialTheme.typography.bodyLarge.fontStyle,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }



            }



        }

    }
}


@Preview(showBackground = true)
@Composable
fun InitialSkOrHU() {
    InitialSkOrHU(
        onSkSelected = {},
        onHuSelected = {}
    )
}

