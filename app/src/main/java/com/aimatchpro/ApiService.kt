package com.aimatchpro

import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import okhttp3.OkHttpClient
import okhttp3.Interceptor

// حط الكي ديالك هنا بين " "
const val API_KEY = "a5da94fc-d777-4a5b-a8fb-facd1c42395c"

data class FixtureResponse(val response: List<FixtureData>)
data class FixtureData(
    val fixture: FixtureInfo,
    val teams: Teams,
    val goals: Goals
)
data class FixtureInfo(val status: Status)
data class Status(val long: String, val elapsed: Int?)
data class Teams(val home: Team, val away: Team)
data class Team(val name: String, val logo: String)
data class Goals(val home: Int?, val away: Int?)

interface ApiService {
    @GET("fixtures?live=all")
    suspend fun getLiveMatches(): FixtureResponse

    @GET("fixtures")
    suspend fun getTodayMatches(@Query("date") date: String): FixtureResponse
}

object ApiClient {
    val api: ApiService by lazy {
        val authInterceptor = Interceptor { chain ->
            val request = chain.request().newBuilder()
                .addHeader("x-apisports-key", API_KEY)
                .build()
            chain.proceed(request)
        }
        val client = OkHttpClient.Builder().addInterceptor(authInterceptor).build()
        Retrofit.Builder()
            .baseUrl("https://v3.football.api-sports.io/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }
}

fun getPrediction(home: String, away: String): String {
    val hash = (home + away).hashCode()
    val homeWin = 30 + (kotlin.math.abs(hash) % 40)
    val draw = 20 + (kotlin.math.abs(hash / 2) % 20)
    val awayWin = 100 - homeWin - draw
    return "1: $homeWin%  X: $draw%  2: $awayWin%"
}
