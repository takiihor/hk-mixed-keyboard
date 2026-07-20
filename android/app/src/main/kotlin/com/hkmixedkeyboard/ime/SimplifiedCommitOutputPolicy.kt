package com.hkmixedkeyboard.ime

import com.hkmixedkeyboard.commit.CommitOutput


/** Aligns controller state with the exact text emitted after output conversion. */
object SimplifiedCommitOutputPolicy {
    fun convert(output: CommitOutput, transform: (String) -> String): CommitOutput {
        val convertedText = output.committedText?.let(transform)
        val autoCommit = output.newState.lastAutoCommit?.let { record ->
            record.copy(text = transform(record.text))
        }
        return output.copy(
            committedText = convertedText,
            newState = output.newState.copy(lastAutoCommit = autoCommit)
        )
    }
}
