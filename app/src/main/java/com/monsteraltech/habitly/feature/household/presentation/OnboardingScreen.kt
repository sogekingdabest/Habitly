package com.monsteraltech.habitly.feature.household.presentation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.monsteraltech.habitly.R
import com.monsteraltech.habitly.feature.routines.domain.model.RoutineFrequency
import com.monsteraltech.habitly.ui.components.HabitlyCard
import com.monsteraltech.habitly.ui.components.HabitlyPrimaryButton
import com.monsteraltech.habitly.ui.components.HabitlyTextButton
import com.monsteraltech.habitly.ui.theme.LeafCornerLarge
import com.monsteraltech.habitly.ui.theme.habitly
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(
    onSignOut: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    // rememberSaveable y no remember: el paso de plantillas vive en el ViewModel y sobrevive a
    // una rotación, así que el nombre de la casa tiene que sobrevivir también o al volver del
    // paso 2 se intentaría crear una casa sin nombre.
    var householdName by rememberSaveable { mutableStateOf("") }
    var inviteCode by rememberSaveable { mutableStateOf("") }
    var showSignOutDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()

    // Atrás en el paso de plantillas vuelve al formulario, no saca de la app.
    BackHandler(enabled = uiState.step == OnboardingStep.TEMPLATES && !uiState.isSubmitting) {
        viewModel.onBackToForm()
    }

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.onErrorShown()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                actions = {
                    TextButton(onClick = { showSignOutDialog = true }) {
                        Icon(
                            Icons.AutoMirrored.Filled.Logout,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.household_sign_out))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                // consumeWindowInsets: el Scaffold ya reserva el hueco de la barra de navegación
                // en `padding`. Sin consumirlo, imePadding volvería a sumar esa zona (el teclado
                // la incluye) y aparecía una banda del color del fondo entre el campo y el teclado.
                .consumeWindowInsets(padding)
                // imePadding: con edge-to-edge activo, adjustResize no reduce la ventana de
                // Compose por sí solo. Sin esto, el teclado tapaba el recuadro de "unirse con
                // código" y su botón; ahora el área desplazable queda por encima del teclado.
                .imePadding()
                .padding(24.dp)
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Segundo paso: las rutinas con las que arranca la casa. Va aquí y no después de
            // crearla porque en cuanto la casa existe el MainViewModel conmuta de pantalla.
            if (uiState.step == OnboardingStep.TEMPLATES) {
                RoutineTemplatesStep(
                    isSubmitting = uiState.isSubmitting,
                    onBack = viewModel::onBackToForm,
                    onCreate = { routines -> viewModel.onCreateHousehold(householdName, routines) }
                )
                return@Column
            }

            Text(
                text = stringResource(R.string.onboarding_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.onboarding_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(32.dp))

            // === Crear una casa ===
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Home, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.onboarding_create_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.onboarding_create_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = householdName,
                        onValueChange = { householdName = it },
                        label = { Text(stringResource(R.string.onboarding_household_name_label)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = viewModel::onContinueToTemplates,
                        enabled = householdName.isNotBlank() && !uiState.isSubmitting,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(stringResource(R.string.onboarding_create_button))
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            Text(
                text = stringResource(R.string.onboarding_or),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(20.dp))

            // === Unirse con código ===
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
            ) {
                Column(Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.GroupAdd, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.onboarding_join_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.onboarding_join_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = inviteCode,
                        onValueChange = { inviteCode = it.uppercase() },
                        label = { Text(stringResource(R.string.household_invite_code_label)) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            // Al enfocar el código, el desplazamiento automático del campo lo
                            // deja pegado al teclado y su botón queda debajo (tapado). Como esta
                            // tarjeta es el último elemento, desplazamos hasta el final para que
                            // el botón "Unirse" quede visible. El delay espera a que el teclado
                            // termine de abrir y el inset esté aplicado antes de medir maxValue.
                            .onFocusEvent { focus ->
                                if (focus.isFocused) {
                                    scope.launch {
                                        delay(300)
                                        scrollState.animateScrollTo(scrollState.maxValue)
                                    }
                                }
                            },
                        shape = RoundedCornerShape(12.dp),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                if (inviteCode.length == 6 && !uiState.isSubmitting) {
                                    viewModel.onJoinHousehold(inviteCode)
                                }
                            }
                        )
                    )
                    Spacer(Modifier.height(12.dp))
                    // Botón sólido (no OutlinedButton) para que se lea claramente como acción,
                    // igual que "Crear casa": color secondary sobre la tarjeta secondaryContainer.
                    Button(
                        onClick = { viewModel.onJoinHousehold(inviteCode) },
                        enabled = inviteCode.length == 6 && !uiState.isSubmitting,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary,
                            contentColor = MaterialTheme.colorScheme.onSecondary
                        )
                    ) {
                        Text(stringResource(R.string.onboarding_join_button))
                    }
                }
            }

            if (uiState.isSubmitting) {
                Spacer(Modifier.height(24.dp))
                CircularProgressIndicator()
            }
        }
    }

    // Confirmación de cierre de sesión (mismo patrón que Ajustes).
    if (showSignOutDialog) {
        AlertDialog(
            onDismissRequest = { showSignOutDialog = false },
            icon = { Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null) },
            title = { Text(stringResource(R.string.household_sign_out)) },
            text = { Text(stringResource(R.string.settings_sign_out_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    showSignOutDialog = false
                    viewModel.onSignOut(onSignOut)
                }) { Text(stringResource(R.string.household_sign_out)) }
            },
            dismissButton = {
                TextButton(onClick = { showSignOutDialog = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }
}

/**
 * Paso opcional: rutinas típicas de casa con las que arrancar.
 *
 * Aterrizar en una pantalla vacía es el momento de mayor abandono de cualquier app de hábitos, así
 * que aquí se ofrecen ocho rutinas **ya marcadas** y con su frecuencia puesta: crear una casa con
 * rutinas es un toque. Y "prefiero empezar de cero" está a la vista, no escondido: nadie está
 * obligado a quedarse con las de la casa modelo.
 */
@Composable
private fun ColumnScope.RoutineTemplatesStep(
    isSubmitting: Boolean,
    onBack: () -> Unit,
    onCreate: (List<NewHouseholdRoutine>) -> Unit
) {
    // Todas marcadas por defecto: es lo que hace que arrancar sea un toque. Saveable para que una
    // rotación no vuelva a marcar lo que el usuario acaba de desmarcar.
    var selectedIds by rememberSaveable {
        mutableStateOf(HOUSEHOLD_ROUTINE_TEMPLATES.map { it.id })
    }
    // Títulos resueltos aquí, en el contexto que sigue el idioma de Ajustes.
    val titles = HOUSEHOLD_ROUTINE_TEMPLATES.associate { it.id to stringResource(it.titleRes) }

    val selectedRoutines = HOUSEHOLD_ROUTINE_TEMPLATES
        .filter { it.id in selectedIds }
        .map { template ->
            NewHouseholdRoutine(
                title = titles[template.id].orEmpty(),
                frequency = template.frequency,
                scheduledDays = template.scheduledDays,
                intervalDays = template.intervalDays
            )
        }

    Text(
        text = stringResource(R.string.templates_title),
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center
    )
    Spacer(Modifier.height(8.dp))
    Text(
        text = stringResource(R.string.templates_subtitle),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center
    )

    Spacer(Modifier.height(20.dp))

    HabitlyCard(
        modifier = Modifier.fillMaxWidth(),
        shape = LeafCornerLarge,
        contentPadding = PaddingValues(vertical = 8.dp, horizontal = 12.dp)
    ) {
        HOUSEHOLD_ROUTINE_TEMPLATES.forEach { template ->
            val checked = template.id in selectedIds
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .toggleable(
                        value = checked,
                        enabled = !isSubmitting,
                        onValueChange = { wanted ->
                            selectedIds = if (wanted) selectedIds + template.id
                            else selectedIds - template.id
                        },
                        role = Role.Checkbox
                    )
                    .heightIn(min = 48.dp)
                    .padding(vertical = 4.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(checked = checked, onCheckedChange = null)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = titles[template.id].orEmpty(),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = templateFrequencyLabel(template),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.habitly.textSecondary
                    )
                }
            }
        }
    }

    Spacer(Modifier.height(20.dp))

    HabitlyPrimaryButton(
        text = if (selectedRoutines.isEmpty()) {
            stringResource(R.string.onboarding_create_button)
        } else {
            pluralStringResource(
                R.plurals.templates_create_with,
                selectedRoutines.size,
                selectedRoutines.size
            )
        },
        onClick = { onCreate(selectedRoutines) },
        loading = isSubmitting,
        modifier = Modifier.fillMaxWidth()
    )

    Spacer(Modifier.height(8.dp))

    // Salida sin fricción: crea la casa igual, pero vacía.
    HabitlyTextButton(
        text = stringResource(R.string.templates_skip),
        onClick = { onCreate(emptyList()) },
        enabled = !isSubmitting,
        modifier = Modifier.align(Alignment.CenterHorizontally)
    )

    Spacer(Modifier.height(4.dp))

    HabitlyTextButton(
        text = stringResource(R.string.cd_back),
        onClick = onBack,
        enabled = !isSubmitting,
        modifier = Modifier.align(Alignment.CenterHorizontally)
    )
}

/** "Diaria", "Semanal" o "Cada 14 días": lo que va a quedar creado, sin sorpresas. */
@Composable
private fun templateFrequencyLabel(template: RoutineTemplate): String = when (template.frequency) {
    RoutineFrequency.WEEKLY -> stringResource(R.string.routines_frequency_weekly)
    RoutineFrequency.EVERY_N_DAYS -> pluralStringResource(
        R.plurals.templates_frequency_interval,
        template.intervalDays ?: 1,
        template.intervalDays ?: 1
    )
    else -> stringResource(R.string.routines_frequency_daily)
}
