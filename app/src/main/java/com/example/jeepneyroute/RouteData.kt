package com.example.jeepneyroute

data class RouteConnection(
    val from: String,
    val to: String,
    val distanceKm: Double
)

object RouteData {

    // The ONLY jeepney terminals that operate
    val terminalRoutes = listOf(
        RouteConnection("Lian", "Calatagan", 25.1),
        RouteConnection("Nasugbu", "Tuy", 17.0),
        RouteConnection("Nasugbu", "Balayan", 22.7),
        RouteConnection("Calatagan", "Balayan", 25.0),
        RouteConnection("Tuy", "Balayan", 12.0),
        RouteConnection("Tuy", "Calaca", 23.0),
        RouteConnection("Balayan", "Lemery", 30.0),
        RouteConnection("Balayan", "Taal", 25.0),
        RouteConnection("Balayan", "Calaca", 10.0),
        RouteConnection("Calaca", "Lemery", 13.5),
        RouteConnection("Calaca", "Taal", 13.0),
        RouteConnection("Lemery", "Taal", 3.0),
    )

    // Both directions
    val connections = terminalRoutes +
            terminalRoutes.map { RouteConnection(it.to, it.from, it.distanceKm) }

    // Distance if a terminal operates this exact pair, otherwise null
    fun directDistance(from: String, to: String): Double? =
        connections.firstOrNull { it.from == from && it.to == to }?.distanceKm

    // Towns reachable by a terminal from this origin
    fun destinationsFrom(origin: String): List<String> =
        connections.filter { it.from == origin }.map { it.to }
}