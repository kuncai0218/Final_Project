package com.example.finalproject

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.esri.arcgisruntime.ArcGISRuntimeEnvironment
import com.esri.arcgisruntime.data.Feature
import com.esri.arcgisruntime.data.ServiceFeatureTable
import com.esri.arcgisruntime.geometry.GeometryEngine
import com.esri.arcgisruntime.geometry.Point
import com.esri.arcgisruntime.geometry.SpatialReferences
import com.esri.arcgisruntime.layers.FeatureLayer
import com.esri.arcgisruntime.loadable.LoadStatus
import com.esri.arcgisruntime.mapping.*
import com.esri.arcgisruntime.mapping.view.Callout
import com.esri.arcgisruntime.mapping.view.DefaultMapViewOnTouchListener
import com.esri.arcgisruntime.mapping.view.GraphicsOverlay
import com.esri.arcgisruntime.mapping.view.Graphic
import com.esri.arcgisruntime.mapping.view.LocationDisplay
import com.esri.arcgisruntime.mapping.view.MapView
import com.esri.arcgisruntime.tasks.geocode.GeocodeParameters
import com.esri.arcgisruntime.tasks.geocode.LocatorTask
import com.esri.arcgisruntime.tasks.geocode.SuggestResult
import com.esri.arcgisruntime.symbology.SimpleMarkerSymbol
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * 数据类表示景点对象
 */
data class Attraction(
    val attraction_id: String,
    val name: String,
    val description: String,
    val tourism: String,
    val website: String,
    val lat: Double,
    val lon: Double
)

/**
 * 数据类表示评论响应
 */
data class ReviewResponse(
    val review_id: String,
    val rating: Int,
    val comment: String
)

class MainActivity : ComponentActivity() {

    private lateinit var mapView: MapView
    private lateinit var locationDisplay: LocationDisplay

    // 自定义 Overlay，用于显示自家后端 Attraction
    private val customOverlay = GraphicsOverlay()

    private lateinit var locatorTask: LocatorTask
    private val client = OkHttpClient()

    // 替换为你的API网关URL（以newstage为例）
    private val apiUrl = "https://hzt3vn213a.execute-api.us-east-2.amazonaws.com/newstage"

    // 地理编码参数
    private val geocodeParameters: GeocodeParameters by lazy {
        GeocodeParameters().apply {
            maxResults = 5
            outputSpatialReference = SpatialReferences.getWgs84()
        }
    }

    // 动态权限请求
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.all { it.value }) {
            startLocationDisplay()
        } else {
            Toast.makeText(this, "Location permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 设置API Key
        ArcGISRuntimeEnvironment.setApiKey("AAPTxy8BH1VEsoebNVZXo8HurMWjno9mwrs9l8mY3d91btkcgz5RVAOLmqAkpKevkHfYqFboRl8uV1ARkitR5R0NwSBeZQEv-HhivL1V4YvfBaWbB5tcgptMo7nUTodv49vEs28WnuP0l89sTpInPsO3lab7137vet490x0bzcsMLyq3mRWR4-ixyElFCKlRh145PWyQB2Z-4T3XMXLV4xEfRyQQrFd3SfrXHD2x3TppG8HSgRb6nCI6xcEMO2QoFsDvAT1_2uUMGKTX")

        // 创建 MapView
        mapView = MapView(this)

        // 创建 ArcGISMap，并加载 OpenStreetMap 底图
        val arcGISMap = ArcGISMap(Basemap.createOpenStreetMap())
        val startPoint = Point(-89.4010, 43.0761, SpatialReferences.getWgs84())
        arcGISMap.initialViewpoint = Viewpoint(startPoint, 15000.0)

        // 加载地图和 OSM 图层
        arcGISMap.addDoneLoadingListener {
            if (arcGISMap.loadStatus == LoadStatus.LOADED) {
                val serviceFeatureTable = ServiceFeatureTable(
                    "https://services6.arcgis.com/Do88DoK2xjTUCXd1/arcgis/rest/services/OSM_NA_Tourism/FeatureServer/0"
                )
                val osmAttractionsLayer = FeatureLayer(serviceFeatureTable)
                arcGISMap.operationalLayers.add(osmAttractionsLayer)
            } else {
                Log.e("ArcGIS", "Map load failed: ${arcGISMap.loadError?.message}")
            }
        }
        mapView.map = arcGISMap

        // 将自定义 Overlay 加到 mapView 中
        mapView.graphicsOverlays.add(customOverlay)

        // 初始化地理编码服务
        locatorTask = LocatorTask(
            "https://geocode-api.arcgis.com/arcgis/rest/services/World/GeocodeServer"
        )

        // 加载已有的自定义景点（可选）
        lifecycleScope.launchWhenResumed {
            loadCustomAttractions()
        }

        setContent {
            MapScreen(
                mapView = mapView,
                onLocationClick = { toggleLocationDisplay() },
                onSearch = { address -> searchAddress(address) },
                onQueryChanged = { query -> fetchSuggestions(query) }
            )
        }
    }

    /**
     * 搜索地址并移动地图视图
     */
    private fun searchAddress(address: String) {
        if (address.isBlank()) return
        val future = locatorTask.geocodeAsync(address, geocodeParameters)
        future.addDoneListener {
            try {
                val results = future.get()
                if (results.isNotEmpty()) {
                    val loc = results[0].displayLocation
                    mapView.setViewpointCenterAsync(loc, 50000.0)
                } else {
                    Toast.makeText(this, "Address not found", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this, "Search error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * 获取地址建议
     */
    private fun fetchSuggestions(query: String): List<SuggestResult> {
        if (query.isBlank()) return emptyList()
        return try {
            locatorTask.suggestAsync(query).get()
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * 检查并请求位置权限
     */
    private fun checkLocationPermission() {
        val required = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        val missing = required.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            requestPermissionLauncher.launch(missing.toTypedArray())
        } else {
            startLocationDisplay()
        }
    }

    /**
     * 启动位置显示
     */
    private fun startLocationDisplay() {
        locationDisplay = mapView.locationDisplay
        locationDisplay.autoPanMode = LocationDisplay.AutoPanMode.RECENTER
        locationDisplay.startAsync()
    }

    /**
     * 切换位置显示状态
     */
    private fun toggleLocationDisplay() {
        if (::locationDisplay.isInitialized) {
            if (locationDisplay.isStarted) {
                locationDisplay.stop()
            } else {
                locationDisplay.startAsync()
            }
        } else {
            checkLocationPermission()
        }
    }

    // -----------------------
    //  后端交互相关方法
    // -----------------------

    /**
     * 检查景点是否存在
     */
    private suspend fun checkAttractionExists(attractionId: String): Boolean = withContext(Dispatchers.IO) {
        val url = "$apiUrl/attractions/$attractionId"
        val request = Request.Builder().url(url).get().build()
        val response = client.newCall(request).execute()
        response.use {
            it.code == 200
        }
    }

    /**
     * 插入新的景点
     */
    private suspend fun insertAttraction(attraction: Attraction): Boolean = withContext(Dispatchers.IO) {
        val url = "$apiUrl/attractions"
        val json = """
            {
              "attraction_id":"${attraction.attraction_id}",
              "name":"${attraction.name}",
              "description":"${attraction.description}",
              "tourism":"${attraction.tourism}",
              "website":"${attraction.website}",
              "lat":${attraction.lat},
              "lon":${attraction.lon}
            }
        """.trimIndent()

        val body = json.toRequestBody("application/json".toMediaTypeOrNull())
        val request = Request.Builder().url(url).post(body).build()
        val response = client.newCall(request).execute()
        response.use {
            it.isSuccessful
        }
    }

    /**
     * 获取景点的评论
     */
    private suspend fun fetchReviews(attractionId: String): List<ReviewResponse> = withContext(Dispatchers.IO) {
        val url = "$apiUrl/attractions/$attractionId/reviews"
        val request = Request.Builder().url(url).get().build()
        val response = client.newCall(request).execute()
        response.use {
            if (it.isSuccessful) {
                val responseBody = it.body?.string() ?: ""
                return@use parseReviewList(responseBody)
            } else {
                return@use emptyList()
            }
        }
    }

    /**
     * 解析评论列表 JSON
     */
    private fun parseReviewList(jsonStr: String): List<ReviewResponse> {
        return try {
            val gson = Gson()
            val listType = object : TypeToken<List<ReviewResponse>>() {}.type
            gson.fromJson(jsonStr, listType)
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    /**
     * 添加新的评论
     */
    private suspend fun addReview(
        attractionId: String,
        rating: Int,
        comment: String,
        user_id: String = "test_user"
    ): ReviewResponse? = withContext(Dispatchers.IO) {
        val url = "$apiUrl/attractions/$attractionId/reviews"
        val json = """{"rating":$rating, "comment":"$comment", "user_id":"$user_id"}"""
        val body = json.toRequestBody("application/json".toMediaTypeOrNull())
        val request = Request.Builder().url(url).post(body).build()

        val response = client.newCall(request).execute()
        response.use {
            if (it.isSuccessful) {
                // 假设后端返回新创建的 Review 对象(json)，我们解析成 ReviewResponse
                val responseBody = it.body?.string().orEmpty()
                return@use try {
                    Gson().fromJson(responseBody, ReviewResponse::class.java)
                } catch (ex: Exception) {
                    ex.printStackTrace()
                    null
                }
            } else {
                null
            }
        }
    }

    /**
     * 显示添加评论的对话框
     */
    private fun showAddReviewDialog(
        attractionId: String,
        onReviewAdded: (ReviewResponse) -> Unit
    ) {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 40, 50, 10)
        }

        val ratingInput = EditText(this).apply {
            hint = "Enter rating (1~5)"
        }
        val commentInput = EditText(this).apply {
            hint = "Enter comment"
        }
        layout.addView(ratingInput)
        layout.addView(commentInput)

        AlertDialog.Builder(this)
            .setTitle("Add Review")
            .setView(layout)
            .setPositiveButton("Submit") { dialog, _ ->
                val ratingStr = ratingInput.text.toString().trim()
                val comment = commentInput.text.toString().trim()
                val rating = ratingStr.toIntOrNull() ?: 0

                if (rating < 1 || rating > 5) {
                    Toast.makeText(this, "Rating must be an integer between 1 and 5.", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                lifecycleScope.launchWhenResumed {
                    val review = addReview(attractionId, rating, comment)
                    if (review != null) {
                        Toast.makeText(this@MainActivity, "Review added", Toast.LENGTH_SHORT).show()
                        onReviewAdded(review)  // 把创建成功的Review对象交给回调
                    } else {
                        Toast.makeText(this@MainActivity, "Failed to add review", Toast.LENGTH_SHORT).show()
                    }
                }
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    /**
     * 显示添加景点的对话框
     */
    private fun showAddAttractionDialog(lat: Double, lon: Double) {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 40, 50, 10)
        }

        val nameInput = EditText(this).apply { hint = "Enter Name" }
        val descInput = EditText(this).apply { hint = "Enter Description" }
        val tourismInput = EditText(this).apply { hint = "Enter Tourism Type" }
        val websiteInput = EditText(this).apply { hint = "Enter Website" }

        layout.addView(nameInput)
        layout.addView(descInput)
        layout.addView(tourismInput)
        layout.addView(websiteInput)

        AlertDialog.Builder(this)
            .setTitle("Add Attraction")
            .setView(layout)
            .setPositiveButton("Submit") { dialog, _ ->
                val name = nameInput.text.toString().trim()
                val desc = descInput.text.toString().trim()
                val tourism = tourismInput.text.toString().trim()
                val website = websiteInput.text.toString().trim()

                if (name.isEmpty()) {
                    Toast.makeText(this, "Name cannot be empty.", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                lifecycleScope.launchWhenResumed {
                    // 注意 lat 是纬度，lon 是经度
                    val attractionId = "custom_${System.currentTimeMillis()}"
                    val newAttr = Attraction(
                        attraction_id = attractionId,
                        name = name,
                        description = desc,
                        tourism = tourism,
                        website = website,
                        lat = lat,
                        lon = lon
                    )
                    val success = insertAttraction(newAttr)
                    if (success) {
                        Toast.makeText(this@MainActivity, "Attraction added to DB", Toast.LENGTH_SHORT).show()

                        // 在地图上显示这个点：加到 customOverlay
                        withContext(Dispatchers.Main) {
                            addGraphicForAttraction(newAttr)
                        }

                        // 显示 Callout
                        showAttractionCallout(newAttr)
                    } else {
                        Toast.makeText(this@MainActivity, "Failed to add attraction", Toast.LENGTH_SHORT).show()
                    }
                }
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    /**
     * 将自建景点渲染到地图上
     */
    private fun addGraphicForAttraction(attraction: Attraction) {
        // ArcGIS 中构造Point: x=经度, y=纬度, SpatialReferences.getWgs84()
        val point = Point(attraction.lon, attraction.lat, SpatialReferences.getWgs84())

        // Symbol 可以自定义，这里简单用蓝色圆点
        val symbol = SimpleMarkerSymbol(
            SimpleMarkerSymbol.Style.CIRCLE,
            Color.BLUE,
            10f
        )

        val graphic = Graphic(point, symbol)

        // 添加属性，用于后续识别和显示 Callout
        graphic.attributes["attraction_id"] = attraction.attraction_id
        graphic.attributes["name"] = attraction.name
        graphic.attributes["description"] = attraction.description
        graphic.attributes["tourism"] = attraction.tourism
        graphic.attributes["website"] = attraction.website

        customOverlay.graphics.add(graphic)
    }

    /**
     * 显示 Callout，包含景点信息和评论
     */
    private fun showAttractionCallout(attraction: Attraction) {
        lifecycleScope.launchWhenResumed {
            val reviews = fetchReviews(attraction.attraction_id)
            showAttractionCallout(attraction, reviews)
        }
    }

    /**
     * 显示 Callout，包含景点信息和评论
     */
    private fun showAttractionCallout(attraction: Attraction, reviews: List<ReviewResponse>) {
        val info = "Name: ${attraction.name}\nTourism: ${attraction.tourism}\nDescription: ${attraction.description}\nWebsite: ${attraction.website}"

        val calloutLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
            setPadding(16, 16, 16, 16)

            // 基本信息
            addView(TextView(this@MainActivity).apply {
                text = info
                setTextColor(Color.BLACK)
            })

            // 添加标题和评分统计
            addView(TextView(this@MainActivity).apply {
                text = "\nReviews"
                textSize = 16f
                setTextColor(Color.BLACK)
            })

            if (reviews.isEmpty()) {
                // 没有评论时显示提示
                addView(TextView(this@MainActivity).apply {
                    text = "No reviews yet"
                    setTextColor(Color.GRAY)
                })
            } else {
                // 显示平均评分
                val avgRating = reviews.map { it.rating }.average()
                addView(TextView(this@MainActivity).apply {
                    text = String.format("Average Rating: %.1f stars (%d reviews)", avgRating, reviews.size)
                    setTextColor(Color.BLACK)
                    setPadding(0, 8, 0, 8)
                })

                // ScrollView 包裹评论列表
                val scrollView = ScrollView(this@MainActivity).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        400  // 固定高度，单位：px
                    )
                    isFillViewport = true  // 充满高度
                }

                // 评论列表容器
                val reviewContainer = LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.VERTICAL
                }

                // 添加所有评论到容器
                reviews.forEach { review ->
                    reviewContainer.addView(TextView(this@MainActivity).apply {
                        text = """
                            ${getStars(review.rating)} (${review.rating} stars)
                            ${review.comment}
                            Review ID: ${review.review_id}
                            ${"─".repeat(30)}
                        """.trimIndent()
                        setPadding(0, 8, 0, 8)
                        setTextColor(Color.BLACK)
                    })
                }

                // 将评论容器添加到 ScrollView
                scrollView.addView(reviewContainer)
                addView(scrollView)
            }

            // Add Review 按钮
            addView(Button(this@MainActivity).apply {
                text = "Add Review"
                setOnClickListener {
                    showAddReviewDialog(attraction.attraction_id) { newReview ->
                        // 重新获取评论并刷新 Callout
                        lifecycleScope.launchWhenResumed {
                            val updatedReviews = fetchReviews(attraction.attraction_id)
                            showAttractionCallout(attraction, updatedReviews)
                        }
                    }
                }
            })
        }

        // 显示 Callout
        mapView.callout.apply {
            content = calloutLayout
            val pt = Point(attraction.lon, attraction.lat, SpatialReferences.getWgs84())
            location = pt
            show()
        }
    }

    /**
     * 生成星级表示
     */
    private fun getStars(rating: Int): String {
        return "★".repeat(rating) + "☆".repeat(5 - rating)
    }

    /**
     * 识别 OSM_NA_Tourism Layer
     */
    private fun identifyOsmLayer(screenPoint: android.graphics.Point) {
        val layer = mapView.map.operationalLayers.getOrNull(0) as? FeatureLayer ?: return
        val identifyFuture = mapView.identifyLayerAsync(layer, screenPoint, 10.0, false, 1)
        identifyFuture.addDoneListener {
            try {
                val result = identifyFuture.get()
                if (result.elements.isNotEmpty()) {
                    val feat = result.elements[0] as? Feature
                    feat?.let { f ->
                        val attrs = f.attributes
                        val attraction_id = attrs["osm_id2"]?.toString() ?: return@let

                        val name = attrs["name"]?.toString() ?: "Unknown"
                        val tourism = attrs["tourism"]?.toString() ?: "N/A"
                        val description = attrs["description"]?.toString() ?: "No Description"
                        val website = attrs["website"]?.toString() ?: "No Website"

                        val geometry = f.geometry as? Point
                        if (geometry != null) {
                            val wgs84Point = GeometryEngine.project(geometry, SpatialReferences.getWgs84()) as Point
                            val lat = wgs84Point.y
                            val lon = wgs84Point.x

                            lifecycleScope.launchWhenResumed {
                                try {
                                    val attraction = Attraction(
                                        attraction_id = attraction_id,
                                        name = name,
                                        description = description,
                                        tourism = tourism,
                                        website = website,
                                        lat = lat,
                                        lon = lon
                                    )
                                    // 调用 showAttractionCallout
                                    showAttractionCallout(attraction, emptyList())

                                    // 后端检查如果还没存过，就插入数据库
                                    val exists = checkAttractionExists(attraction_id)
                                    if (!exists) {
                                        val success = insertAttraction(attraction)
                                        if (!success) {
                                            Toast.makeText(this@MainActivity, "Failed to insert attraction", Toast.LENGTH_SHORT).show()
                                            return@launchWhenResumed
                                        }
                                    }

                                    val reviews = fetchReviews(attraction_id)
                                    showAttractionCallout(attraction, reviews)

                                } catch (e: Exception) {
                                    Log.e("MapClick", "Error: ${e.message}")
                                }
                            }
                        }
                    }
                } else {
                    mapView.callout.dismiss()
                }
            } catch (e: Exception) {
                Log.e("MapClick", "Error in identify OSM layer: ${e.message}")
            }
        }
    }

    /**
     * 加载已有的自定义景点
     * （可选，如果你希望在应用启动时加载并显示后端数据库中的所有自定义景点）
     */
    private suspend fun loadCustomAttractions() {
        withContext(Dispatchers.IO) {
            // 假设后端提供了一个接口来获取所有自定义景点
            val url = "$apiUrl/attractions"
            val request = Request.Builder().url(url).get().build()
            val response = client.newCall(request).execute()
            response.use {
                if (it.isSuccessful) {
                    val responseBody = it.body?.string().orEmpty()
                    val attractions = parseAttractionList(responseBody)
                    attractions.forEach { attraction ->
                        withContext(Dispatchers.Main) {
                            addGraphicForAttraction(attraction)
                        }
                    }
                } else {
                    Log.e("LoadAttractions", "Failed to load custom attractions: ${it.message}")
                }
            }
        }
    }

    /**
     * 解析景点列表 JSON
     */
    private fun parseAttractionList(jsonStr: String): List<Attraction> {
        return try {
            val gson = Gson()
            val listType = object : TypeToken<List<Attraction>>() {}.type
            gson.fromJson(jsonStr, listType)
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    // -----------------------
    //  生命周期管理
    // -----------------------
    override fun onPause() {
        mapView.pause()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        mapView.resume()
    }

    override fun onDestroy() {
        mapView.dispose()
        super.onDestroy()
    }

    // -----------------------
    //  UI Composable
    // -----------------------

    @SuppressLint("ClickableViewAccessibility")
    @Composable
    fun MapScreen(
        mapView: MapView,
        onLocationClick: () -> Unit,
        onSearch: (String) -> Unit,
        onQueryChanged: (String) -> List<SuggestResult>
    ) {
        var searchText by remember { mutableStateOf("") }
        val context = LocalContext.current

        var showSuggestions by remember { mutableStateOf(false) }
        var suggestions by remember { mutableStateOf<List<SuggestResult>>(emptyList()) }

        // 是否进入“添加景点模式”
        var addAttractionMode by remember { mutableStateOf(false) }

        // 设置点击地图要素后逻辑（按需同步）
        LaunchedEffect(Unit) {
            mapView.onTouchListener = object : DefaultMapViewOnTouchListener(context, mapView) {

                override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                    val screenPoint = android.graphics.Point(e.x.toInt(), e.y.toInt())

                    if (addAttractionMode) {
                        // “添加模式”下，点击地图任意位置即添加自定义景点
                        val mapPoint = mapView.screenToLocation(screenPoint)
                        // 投影到 WGS84
                        val wgs84Point = GeometryEngine.project(mapPoint, SpatialReferences.getWgs84()) as Point
                        val lat = wgs84Point.y
                        val lon = wgs84Point.x
                        showAddAttractionDialog(lat, lon)
                        return true
                    } else {
                        // 1) 优先判断是否点到了“自建景点 Overlay”
                        val identifyGraphicsFuture = mapView.identifyGraphicsOverlayAsync(
                            customOverlay, // 你的 overlay
                            screenPoint,
                            10.0,
                            false,
                            1
                        )
                        identifyGraphicsFuture.addDoneListener {
                            try {
                                val result = identifyGraphicsFuture.get()
                                // 如果 identify 到了自建的 Graphic
                                if (result.graphics.isNotEmpty()) {
                                    val graphic = result.graphics[0]
                                    // 取出 Attraction 信息
                                    val attractionId = graphic.attributes["attraction_id"]?.toString()
                                    if (attractionId != null) {
                                        val attraction = Attraction(
                                            attraction_id = attractionId,
                                            name = graphic.attributes["name"]?.toString() ?: "",
                                            description = graphic.attributes["description"]?.toString() ?: "",
                                            tourism = graphic.attributes["tourism"]?.toString() ?: "",
                                            website = graphic.attributes["website"]?.toString() ?: "",
                                            lat = (graphic.geometry as? Point)?.y ?: 0.0,
                                            lon = (graphic.geometry as? Point)?.x ?: 0.0
                                        )
                                        // 直接调用同样的 Show Callout
                                        showAttractionCallout(attraction)
                                    }
                                } else {
                                    // 2) 如果没点到 Overlay，再去判断 OSM_NA_Tourism Layer
                                    identifyOsmLayer(screenPoint)
                                }
                            } catch (ex: Exception) {
                                Log.e("MapClick", "Error identify overlay: ${ex.message}")
                            }
                        }
                        return true
                    }
                }

                // 重写 onTouch 方法来拦截 Callout 区域内的触摸事件
                @SuppressLint("ClickableViewAccessibility")
                override fun onTouch(view: View?, event: MotionEvent?): Boolean {
                    // 如果没有事件或 Callout 未显示，交由父类处理
                    if (event == null) return super.onTouch(view, event)

                    val callout = mapView.callout
                    val contentView = callout?.content

                    if (callout != null && contentView != null && callout.isShowing) {
                        // 获取 Callout 内容的屏幕坐标
                        val locationOnScreen = IntArray(2)
                        contentView.getLocationOnScreen(locationOnScreen)
                        val left = locationOnScreen[0]
                        val top = locationOnScreen[1]
                        val right = left + contentView.width
                        val bottom = top + contentView.height

                        val x = event.rawX.toInt()
                        val y = event.rawY.toInt()

                        // 如果触摸点在 Callout 的内容区域内，派发事件给 Callout 子 View
                        if (x in left..right && y in top..bottom) {
                            contentView.dispatchTouchEvent(event)
                            return true // 拦截事件，ScrollView 接收事件
                        }
                    }

                    // 其他情况交给父类处理，地图可继续平移和缩放
                    return super.onTouch(view, event)
                }
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            // 地图视图
            AndroidView(
                factory = { mapView },
                modifier = Modifier.fillMaxSize()
            )

            // 搜索框 UI
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .align(Alignment.TopCenter)
            ) {
                OutlinedTextField(
                    value = searchText,
                    onValueChange = {
                        searchText = it
                        if (it.isNotBlank()) {
                            showSuggestions = true
                            suggestions = onQueryChanged(it)
                        } else {
                            showSuggestions = false
                            suggestions = emptyList()
                        }
                    },
                    placeholder = { Text("Search Address") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(ComposeColor.White, shape = RoundedCornerShape(8.dp)),
                    trailingIcon = {
                        IconButton(onClick = {
                            onSearch(searchText)
                            showSuggestions = false
                        }) {
                            Icon(Icons.Default.Search, contentDescription = "Search")
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                        focusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                    )
                )

                // 显示建议列表（可选）
                if (showSuggestions && suggestions.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(ComposeColor.White)
                            .padding(8.dp)
                            .heightIn(max = 200.dp)
                    ) {
                        suggestions.forEach { suggestion ->
                            Text(
                                text = suggestion.label,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        searchText = suggestion.label
                                        showSuggestions = false
                                        onSearch(suggestion.label)
                                    }
                                    .padding(vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            // 右侧按钮区：强制靠屏幕右侧垂直居中
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(end = 8.dp), // 右侧间距
                horizontalArrangement = Arrangement.End, // 水平靠右
                verticalAlignment = Alignment.CenterVertically // 垂直居中
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    FloatingActionButton(
                        onClick = { mapView.setViewpointScaleAsync(mapView.mapScale * 0.5) },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Zoom In")
                    }

                    FloatingActionButton(
                        onClick = { mapView.setViewpointScaleAsync(mapView.mapScale * 2.0) },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(Icons.Default.Remove, contentDescription = "Zoom Out")
                    }

                    FloatingActionButton(
                        onClick = onLocationClick,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(Icons.Default.LocationOn, contentDescription = "Location")
                    }
                }
            }

            // Add Attraction 按钮：底部居中位置
            Button(
                onClick = {
                    addAttractionMode = !addAttractionMode
                    val msg = if (addAttractionMode) {
                        "Tap on map to add attraction."
                    } else {
                        "Add attraction mode off."
                    }
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                },
                colors = ButtonDefaults.buttonColors(containerColor = ComposeColor(0xFFF57C00)),
                modifier = Modifier
                    .align(Alignment.BottomCenter) // 按钮居中于屏幕底部
                    .padding(bottom = 32.dp) // 与底部保持间距
            ) {
                Text(text = if (addAttractionMode) "Cancel" else "Add Attraction")
            }
        }
    }
}
