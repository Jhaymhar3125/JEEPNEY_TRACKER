package com.example.jeepneyroute

import android.content.Intent
import android.provider.Settings
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.runtime.withFrameMillis
import androidx.compose.material3.LinearProgressIndicator
import org.maplibre.android.style.layers.PropertyFactory.circleOpacity
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject
import android.annotation.SuppressLint
import android.os.Bundle
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.jeepneyroute.ui.theme.JeepneyRouteTheme
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeColor
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeWidth
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ButtonDefaults
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color


val municipalities = listOf(
    "Nasugbu",
    "Lian",
    "Calatagan",
    "Tuy",
    "Balayan",
    "Calaca",
    "Lemery",
    "Taal")
val townCoords = mapOf(
    "Nasugbu"   to LatLng(14.0687, 120.6325),
    "Lian"      to LatLng(14.0411, 120.6544),
    "Calatagan" to LatLng(13.8325, 120.6317),
    "Tuy"       to LatLng(14.0165, 120.7300),
    "Balayan"   to LatLng(13.9390, 120.7330),
    "Calaca"    to LatLng(13.9333, 120.8139),
    "Lemery"    to LatLng(13.8847, 120.9144),
    "Taal"      to LatLng(13.8789, 120.9230))

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            JeepneyRouteTheme {
                JeepneyHomeScreen()
            }
        }
    }
}

@SuppressLint("DefaultLocale")
@Composable
fun JeepneyHomeScreen() {
    var origin by remember { mutableStateOf("") }
    var destination by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("Choose your starting point and destination.") }
    var routeResult by remember {mutableStateOf<RouteResult?>(null)}
    var tripKey by remember { mutableStateOf(0) }
    var tripProgress by remember { mutableStateOf(0f) }

    val context = LocalContext.current
    var tracking by remember { mutableStateOf(false) }
    var liveLocation by remember { mutableStateOf<LatLng?>(null) }

    val startTracking: () -> Unit = {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val locationOn = lm.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)

        if (locationOn) {
            tracking = true
            destination = ""
            routeResult = null
            tripKey = 0
            tripProgress = 0f
            message = "Live tracking started. Waiting for GPS signal..."}
        else {
            message = "Location is turned off. Turn it on, then tap Start Live Tracking again."
            context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startTracking()
        } else {message = "Location permission was denied."}
    }

    DisposableEffect(tracking) {
        if (!tracking) return@DisposableEffect onDispose { }

        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                liveLocation = LatLng(location.latitude, location.longitude)}
            @Deprecated("Deprecated in Java")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}
        }

        try {
            if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 3f, listener)
            }
            if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 1000L, 3f, listener)
            }
        } catch (e: SecurityException) {
            tracking = false
        }

        onDispose { lm.removeUpdates(listener) }
    }

    val nearest = liveLocation?.let { nearestTown(it) }
    val liveOrigin: String? =
        if (tracking && nearest != null && nearest.second <= MAX_TERMINAL_KM * 1000) nearest.first
        else null

    val effectiveOrigin = if (tracking) (liveOrigin ?: "") else origin

    LaunchedEffect(effectiveOrigin) {
        if (effectiveOrigin.isNotBlank() &&
            destination !in RouteData.destinationsFrom(effectiveOrigin)
        ) {
            destination = ""
            routeResult = null
        }
    }
    Scaffold(modifier = Modifier.fillMaxSize(),
        containerColor = JeepBg
    ) { innerPadding ->

        Column(modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(Brush.horizontalGradient(listOf(JeepBlue, Color(0xFF1976D2)))
                    ).padding(20.dp)
            ) {
                Column {
                    Text(
                        text = "🚌 Jeepney Shortest Route",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .width(64.dp)
                            .height(4.dp)
                            .background(JeepYellow, RoundedCornerShape(2.dp))
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Batangas First District",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = JeepYellow
                    )
                    Text(
                        text = "Find your route. Travel smarter.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.85f)
                    )
                }
            }

            Card(modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {

                    Text(text = "Where are you going?",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = JeepBlue
                    )
                    Text(text = "🟢 Origin",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.DarkGray
                    )

                    if (tracking) {
                        Card(colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(text = when {
                                    liveLocation == null -> "Getting your location..."
                                    liveOrigin == null ->
                                        "You are outside the covered area. Nearest terminal: ${nearest?.first} " +
                                                "(${String.format("%.1f", (nearest?.second ?: 0.0) / 1000)} km away)"
                                    else -> "Your location: near $liveOrigin terminal"},
                                modifier = Modifier.padding(16.dp))
                        }
                    } else{
                        LocationDropdown(
                            label = "Origin",
                            selectedLocation = origin,
                            municipalities = municipalities,
                            onLocationSelected = {
                                origin = it
                                destination = ""
                                routeResult = null
                                tripKey = 0
                                tripProgress = 0f
                                message = "" })
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(text = "🔴Destination",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.DarkGray
                    )

                    LocationDropdown(label = "Destination",
                        selectedLocation = destination,
                        municipalities = if (effectiveOrigin.isBlank()) municipalities
                        else RouteData.destinationsFrom(effectiveOrigin),
                        onLocationSelected = {
                            destination = it
                            routeResult = null
                            message = ""
                        }
                    )

                    OutlinedButton(onClick = {
                            if (tracking) {
                                tracking = false
                                liveLocation = null
                                message = "Live tracking stopped."
                            } else {
                                tripKey = 0
                                val granted = ContextCompat.checkSelfPermission(
                                    context, Manifest.permission.ACCESS_FINE_LOCATION
                                ) == PackageManager.PERMISSION_GRANTED

                                if (granted) {
                                    startTracking()
                                } else {
                                    permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                                }
                            }
                        },
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(2.dp, if (tracking) JeepRed else JeepBlue),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = if (tracking) JeepRed else JeepBlue),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                    ) {
                        Text(
                            text = if (tracking) "⏹ Stop Live Tracking" else "📍 Start Live Tracking",
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Button(onClick = {
                            routeResult = null
                            tripKey = 0
                            tripProgress = 0f

                            if (tracking && liveOrigin == null) {
                                message = "Waiting for a location inside the covered area."

                            } else if (effectiveOrigin.isBlank() || destination.isBlank()) {
                                message = "Please select both locations."

                            } else if (effectiveOrigin == destination) {
                                message = "Your origin and destination are the same."

                            } else {
                                val finder: RouteFinder = DirectRouteFinder()
                                val result = finder.findRoute(effectiveOrigin, destination)

                                if (result == null) {
                                    message = "No jeepney terminal operates from $effectiveOrigin to $destination."
                                } else {
                                    routeResult = result
                                    message = "Jeepney available from $effectiveOrigin to $destination."
                                }
                            }
                        },
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = JeepBlue,
                            contentColor = Color.White),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                    ) {
                        Text("🔍 Find Route", fontWeight = FontWeight.Bold, fontSize = 16.sp)}
                }
            }

            Text(text = "🗺️ Batangas First District Map",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = JeepBlue)

            Box(modifier = Modifier
                    .fillMaxWidth()
                    .height(340.dp)
                    .border(4.dp, JeepBlue, RoundedCornerShape(20.dp))
                    .padding(4.dp)
                    .clip(RoundedCornerShape(16.dp))
            ) {
                MapLibreMapSection(
                    modifier = Modifier.fillMaxSize(),
                    routePath = routeResult?.path ?: emptyList(),
                    tripKey = tripKey,
                    liveLocation = liveLocation,
                    onProgress = {tripProgress = it})
            }

            if (message.isNotBlank()) {
                val style = statusStyle(message)
                Card(modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = style.container,
                        contentColor = style.content),
                    border = BorderStroke(1.5.dp, style.content.copy(alpha = 0.4f))
                ) {
                    Row(modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(style.icon, fontSize = 22.sp)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(text = message,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            routeResult?.let { result ->
                Card(modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(2.dp, JeepYellow),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(text = "🏁 Shortest Route Result",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = JeepBlue)

                        Spacer(modifier = Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier
                                    .size(12.dp)
                                    .background(JeepGreen, RoundedCornerShape(50)))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = result.path.joinToString("  →  "),
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                        Row(modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Column(modifier = Modifier
                                    .weight(1f)
                                    .background(Color(0xFFE3F0FF), RoundedCornerShape(14.dp))
                                    .padding(12.dp)
                            ) {
                                Text("Total Distance", style = MaterialTheme.typography.labelMedium)
                                Text("${String.format("%.1f", result.totalDistance)} km",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 20.sp,
                                    color = JeepBlue)
                            }
                            Column(modifier = Modifier
                                    .weight(1f)
                                    .background(Color(0xFFFFF4D6), RoundedCornerShape(14.dp))
                                    .padding(12.dp)
                            ){
                                Text("Remaining", style = MaterialTheme.typography.labelMedium)
                                Text("${String.format("%.1f", result.totalDistance * (1 - tripProgress))} km",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 20.sp,
                                    color = Color(0xFF7A5900))
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                        LinearProgressIndicator(
                            progress = {tripProgress},
                            color = JeepBlue,
                            trackColor = Color(0xFFDDE6F3),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(10.dp)
                                .clip(RoundedCornerShape(5.dp)))

                        Spacer(modifier = Modifier.height(14.dp))
                        Button(onClick = {
                                tripProgress = 0f
                                tripKey++},
                            enabled = !tracking,
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = JeepYellow,
                                contentColor = Color.Black),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                        ){
                            Text(text = if (tripKey == 0) "▶ Start Trip" else "🔄 Restart Trip",
                                fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Text(text = "Jeepney Shortest Route | Kotlin Android Application",
                style = MaterialTheme.typography.labelSmall,
                color = Color.Gray)
        }
    }
}

@Preview(showBackground = true)
@Composable
fun JeepneyHomePreview() {
    JeepneyRouteTheme{
        JeepneyHomeScreen()}
}

@Composable
fun LocationDropdown(
    label: String,
    selectedLocation: String,
    municipalities: List<String>,
    onLocationSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false)}
    Box(modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(
            onClick = { expanded = true },
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(
                2.dp,
                if (selectedLocation.isEmpty()) Color(0xFFB0BEC5) else JeepBlue),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = Color(0xFFF8FAFF),
                contentColor = if (selectedLocation.isEmpty()) Color.Gray else JeepBlue),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = if (selectedLocation.isEmpty()) "Select $label" else selectedLocation,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                Text("▼", fontSize = 12.sp)}
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(Color.White)
        ) {
            municipalities.forEach {municipality ->
                DropdownMenuItem(
                    text = {
                        Text("🚏 $municipality",fontWeight = FontWeight.Medium, color = Color.Black)},
                    onClick = {onLocationSelected(municipality)
                        expanded = false})
            }
        }
    }
}

@SuppressLint("ClickableViewAccessibility")
@Composable
fun MapLibreMapSection(
    modifier: Modifier = Modifier,
    routePath: List<String> = emptyList(),
    tripKey: Int = 0,
    liveLocation: LatLng? = null,
    onProgress: (Float) -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var mapRef by remember { mutableStateOf<MapLibreMap?>(null) }
    var styleReady by remember { mutableStateOf(false) }
    var roadPoints by remember { mutableStateOf<List<Point>>(emptyList()) }

    val mapView = remember {
        MapLibre.getInstance(context)
        MapView(context).apply { onCreate(null) }
    }

    DisposableEffect(lifecycleOwner, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onDestroy()
        }
    }

    AndroidView(
        factory = {
            mapView.apply{
                setOnTouchListener{v, event -> when (event.actionMasked)
                {MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE ->
                        v.parent.requestDisallowInterceptTouchEvent(true)
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                        v.parent.requestDisallowInterceptTouchEvent(false)}
                    false
                }

                getMapAsync { map ->
                    map.setStyle("https://tiles.openfreemap.org/styles/liberty") { style ->
                        val bounds = LatLngBounds.Builder()
                            .include(LatLng(13.75, 120.50))
                            .include(LatLng(14.22, 121.00))
                            .build()
                        map.setLatLngBoundsForCameraTarget(bounds)
                        map.setMinZoomPreference(9.0)
                        map.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, 20))

                        val townFeatures = townCoords.map { (_, ll) ->
                            Feature.fromGeometry(Point.fromLngLat(ll.longitude, ll.latitude))
                        }
                        style.addSource(GeoJsonSource("towns", FeatureCollection.fromFeatures(townFeatures)))
                        style.addLayer(
                            CircleLayer("towns-layer", "towns").withProperties(
                                circleRadius(6f),
                                circleColor("#D32F2F"),
                                circleStrokeColor("#FFFFFF"),
                                circleStrokeWidth(2f))
                        )
                        val empty = FeatureCollection.fromFeatures(emptyList<Feature>())
                        style.addSource(GeoJsonSource("route-done", empty))
                        style.addLayer(
                            LineLayer("route-done-layer", "route-done").withProperties(
                                lineColor("#9E9E9E"),
                                lineWidth(5f))
                        )

                        style.addSource(GeoJsonSource("route", FeatureCollection.fromFeatures(emptyList<Feature>())))
                        style.addLayer(
                            LineLayer("route-layer", "route").withProperties(
                                lineColor("#1976D2"),
                                lineWidth(5f))
                        )

                        style.addSource(GeoJsonSource("jeep", empty))
                        style.addLayer(
                            CircleLayer("jeep-layer", "jeep").withProperties(
                                circleRadius(13f),
                                circleColor("#FF9800"),
                                circleStrokeColor("#FFFFFF"),
                                circleStrokeWidth(3f))
                        )
                        style.addSource(GeoJsonSource("user", empty))
                        style.addLayer(
                            CircleLayer("user-halo", "user").withProperties(
                                circleRadius(22f),
                                circleColor("#1976D2"),
                                circleOpacity(0.25f))
                        )
                        style.addLayer(
                            CircleLayer("user-layer", "user").withProperties(
                                circleRadius(6f),
                                circleColor("#1976D2"),
                                circleStrokeColor("#FFFFFF"),
                                circleStrokeWidth(2f))
                        )
                        mapRef = map
                        styleReady = true
                    }
                }
            }
        },
        modifier = modifier
    )

    LaunchedEffect(routePath) {
        if (routePath.size < 2) {
            roadPoints = emptyList()
            return@LaunchedEffect
        }

        val allPoints = mutableListOf<Point>()
        routePath.zipWithNext().forEach { (fromName, toName) ->
            val a = townCoords[fromName] ?: return@forEach
            val b = townCoords[toName] ?: return@forEach

            val road = fetchRoadRoute(a, b)
                ?: listOf(
                    Point.fromLngLat(a.longitude, a.latitude),
                    Point.fromLngLat(b.longitude, b.latitude))
            allPoints.addAll(road)
        }
        roadPoints = allPoints
    }

    LaunchedEffect(roadPoints, tripKey, styleReady) {
        val style = mapRef?.style ?: return@LaunchedEffect
        val routeSrc = style.getSourceAs<GeoJsonSource>("route") ?: return@LaunchedEffect
        val doneSrc = style.getSourceAs<GeoJsonSource>("route-done") ?: return@LaunchedEffect
        val jeepSrc = style.getSourceAs<GeoJsonSource>("jeep") ?: return@LaunchedEffect
        val userSrc = style.getSourceAs<GeoJsonSource>("user") ?: return@LaunchedEffect
        val empty = FeatureCollection.fromFeatures(emptyList<Feature>())
        if (roadPoints.size < 2) {
            routeSrc.setGeoJson(empty)
            doneSrc.setGeoJson(empty)
            jeepSrc.setGeoJson(empty)
            userSrc.setGeoJson(empty)
            return@LaunchedEffect
        }

        routeSrc.setGeoJson(LineString.fromLngLats(roadPoints))
        doneSrc.setGeoJson(empty)
        jeepSrc.setGeoJson(roadPoints.first())
        userSrc.setGeoJson(roadPoints.first())
        if (tripKey == 0) return@LaunchedEffect

        val cum = DoubleArray(roadPoints.size)
        for (i in 1 until roadPoints.size) {
            val p = roadPoints[i - 1]
            val q = roadPoints[i]
            cum[i] = cum[i - 1] +
                    LatLng(p.latitude(), p.longitude()).distanceTo(LatLng(q.latitude(), q.longitude()))
        }
        val total = cum.last()
        if (total <= 0.0) return@LaunchedEffect
        val durationMs = 20_000f
        val startTime = withFrameMillis { it }
        var idx = 0

        while (true) {
            val now = withFrameMillis { it }
            val frac = ((now - startTime) / durationMs).coerceIn(0f, 1f)
            val target = frac * total

            while (idx < cum.size - 2 && cum[idx + 1] < target) idx++
            val segLen = cum[idx + 1] - cum[idx]
            val t = if (segLen > 0) ((target - cum[idx]) / segLen).coerceIn(0.0, 1.0) else 0.0
            val a = roadPoints[idx]
            val b = roadPoints[idx + 1]
            val cur = Point.fromLngLat(
                a.longitude() + (b.longitude() - a.longitude()) * t,
                a.latitude() + (b.latitude() - a.latitude()) * t)
            val remaining = listOf(cur) + roadPoints.subList(idx + 1, roadPoints.size)
            val done = roadPoints.subList(0, idx + 1) + cur
            routeSrc.setGeoJson(LineString.fromLngLats(remaining))
            doneSrc.setGeoJson(LineString.fromLngLats(done))
            jeepSrc.setGeoJson(cur)
            userSrc.setGeoJson(cur)
            onProgress(frac)
            if (frac >= 1f) break
        }
    }
    LaunchedEffect(liveLocation, roadPoints, styleReady) {
        val loc = liveLocation ?: return@LaunchedEffect
        val style = mapRef?.style ?: return@LaunchedEffect
        val userSrc = style.getSourceAs<GeoJsonSource>("user") ?: return@LaunchedEffect
        val routeSrc = style.getSourceAs<GeoJsonSource>("route") ?: return@LaunchedEffect
        val doneSrc = style.getSourceAs<GeoJsonSource>("route-done") ?: return@LaunchedEffect
        val jeepSrc = style.getSourceAs<GeoJsonSource>("jeep") ?: return@LaunchedEffect
        val empty = FeatureCollection.fromFeatures(emptyList<Feature>())

        userSrc.setGeoJson(Point.fromLngLat(loc.longitude, loc.latitude))

        if (roadPoints.size < 2) return@LaunchedEffect

        var best = 0
        var bestDist = Double.MAX_VALUE
        roadPoints.forEachIndexed { i, p ->
            val d = loc.distanceTo(LatLng(p.latitude(), p.longitude()))
            if (d < bestDist){bestDist = d; best = i }}

        if (bestDist > 300) return@LaunchedEffect

        val cum = DoubleArray(roadPoints.size)
        for (i in 1 until roadPoints.size){
            val p = roadPoints[i - 1]
            val q = roadPoints[i]
            cum[i] = cum[i - 1] +
                    LatLng(p.latitude(), p.longitude()).distanceTo(LatLng(q.latitude(),q.longitude()))}
        val total = cum.last()
        val remaining = roadPoints.subList(best, roadPoints.size)
        val done = roadPoints.subList(0, best + 1)
        routeSrc.setGeoJson(lineOrEmpty(remaining))
        doneSrc.setGeoJson(lineOrEmpty(done))
        jeepSrc.setGeoJson(roadPoints[best])
        if (total > 0) onProgress((cum[best] / total).toFloat())}

    LaunchedEffect(liveLocation != null, styleReady) {
        val loc = liveLocation ?: return@LaunchedEffect
        mapRef?.animateCamera(CameraUpdateFactory.newLatLngZoom(loc, 13.0))}
}

suspend fun fetchRoadRoute(from: LatLng, to: LatLng): List<Point>? =
    withContext(Dispatchers.IO) {
        try {
            val url = URL(
                "https://routing.openstreetmap.de/routed-car/route/v1/driving/" +
                        "${from.longitude},${from.latitude};${to.longitude},${to.latitude}" +
                        "?overview=full&geometries=geojson")
            val conn = url.openConnection() as HttpURLConnection
            conn.setRequestProperty("User-Agent", "JeepneyRouteApp/1.0 (school project)")
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            val code = conn.responseCode
            Log.d("ROAD", "HTTP code: $code")

            if (code != 200){
                Log.e("ROAD", "Server error: ${conn.errorStream?.bufferedReader()?.readText()}")
                conn.disconnect()
                return@withContext null}

            val text = conn.inputStream.bufferedReader().use{it.readText()}
            conn.disconnect()

            val coords = JSONObject(text)
                .getJSONArray("routes")
                .getJSONObject(0)
                .getJSONObject("geometry")
                .getJSONArray("coordinates")
            Log.d("ROAD", "Got ${coords.length()} road points")
            List(coords.length()) { i ->
                val c = coords.getJSONArray(i)
                Point.fromLngLat(c.getDouble(0), c.getDouble(1))}
        } catch (e: Exception){Log.e("ROAD", "Failed: ${e.message}", e)
            null}
    }

fun lineOrEmpty(points: List<Point>): FeatureCollection =
    if (points.size >= 2) {FeatureCollection.fromFeature(Feature.fromGeometry(LineString.fromLngLats(points)))
    } else {FeatureCollection.fromFeatures(emptyList<Feature>())}
const val MAX_TERMINAL_KM = 10.0

fun nearestTown(loc: LatLng): Pair<String, Double>? =
    townCoords.map { (name, ll) -> name to loc.distanceTo(ll)}.minByOrNull{it.second}

val JeepBlue = Color(0xFF0D47A1)
val JeepYellow = Color(0xFFFFC107)
val JeepRed = Color(0xFFD32F2F)
val JeepGreen = Color(0xFF2E7D32)
val JeepBg = Color(0xFFF3F6FB)

data class StatusStyle(val container: Color, val content: Color, val icon: String)
fun statusStyle(msg: String): StatusStyle = when {
    msg.contains("No jeepney", true) ||
            msg.contains("denied", true) ||
            msg.contains("outside", true) ||
            msg.contains("same", true) ->
        StatusStyle(Color(0xFFFDECEA), Color(0xFFB71C1C), "⛔")

    msg.contains("Please", true) ||
            msg.contains("turned off", true) ||
            msg.contains("Waiting", true) ->
        StatusStyle(Color(0xFFFFF4D6), Color(0xFF7A5900), "⚠️")

    msg.contains("available", true) ||
            msg.contains("started", true) ->
        StatusStyle(Color(0xFFE6F4EA), Color(0xFF1B5E20), "✅")

    else -> StatusStyle(Color(0xFFE3F0FF), JeepBlue, "ℹ️")
}