package io.vrto.movielist

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.jsoup.Connection.Method.POST
import org.jsoup.Connection.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.select.Elements

data class CsfdCredentials(val login: String, val pw: String)

class Csfd(val credentials: CsfdCredentials) {

    lateinit var cookies: Map<String, String>

    fun loadIndex() = Jsoup.connect("https://www.csfd.cz").get()

    fun homePage(): Response = Jsoup.connect("https://cas.csfd.cz/login?s=aHR0cHM6Ly93d3cuY3NmZC5jei8%3D")
        .data("username", credentials.login)
        .data("password", credentials.pw)
        .method(POST)
        .execute()
        .also {
            this.cookies = it.cookies()
            println("Logged ino CSFD.cz as ${credentials.login}")
        }

    fun loadWatchList(): List<MovieReference> = homePage().watchList().toMovies()

    fun loadMovieDetails(watchlist: List<MovieReference>): List<CsfdMovie> = runBlocking {
        watchlist.map { movie ->
            async(Dispatchers.IO) {
                val page = fetchMoviePage(movie)
                println("Movie details for ${movie.name} fetched!")

                val rating = page.select("#rating > h2").text().replace("%", "").ifEmpty { "N/A" }
                val note = page.select("#frm-privateNoteForm > div > div").text()
                    .takeUnless { it == NO_NOTE } ?: "N/A"
                CsfdMovie(movie.name, rating, note)
            }
        }.awaitAll()
    }

    private fun fetchMoviePage(movie: MovieReference): Document =
        Jsoup.connect("https://www.csfd.cz/${movie.link}").cookies(cookies).get()

    private fun Response.watchList(): Document {
        val watchListUrl = this.parse().select("#user-menu > ul.first > li:nth-child(4) > a").first().attr("href")
        return Jsoup.connect("https://www.csfd.cz/$watchListUrl").cookies(cookies).get()
    }
}

private fun Document.toMovies(): List<MovieReference> {
    val watchList = this.select("#frm-watchlistOperationsForm > ul")
    return (1..watchList.moviesCount()).map {
        val link = watchList.selectMovieLink(it)
        link.toMovie()
    }
}

private fun Elements.moviesCount() = select("li").size

private fun Elements.selectMovieLink(index: Int) = select("li:nth-child($index) > table > tbody > tr > td.film > a")

private fun Elements.toMovie() = MovieReference(this.text(), this.attr("href"))

data class MovieReference(val name: String, val link: String)
data class CsfdMovie(val name: String, val rating: String, val note: String)

private const val NO_NOTE = "zatím k tomuto filmu nemáš soukromou poznámku"