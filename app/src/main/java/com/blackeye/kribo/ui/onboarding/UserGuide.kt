package com.blackeye.kribo.ui.onboarding

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LaptopMac
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.blackeye.kribo.R
import com.blackeye.kribo.ui.components.AppModal
import com.blackeye.kribo.ui.components.AppScreen
import com.blackeye.kribo.ui.components.StatusPill
import kotlinx.coroutines.launch

private data class GuidePage(
    val drawableRes: Int,
    val titleRes: Int,
    val bodyRes: Int,
)

/**
 * One manual topic. [drawableName] is looked up safely in `res/drawable-nodpi/`; when the
 * optional WebP is not present the card falls back to a compact native Compose visual.
 */
enum class GuideTopic(
    val drawableRes: Int,
    val titleRes: Int,
    val introRes: Int,
    val items: List<Int>,
    val icon: ImageVector,
    val pro: Boolean = false,
) {
    WELCOME(
        drawableRes = R.drawable.guide_onboarding,
        titleRes = R.string.guide_topic_welcome_title,
        introRes = R.string.guide_topic_welcome_intro,
        items = listOf(
            R.string.guide_topic_welcome_item_1,
            R.string.guide_topic_welcome_item_2,
            R.string.guide_topic_welcome_item_3,
        ),
        icon = Icons.AutoMirrored.Rounded.Notes,
    ),
    HOME(
        drawableRes = R.drawable.guide_home,
        titleRes = R.string.guide_topic_home_title,
        introRes = R.string.guide_topic_home_intro,
        items = listOf(
            R.string.guide_topic_home_item_1,
            R.string.guide_topic_home_item_2,
            R.string.guide_topic_home_item_3,
        ),
        icon = Icons.Rounded.Home,
    ),
    EDITOR_BLOCKS(
        drawableRes = R.drawable.guide_editor_flow,
        titleRes = R.string.guide_topic_editor_title,
        introRes = R.string.guide_topic_editor_intro,
        items = listOf(
            R.string.guide_topic_editor_item_1,
            R.string.guide_topic_editor_item_2,
            R.string.guide_topic_editor_item_3,
            R.string.guide_topic_editor_item_4,
        ),
        icon = Icons.Rounded.Edit,
    ),
    VOICE_DICTATION(
        drawableRes = R.drawable.guide_voice_dictation,
        titleRes = R.string.guide_topic_voice_title,
        introRes = R.string.guide_topic_voice_intro,
        items = listOf(
            R.string.guide_topic_voice_item_1,
            R.string.guide_topic_voice_item_2,
            R.string.guide_topic_voice_item_3,
        ),
        icon = Icons.Rounded.Mic,
    ),
    OCR_TIPS(
        drawableRes = R.drawable.guide_ocr_tips,
        titleRes = R.string.guide_topic_ocr_title,
        introRes = R.string.guide_topic_ocr_intro,
        items = listOf(
            R.string.guide_topic_ocr_do_1,
            R.string.guide_topic_ocr_do_2,
            R.string.guide_topic_ocr_avoid_1,
            R.string.guide_topic_ocr_avoid_2,
        ),
        icon = Icons.Rounded.DocumentScanner,
    ),
    WIFI_TRANSFER(
        drawableRes = R.drawable.guide_wifi_transfer,
        titleRes = R.string.guide_topic_wifi_title,
        introRes = R.string.guide_topic_wifi_intro,
        items = listOf(
            R.string.guide_topic_wifi_step_1,
            R.string.guide_topic_wifi_step_2,
            R.string.guide_topic_wifi_step_3,
            R.string.guide_topic_wifi_step_4,
        ),
        icon = Icons.Rounded.Wifi,
        pro = true,
    ),
    GOOGLE_DRIVE(
        drawableRes = R.drawable.guide_google_drive,
        titleRes = R.string.guide_topic_drive_title,
        introRes = R.string.guide_topic_drive_intro,
        items = listOf(
            R.string.guide_topic_drive_sync_now,
            R.string.guide_topic_drive_automatic,
            R.string.guide_topic_drive_wifi_only,
            R.string.guide_topic_drive_restore,
            R.string.guide_topic_drive_privacy,
            R.string.guide_topic_drive_web,
        ),
        icon = Icons.Rounded.Cloud,
        pro = true,
    ),
    WEB_AUTOSYNC(
        drawableRes = R.drawable.guide_web_autosync,
        titleRes = R.string.guide_topic_web_autosync_title,
        introRes = R.string.guide_topic_web_autosync_intro,
        items = listOf(
            R.string.guide_topic_web_autosync_item_1,
            R.string.guide_topic_web_autosync_item_2,
            R.string.guide_topic_web_autosync_item_3,
            R.string.guide_topic_web_autosync_item_4,
        ),
        icon = Icons.Rounded.LaptopMac,
        pro = true,
    ),
    EXPORT_PDF(
        drawableRes = R.drawable.guide_export_pdf,
        titleRes = R.string.guide_topic_export_title,
        introRes = R.string.guide_topic_export_intro,
        items = listOf(
            R.string.guide_topic_export_item_1,
            R.string.guide_topic_export_item_2,
            R.string.guide_topic_export_item_3,
            R.string.guide_topic_export_item_4,
            R.string.guide_topic_export_item_5,
        ),
        icon = Icons.Rounded.PictureAsPdf,
    ),
}

/** First-run, swipeable welcome guide built from native Compose cards (zero extra assets). */
@Composable
fun UserGuideScreen(onFinish: () -> Unit) {
    val pages = listOf(
        GuidePage(
            R.drawable.onboarding_offline_notes,
            R.string.guide_page_1_title,
            R.string.guide_page_1_body,
        ),
        GuidePage(
            R.drawable.onboarding_ocr_scan,
            R.string.guide_page_2_title,
            R.string.guide_page_2_body,
        ),
        GuidePage(
            R.drawable.onboarding_export_free,
            R.string.guide_page_3_title,
            R.string.guide_page_3_body,
        ),
        GuidePage(R.drawable.onboarding_pro_lifetime, R.string.guide_page_4_title, R.string.guide_page_4_body),
    )
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()
    val isLast = pagerState.currentPage == pages.lastIndex

    AppScreen(
        title = stringResource(R.string.guide_title),
        scrollable = false,
        actions = {
            TextButton(
                onClick = onFinish,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("guide-skip"),
            ) {
                Text(
                    text = stringResource(R.string.guide_skip),
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        footer = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    pages.indices.forEach { index ->
                        Box(
                            modifier = Modifier
                                .size(if (index == pagerState.currentPage) 10.dp else 7.dp)
                                .background(
                                    color = if (index == pagerState.currentPage) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
                                    },
                                    shape = CircleShape,
                                ),
                        )
                    }
                }
                Button(
                    onClick = {
                        if (isLast) {
                            onFinish()
                        } else {
                            scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                        }
                    },
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("guide-next"),
                ) {
                    Text(
                        text = stringResource(
                            if (isLast) R.string.guide_start else R.string.guide_next,
                        ),
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        },
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .testTag("guide-pager"),
        ) { pageIndex ->
            val page = pages[pageIndex]
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Image(
                    painter = painterResource(page.drawableRes),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(16.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp)),
                    contentScale = ContentScale.Crop,
                )
                Spacer(Modifier.height(24.dp))
                Text(
                    text = stringResource(page.titleRes),
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(page.bodyRes),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Full reference manual: nine illustrated cards plus the Free / Pro summary. */
@Composable
fun UserManualScreen(onBack: () -> Unit) {
    AppScreen(
        title = stringResource(R.string.manual_title),
        onBack = onBack,
    ) {
        Text(
            text = stringResource(R.string.manual_intro),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))

        GuideTopic.entries.forEach { topic ->
            GuideTopicCard(topic = topic)
        }

        PlanSummaryCard()
    }
}

@Composable
fun GuideTopicCard(
    topic: GuideTopic,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
            .testTag("guide-card-${topic.name.lowercase()}"),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
    ) {
        Column {
            GuideImageSlot(topic)
            GuideTopicBody(topic = topic, showHeader = true)
        }
    }
}

/** Modal card used by the `?` buttons on Wi-Fi and Google Drive without leaving the screen. */
@Composable
fun GuideTopicModal(
    topic: GuideTopic,
    onDismiss: () -> Unit,
) {
    AppModal(
        onDismissRequest = onDismiss,
        title = stringResource(topic.titleRes),
        dismissLabel = stringResource(R.string.action_close),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            GuideImageSlot(topic)
            GuideTopicBody(topic = topic, showHeader = false)
        }
    }
}

@Composable
private fun GuideTopicBody(
    topic: GuideTopic,
    showHeader: Boolean,
) {
    Column(modifier = Modifier.padding(16.dp)) {
        if (showHeader) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .background(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            RoundedCornerShape(12.dp),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = topic.icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    text = stringResource(topic.titleRes),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (topic.pro) {
                    Spacer(Modifier.width(8.dp))
                    StatusPill(text = stringResource(R.string.settings_pro_label))
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        Text(
            text = stringResource(topic.introRes),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        topic.items.forEach { itemRes ->
            BulletLine(text = stringResource(itemRes))
        }
    }
}

@Composable
private fun BulletLine(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .padding(top = 7.dp)
                .size(5.dp)
                .background(MaterialTheme.colorScheme.primary, CircleShape),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Renders the static WebP illustration in `drawable-nodpi` for each manual card:
 * `guide_onboarding`, `guide_home`, `guide_editor_flow`, `guide_voice_dictation`,
 * `guide_ocr_tips`, `guide_wifi_transfer`, `guide_google_drive`, `guide_web_autosync`,
 * and `guide_export_pdf`.
 */
@Composable
private fun GuideImageSlot(topic: GuideTopic) {
    // Static R references keep the WebP files alive and correctly remapped when release
    // resource shrinking rewrites resource names (dynamic getIdentifier would break).
    Image(
        painter = painterResource(topic.drawableRes),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)),
    )
}

@Composable
private fun PlanSummaryCard() {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.manual_plans_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.manual_free_title),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            listOf(
                R.string.manual_free_notes,
                R.string.manual_free_themes,
                R.string.manual_free_import,
                R.string.manual_free_export,
                R.string.manual_free_ocr,
            ).forEach { BulletLine(stringResource(it)) }
            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.28f))
            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.manual_pro_title),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            listOf(
                R.string.manual_pro_payment,
                R.string.manual_pro_ocr,
                R.string.manual_pro_themes,
                R.string.manual_pro_sync,
                R.string.manual_pro_extras,
            ).forEach { BulletLine(stringResource(it)) }
        }
    }
}
