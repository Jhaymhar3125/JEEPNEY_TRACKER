
package com.example.jeepneyroute

import java.util.PriorityQueue

data class RouteResult(
    val path: List<String>,
    val totalDistance: Double
)

object ShortestPath {

    fun findShortestPath(
        start: String,
        destination: String
    ): RouteResult? {

        val distances = mutableMapOf<String, Double>()
        val previous = mutableMapOf<String, String>()

        val queue = PriorityQueue<Pair<String, Double>>(
            compareBy { it.second }
        )

        for (place in municipalities) {
            distances[place] = Double.POSITIVE_INFINITY
        }

        if (start !in distances || destination !in distances) {
            return null
        }

        distances[start] = 0.0
        queue.add(Pair(start, 0.0))

        while (queue.isNotEmpty()) {

            val current = queue.poll()
            val currentPlace = current.first
            val currentDistance = current.second

            if (currentDistance > distances[currentPlace]!!) {
                continue
            }

            if (currentPlace == destination) {
                break
            }

            // Check available connections
            for (connection in RouteData.connections) {

                if (connection.from == currentPlace) {

                    val nextPlace = connection.to
                    val newDistance =
                        currentDistance + connection.distanceKm

                    if (newDistance < (distances[nextPlace]
                            ?: Double.POSITIVE_INFINITY)) {

                        distances[nextPlace] = newDistance
                        previous[nextPlace] = currentPlace

                        queue.add(
                            Pair(nextPlace, newDistance)
                        )
                    }
                }
            }
        }

        val totalDistance =
            distances[destination] ?: Double.POSITIVE_INFINITY

        if (totalDistance == Double.POSITIVE_INFINITY) {
            return null
        }

        // Reconstruct the shortest path
        val path = mutableListOf<String>()
        var currentPlace: String? = destination

        while (currentPlace != null) {
            path.add(currentPlace)
            currentPlace = previous[currentPlace]
        }

        path.reverse()

        return RouteResult(
            path = path,
            totalDistance = totalDistance
        )
    }
}


