package io.vrto.movielist

import com.beust.klaxon.Klaxon
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking

typealias UUID = String

data class BoardDetails(val name: String)
data class Board(val name: String, val lists: List<CardList>)
data class CardList(val id: UUID, val name: String, val cards: List<Card>)
data class Card(val id: UUID, val name: String)
data class CardDetails(val name: String, val labels: List<Label>)
data class Label(val name: String)

data class TrelloApi(val key: String, val token: String) {

    fun resource(path: String, queryParams: String = "") =
        "https://api.trello.com/1$path".withTrelloAuth() + queryParams

    private fun String.withTrelloAuth() = "$this?key=$key&token=$token"
}

class TrelloQueries(private val trello: TrelloApi,
                    private val client: HttpClient = HttpClient(),
                    private val klaxon: Klaxon = Klaxon()) {

    fun loadBoard(): Board = runBlocking {
        val details = async(Dispatchers.IO) {
            val json = getJson("/boards/0Wwm6zzk")
            klaxon.parse<BoardDetails>(json)!!
        }
        val lists = async(Dispatchers.IO) {
            val json = getJson(
                path = "/boards/0Wwm6zzk/lists",
                queryParams = "&cards=open&card_fields=name&filter=open&fields=name")
            klaxon.parseArray<CardList>(json) ?: emptyList()
        }
        Board(details.await().name, lists.await())
    }

    fun loadCard(id: UUID): CardDetails? = runBlocking {
        val json = getJson("/cards/$id", queryParams = "&member_fields=all")
        klaxon.parse<CardDetails>(json)
    }

    private suspend fun getJson(path: String, queryParams: String = "") =
        client.get<String>(trello.resource(path, queryParams))
}

data class SaveBoardCommand(val lists: List<CardList>) {
    data class CardList(val name: String, val cards: List<Card>)
    data class Card(val name: String, val watchTogether: Boolean = false)
}

class TrelloCommandHandlers(private val trello: TrelloApi,
                            private val client: HttpClient = HttpClient(),
                            private val klaxon: Klaxon = Klaxon(),
                            private val trelloQueries: TrelloQueries = TrelloQueries(trello, client, klaxon)) {

    fun clearBoard() = runBlocking {
        val lists = trelloQueries.loadBoard().lists
        lists.map { list ->
            async(Dispatchers.IO) {
                client.put<Unit>(trello.resource(path = "/lists/${list.id}/closed", queryParams = "&value=true"))
            }
        }.awaitAll()
    }

    fun saveBoard(command: SaveBoardCommand) = runBlocking {
        command.lists.map { list ->
            async(Dispatchers.IO) {
                val listId = client.post<String>(trello.resource(
                    path = "/lists",
                    queryParams = "&name=${list.name}&idBoard=5e73ef63456f841d39221d80")).toResource()
                list.cards.map { addCard(it, listId) }
                println("${list.name} saved!")
            }
        }.awaitAll()
    }

    private suspend fun addCard(card: SaveBoardCommand.Card, listId: CreatedResource) {
        val cardId = client.post<String>(trello.resource(
            path = "/cards",
            queryParams = "&idList=${listId.id}&name=${card.name}")).toResource()
        if (card.watchTogether) {
            client.post<Unit>(trello.resource(
                path = "/cards/${cardId.id}/idLabels",
                queryParams = "&value=$watchTogetherLabel"))
        }
    }

    private fun String.toResource() = klaxon.parse<CreatedResource>(this)!!
}

data class CreatedResource(val id: UUID)

const val watchTogetherLabel = "5e73ef637669b22549293a75"