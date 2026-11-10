package com.example.findme.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.findme.R
import com.example.findme.ui.theme.*

@Composable
fun BrandMark(modifier: Modifier = Modifier) {
    Image(painterResource(R.drawable.findme_mark), contentDescription = "FindMe",
        modifier = modifier.clip(RoundedCornerShape(18.dp)))
}

/** Decorative curves echo the eye mark without implying a live location. */
@Composable
fun BrandContour(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val upper = androidx.compose.ui.graphics.Path().apply {
            moveTo(0f, size.height * .7f)
            cubicTo(size.width * .3f, size.height * .1f, size.width * .75f, 0f, size.width, size.height * .45f)
        }
        val lower = androidx.compose.ui.graphics.Path().apply {
            moveTo(size.width * .2f, size.height * .8f)
            cubicTo(size.width * .5f, size.height * 1.1f, size.width * .8f, size.height, size.width, size.height * .7f)
        }
        drawPath(upper, TextPrimary, style = Stroke(6.dp.toPx(), cap = StrokeCap.Round))
        drawPath(lower, BrandBlue, style = Stroke(8.dp.toPx(), cap = StrokeCap.Round))
        drawCircle(TextPrimary, size.width * .08f, Offset(size.width * .8f, size.height * .55f))
    }
}

@Composable
fun BrandWelcome(eyebrow: String, title: String, subtitle: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(eyebrow.uppercase(), color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.labelSmall, letterSpacing = 1.2.sp)
        Text(title, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, color = TextPrimary)
        Text(subtitle, color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun ProfileAvatar(name: String, modifier: Modifier = Modifier) {
    Box(modifier.size(50.dp).background(
        Brush.linearGradient(listOf(BrandBlue, BrandSlate)), CircleShape)
        .padding(2.dp).background(DarkSurface, CircleShape), contentAlignment = Alignment.Center) {
        Text(name.take(1).uppercase(), color = TextPrimary, style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold)
    }
}
