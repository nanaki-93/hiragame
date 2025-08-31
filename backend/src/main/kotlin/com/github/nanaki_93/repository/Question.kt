package com.github.nanaki_93.repository

import com.github.nanaki_93.models.GameMode
import com.github.nanaki_93.models.Level
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.proxy.HibernateProxy
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.time.LocalDateTime
import java.util.UUID


@Entity
@Table(name = "question")
data class Question(
    @Id
    @GeneratedValue
    @Column(nullable = false)
    val id: UUID? = null,
    @Column(nullable = false, unique = true)
    val japanese: String,
    @Column(nullable = false)
    val romanization: String,
    @Column(nullable = true)
    val translation: String,
    @Column(nullable = false)
    val topic: String = "",
    @Column(nullable = false)
    val level: String = Level.N5.name,
    @Column(nullable = false)
    val gameMode: String = GameMode.SIGN.name,
    @Column(nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),
    @Column(nullable = false)
    val hasKatakana: Boolean = false,
    @Column(nullable = false)
    val hasKanji: Boolean = false,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null) return false
        val oEffectiveClass =
            if (other is HibernateProxy) other.hibernateLazyInitializer.persistentClass else other.javaClass
        val thisEffectiveClass = this.javaClass
        if (thisEffectiveClass != oEffectiveClass) return false
        other as Question

        return if (id != null) id == other.id else japanese.equals(other)
    }

    override fun hashCode(): Int =
        javaClass.hashCode()

    override fun toString(): String {
        return "Question(id=$id, japanese='$japanese', romanization='$romanization', translation='$translation', topic='$topic', level='$level', gameMode='$gameMode', createdAt=$createdAt, hasKatakana=$hasKatakana, hasKanji=$hasKanji)"
    }


}


@Repository
interface QuestionRepository : JpaRepository<Question, UUID> {

    fun countByGameMode(gameMode: String): Long
    fun findAllByJapaneseIn(japanese: List<String>): List<Question>

}