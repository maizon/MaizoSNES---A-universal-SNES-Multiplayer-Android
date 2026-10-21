package com.maizon.maizosnes

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

private val palette = listOf(
    Color(0xFF364FC7), Color(0xFF16646C), Color(0xFF7845A6), Color(0xFF9D482C),
    Color(0xFF306A38), Color(0xFF9E3859), Color(0xFF465C99), Color(0xFF806D21),
    Color(0xFF79523A), Color(0xFF327E88), Color(0xFF954E79), Color(0xFF556B43)
)

@Composable
internal fun PlayerAvatar(name: String, id: String, bitmap: Bitmap?,
    modifier: Modifier = Modifier, size: Int = 52) {
    val background = palette[PlayerIdentity.colorIndex(id, name)]
    Box(modifier.size(size.dp).clip(CircleShape).background(background),
        contentAlignment = Alignment.Center) {
        if (bitmap != null) {
            Image(bitmap.asImageBitmap(), contentDescription = "Foto do perfil",
                modifier = Modifier.size(size.dp).clip(CircleShape),
                contentScale = ContentScale.Crop)
        } else {
            Text(PlayerIdentity.initials(name), fontSize = (size / 3).sp,
                color = Color.White, fontWeight = FontWeight.ExtraBold)
        }
    }
}

/** Self-contained onboarding; does not delay or modify any netplay handshake. */
@Composable
internal fun PlayerProfileDialog(
    profile: PlayerProfile,
    avatar: Bitmap?,
    welcome: Boolean,
    onPhoto: () -> Unit,
    onDeletePhoto: () -> Unit,
    onSave: (String) -> Unit,
    onSkip: () -> Unit
) {
    var entered by remember(welcome, profile.name) { mutableStateOf(profile.name) }
    Dialog(onDismissRequest = { if (!welcome) onSkip() },
        properties = DialogProperties(dismissOnBackPress = !welcome,
            dismissOnClickOutside = false, usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxWidth(0.92f).widthIn(max = 440.dp),
            shape = RoundedCornerShape(22.dp), color = Color(0xFF1B2333)) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (welcome) "BEM-VINDO AO MAIZOSNES" else "MEU PERFIL",
                    fontWeight = FontWeight.Black, color = Color.White, fontSize = 20.sp)
                Text(if (welcome) "Seu universo SNES começa aqui."
                    else "Este perfil fica salvo somente neste aparelho.",
                    color = Color(0xFFB9C9D4), fontSize = 12.sp)
                Spacer(Modifier.height(6.dp))
                PlayerAvatar(entered, profile.localId, avatar, size = 98)
                OutlinedButton(onClick = onPhoto) { Text("ESCOLHER FOTO DA GALERIA") }
                if (avatar != null) TextButton(onClick = onDeletePhoto) {
                    Text("Remover foto", color = Color(0xFFFFB4AB))
                }
                TextField(value = entered, onValueChange = { entered = it.take(64) },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    label = { Text("Nome de usuário") },
                    supportingText = { Text("Até 32 caracteres • usado nas salas LAN e online") })
                Text("Sua foto permanece neste aparelho. Nas listas públicas, " +
                    "outros jogadores veem apenas um avatar com iniciais.",
                    fontSize = 11.sp, color = Color(0xFFB9C9D4))
                Button(onClick = { onSave(entered) }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (welcome) "COMEÇAR A JOGAR" else "SALVAR PERFIL")
                }
                TextButton(onClick = onSkip) {
                    Text(if (welcome) "PULAR POR ENQUANTO" else "CANCELAR")
                }
            }
        }
    }
}
