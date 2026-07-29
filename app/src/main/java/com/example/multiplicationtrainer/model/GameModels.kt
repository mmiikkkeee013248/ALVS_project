package com.example.multiplicationtrainer.model

import kotlin.random.Random

data class MultiplicationProblem(
    val left: Int,
    val right: Int,
) {
    val answer: Int get() = left * right
    val canonicalKey: String get() = "${minOf(left, right)}x${maxOf(left, right)}"
}

enum class GamePhase {
    MAIN,
    RETRY,
}

enum class Feedback {
    NONE,
    CORRECT,
    WRONG,
}

data class GameResult(
    val challengeCode: String,
    val variant: Int,
    val runId: Long,
    val mainTimeMillis: Long,
    val totalTimeMillis: Long,
    val errors: Int,
    val attempts: Int,
)

data class SpellingWord(
    val id: String,
    val word: String,
    val image: String,
    val grade: Int,
)

object ProblemGenerator {
    private val canonicalPool: List<Pair<Int, Int>> = buildList {
        for (left in 2..9) {
            for (right in left..9) {
                if (left * right in 10..99) add(left to right)
            }
        }
    }

    fun generate(count: Int, random: Random = Random.Default): List<MultiplicationProblem> {
        require(count in 1..canonicalPool.size) {
            "Requested $count problems, but only ${canonicalPool.size} unique pairs exist"
        }

        return canonicalPool
            .shuffled(random)
            .take(count)
            .map { (a, b) ->
                if (a != b && random.nextBoolean()) {
                    MultiplicationProblem(b, a)
                } else {
                    MultiplicationProblem(a, b)
                }
            }
    }
}

object SpellingCatalog {
    fun normalizeAnswer(value: String): String {
        return value.trim().lowercase().replace('ё', 'е')
    }

    fun isCorrect(expected: String, actual: String): Boolean {
        return normalizeAnswer(expected) == normalizeAnswer(actual)
    }
}
