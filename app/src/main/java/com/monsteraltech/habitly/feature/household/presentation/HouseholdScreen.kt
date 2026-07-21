package com.monsteraltech.habitly.feature.household.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.ui.res.stringResource
import com.monsteraltech.habitly.R
import com.monsteraltech.habitly.feature.household.domain.model.UserProfile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.graphics.Color
import com.monsteraltech.habitly.ui.components.HabitlyBackground
import com.monsteraltech.habitly.ui.components.HabitlyCard
import com.monsteraltech.habitly.ui.components.MeshArrangement
import com.monsteraltech.habitly.ui.theme.LeafCornerLarge
import com.monsteraltech.habitly.ui.theme.LeafCornerMedium

@Composable
fun HouseholdScreen(
    onSignOut: () -> Unit,
    viewModel: HouseholdViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    @Suppress("DEPRECATION")
    val clipboardManager = LocalClipboardManager.current
    var inviteCodeInput by remember { mutableStateOf("") }
    var showEditNameDialog by remember { mutableStateOf(false) }
    var editNameInput by remember { mutableStateOf("") }
    var showEditNicknameDialog by remember { mutableStateOf(false) }
    var editNicknameInput by remember { mutableStateOf("") }
    var showDeleteAccountDialog by remember { mutableStateOf(false) }
    var showLeaveDialog by remember { mutableStateOf(false) }
    var showRegenerateDialog by remember { mutableStateOf(false) }
    var memberToRemove by remember { mutableStateOf<UserProfile?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }
    val joinSuccessMsg = stringResource(R.string.household_join_success)

    LaunchedEffect(uiState.joinSuccess) {
        if (uiState.joinSuccess) {
            inviteCodeInput = ""
            snackbarHostState.showSnackbar(joinSuccessMsg)
            viewModel.resetJoinState()
        }
    }
    
    LaunchedEffect(uiState.joinError) {
        uiState.joinError?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.resetJoinState()
        }
    }

    LaunchedEffect(uiState.deleteAccountError) {
        uiState.deleteAccountError?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.onDeleteAccountErrorShown()
        }
    }

    LaunchedEffect(uiState.error) {
        uiState.error?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.onErrorShown()
        }
    }

    HabitlyBackground(arrangement = MeshArrangement.Household) {
    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        containerColor = Color.Transparent
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.household_title),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = { viewModel.onSignOut(onSignOut) }) {
                    Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = stringResource(R.string.household_sign_out))
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            if (uiState.isLoading) {
                CircularProgressIndicator()
            } else if (uiState.household != null) {
                
                // === SECCIÓN: Tu Perfil ===
                HabitlyCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = LeafCornerLarge,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Avatar con inicial
                        val nickname = uiState.userProfile?.nickname ?: ""
                        val initial = nickname.firstOrNull()?.uppercase() ?: "?"
                        
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = initial,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = nickname.ifBlank { stringResource(R.string.household_no_nickname) },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            IconButton(onClick = {
                                editNicknameInput = nickname
                                showEditNicknameDialog = true
                            }) {
                                Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.household_edit_nickname), modifier = Modifier.size(18.dp))
                            }
                        }

                        Text(
                            text = stringResource(R.string.household_nickname_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.6f)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))

                // === SECCIÓN: Info de la Casa ===
                HabitlyCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = LeafCornerLarge,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = uiState.household!!.name,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            IconButton(onClick = {
                                editNameInput = uiState.household!!.name
                                showEditNameDialog = true
                            }) {
                                Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.household_edit_name), modifier = Modifier.size(20.dp))
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(stringResource(R.string.household_invite_code_title), style = MaterialTheme.typography.bodyMedium)
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surface,
                            onClick = {
                                clipboardManager.setText(AnnotatedString(uiState.household!!.inviteCode))
                            }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = uiState.household!!.inviteCode,
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 4.sp
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Icon(Icons.Filled.ContentCopy, contentDescription = stringResource(R.string.household_copy))
                            }
                        }

                        TextButton(onClick = { showRegenerateDialog = true }) {
                            Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(stringResource(R.string.household_regenerate_code))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // === SECCIÓN: Miembros ===
                Text(
                    text = stringResource(R.string.household_members, uiState.memberProfiles.size),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
                
                uiState.memberProfiles.forEach { member ->
                    val memberInitial = member.nickname.firstOrNull()?.uppercase() 
                        ?: member.displayName.firstOrNull()?.uppercase() 
                        ?: "?"
                    val memberName = member.nickname.ifBlank { member.displayName }
                    val isYou = member.id == uiState.userProfile?.id
                    
                    HabitlyCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        shape = LeafCornerMedium,
                        elevation = 6.dp,
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.tertiary),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = memberInitial,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onTertiary
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = if (isYou) stringResource(R.string.household_you, memberName) else memberName,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (isYou) FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier.weight(1f)
                            )
                            if (!isYou) {
                                IconButton(onClick = { memberToRemove = member }) {
                                    Icon(
                                        Icons.Filled.PersonRemove,
                                        contentDescription = stringResource(R.string.household_remove_member),
                                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))
                
                // === SECCIÓN: Unirse a otra casa ===
                Text(
                    text = stringResource(R.string.household_join_question),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.household_join_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                OutlinedTextField(
                    value = inviteCodeInput,
                    onValueChange = { inviteCodeInput = it.uppercase() },
                    label = { Text(stringResource(R.string.household_invite_code_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Button(
                    onClick = { viewModel.onJoinHousehold(inviteCodeInput) },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    enabled = inviteCodeInput.length == 6 && !uiState.isJoining,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (uiState.isJoining) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                    } else {
                        Icon(Icons.Filled.GroupAdd, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.household_join_button))
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                OutlinedButton(
                    onClick = { showLeaveDialog = true },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.household_leave))
                }

                Spacer(modifier = Modifier.height(40.dp))

                // === SECCIÓN: Zona de peligro ===
                HorizontalDivider()
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.household_danger_zone),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.household_delete_account_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(
                    onClick = { showDeleteAccountDialog = true },
                    enabled = !uiState.isDeletingAccount,
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    if (uiState.isDeletingAccount) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.error)
                    } else {
                        Icon(Icons.Filled.DeleteForever, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.household_delete_account))
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
    }

    // Dialog de confirmación de salir de la casa
    if (showLeaveDialog) {
        AlertDialog(
            onDismissRequest = { showLeaveDialog = false },
            icon = { Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null) },
            title = { Text(stringResource(R.string.household_leave_confirm_title)) },
            text = { Text(stringResource(R.string.household_leave_confirm_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showLeaveDialog = false
                    viewModel.onLeaveHousehold()
                }) {
                    Text(stringResource(R.string.household_leave))
                }
            },
            dismissButton = {
                TextButton(onClick = { showLeaveDialog = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }

    // Dialog de confirmación de regenerar código
    if (showRegenerateDialog) {
        AlertDialog(
            onDismissRequest = { showRegenerateDialog = false },
            icon = { Icon(Icons.Filled.Refresh, contentDescription = null) },
            title = { Text(stringResource(R.string.household_regenerate_confirm_title)) },
            text = { Text(stringResource(R.string.household_regenerate_confirm_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showRegenerateDialog = false
                    viewModel.onRegenerateInviteCode()
                }) {
                    Text(stringResource(R.string.household_regenerate_code))
                }
            },
            dismissButton = {
                TextButton(onClick = { showRegenerateDialog = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }

    // Dialog de confirmación de expulsar miembro
    memberToRemove?.let { member ->
        val name = member.nickname.ifBlank { member.displayName }
        AlertDialog(
            onDismissRequest = { memberToRemove = null },
            icon = { Icon(Icons.Filled.PersonRemove, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text(stringResource(R.string.household_remove_confirm_title)) },
            text = { Text(stringResource(R.string.household_remove_confirm_message, name)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.onRemoveMember(member.id)
                        memberToRemove = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.household_remove_member))
                }
            },
            dismissButton = {
                TextButton(onClick = { memberToRemove = null }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }

    // Dialog de confirmación de borrado de cuenta
    if (showDeleteAccountDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAccountDialog = false },
            icon = { Icon(Icons.Filled.DeleteForever, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text(stringResource(R.string.household_delete_confirm_title)) },
            text = {
                Text(stringResource(R.string.household_delete_confirm_message))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteAccountDialog = false
                        viewModel.onDeleteAccount(onSignOut)
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.household_delete_confirm_button))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAccountDialog = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }
    
    // Dialog para editar nombre de la casa
    if (showEditNameDialog) {
        AlertDialog(
            onDismissRequest = { showEditNameDialog = false },
            title = { Text(stringResource(R.string.household_change_name_title)) },
            text = {
                OutlinedTextField(
                    value = editNameInput,
                    onValueChange = { editNameInput = it },
                    label = { Text(stringResource(R.string.household_new_name)) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.onEditHouseholdName(editNameInput)
                        showEditNameDialog = false
                    },
                    enabled = editNameInput.isNotBlank()
                ) {
                    Text(stringResource(R.string.household_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditNameDialog = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }
    
    // Dialog para editar nickname
    if (showEditNicknameDialog) {
        AlertDialog(
            onDismissRequest = { showEditNicknameDialog = false },
            title = { Text(stringResource(R.string.household_change_nickname_title)) },
            text = {
                OutlinedTextField(
                    value = editNicknameInput,
                    onValueChange = { editNicknameInput = it },
                    label = { Text(stringResource(R.string.household_short_name)) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.onUpdateNickname(editNicknameInput)
                        showEditNicknameDialog = false
                    },
                    enabled = editNicknameInput.isNotBlank()
                ) {
                    Text(stringResource(R.string.household_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditNicknameDialog = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }
}
