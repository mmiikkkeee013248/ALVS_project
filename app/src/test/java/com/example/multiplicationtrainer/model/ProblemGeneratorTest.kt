package com.example.multiplicationtrainer.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class ProblemGeneratorTest {
    @Test
    fun generatesThirtyUniqueTwoDigitProblems() {
        val problems = ProblemGenerator.generate(30, Random(1234))

        assertEquals(30, problems.size)
        assertEquals(30, problems.map { it.canonicalKey }.toSet().size)
        assertTrue(problems.all { it.answer in 10..99 })
        assertTrue(problems.all { it.left in 2..9 && it.right in 2..9 })
    }

    @Test
    fun poolContainsThirtyTwoCanonicalProblems() {
        assertEquals(32, ProblemGenerator.availableUniqueProblems())
    }
}
