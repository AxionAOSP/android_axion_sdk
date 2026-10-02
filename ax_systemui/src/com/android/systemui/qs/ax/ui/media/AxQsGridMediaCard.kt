package com.android.systemui.qs.ax.ui.media

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import com.android.compose.animation.Expandable as ExpandableContainer
import com.android.compose.animation.rememberExpandableController
import com.android.systemui.media.remedia.domain.model.MediaSessionModel
import com.android.systemui.qs.ax.res.R
import com.android.systemui.qs.ax.shared.model.AxQsSpan
import com.android.systemui.qs.ax.ui.viewmodel.AxMediaViewModel
import com.android.systemui.res.R as SysuiR

@Composable
internal fun AxQsGridMediaCard(
    viewModel: AxMediaViewModel,
    session: MediaSessionModel?,
    lastMediaPackage: String?,
    span: AxQsSpan,
    interactive: Boolean,
    hasMultipleSessions: Boolean = false,
    modifier: Modifier = Modifier
) {
    val shape = AxMediaTokens.MediaCardShape
    val artwork = session?.background?.takeIf { span.columns > 1 }
    val theme = rememberAxQsGridMediaTheme(session = session)
    val clickLabel =
        if (session != null) {
            stringResource(
                SysuiR.string.controls_media_playing_item_description,
                session.title,
                session.subtitle,
                session.appName
            )
        } else if (lastMediaPackage != null) {
            stringResource(R.string.ax_qs_media_open_last_app)
        } else {
            null
        }
    val cardInteractionSource = remember { MutableInteractionSource() }
    val isCardPressed by cardInteractionSource.collectIsPressedAsState()
    val cardPressScale by
        animateFloatAsState(
            targetValue = if (isCardPressed) 0.985f else 1f,
            animationSpec = AxMediaTokens.ButtonPressSpring,
            label = "AxQsGridMediaCardPressScale"
        )

    ExpandableContainer(
        controller = rememberExpandableController(color = { theme.containerBackground }, shape = shape),
        modifier =
            modifier
                .graphicsLayer {
                    scaleX = cardPressScale
                    scaleY = cardPressScale
                }
                .fillMaxSize()
                .clip(shape),
        onClick = null,
        onClickLabel = null,
        defaultMinSize = false,
        useModifierBasedImplementation = true
    ) { expandable ->
        Box(
            Modifier.fillMaxSize().combinedClickable(
                interactionSource = cardInteractionSource,
                indication = null,
                enabled = interactive && (session != null || lastMediaPackage != null),
                onClick = {
                    if (session != null) {
                        viewModel.openSession(session, expandable)
                    } else {
                        viewModel.openLastMediaApp(expandable)
                    }
                },
                onClickLabel = clickLabel,
                onLongClick = null
            )
        ) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val gridConfig =
                    remember(span, maxHeight) {
                        AxQsMediaGridLayoutConfig.resolve(span, maxHeight)
                    }
                val songKey =
                    session?.let { "${it.key}:${it.appName}:${it.title}:${it.subtitle}" }.orEmpty()
                when (gridConfig.variant) {
                    AxQsMediaLayoutVariant.AxStudio4x2 ->
                        AxStudio4x2MediaContent(
                            session = session,
                            viewModel = viewModel,
                            colors = theme.colors,
                            artwork = artwork,
                            songKey = songKey,
                            interactive = interactive,
                            hasMultipleSessions = hasMultipleSessions
                        )
                    AxQsMediaLayoutVariant.AxSquare2x2 ->
                        AxSquare2x2MediaContent(
                            session = session,
                            viewModel = viewModel,
                            colors = theme.colors,
                            artwork = artwork,
                            songKey = songKey,
                            interactive = interactive,
                            hasMultipleSessions = hasMultipleSessions
                        )
                    AxQsMediaLayoutVariant.AxCompact,
                    AxQsMediaLayoutVariant.AxHalfRow2x1 ->
                        AxOneRowMediaContent(
                            session = session,
                            viewModel = viewModel,
                            colors = theme.colors,
                            artwork = artwork,
                            songKey = songKey,
                            config = gridConfig,
                            interactive = interactive,
                            hasMultipleSessions = hasMultipleSessions
                        )
                }
            }
        }
    }
}
