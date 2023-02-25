package de.amirrocker.material3imageclassification.component.model

sealed class Header {
    data class HeaderPlain(val titleText: String) : Header()
    data class HeaderImage(val titleText: String, val imageResourceId: Int?) : Header()
}

sealed class Content {
    data class Center(val valueText: String) : Content()
    data class Left(val valueText: String) : Content()
}

sealed class Footer {
    object Plain : Footer()

    sealed class ButtonSingle : Footer() {
        data class NegativeButton(val valueText: String) : Content()
        data class PositiveButton(val valueText: String) : Content()
    }

    data class ButtonMultiple(
        val negativeButtonLabel: String,
        val onClickNegative: (() -> Unit)?,
        val positiveButtonLabel: String,
        val onClickPositive: (() -> Unit)?,
    ) : Footer()
}