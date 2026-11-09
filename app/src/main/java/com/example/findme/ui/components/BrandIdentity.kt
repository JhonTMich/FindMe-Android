package com.example.findme.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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

/** Decorative orbit echoes the existing logo; it never represents someone's live location. */
@Composable
fun BrandOrbit(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val diameter = size.minDimension * .9f
        val origin = Offset((size.width - diameter) / 2, (size.height - diameter) / 2)
        drawArc(brush = Brush.sweepGradient(listOf(BrandCyan, BrandViolet, BrandBlue, BrandLime, BrandCyan)),
            startAngle = 48f, sweepAngle = 290f, useCenter = false, topLeft = origin,
            size = Size(diameter, diameter), style = Stroke(5.dp.toPx(), cap = StrokeCap.Round))
        drawCircle(BrandLime, 8.dp.toPx(), Offset(origin.x + diameter * .85f, origin.y + diameter * .85f))
        drawCircle(BrandCyan.copy(alpha = .12f), diameter * .36f,
            center = Offset(size.width / 2, size.height / 2), style = Stroke(1.dp.toPx()))
    }
}

@Composable
fun BrandWelcome(eyebrow: String, title: String, subtitle: String) {
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp))
        .background(Brush.linearGradient(listOf(DarkSurface, BrandViolet.copy(alpha = .22f), DarkCardBg)))
        .border(1.dp, DarkCardBorder, RoundedCornerShape(28.dp))) {
        BrandOrbit(Modifier.alpha(.22f).align(Alignment.TopEnd).offset(x = 72.dp, y = (-40).dp).size(240.dp))
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            BrandMark(Modifier.size(64.dp))
            Text(eyebrow.uppercase(), color = BrandLime, style = MaterialTheme.typography.labelSmall,
                letterSpacing = 1.5.sp)
            Text(title, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold,
                color = TextPrimary)
            Text(subtitle, color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun OrbitAvatar(name: String, modifier: Modifier = Modifier) {
    Box(modifier.size(50.dp).background(
        Brush.sweepGradient(listOf(BrandCyan, BrandViolet, BrandBlue, BrandLime, BrandCyan)), CircleShape)
        .padding(2.dp).background(DarkSurface, CircleShape), contentAlignment = Alignment.Center) {
        Text(name.take(1).uppercase(), color = TextPrimary, style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold)
    }
}
