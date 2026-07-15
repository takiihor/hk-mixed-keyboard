package com.hkmixedkeyboard

import android.view.View
import android.widget.LinearLayout
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.hkmixedkeyboard.decoder.CandidateType
import com.hkmixedkeyboard.decoder.DecodeCandidate
import com.hkmixedkeyboard.decoder.SourceSchema
import com.hkmixedkeyboard.ui.CandidateBarView
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CandidateAccessibilityTest {
    @Test
    fun candidateExposesPositionReadingSelectionAndOperableTarget() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val bar = CandidateBarView(
                context
            )
            bar.setCandidates(
                listOf(
                    DecodeCandidate(
                        "喺", "hai", SourceSchema.JYUTPING, CandidateType.CHAR,
                        1.0, true, annotation = "hai6"
                    )
                )
            )
            bar.measure(
                View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(48, View.MeasureSpec.EXACTLY)
            )
            bar.layout(0, 0, 800, 48)
            val row = bar.getChildAt(0) as LinearLayout
            val candidate = row.getChildAt(0)
            assertTrue(candidate.contentDescription.contains(
                context.getString(R.string.candidate_position, 1, 1, "喺")
            ))
            assertTrue(candidate.contentDescription.contains("hai6"))
            assertTrue(candidate.isSelected)
            assertTrue(candidate.isClickable && candidate.isFocusable)
        }
    }
}
