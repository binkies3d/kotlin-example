package com.example.kotlinexample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.kotlinexample.ui.theme.KotlinExampleTheme

/**
 * The product variants the app offers. [productId] is the Binkies content id that is sent to
 * the viewer as the "product" setting, the rest is only used by the app's own UI.
 */
enum class ProductColor(val label: String, val swatch: Color, val productId: String) {
    Cream("Cream", Color(0xFFF1E9D6), "samsung-galaxy-z-flip8-cream"),
    Mint("Mint", Color(0xFFBFE3D0), "samsung-galaxy-z-flip8-mint"),

    // No 3D model exists for this content id, so the page answers with "hide"
    Pink("Pink", Color(0xFFF4C6D2), "samsung-galaxy-z-flip8-pink"),
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            KotlinExampleTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    ProductScreen(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

/**
 * Example product page: the 3D viewer on top, the color selector below it.
 * Selecting a color only changes [BinkiesViewer]'s product, the WebView stays loaded.
 */
@Composable
fun ProductScreen(modifier: Modifier = Modifier) {
    var selected by rememberSaveable { mutableStateOf(ProductColor.Cream) }
    // null until the page sends its first "show" or "hide"
    var visible by remember { mutableStateOf<Boolean?>(null) }

    Column(modifier = modifier.fillMaxSize()) {
        // Stays collapsed until the page reports that the selected product has a 3D model
        BinkiesViewer(
            settings = BinkiesSettings(
                product = selected.productId,
                // Optional, these are the defaults
                thumbnailsPlacement = ThumbnailsPlacement.Bottom,
                thumbnailsSelectedColor = Color(0xFF262626),
            ),
            visible = visible == true,
            onVisibilityChange = { visible = it },
        )

        Spacer(Modifier.height(16.dp))
        Text(
            text = "Color: ${selected.label}" + if (visible == false) " (no 3D)" else "",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Spacer(Modifier.height(12.dp))
        ColorSelector(
            selected = selected,
            onSelect = { selected = it },
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }
}

/** Row of round color swatches, the selected one gets a border. */
@Composable
fun ColorSelector(selected: ProductColor, onSelect: (ProductColor) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        ProductColor.entries.forEach { color ->
            val borderColor = if (color == selected) MaterialTheme.colorScheme.onSurface else Color.Transparent
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .border(2.dp, borderColor, CircleShape)
                    .padding(4.dp)
                    .clip(CircleShape)
                    .background(color.swatch)
                    .clickable { onSelect(color) },
            )
        }
    }
}
