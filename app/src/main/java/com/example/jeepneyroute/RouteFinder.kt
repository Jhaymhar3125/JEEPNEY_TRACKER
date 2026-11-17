package com.example.jeepneyroute

abstract class RouteFinder {

    abstract val name: String

    abstract fun findRoute(start: String, destination: String): RouteResult?

    fun isValidTrip(start: String, destination: String): Boolean =
        start.isNotBlank() && destination.isNotBlank() && start != destination
}

class DirectRouteFinder : RouteFinder() {
    override val name = "Direct terminal route"

    override fun findRoute(start: String, destination: String): RouteResult? {
        if (!isValidTrip(start, destination)) return null
        val distance = RouteData.directDistance(start, destination) ?: return null
        return RouteResult(listOf(start, destination), distance)
    }
}

class TransferRouteFinder : RouteFinder() {
    override val name = "Shortest route with transfers"

    override fun findRoute(start: String, destination: String): RouteResult? {
        if (!isValidTrip(start, destination)) return null
        return ShortestPath.findShortestPath(start, destination)
    }
}