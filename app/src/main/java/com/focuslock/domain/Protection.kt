package com.focuslock.domain

import android.graphics.Bitmap
import com.focuslock.data.AppSettingsEntity
import com.focuslock.data.ChallengeEntity
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime

enum class ContentLevel { SAFE, SUGGESTIVE, SEXUAL, EXPLICIT }

/** Classificador visual local. Nenhum modelo vem embutido: sem modelo, `available` é falso e nada é bloqueado por imagem. */
interface ImageClassifier {
    val available: Boolean
    val description: String
    fun classify(bitmap: Bitmap): ContentLevel
}

class UnavailableImageClassifier : ImageClassifier {
    override val available = false
    override val description = "Nenhum classificador"
    override fun classify(bitmap: Bitmap) = ContentLevel.SAFE
}

object DomainClassifier {
    private val explicit = listOf("porn", "xxx", "xvideos", "xnxx", "xhamster", "redtube", "youporn", "hentai", "brazzers", "spankbang", "rule34", "tube8")
    private val sexual = listOf("nsfw", "erotic", "nude", "chaturbate", "stripchat", "livejasmin", "onlyfans", "fansly", "camgirl")
    private val suggestive = listOf("playboy", "sexcam", "sexy")

    fun classify(host: String): ContentLevel {
        val h = host.lowercase()
        return when {
            explicit.any { h.contains(it) } -> ContentLevel.EXPLICIT
            sexual.any { h.contains(it) } -> ContentLevel.SEXUAL
            suggestive.any { h.contains(it) } -> ContentLevel.SUGGESTIVE
            else -> ContentLevel.SAFE
        }
    }

    fun shouldBlock(level: ContentLevel, minBlocked: ContentLevel): Boolean =
        level != ContentLevel.SAFE && level.ordinal >= minBlocked.ordinal
}

object Protection {
    fun endOf(ch: ChallengeEntity): LocalDateTime =
        LocalDate.parse(ch.startDate).plusDays(ch.totalDays.toLong()).atStartOfDay()

    fun isActive(ch: ChallengeEntity?, now: LocalDateTime = LocalDateTime.now()): Boolean =
        ch != null && now.isBefore(endOf(ch))

    fun hardcoreActive(st: AppSettingsEntity, now: LocalDateTime = LocalDateTime.now()): Boolean =
        st.hardcore && st.hardcoreEnd.isNotEmpty() && now.isBefore(LocalDateTime.parse(st.hardcoreEnd))

    fun remainingText(ch: ChallengeEntity, now: LocalDateTime = LocalDateTime.now()): String {
        val d = Duration.between(now, endOf(ch))
        if (d.isNegative) return "0 dias 00 horas 00 minutos"
        return "${d.toDays()} dias ${"%02d".format(d.toHours() % 24)} horas ${"%02d".format(d.toMinutes() % 60)} minutos"
    }

    fun domainBlocked(host: String, userDomains: Set<String>, adult: Boolean, minLevel: Int): Boolean {
        val h = host.lowercase().trimEnd('.')
        if (userDomains.any { h == it || h.endsWith(".$it") }) return true
        val min = ContentLevel.entries[minLevel.coerceIn(1, 3)]
        if (!adult) return false
        return AdultDomains.matches(h) || DomainClassifier.shouldBlock(DomainClassifier.classify(h), min)
    }
}

data class AchievementDef(val id: String, val title: String, val description: String)

object Achievements {
    val all = listOf(
        AchievementDef("first_day", "Primeiro passo", "Conclua o seu primeiro dia."),
        AchievementDef("week", "Semana firme", "7 dias seguidos."),
        AchievementDef("month", "Mês de ferro", "30 dias seguidos."),
        AchievementDef("goal", "Meta batida", "Conclua um objetivo."),
        AchievementDef("xp1000", "1.000 XP", "Alcance 1.000 XP."),
        AchievementDef("block", "Resistência", "Tenha um aplicativo ou site bloqueado.")
    )

    fun earned(best: Int, days: Int, goalsDone: Int, xp: Int, blocks: Int): Set<String> = buildSet {
        if (days >= 1) add("first_day")
        if (best >= 7) add("week")
        if (best >= 30) add("month")
        if (goalsDone >= 1) add("goal")
        if (xp >= 1000) add("xp1000")
        if (blocks >= 1) add("block")
    }
}

/** Lista embutida de sites adultos conhecidos, bloqueados sempre que "Conteúdo adulto" estiver ligado. */
object AdultDomains {
    private val tlds = listOf(".xxx", ".porn", ".adult", ".sex")

    private val list = setOf(
        "pornhub.com", "xvideos.com", "xnxx.com", "xhamster.com", "redtube.com", "youporn.com", "tube8.com",
        "spankbang.com", "brazzers.com", "chaturbate.com", "stripchat.com", "bongacams.com", "livejasmin.com",
        "cam4.com", "myfreecams.com", "onlyfans.com", "fansly.com", "eporner.com", "beeg.com", "porn.com",
        "txxx.com", "hclips.com", "tnaflix.com", "drtuber.com", "sunporno.com", "xtube.com", "thumbzilla.com",
        "motherless.com", "e-hentai.org", "nhentai.net", "hanime.tv", "literotica.com", "fetlife.com",
        "naughtyamerica.com", "realitykings.com", "bangbros.com", "mofos.com", "pornpics.com", "xxxbunker.com",
        "porntrex.com", "pornone.com", "4tube.com", "fapello.com", "erome.com", "javhd.com", "gelbooru.com",
        "e621.net", "rule34.xxx", "pornhd.com", "xvideos.red", "xnxx.tv", "porno.com", "sexo.com", "camsoda.com",
        "flirt4free.com", "adultfriendfinder.com", "ashleymadison.com", "pornhubpremium.com", "youjizz.com",
        "slutload.com", "hotmovies.com", "playboy.com", "nudevista.com", "camwhores.tv", "heavy-r.com"
    )

    fun matches(host: String): Boolean {
        val h = host.lowercase().trimEnd('.')
        if (tlds.any { h.endsWith(it) }) return true
        val parts = h.split('.')
        for (i in parts.indices) {
            if (parts.subList(i, parts.size).joinToString(".") in list) return true
        }
        return false
    }
}
