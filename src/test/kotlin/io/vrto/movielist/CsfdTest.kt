package io.vrto.movielist

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class CsfdTest {

    val credentials = loadCredentialsFromFile()
    val csfd = Csfd(credentials)

    @Test
    fun `should grab the index page`() {
        val index = csfd.loadIndex()
        assertThat(index).isNotNull
        assertThat(index.title()).isEqualTo("Česko-Slovenská filmová databáze | ČSFD.cz")
    }

    @Test
    fun `should log in`() {
        val loggedContent = csfd.homePage().parse()
        val username = loggedContent.select("#user-menu > ul.first > li:nth-child(1) > h3 > a").text()
        assertThat(username).isEqualTo(credentials.login)
    }

    @Test
    fun `should load the Watchlist`() {
        val watchlist = csfd.loadWatchList()
        println("Testing watchlist: $watchlist")

        assertThat(watchlist).isNotEmpty
        watchlist.forEach {
            assertThat(it.name).isNotBlank()
        }
    }

    @Test
    fun `should load all notes for the Watchlist`() {
        val watchlist = csfd.loadWatchList()
        val moviesWithNotes = csfd.loadMovieDetails(watchlist)

        println("Test private notes")
        moviesWithNotes.forEach { println(it) }

        assertThat(watchlist.size).isEqualTo(moviesWithNotes.size)
        moviesWithNotes.forEach {
            assertThat(it.name).isNotBlank()
            assertThat(it.note).isNotBlank()
        }
    }

    private fun loadCredentialsFromFile() : CsfdCredentials {
        val text = this.javaClass.getResource("/csfd.credentials").readText()
        val lines = text.split("\n")
        return CsfdCredentials(login = lines[0], pw = lines[1])
    }
}