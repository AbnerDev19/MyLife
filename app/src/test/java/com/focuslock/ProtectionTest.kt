package com.focuslock

import com.focuslock.data.ChallengeEntity
import com.focuslock.domain.ContentLevel
import com.focuslock.domain.DomainClassifier
import com.focuslock.domain.Protection
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProtectionTest {
    private val ch = ChallengeEntity(name = "t", startDate = "2026-10-01", totalDays = 30)

    @Test fun classifierLevels() {
        assertEquals(ContentLevel.EXPLICIT, DomainClassifier.classify("www.xvideos.com"))
        assertEquals(ContentLevel.SAFE, DomainClassifier.classify("sussex.ac.uk"))
        assertTrue(DomainClassifier.shouldBlock(ContentLevel.EXPLICIT, ContentLevel.SEXUAL))
        assertFalse(DomainClassifier.shouldBlock(ContentLevel.SUGGESTIVE, ContentLevel.SEXUAL))
    }

    @Test fun userDomainsMatchSubdomains() {
        assertTrue(Protection.domainBlocked("m.exemplo.com", setOf("exemplo.com"), false, 2))
        assertFalse(Protection.domainBlocked("outroexemplo.com", setOf("exemplo.com"), false, 2))
        assertTrue(Protection.domainBlocked("site-porn.net", emptySet(), true, 2))
        assertFalse(Protection.domainBlocked("site-porn.net", emptySet(), false, 2))
    }

    @Test fun challengeActiveUntilEndDate() {
        assertTrue(Protection.isActive(ch, LocalDateTime.of(2026, 10, 30, 23, 0)))
        assertFalse(Protection.isActive(ch, LocalDateTime.of(2026, 10, 31, 0, 0)))
    }

    @Test fun remainingTextFormatsDaysHoursMinutes() {
        assertEquals("1 dias 02 horas 30 minutos", Protection.remainingText(ch, LocalDateTime.of(2026, 10, 29, 21, 30)))
    }
}

class AdultDomainsTest {
    @Test fun builtInListMatchesSubdomainsAndTlds() {
        assertTrue(com.focuslock.domain.AdultDomains.matches("www.pornhub.com"))
        assertTrue(com.focuslock.domain.AdultDomains.matches("exemplo.xxx"))
        assertFalse(com.focuslock.domain.AdultDomains.matches("wikipedia.org"))
    }

    @Test fun adultFilterBlocksListedSitesWithoutUserList() {
        assertTrue(Protection.domainBlocked("br.xhamster.com", emptySet(), true, 3))
        assertFalse(Protection.domainBlocked("br.xhamster.com", emptySet(), false, 3))
    }
}
