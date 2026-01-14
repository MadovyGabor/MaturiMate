package com.madovygabor.maturimate.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.compose.AppTheme
import org.jetbrains.compose.ui.tooling.preview.Preview


@Composable
fun NavBar(){
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp)
            .padding(bottom = 8.dp),
        contentAlignment = androidx.compose.ui.Alignment.BottomCenter
    ){
        HorizontalDivider(
            modifier = Modifier
                .fillMaxWidth()
                .align(androidx.compose.ui.Alignment.TopCenter),

        )
        Row(
            modifier = Modifier
                .padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ){
            IconButton(
                modifier = Modifier.size(45.dp),
                onClick = { /* TODO: Handle back navigation */ }
            ){
                Icon(
                    modifier = Modifier.size(45.dp),
                    imageVector = Icons.Outlined.Home,
                    contentDescription = "Search Icon",
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }
            IconButton(
                modifier = Modifier.size(45.dp),
                onClick = { /* TODO: Handle back navigation */ }
            ){
                Icon(
                    modifier = Modifier.size(45.dp),
                    imageVector = Icons.Outlined.AccountCircle,
                    contentDescription = "Search Icon",
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }

            IconButton(
                modifier = Modifier.size(45.dp),
                onClick = { /* TODO: Handle back navigation */ }
            ){
                Icon(
                    modifier = Modifier.size(45.dp),
                    imageVector = Icons.Outlined.Settings,
                    contentDescription = "Search Icon",
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }
        }

    }





}


@Preview(showBackground = true)
@Composable
private fun NavBarPreview(){
    AppTheme {
        NavBar()
    }

}