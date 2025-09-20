package com.alarmise.app.data.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlin.random.Random

@Parcelize
data class MathPuzzle(
    val question: String,
    val correctAnswer: Int,
    val difficulty: Difficulty = Difficulty.MEDIUM
) : Parcelable {
    
    enum class Difficulty {
        EASY,   // Single digit operations
        MEDIUM, // Double digit operations
        HARD    // Triple digit operations
    }
    
    companion object {
        /**
         * Generate a random math puzzle based on difficulty level
         */
        fun generate(difficulty: Difficulty = Difficulty.MEDIUM): MathPuzzle {
            return when (difficulty) {
                Difficulty.EASY -> generateEasyPuzzle()
                Difficulty.MEDIUM -> generateMediumPuzzle()
                Difficulty.HARD -> generateHardPuzzle()
            }
        }
        
        private fun generateEasyPuzzle(): MathPuzzle {
            val operation = Random.nextInt(4) // 0: +, 1: -, 2: *, 3: /
            val num1 = Random.nextInt(1, 10)
            val num2 = Random.nextInt(1, 10)
            
            return when (operation) {
                0 -> {
                    val answer = num1 + num2
                    MathPuzzle("$num1 + $num2 = ?", answer, Difficulty.EASY)
                }
                1 -> {
                    val larger = maxOf(num1, num2)
                    val smaller = minOf(num1, num2)
                    val answer = larger - smaller
                    MathPuzzle("$larger - $smaller = ?", answer, Difficulty.EASY)
                }
                2 -> {
                    val answer = num1 * num2
                    MathPuzzle("$num1 × $num2 = ?", answer, Difficulty.EASY)
                }
                else -> {
                    // Division - ensure clean division
                    val divisor = Random.nextInt(2, 6)
                    val quotient = Random.nextInt(2, 10)
                    val dividend = divisor * quotient
                    MathPuzzle("$dividend ÷ $divisor = ?", quotient, Difficulty.EASY)
                }
            }
        }
        
        private fun generateMediumPuzzle(): MathPuzzle {
            val operation = Random.nextInt(4)
            val num1 = Random.nextInt(10, 100)
            val num2 = Random.nextInt(10, 100)
            
            return when (operation) {
                0 -> {
                    val answer = num1 + num2
                    MathPuzzle("$num1 + $num2 = ?", answer, Difficulty.MEDIUM)
                }
                1 -> {
                    val larger = maxOf(num1, num2)
                    val smaller = minOf(num1, num2)
                    val answer = larger - smaller
                    MathPuzzle("$larger - $smaller = ?", answer, Difficulty.MEDIUM)
                }
                2 -> {
                    val smallNum1 = Random.nextInt(10, 25)
                    val smallNum2 = Random.nextInt(2, 10)
                    val answer = smallNum1 * smallNum2
                    MathPuzzle("$smallNum1 × $smallNum2 = ?", answer, Difficulty.MEDIUM)
                }
                else -> {
                    val divisor = Random.nextInt(5, 15)
                    val quotient = Random.nextInt(5, 20)
                    val dividend = divisor * quotient
                    MathPuzzle("$dividend ÷ $divisor = ?", quotient, Difficulty.MEDIUM)
                }
            }
        }
        
        private fun generateHardPuzzle(): MathPuzzle {
            val operation = Random.nextInt(5) // Include mixed operations
            
            return when (operation) {
                0 -> {
                    val num1 = Random.nextInt(100, 1000)
                    val num2 = Random.nextInt(100, 1000)
                    val answer = num1 + num2
                    MathPuzzle("$num1 + $num2 = ?", answer, Difficulty.HARD)
                }
                1 -> {
                    val num1 = Random.nextInt(500, 1000)
                    val num2 = Random.nextInt(100, 500)
                    val answer = num1 - num2
                    MathPuzzle("$num1 - $num2 = ?", answer, Difficulty.HARD)
                }
                2 -> {
                    val num1 = Random.nextInt(25, 50)
                    val num2 = Random.nextInt(10, 25)
                    val answer = num1 * num2
                    MathPuzzle("$num1 × $num2 = ?", answer, Difficulty.HARD)
                }
                3 -> {
                    val divisor = Random.nextInt(15, 30)
                    val quotient = Random.nextInt(10, 40)
                    val dividend = divisor * quotient
                    MathPuzzle("$dividend ÷ $divisor = ?", quotient, Difficulty.HARD)
                }
                else -> {
                    // Mixed operation: (a + b) × c
                    val a = Random.nextInt(5, 15)
                    val b = Random.nextInt(5, 15)
                    val c = Random.nextInt(2, 8)
                    val answer = (a + b) * c
                    MathPuzzle("($a + $b) × $c = ?", answer, Difficulty.HARD)
                }
            }
        }
    }
    
    /**
     * Check if the provided answer is correct
     */
    fun isCorrectAnswer(answer: Int): Boolean {
        return answer == correctAnswer
    }
}
