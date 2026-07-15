package com.hkmixedkeyboard.settings

import android.text.InputType
import android.view.textclassifier.TextClassifier
import android.widget.EditText

/** Avoids the legacy framework spelling popup while leaving IME input available. */
internal fun EditText.disableSystemTextSuggestions() {
    inputType = inputType or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
    setTextClassifier(TextClassifier.NO_OP)
}
