package dev.catsradar.ui.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.TextStyle

const val FlagTestTag = "flag"

// TalkBack would read the emoji as "flag: Spain" right before the name that already says Spain.
@Composable
fun Flag(flag: String, style: TextStyle, modifier: Modifier = Modifier) {
    Text(text = flag, style = style, modifier = modifier.clearAndSetSemantics { testTag = FlagTestTag })
}
