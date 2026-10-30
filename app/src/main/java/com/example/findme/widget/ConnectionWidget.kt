@file:Suppress("RestrictedApi")

package com.example.findme.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.example.findme.MainActivity
import com.example.findme.R
import com.example.findme.data.local.dataStore
import com.example.findme.data.model.AccountProfile
import com.example.findme.data.model.ActivityCategory
import com.example.findme.data.repository.AccountRepository
import com.example.findme.data.repository.StatusRepository
import com.example.findme.domain.model.UserCurrentStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import java.time.ZoneId
import java.time.ZonedDateTime

data class ConnectionWidgetSelection(val personId: String, val messageId: String = "")

object ConnectionWidgetSettings {
    suspend fun save(context: Context, ownerId: String, personId: String, messageId: String = "") {
        context.dataStore.edit {
            it[stringPreferencesKey("connection_widget_owner")] = ownerId
            it[stringPreferencesKey("connection_widget_person")] = personId
            it[stringPreferencesKey("connection_widget_message")] = messageId
        }
        ConnectionWidget().updateAll(context)
    }

    suspend fun readPersonId(context: Context, ownerId: String): String? {
        val prefs = context.dataStore.data.first()
        if (prefs[stringPreferencesKey("connection_widget_owner")] != ownerId) return null
        return prefs[stringPreferencesKey("connection_widget_person")]
    }

    suspend fun read(context: Context, ownerId: String): ConnectionWidgetSelection? {
        val personId = readPersonId(context, ownerId) ?: return null
        val prefs = context.dataStore.data.first()
        val message = prefs[stringPreferencesKey("connection_widget_message")].orEmpty()
        return ConnectionWidgetSelection(personId, message)
    }
}

class ConnectionWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ConnectionWidget()
    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        GlanceWidgetWorker.enqueuePeriodicUpdate(context)
    }
}

private data class PersonCardState(
    val personName: String,
    val badgeText: String,
    val badgeColor: Color,
    val categoryIconText: String,
    val categorySubtitle: String,
    val categoryTitle: String,
    val cardBgColor: Color,
    val innerCardBgColor: Color,
    val illustrationResId: Int
)

private data class ConnectionWidgetSnapshot(
    val myState: PersonCardState,
    val otherState: PersonCardState?,
    val hasMultipleConnected: Boolean,
    val isUnlinked: Boolean = false
)

class ConnectionWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = try {
            withTimeout(15_000) {
                val repo = AccountRepository()
                repo.auth.awaitInitialization()
                val ownerId = repo.userId()
                val profiles = repo.profiles()

                val myProfile = profiles.firstOrNull { it.id == ownerId }
                    ?: AccountProfile(ownerId, "Tú", ZoneId.systemDefault().id)

                val otherProfiles = profiles.filter { it.id != ownerId }

                // AUTOMATIC ASSIGNMENT:
                // If saved person ID exists in current connections, use it.
                // Otherwise, automatically assign the single connected person (or 1st connected person).
                var selectedPersonId = ConnectionWidgetSettings.readPersonId(context, ownerId)

                if (selectedPersonId == null || otherProfiles.none { it.id == selectedPersonId }) {
                    if (otherProfiles.isNotEmpty()) {
                        selectedPersonId = otherProfiles.first().id
                        ConnectionWidgetSettings.save(context, ownerId, selectedPersonId)
                    }
                }

                val selectedProfile = otherProfiles.firstOrNull { it.id == selectedPersonId }

                val myState = buildPersonCardState(repo, myProfile, defaultName = "Tú")
                val otherState = selectedProfile?.let { buildPersonCardState(repo, it, defaultName = it.displayName) }

                ConnectionWidgetSnapshot(
                    myState = myState,
                    otherState = otherState,
                    hasMultipleConnected = otherProfiles.size > 1,
                    isUnlinked = otherProfiles.isEmpty()
                )
            }
        } catch (_: TimeoutCancellationException) {
            ConnectionWidgetSnapshot(
                myState = buildFallbackCardState("Tú", "Sin conexión reciente"),
                otherState = buildFallbackCardState("Conexión", "Pulsa para reintentar"),
                hasMultipleConnected = false
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            ConnectionWidgetSnapshot(
                myState = buildFallbackCardState("Tú", "Abre FindMe"),
                otherState = buildFallbackCardState("Amigo", "Inicia sesión en FindMe"),
                hasMultipleConnected = false,
                isUnlinked = true
            )
        }

        provideContent {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }

            Box(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(Color(0xFF0F172A))
                    .padding(8.dp)
            ) {
                Row(
                    modifier = GlanceModifier.fillMaxSize(),
                    verticalAlignment = Alignment.Vertical.CenterVertically,
                    horizontalAlignment = Alignment.Horizontal.CenterHorizontally
                ) {
                    // Left Card: "Tú"
                    Box(
                        modifier = GlanceModifier
                            .defaultWeight()
                            .fillMaxHeight()
                            .clickable(actionStartActivity(intent))
                    ) {
                        PersonCard(state = snapshot.myState)
                    }

                    Spacer(modifier = GlanceModifier.width(8.dp))

                    // Right Card: Connected Person ("Juan", "María", etc.) or Unlinked Promo
                    Box(
                        modifier = GlanceModifier
                            .defaultWeight()
                            .fillMaxHeight()
                            .clickable(
                                if (snapshot.hasMultipleConnected) {
                                    actionRunCallback<CycleConnectedPersonAction>()
                                } else {
                                    actionStartActivity(intent)
                                }
                            )
                    ) {
                        if (snapshot.otherState != null) {
                            PersonCard(state = snapshot.otherState)
                        } else {
                            UnlinkedPersonCard()
                        }
                    }
                }
            }
        }
    }

    private suspend fun buildPersonCardState(
        repo: AccountRepository,
        profile: AccountProfile,
        defaultName: String
    ): PersonCardState {
        val now = ZonedDateTime.now(runCatching { ZoneId.of(profile.timezone) }.getOrDefault(ZoneId.systemDefault()))
        val schedules = try { repo.schedules(profile.id) } catch (_: Exception) { emptyList() }
        val overrides = try { repo.overrides(profile.id) } catch (_: Exception) { emptyList() }

        val status = StatusRepository.calculateStatus(
            displayName = profile.displayName.ifBlank { defaultName },
            schedules = schedules,
            overrides = overrides,
            selectedDayOfWeek = now.dayOfWeek.value,
            nowInLima = now,
            manualStatusFirst = true
        )

        val displayName = profile.displayName.ifBlank { defaultName }.take(14)

        return when (status) {
            is UserCurrentStatus.InClass -> {
                val schedule = status.currentSchedule
                val category = ActivityCategory.fromKey(schedule.category, schedule.title)
                val titleClean = schedule.title.lowercase()

                val (iconText, subtitle) = when {
                    titleClean.contains("programación") || titleClean.contains("code") || titleClean.contains("software") -> "</>" to "Clase de"
                    titleClean.contains("álgebra") || titleClean.contains("matemática") || titleClean.contains("física") || titleClean.contains("cálculo") -> "Σ" to "Clase de"
                    category == ActivityCategory.WORK -> "💼" to "Trabajo"
                    category == ActivityCategory.FREE -> "☕" to "Tiempo libre"
                    else -> "📚" to "Clase de"
                }

                val (cardBg, illustration) = when (category) {
                    ActivityCategory.WORK -> Color(0xFF132238) to R.drawable.ic_widget_work
                    ActivityCategory.FREE -> Color(0xFF0F2E28) to R.drawable.ic_widget_free
                    ActivityCategory.UNIVERSITY -> Color(0xFF2A1B36) to R.drawable.ic_widget_class
                }

                PersonCardState(
                    personName = displayName,
                    badgeText = if (category == ActivityCategory.WORK) "● Trabajando" else "● En clase",
                    badgeColor = Color(0xFF4CAF50),
                    categoryIconText = iconText,
                    categorySubtitle = subtitle,
                    categoryTitle = schedule.title,
                    cardBgColor = cardBg,
                    innerCardBgColor = Color(0x33FFFFFF),
                    illustrationResId = illustration
                )
            }
            is UserCurrentStatus.Override -> {
                val location = status.customLocation
                val isWalking = location.contains("camino", ignoreCase = true)

                PersonCardState(
                    personName = displayName,
                    badgeText = "● $location",
                    badgeColor = Color(0xFFFF9800),
                    categoryIconText = if (isWalking) "🚶" else "📍",
                    categorySubtitle = "Aviso especial",
                    categoryTitle = location,
                    cardBgColor = Color(0xFF331E12),
                    innerCardBgColor = Color(0x33FFFFFF),
                    illustrationResId = R.drawable.ic_widget_transit
                )
            }
            else -> {
                // FreeTime / Default
                PersonCardState(
                    personName = displayName,
                    badgeText = "● Tiempo libre",
                    badgeColor = Color(0xFF4CAF50),
                    categoryIconText = "🏠",
                    categorySubtitle = "En",
                    categoryTitle = "Tiempo Libre",
                    cardBgColor = Color(0xFF0F2E28),
                    innerCardBgColor = Color(0x33FFFFFF),
                    illustrationResId = R.drawable.ic_widget_free
                )
            }
        }
    }

    private fun buildFallbackCardState(name: String, msg: String): PersonCardState {
        return PersonCardState(
            personName = name,
            badgeText = "● $msg",
            badgeColor = Color(0xFF9E9E9E),
            categoryIconText = "📍",
            categorySubtitle = "Estado",
            categoryTitle = msg,
            cardBgColor = Color(0xFF1E293B),
            innerCardBgColor = Color(0x22FFFFFF),
            illustrationResId = R.drawable.ic_widget_free
        )
    }
}

@Composable
private fun PersonCard(state: PersonCardState) {
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(state.cardBgColor)
            .cornerRadius(16.dp)
            .padding(10.dp)
    ) {
        // Illustration Background positioned at bottom right
        Box(
            modifier = GlanceModifier.fillMaxSize(),
            contentAlignment = Alignment.BottomEnd
        ) {
            Image(
                provider = ImageProvider(state.illustrationResId),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = GlanceModifier
                    .height(85.dp)
                    .width(85.dp)
            )
        }

        // Main Content Overlay on the left
        Column(
            modifier = GlanceModifier.fillMaxSize(),
            verticalAlignment = Alignment.Vertical.Top,
            horizontalAlignment = Alignment.Horizontal.Start
        ) {
            // Person Name
            Text(
                text = state.personName,
                style = TextStyle(
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = ColorProvider(Color.White)
                )
            )

            Spacer(modifier = GlanceModifier.height(3.dp))

            // Status Badge Pill
            Box(
                modifier = GlanceModifier
                    .background(Color(0x2BFFFFFF))
                    .cornerRadius(12.dp)
                    .padding(horizontal = 7.dp, vertical = 2.dp)
            ) {
                Text(
                    text = state.badgeText,
                    style = TextStyle(
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = ColorProvider(state.badgeColor)
                    )
                )
            }

            Spacer(modifier = GlanceModifier.height(10.dp))

            // Category Inner Card (Icon + Title)
            Box(
                modifier = GlanceModifier
                    .background(state.innerCardBgColor)
                    .cornerRadius(12.dp)
                    .padding(6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.Vertical.CenterVertically,
                    horizontalAlignment = Alignment.Horizontal.Start
                ) {
                    // Category Icon Box
                    Box(
                        modifier = GlanceModifier
                            .background(Color(0x33FFFFFF))
                            .cornerRadius(8.dp)
                            .padding(vertical = 4.dp, horizontal = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = state.categoryIconText,
                            style = TextStyle(
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = ColorProvider(Color.White)
                            )
                        )
                    }

                    Spacer(modifier = GlanceModifier.width(6.dp))

                    Column(
                        verticalAlignment = Alignment.Vertical.CenterVertically,
                        horizontalAlignment = Alignment.Horizontal.Start
                    ) {
                        Text(
                            text = state.categorySubtitle,
                            style = TextStyle(
                                fontSize = 9.sp,
                                color = ColorProvider(Color(0xFFE2E8F0))
                            )
                        )
                        Text(
                            text = state.categoryTitle,
                            style = TextStyle(
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = ColorProvider(Color.White)
                            ),
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun UnlinkedPersonCard() {
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(Color(0xFF1E1B4B))
            .cornerRadius(16.dp)
            .padding(10.dp)
    ) {
        Column(
            modifier = GlanceModifier.fillMaxSize(),
            verticalAlignment = Alignment.Vertical.CenterVertically,
            horizontalAlignment = Alignment.Horizontal.Start
        ) {
            Text(
                text = "Vincular Amigo",
                style = TextStyle(
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = ColorProvider(Color.White)
                )
            )

            Spacer(modifier = GlanceModifier.height(4.dp))

            Text(
                text = "🔗 Toca para conectar con un amigo en FindMe",
                style = TextStyle(
                    fontSize = 11.sp,
                    color = ColorProvider(Color(0xFFC7D2FE))
                )
            )
        }
    }
}

class CycleConnectedPersonAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        try {
            val repo = AccountRepository()
            repo.auth.awaitInitialization()
            val ownerId = repo.userId()
            val otherProfiles = repo.profiles().filter { it.id != ownerId }

            if (otherProfiles.isNotEmpty()) {
                val currentPersonId = ConnectionWidgetSettings.readPersonId(context, ownerId)
                val currentIndex = otherProfiles.indexOfFirst { it.id == currentPersonId }
                val nextIndex = if (currentIndex < 0) 0 else (currentIndex + 1) % otherProfiles.size
                val nextPersonId = otherProfiles[nextIndex].id

                ConnectionWidgetSettings.save(context, ownerId, nextPersonId)
            }
        } catch (_: Exception) {
            // Ignore error
        }
    }
}

class RefreshConnectionWidget : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        ConnectionWidget().updateAll(context)
    }
}
