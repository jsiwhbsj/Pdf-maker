package com.example.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.localization.LocalAppStrings
import com.example.ui.components.ShimmerBar
import com.example.ui.theme.ChampagneGold
import com.example.ui.theme.ElectricViolet
import com.example.ui.theme.LuminousCyan
import com.example.ui.theme.ObsidianBlack
import com.example.ui.theme.OutfitDisplayFamily
import com.example.ui.theme.RoyalBlue
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(onSplashComplete: () -> Unit) {
    val strings = LocalAppStrings.current
    val logoScale = remember { Animatable(0.68f) }
    val contentAlpha = remember { Animatable(0f) }

    val infinite = rememberInfiniteTransition(label = "splash_ring")
    val ringRotation by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(3800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ring_rot"
    )
    val pulse by infinite.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "halo_pulse"
    )

    LaunchedEffect(Unit) {
        contentAlpha.animateTo(1f, animationSpec = tween(550))
        logoScale.animateTo(
            1f,
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)
        )
        delay(1250)
        onSplashComplete()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBlack)
            .testTag("splash_screen"),
        contentAlignment = Alignment.Center
    ) {
        // Ambient radial glows
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(ElectricViolet.copy(alpha = 0.28f), Color.Transparent)
                ),
                radius = size.minDimension * 0.6f * pulse,
                center = center
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(LuminousCyan.copy(alpha = 0.16f), Color.Transparent)
                ),
                radius = size.minDimension * 0.44f,
                center = center
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .alpha(contentAlpha.value)
                .padding(horizontal = 32.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(164.dp)
                    .graphicsLayer {
                        scaleX = logoScale.value
                        scaleY = logoScale.value
                    },
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    rotate(ringRotation) {
                        drawArc(
                            brush = Brush.sweepGradient(
                                listOf(ElectricViolet, RoyalBlue, LuminousCyan, ChampagneGold, ElectricViolet)
                            ),
                            startAngle = 0f,
                            sweepAngle = 315f,
                            useCenter = false,
                            style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }
                }

                Image(
                    painter = painterResource(id = R.drawable.img_pdf_plus_logo),
                    contentDescription = strings.appName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(136.dp)
                        .shadow(24.dp, RoundedCornerShape(32.dp), spotColor = ElectricViolet)
                        .clip(RoundedCornerShape(32.dp))
                        .border(1.5.dp, Color.White.copy(alpha = 0.24f), RoundedCornerShape(32.dp))
                )
            }

            Spacer(Modifier.height(28.dp))

            Text(
                text = strings.appName,
                style = TextStyle(
                    fontFamily = OutfitDisplayFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 38.sp,
                    letterSpacing = 2.sp,
                    brush = Brush.horizontalGradient(
                        listOf(Color.White, LuminousCyan, ChampagneGold)
                    )
                )
            )

            Spacer(Modifier.height(6.dp))

            Text(
                text = strings.tagline,
                style = MaterialTheme.typography.titleMedium,
                color = Color(0xFFB5B9D6)
            )

            Spacer(Modifier.height(34.dp))

            ShimmerBar(
                modifier = Modifier.width(180.dp),
                height = 5.dp
            )
        }
    }
}
