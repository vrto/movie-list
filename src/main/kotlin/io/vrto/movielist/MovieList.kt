package io.vrto.movielist

import com.xenomachina.argparser.ArgParser
import io.vrto.movielist.Stepper.step
import kotlin.time.ExperimentalTime
import kotlin.time.measureTime

class ProgramArgs(parser: ArgParser) {
    val csfdLogin by parser.storing("--csfd-login", help = "CSFD.cz uername")
    val csfdPw by parser.storing("--csfd-pw", help = "CSFD.cz user password")
    val trelloKey by parser.storing("--trello-key", help = "Trello API key")
    val trelloToken by parser.storing("--trello-token", help = "Trello API token")
}

@ExperimentalTime
fun main(args: Array<String>) {
    println(args.toList())
    ArgParser(args).parseInto(::ProgramArgs).run {

        val csfd = Csfd(CsfdCredentials(csfdLogin, csfdPw))
        val trelloCommandHandlers = TrelloCommandHandlers(TrelloApi(trelloKey, trelloToken))

        val duration = measureTime {
            MovieList(csfd, trelloCommandHandlers).refresh()
        }
        println("\nProgram completed in ${duration.inSeconds} seconds.")
    }
}

class MovieList(
    private val csfd: Csfd,
    private val trelloCommandHandlers: TrelloCommandHandlers
) {

    fun refresh() {
        step("Fetching watchlist")
        val watchlist = csfd.loadWatchList()
        println("Watchlist loaded: [${watchlist.printMovies()}]")

        step("Loading private notes")
        val moviesWithNotes = if (watchlist.isNotEmpty()) csfd.loadMovieDetails(watchlist) else emptyList()
        println("Private notes loaded!")

        step("Clearing MovieList Trello board")
        trelloCommandHandlers.clearBoard()
        println("Trello board cleared")

        step("Creating new lists")
        val moviesByServices = moviesWithNotes.organizeByServices()
        val lists = moviesByServices.toTrelloLists()
        println("Movie lists created: [${lists.printLists()}]")

        step("Attempting to save the new board")
        trelloCommandHandlers.saveBoard(SaveBoardCommand(lists))
        print("Board saved!")
    }
}

data class Movie(val name: String, val watchTogether: Boolean)

private fun List<MovieReference>.printMovies(): String =
    this.joinToString { it.name }

private fun List<SaveBoardCommand.CardList>.printLists() =
    this.joinToString { """${it.name}: [${it.cards.printCards()}]""" }

private fun List<SaveBoardCommand.Card>.printCards() =
    this.joinToString { it.name }

typealias MovieServices = Map<String, List<Movie>>

private fun List<CsfdMovie>.organizeByServices(): MovieServices {
    val moviesByServices: Map<String, List<CsfdMovie>> = this.groupBy { it.note.toService() }
    return moviesByServices.map { entry ->
        val serviceName = entry.key // Netflix, HBO, eg.
        val movies = entry.value.map {
            Movie(
                name = it.name + " (${it.rating})",
                watchTogether = it.note.contains(WATCH_TOGETHER_NOTE)
            )
        }
        serviceName to movies
    }.toMap()
}

private fun String.toService(): String = SERVICES.find { this.toLowerCase().contains(it.toLowerCase()) } ?: "N/A"

private fun MovieServices.toTrelloLists(): List<SaveBoardCommand.CardList> = this.map {
    SaveBoardCommand.CardList(name = it.key, cards = it.value.toCards())
}

private fun List<Movie>.toCards() = this.map { SaveBoardCommand.Card(name = it.name, watchTogether = it.watchTogether) }

object Stepper {
    var count = 1

    fun step(description: String) {
        println("\n========= Step $count: $description")
        count += 1
    }
}

private val SERVICES = listOf("Netflix", "iTunes", "Apple TV+", "HBO", "Amazon", "Kino")
private const val WATCH_TOGETHER_NOTE = "Lucka"