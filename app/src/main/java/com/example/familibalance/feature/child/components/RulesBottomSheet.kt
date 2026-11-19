package com.example.familibalance.feature.child.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.content.pm.PackageManager
import android.widget.Toast
import android.media.AudioManager
import android.media.ToneGenerator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.ui.zIndex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.core.content.ContextCompat
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalHapticFeedback
import coil3.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.familibalance.*
import com.example.familibalance.data.model.*
import com.example.familibalance.data.storage.*
import com.example.familibalance.util.*
import com.example.familibalance.R
import com.example.familibalance.feature.child.QuestDeliveryScreen
import com.example.familibalance.feature.child.QuestDeliveryTarget
import com.example.familibalance.feature.child.components.*
import com.example.familibalance.ui.theme.*
import com.example.familibalance.ui.components.*
import com.example.familibalance.viewmodel.actions.*


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RulesBottomSheet(
    wardrobeReward: Int,
    roomReward: Int,
    onDismissRequest: () -> Unit
) {
    val offlineDesc = stringResource(R.string.a11y_offline_status)
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        containerColor = SpaceBgStart,
        scrimColor = Color.Black.copy(alpha = 0.6f),
        dragHandle = {
            BottomSheetDefaults.DragHandle(
                color = GlassStroke
            )
        }
    ) {
        AppLocaleContent {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = stringResource(R.string.rules_title),
                color = AccentColor,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, AccentColor.copy(alpha = 0.5f), RoundedCornerShape(12.dp)),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = AccentColor.copy(alpha = 0.08f))
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    EmojiIcon(
                        emoji = "📴",
                        size = 22.dp,
                        contentDescription = offlineDesc
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = stringResource(R.string.rules_offline),
                        color = TextSecondary,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            RuleBlock(
                title = stringResource(R.string.rules_balance_title),
                text = stringResource(R.string.rules_balance_text)
            )

            Spacer(modifier = Modifier.height(12.dp))

            RuleBlock(
                title = stringResource(R.string.rules_earn_title),
                text = stringResource(R.string.rules_earn_text, wardrobeReward, roomReward)
            )

            Spacer(modifier = Modifier.height(12.dp))

            RuleBlock(
                title = stringResource(R.string.rules_penalty_title),
                text = stringResource(R.string.rules_penalty_text)
            )

            Spacer(modifier = Modifier.height(12.dp))

            RuleBlock(
                title = stringResource(R.string.rules_streak_title),
                text = stringResource(R.string.rules_streak_text)
            )

            Spacer(modifier = Modifier.height(12.dp))

            RuleBlock(
                title = stringResource(R.string.rules_screentime_title),
                text = stringResource(R.string.rules_screentime_text)
            )

            Spacer(modifier = Modifier.height(12.dp))

            RuleBlock(
                title = stringResource(R.string.rules_honest_title),
                text = stringResource(R.string.rules_honest_text)
            )

            Spacer(modifier = Modifier.height(12.dp))

            RuleBlock(
                title = stringResource(R.string.rules_diary_title),
                text = stringResource(R.string.rules_diary_text)
            )

            Spacer(modifier = Modifier.height(12.dp))

            RuleBlock(
                title = stringResource(R.string.rules_achievements_title),
                text = stringResource(R.string.rules_achievements_text)
            )

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = onDismissRequest,
                colors = ButtonDefaults.buttonColors(
                    containerColor = AccentColor,
                    contentColor = SpaceBgStart
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.rules_understood), fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
        }
    }
}

@Composable
private fun RuleBlock(
    title: String,
    text: String
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, GlassStroke, RoundedCornerShape(12.dp)),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = GlassBackground)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = title,
                color = TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = text,
                color = TextMuted,
                fontSize = 14.sp,
                lineHeight = 20.sp
            )
        }
    }
}
