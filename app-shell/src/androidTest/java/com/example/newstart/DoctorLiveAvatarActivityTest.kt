package com.example.newstart

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isEnabled
import androidx.test.espresso.matcher.ViewMatchers.withEffectiveVisibility
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.newstart.ui.doctor.DoctorLiveAvatarActivity
import com.example.newstart.xfyun.XfyunConfig
import org.hamcrest.Matchers.not
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DoctorLiveAvatarActivityTest {

    @Test
    fun launch_reflects_current_xfyun_configuration_state() {
        var configured = false
        ActivityScenario.launch(DoctorLiveAvatarActivity::class.java).use { scenario ->
            scenario.onActivity {
                configured = XfyunConfig.aiuiCredentials.isReady && XfyunConfig.defaultAvatarId.isNotBlank()
            }

            onView(withId(R.id.tv_doctor_live_avatar_status)).check(matches(isDisplayed()))
            onView(withId(R.id.btn_doctor_live_avatar_start)).check(
                matches(if (configured) isEnabled() else not(isEnabled()))
            )
            onView(withId(R.id.tv_doctor_live_avatar_hint)).check(
                matches(
                    if (configured) {
                        withEffectiveVisibility(androidx.test.espresso.matcher.ViewMatchers.Visibility.GONE)
                    } else {
                        isDisplayed()
                    }
                )
            )
            if (!configured) {
                onView(withId(R.id.tv_doctor_live_avatar_status)).check(
                    matches(withText(R.string.doctor_live_avatar_status_not_configured))
                )
            }
        }
    }

    @Test
    fun clear_button_restores_empty_transcript() {
        ActivityScenario.launch(DoctorLiveAvatarActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.findViewById<android.widget.TextView>(R.id.tv_doctor_live_avatar_transcript).text = "用户：昨晚没睡好"
            }

            onView(withId(R.id.btn_doctor_live_avatar_clear)).perform(click())
            onView(withId(R.id.tv_doctor_live_avatar_transcript)).check(
                matches(withText(R.string.doctor_live_avatar_transcript_empty))
            )
        }
    }

    @Test
    fun doctor_page_entry_opens_live_avatar_activity() {
        ActivityScenario.launch(MainActivity::class.java).use {
            onView(withId(R.id.navigation_doctor)).perform(click())
            onView(withId(R.id.btn_doctor_open_live_avatar)).perform(click())
            onView(withId(R.id.tv_doctor_live_avatar_status)).check(matches(isDisplayed()))
        }
    }

    @Test
    fun configured_live_avatar_can_leave_ready_state_after_start() {
        org.junit.Assume.assumeTrue(
            XfyunConfig.aiuiCredentials.isReady && XfyunConfig.defaultAvatarId.isNotBlank()
        )
        ActivityScenario.launch(DoctorLiveAvatarActivity::class.java).use { scenario ->
            onView(withId(R.id.btn_doctor_live_avatar_start)).perform(click())
            Thread.sleep(12_000)
            scenario.onActivity { activity ->
                val status = activity.findViewById<android.widget.TextView>(R.id.tv_doctor_live_avatar_status)
                    .text
                    .toString()
                assertNotEquals(activity.getString(R.string.doctor_live_avatar_status_ready), status)
                assertNotEquals(activity.getString(R.string.doctor_live_avatar_status_not_configured), status)
            }
        }
    }
}
