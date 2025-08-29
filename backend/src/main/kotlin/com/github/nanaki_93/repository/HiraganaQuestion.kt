package com.github.nanaki_93.repository

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID


@Entity
@Table(name = "hiragana_questions")
data class HiraganaQuestion(
    @Id
    @GeneratedValue
    @Column(nullable = false)
    val id: UUID? = null,
    @Column(nullable = false, unique = true)
    val hiragana: String,
    @Column(nullable = false)
    val romanization: String,
    @Column(nullable = true)
    val translation: String,
    @Column(nullable = false)
    val topic: String = "",
    @Column(nullable = false)
    val difficulty: Int = 1,
    @Column(nullable = false)
    val gameMode: String = ""
)

@Repository
interface HiraganaQuestionRepository : JpaRepository<HiraganaQuestion, UUID>{

    fun countByGameMode(gameMode: String): Long
    fun findAllByHiraganaIn(hiragana: List<String>): List<HiraganaQuestion>

}