package io.vrto.movielist

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class MovieListTest {

    val csfd = mockk<Csfd>()
    val trelloCommandHandlers = mockk<TrelloCommandHandlers>()

    val movieList = MovieList(csfd, trelloCommandHandlers)

    @Test
    fun `should clear the board when the watchlist is empty`() {
        every { csfd.loadWatchList() } returns emptyList()
        every { trelloCommandHandlers.clearBoard() } returns emptyList()
        every { trelloCommandHandlers.saveBoard(any()) } returns emptyList()

        movieList.refresh()

        verify(exactly = 0) { csfd.loadMovieDetails(any()) }
        verifySaveBoard {
            assertThat(lists).isEmpty()
        }
    }

    @Test
    fun `should group CSFD movies by service and create the Trello board`() {
        val watchlist = listOf(
            MovieReference("Pulp Fiction", "link"),
            MovieReference("Big Lebowski", "link"),
            MovieReference("Weird Movie", "link"),
            MovieReference("Clockwork Orange", "link"),
            MovieReference("Silicon Valley", "link"),
            MovieReference("FRIENDS", "link"),
            MovieReference("You", "link")
        )

        every { csfd.loadWatchList() } returns watchlist
        every { csfd.loadMovieDetails(watchlist) } returns listOf(
            CsfdMovie("Pulp Fiction", "94", "iTunes"),
            CsfdMovie("Big Lebowski", "91", "iTunes, Lucka"),
            CsfdMovie("Weird Movie", "54", "N/A"),
            CsfdMovie("Silicon Valley", "82", "HBO"),
            CsfdMovie("FRIENDS", "90", "Netflix, Lucka"),
            CsfdMovie("You", "77", "Netflix")
        )
        every { trelloCommandHandlers.clearBoard() } returns emptyList()
        every { trelloCommandHandlers.saveBoard(any()) } returns emptyList()

        movieList.refresh()

        verifySaveBoard {
            assertThat(lists).isEqualTo(
                listOf(
                    "iTunes|Pulp Fiction (94),Big Lebowski (91)*".parseCardList(),
                    "N/A|Weird Movie (54)".parseCardList(),
                    "HBO|Silicon Valley (82)".parseCardList(),
                    "Netflix|FRIENDS (90)*,You (77)".parseCardList()
                )
            )
        }
    }

    private fun verifySaveBoard(boardCheck: SaveBoardCommand.() -> Unit) {
        val slot = slot<SaveBoardCommand>()
        verify { trelloCommandHandlers.saveBoard(capture(slot)) }
        boardCheck.invoke(slot.captured)
    }

    // "List|Movie1,Movie2
    private fun String.parseCardList(): SaveBoardCommand.CardList {
        val parts = this.split("|")
        val movies = parts[1].split(",")
        return SaveBoardCommand.CardList(parts[0], movies.map {
            SaveBoardCommand.Card(
                name = it.removeLastAsteriskIfNeeded(),
                watchTogether = it.endsWith("*")
            )
        })
    }
}

private fun String.removeLastAsteriskIfNeeded() = if (this.last() == '*') this.dropLast(1) else this
