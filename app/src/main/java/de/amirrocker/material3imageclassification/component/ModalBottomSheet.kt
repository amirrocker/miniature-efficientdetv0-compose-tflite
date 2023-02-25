package de.amirrocker.material3imageclassification.component

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.amirrocker.material3imageclassification.component.model.Content
import de.amirrocker.material3imageclassification.component.model.Footer
import de.amirrocker.material3imageclassification.component.model.Header

//@OptIn(ExperimentalMaterial3Api::class)
//@Composable
//fun ModalConfigurableBottomSheet(
//    header: Header,
//    content: Content,
//    footer:Footer,
//    onDismissRequest: () -> Unit = { /*TODO*/ },
//) {
//
//    val sheetState = rememberSheetState()
//    val coroutineScope = rememberCoroutineScope()
//
//    ModalBottomSheet(
//        onDismissRequest = onDismissRequest,
//        sheetState = sheetState,
//        modifier = Modifier.padding(16.dp)
//    ) {
//        B
//    }
//
//}