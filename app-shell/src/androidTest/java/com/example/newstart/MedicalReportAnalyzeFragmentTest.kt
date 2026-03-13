package com.example.newstart

import androidx.navigation.Navigation
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.Visibility
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withClassName
import androidx.test.espresso.matcher.ViewMatchers.withEffectiveVisibility
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.hamcrest.CoreMatchers.containsString
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MedicalReportAnalyzeFragmentTest {

    @Test
    fun medical_report_page_prefers_readable_summary_and_collapses_editor_by_default() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                Navigation.findNavController(activity, R.id.nav_host_fragment)
                    .navigate(R.id.navigation_medical_report_analyze)
            }

            onView(withId(R.id.tv_medical_readable)).check(matches(isDisplayed()))
            onView(withId(R.id.tv_medical_readable)).check(
                matches(withText(R.string.medical_report_readable_empty))
            )
            onView(withId(R.id.layout_medical_editor_container)).check(
                matches(withEffectiveVisibility(Visibility.GONE))
            )

            onView(withId(R.id.btn_medical_toggle_editor)).perform(scrollTo(), click())
            onView(withId(R.id.btn_medical_toggle_editor)).check(
                matches(withText(R.string.medical_report_hide_editor))
            )
            onView(withClassName(containsString("TextInputEditText"))).perform(scrollTo())
            onView(withId(R.id.et_medical_ocr_editable)).check(matches(isDisplayed()))
        }
    }
}
