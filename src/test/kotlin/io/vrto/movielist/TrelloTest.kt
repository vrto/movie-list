package io.vrto.movielist

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class TrelloTest {

    val trello = createTrelloApi()

    val queries = TrelloQueries(trello)
    val commands = TrelloCommandHandlers(trello)

    @Test
    fun `should grab the MovieList board`() {
        val board = queries.loadBoard()
        assertThat(board).isNotNull
        assertThat(board.name).isEqualTo("MovieList")
    }

    @Test
    fun `should clear board`() {
        commands.clearBoard()

        val cleanedBoard = queries.loadBoard()
        assertThat(cleanedBoard.lists).isEmpty()
    }

    @Test
    fun `should save the board`() {
        commands.clearBoard()

        commands.saveBoard(
            SaveBoardCommand(lists = listOf(SaveBoardCommand.CardList(
                name = "Test List",
                cards = listOf(
                    SaveBoardCommand.Card(name = "Test Card 1"),
                    SaveBoardCommand.Card(name = "Test Card 2", watchTogether = true)
                )))))

        val updatedBoard = queries.loadBoard()
        assertThat(updatedBoard.name).isEqualTo("MovieList")
        assertThat(updatedBoard.lists).hasSize(1)
        assertThat(updatedBoard.lists[0].name).isEqualTo("Test List")
        assertThat(updatedBoard.lists[0].cards).extracting("name")
            .containsExactlyInAnyOrder("Test Card 1", "Test Card 2")

        val cardDetails = queries.loadCard(updatedBoard.lists[0].cards[1].id)
        assertThat(cardDetails?.name).isEqualTo("Test Card 2")
        assertThat(cardDetails?.labels).extracting("name").containsExactly("Together")
    }

    private fun createTrelloApi() : TrelloApi {
        val text = this.javaClass.getResource("/trello.auth").readText()
        val lines = text.split("\n")
        return TrelloApi(key = lines[0], token = lines[1])
    }
}