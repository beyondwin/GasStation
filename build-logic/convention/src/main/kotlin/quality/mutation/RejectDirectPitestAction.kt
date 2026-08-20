package com.gasstation.buildlogic.quality.mutation

import org.gradle.api.Action
import org.gradle.api.GradleException
import org.gradle.api.Task
import java.io.Serializable

internal class RejectDirectPitestAction(
    private val verifiedTaskPath: String,
) : Action<Task>, Serializable {
    override fun execute(task: Task) {
        throw GradleException("Direct pitest is unsupported; use $verifiedTaskPath")
    }
}
