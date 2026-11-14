package com.sokpyeon.app

// =====================================================================
// 속편한 — 1단계 앱
//   - 주행 데이터: 실제 차량 신호(차속·브레이크·기어) 또는 시연용 주행 재생
//   - 승차감 판단: 급가속·급제동·회생제동 '울컥'·급커브 감지 → 멀미 위험도(0~100)
//   - 동승자 모드: 민감도 3단계, 위험도가 높을 때만 듣는 '조건부 듣기'
//   - 동승자 신호: '멀미' 음성 감지 또는 "멀미 나요" 버튼
//   - 승차감 안내: 음성 안내(같은 종류는 10분에 한 번) + 실내 환경 케어(외기·조명)
// =====================================================================

import android.content.Context
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.animation.Crossfade
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.core.content.ContextCompat
import ai.pleos.playground.location.fused.FusedLocation
import ai.pleos.playground.location.fused.listener.FusedLocationListener
import ai.pleos.playground.location.fused.model.LocationData
import ai.pleos.playground.stt.SpeechToText
import ai.pleos.playground.stt.listener.ResultListener
import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.listener.EventListener
import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.constant.area.SeatValue
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import ai.pleos.playground.vehicle.listener.BrakePedalStatusListener
import ai.pleos.playground.vehicle.listener.GearSelectionListener
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import ai.pleos.playground.stt.constant.ErrorType as SttErrorType
import ai.pleos.playground.stt.constant.Mode as SttMode
import ai.pleos.playground.stt.listener.OnInitializeListener as SttInitListener
import ai.pleos.playground.tts.constant.ErrorType as TtsErrorType
import ai.pleos.playground.tts.constant.Mode as TtsMode
import ai.pleos.playground.tts.listener.OnInitializeListener as TtsInitListener

// =====================================================================
// 디자인 토큰: Pleos Connect UI 디자인 가이드 기준 (라이트·다크 테마)
//   회색조 = Pleos basic 색상, 상태색 = Pleos informative·regulation 색상
//   우리 앱 상징색은 청록 하나만 사용
// =====================================================================
internal data class Pal(
    val bg: Color,          // background_primary
    val surface: Color,     // 카드
    val surfaceHigh: Color, // 강조 카드·선택 안 된 칸
    val line: Color,
    val text1: Color,       // 본문
    val text2: Color,       // 보조
    val text3: Color,       // 설명
    val brand: Color,       // 우리 앱 상징색(청록)
    val brandSoft: Color,
    val onBrand: Color,
    val calm: Color,        // 편안함 (informative_active)
    val warn: Color,        // 주의 (regulation_orange)
    val high: Color,        // 높음 (informative_negative)
    val brake: Color,       // 급제동 표시 (informative_positive)
    val curve: Color,       // 급커브 표시 (informative_autonomous_driving)
    val sickButton: Color,  // "멀미 나요" 버튼 (차분한 산호색)
    val dark: Boolean
)

private val LightPal = Pal(
    bg = Color(0xFFF7F8FA), surface = Color(0xFFFFFFFF), surfaceHigh = Color(0xFFEDEEF2),
    line = Color(0xFFE0E1E6), text1 = Color(0xFF131417), text2 = Color(0xFF44464E), text3 = Color(0xFF686A72),
    brand = Color(0xFF0E9F88), brandSoft = Color(0x1A0E9F88), onBrand = Color.White,
    calm = Color(0xFF02C265), warn = Color(0xFFFF8A00), high = Color(0xFFFE3D16),
    brake = Color(0xFF0064FF), curve = Color(0xFF5A46FA), sickButton = Color(0xFFE8664F), dark = false
)

private val DarkPal = Pal(
    bg = Color(0xFF131417), surface = Color(0xFF1D1E21), surfaceHigh = Color(0xFF313236),
    line = Color(0xFF313236), text1 = Color(0xFFFFFFFF), text2 = Color(0xFFC7C9CE), text3 = Color(0xFFA4A8AE),
    brand = Color(0xFF3DD6B5), brandSoft = Color(0x263DD6B5), onBrand = Color(0xFF06231D),
    calm = Color(0xFF00E074), warn = Color(0xFFFF8A00), high = Color(0xFFFF4C28),
    brake = Color(0xFF0082FF), curve = Color(0xFF6A57FF), sickButton = Color(0xFFE0705C), dark = true
)

private val LocalPal = staticCompositionLocalOf { LightPal }

// 글꼴: Pleos 시스템 글꼴 Asta Sans (OFL)
private val AstaSans = FontFamily(
    Font(R.font.asta_sans_regular, FontWeight.Normal),
    Font(R.font.asta_sans_semibold, FontWeight.SemiBold),
    Font(R.font.asta_sans_extrabold, FontWeight.ExtraBold)
)

// 글씨 크기: Pleos 글꼴 단계 (Headline 48~60 / Body 26~30 / Label 20~24)
private object TS {
    val headlineS = 48.sp; val headlineLH = 64.sp
    val bodyL = 30.sp; val bodyLLH = 38.sp
    val bodyM = 28.sp; val bodyMLH = 36.sp
    val bodyS = 26.sp; val bodySLH = 34.sp
    val labelM = 24.sp; val labelMLH = 28.sp
    val labelS = 20.sp; val labelSLH = 24.sp
}

private val TouchMin = 76.dp   // 차량 화면 최소 터치 영역

// =====================================================================
// 판단 엔진: 화면과 분리되어 서비스 안에서 계속 동작 (내비를 띄워도 감지·안내 유지)
// =====================================================================
class SokpyeonEngine private constructor(private val ctx: Context) {

    internal val tag = "SOKPYEON"
    internal val main = Handler(Looper.getMainLooper())

    // ---------------- 화면 상태 ----------------
    internal val speedKmh = mutableStateOf(0f)
    internal val gearLabel = mutableStateOf("-")
    internal val brakeOn = mutableStateOf(false)
    internal val risk = mutableStateOf(0f)
    internal val jerkCount = mutableStateOf(0)
    internal val accelCount = mutableStateOf(0)
    internal val brakeCount = mutableStateOf(0)
    internal val curveCount = mutableStateOf(0)
    internal val peakRisk = mutableStateOf(0f)               // 이번 주행 최고 위험도
    internal val sickTotal = mutableStateOf(0)               // 이번 주행 동승자 멀미 신호 횟수
    internal val passengerOn = mutableStateOf(true)
    internal val sensitivity = mutableStateOf(1)            // 0: 거의 안 해요, 1: 가끔 해요, 2: 자주 해요
    internal val listening = mutableStateOf(false)
    internal val heardKeyword = mutableStateOf("")          // 들은 '단어'만 표시 (대화 원문은 표시·저장하지 않음)
    internal val guideText = mutableStateOf("아직 알린 내용이 없어요.")
    internal val lastEvent = mutableStateOf("")
    internal val guideHistory = mutableStateListOf<String>()   // 화면용 안내 기록
    // 이번 주행 기록 (주행 후 화면): 경과 시간, 내용, 종류(1 울컥 2 급가속 3 급제동 4 급커브 5 음성 안내 6 멀미 신호)
    internal data class TripItem(val time: String, val text: String, val kind: Int)
    internal val tripLog = mutableStateListOf<TripItem>()
    internal var tripStart = 0L
    internal val cabinText = mutableStateOf("")
    internal val demoRunning = mutableStateOf(false)
    internal val chartSpeeds = mutableStateListOf<Float>()     // 그래프용 속도(km/h)
    internal val chartMarks = mutableStateListOf<Int>()        // 0 없음, 1 울컥, 2 급가속, 3 급제동, 4 급커브
    internal val chartGuides = mutableStateListOf<Int>()       // 음성 안내가 나간 그래프 위치
    internal val chartCapacity = 500                          // 0.2초 × 500 = 최근 100초
    internal var pendingMark = 0

    // ---------------- 차량·음성 SDK ----------------
    @Volatile private var vehicle: Vehicle? = null
    internal var fused: FusedLocation? = null
    @Volatile private var tts: TextToSpeech? = null
    @Volatile private var ttsReady = false
    @Volatile private var speakingUntil = 0L                       // 안내 말하는 중에는 듣지 않음
    @Volatile private var stt: SpeechToText? = null
    @Volatile private var sttReady = false

    // 실제 차량 신호 (다른 스레드에서 갱신 → @Volatile)
    internal var carPropertyManager: Any? = null
    internal var getFloatMethod: java.lang.reflect.Method? = null
    @Volatile private var realSpeed = 0f                 // m/s
    @Volatile private var realBrake = false
    @Volatile private var realGear = "-"
    internal val speedPoller = java.util.concurrent.Executors.newSingleThreadScheduledExecutor()

    // ---------------- 판단 엔진 ----------------
    // 기준값: 규칙 기반 초기값. 실차 주행 데이터로 조정 예정
    internal val decelLimit = -3.0f        // m/s², 이보다 강한 감속 = 급감속
    internal val accelLimit = 3.0f         // m/s², 이보다 강한 가속 = 급가속
    internal val lateralLimit = 3.0f       // m/s², 이보다 강한 옆 방향 가속도 = 급커브
    internal val tickMs = 200L

    internal data class Sample(val t: Long, val v: Float, val pedal: Boolean)
    internal data class HeadingSample(val t: Long, val deg: Float)
    internal val samples = ArrayDeque<Sample>()             // 최근 1초 (잡음 걸러낸 속도)
    internal val headings = ArrayDeque<HeadingSample>()     // 최근 3초 진행 방향
    internal var filteredSpeed = 0f
    internal var lastTickT = -1L
    internal var inEvent = false
    internal var inCurve = false
    internal var lastEventAt = -10_000L
    internal var simClock = 0L                              // 재생 데이터 시간 (0.2초씩 증가)
    internal val recentJerks = ArrayDeque<Long>()           // 최근 '울컥' 시각
    internal val recentCurves = ArrayDeque<Long>()
    internal val recentRough = ArrayDeque<Long>()           // 최근 급가속·급제동 시각
    internal val sickSignals = ArrayDeque<Long>()           // 최근 동승자 멀미 신호 시각 (실제 시각)
    internal val lastGuideAt = HashMap<String, Long>()      // 안내 종류별 마지막 시각 (실제 시각)
    internal val guideIntervalMs = 10 * 60 * 1000L          // 같은 종류 안내는 10분에 한 번
    internal var listenUntil = 0L
    internal var lastSpokeAt = -120_000L                   // 안내끼리 겹치지 않게 (최소 60초 간격)
    internal var highSince = 0L                            // 위험도 90 이상이 시작된 시각 (0 = 아님)
    internal var lastCause = ""                            // 가장 최근 흔들림 원인: ROUGH / CURVE
    internal var listenCooldownUntil = 0L

    // ---------------- 시연용 주행 재생 ----------------
    internal data class Frame(val speed: Float, val pedal: Boolean, val gear: String, val heading: Float)
    internal var demoFrames: List<Frame> = emptyList()
    internal var demoIndex = 0

    internal val tick = object : Runnable {
        override fun run() {
            runCatching { onTick() }.onFailure { log("판단 오류: $it") }
            main.postDelayed(this, tickMs)
        }
    }

    // =================================================================
    // 시작·종료 (서비스가 한 번만 시작한다)
    // =================================================================
    internal var started = false

    fun start() {
        if (started) return
        started = true
        loadSettings()
        log("판단 엔진 시작")
        main.postDelayed({
            bg { connectVehicle() }
            bgVoice { initTts() }
            bgVoice { initStt() }
            connectPermittedSignals()
        }, 300)
        main.post(tick)
    }

    // 권한이 있는 신호만 연결 (권한을 나중에 받으면 화면이 다시 부른다)
    internal var speedConnected = false
    internal var locationConnected = false
    fun connectPermittedSignals() {
        if (!speedConnected && granted(PERM_SPEED)) { speedConnected = true; bg { connectCarSpeed() } }
        if (!locationConnected && granted(PERM_LOCATION)) { locationConnected = true; bg { connectLocation() } }
    }

    private fun granted(p: String) =
        ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED

    fun stop() {
        main.removeCallbacksAndMessages(null)
        speedPoller.shutdownNow()
        runCatching { stt?.stop() }
        runCatching { vehicle?.release() }
        runCatching { tts?.release() }
        runCatching { stt?.release() }
        workerThread.quitSafely()
        voiceThread.quitSafely()
        started = false
    }

    // ---------------- 설정 저장 (앱을 다시 켜도 유지) ----------------
    internal fun loadSettings() {
        val p = ctx.getSharedPreferences("sokpyeon", Context.MODE_PRIVATE)
        passengerOn.value = p.getBoolean("passengerOn", true)
        sensitivity.value = p.getInt("sensitivity", 1)
    }

    internal fun saveSettings() {
        ctx.getSharedPreferences("sokpyeon", Context.MODE_PRIVATE).edit()
            .putBoolean("passengerOn", passengerOn.value)
            .putInt("sensitivity", sensitivity.value)
            .apply()
    }

    // =================================================================
    // 차량 신호 연결
    // =================================================================
    internal fun connectVehicle() {
        try {
            val v = Vehicle(ctx)
            v.initialize()
            vehicle = v
            log("차량 연결 요청")
            // 연결이 끝날 시간을 잠깐 준 뒤 구독
            workerHandler.postDelayed({ runCatching { subscribeBrakeAndGear() } }, 1500)
        } catch (e: Exception) {
            log("차량 연결 오류: $e")
        }
    }

    internal fun subscribeBrakeAndGear() {
        val v = vehicle ?: return
        runCatching {
            v.getBrake().registerBrakePedalStatus(object : BrakePedalStatusListener {
                override fun onBrakePedalStatusUpdated(pressed: Boolean?) {
                    realBrake = pressed == true
                }
                override fun onFailed(e: Exception) { log("브레이크 구독 오류: $e") }
            })
        }.onFailure { log("브레이크 구독 예외: $it") }
        runCatching {
            val gear = v.getGear()
            gear.getCurrentGearSelectionState({ g -> realGear = gearName(g) }, { e -> log("기어 오류: $e") })
            gear.registerGearSelection(object : GearSelectionListener {
                override fun onSelected(g: VehicleGear?) { realGear = gearName(g) }
                override fun onFailed(e: Exception) { log("기어 구독 오류: $e") }
            })
        }.onFailure { log("기어 구독 예외: $it") }
        log("브레이크·기어 구독 시작")
    }

    // 차속: 화면 스레드가 아닌 별도 스레드에서 0.1초마다 읽어 realSpeed에 담아 둔다
    internal fun connectCarSpeed() {
        try {
            val carClass = Class.forName("android.car.Car")
            val car = carClass.getMethod("createCar", Context::class.java).invoke(null, ctx)
            val mgr = carClass.getMethod("getCarManager", String::class.java).invoke(car, "property") ?: return
            carPropertyManager = mgr
            getFloatMethod = mgr.javaClass.getMethod(
                "getFloatProperty", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType
            )
            speedPoller.scheduleWithFixedDelay({
                runCatching {
                    val v = getFloatMethod?.invoke(carPropertyManager, PERF_VEHICLE_SPEED, 0) as? Float ?: 0f
                    realSpeed = kotlin.math.abs(v)      // 후진 시 음수 → 크기만 사용
                }
            }, 0, 100, java.util.concurrent.TimeUnit.MILLISECONDS)
            log("차속 신호 연결")
        } catch (e: Throwable) {
            log("차속 연결 오류: $e")
        }
    }

    // 위치: 진행 방향(heading)으로 커브의 세기를 계산 (갱신 1~2초)
    internal fun connectLocation() {
        runCatching {
            val f = FusedLocation(ctx)
            f.initialize()
            fused = f
            f.registerFusedLocationCallback(object : FusedLocationListener {
                override fun onChanged(data: LocationData?) {
                    if (data == null) return
                    val h = data.heading.toFloat()
                    ui { if (!demoRunning.value) addHeading(SystemClock.elapsedRealtime(), h) }
                }
                override fun onError(e: Exception) { log("위치 오류: $e") }
            })
            log("위치 신호 연결")
        }.onFailure { log("위치 연결 오류: $it") }
    }

    // =================================================================
    // 0.2초마다: 데이터 수집 → 판단 → 실내 케어·듣기·안내
    // =================================================================
    internal fun onTick() {
        val now = SystemClock.elapsedRealtime()
        val demo = demoRunning.value

        val frame = if (demo) nextDemoFrame() else Frame(realSpeed, realBrake, realGear, Float.NaN)
        // 판단에 쓰는 시간: 재생 중에는 데이터 시간, 실제 주행에서는 실제 시각
        // (화면이 버벅여도 가속도가 부풀려지지 않도록 실제 경과 시간으로 나눈다)
        val t = if (demo) { simClock += tickMs; simClock } else now
        val dt = if (lastTickT < 0) tickMs / 1000f else ((t - lastTickT).coerceIn(50, 2000)) / 1000f
        lastTickT = t

        // 잡음 거르기: 지수 이동 평균 (시간 상수 0.25초)
        val alpha = 1f - kotlin.math.exp(-dt / 0.25f)
        filteredSpeed += alpha * (frame.speed - filteredSpeed)

        speedKmh.value = frame.speed * 3.6f
        brakeOn.value = frame.pedal
        gearLabel.value = frame.gear

        samples.addLast(Sample(t, filteredSpeed, frame.pedal))
        while (samples.isNotEmpty() && t - samples.first().t > 1000) samples.removeFirst()
        if (demo && !frame.heading.isNaN()) addHeading(t, frame.heading)

        // 주행 중(D·N)에만 감지. 주차(P)·후진(R)은 제외
        val driving = frame.gear != "P" && frame.gear != "R"
        if (driving) {
            detectLongitudinal(t)
            detectCurve(t, filteredSpeed)
        } else {
            inEvent = false; inCurve = false
        }

        // 그래프: 차가 움직일 때만 쌓는다 → 정차 후에도 방금 주행의 흐름이 남아 있음
        if (frame.gear != "P" && (demo || frame.speed > 0.1f || pendingMark != 0)) {
            if (tripStart == 0L) tripStart = SystemClock.elapsedRealtime()   // 주행 기록 시간은 출발 순간부터
            chartSpeeds.add(frame.speed * 3.6f); chartMarks.add(pendingMark)
            if (chartSpeeds.size > chartCapacity) {
                chartSpeeds.removeAt(0); chartMarks.removeAt(0)
                for (k in chartGuides.indices.reversed()) {
                    if (chartGuides[k] <= 0) chartGuides.removeAt(k) else chartGuides[k] = chartGuides[k] - 1
                }
            }
        }
        pendingMark = 0

        // 위험도는 시간이 지나면 천천히 내려간다 (분당 약 9점)
        risk.value = max(0f, risk.value - 0.15f * dt)

        prune(recentJerks, t, 3 * 60_000L); prune(recentCurves, t, 3 * 60_000L); prune(recentRough, t, 3 * 60_000L)
        prune(sickSignals, now, 10 * 60_000L)

        respondByStage(now)
        updateListening(now)
    }

    internal fun prune(q: ArrayDeque<Long>, t: Long, keep: Long) {
        while (q.isNotEmpty() && (t - q.first() > keep || q.first() > t)) q.removeFirst()
    }

    // ---------------- 앞뒤 방향: 급가속·급제동·회생제동 '울컥' ----------------
    internal fun detectLongitudinal(t: Long) {
        val newest = samples.lastOrNull() ?: return
        // 약 0.6초 전 표본과 비교 (최소 0.4초 이상 떨어진 표본이 있어야 계산)
        val ref = samples.firstOrNull { newest.t - it.t <= 600 } ?: return
        val span = (newest.t - ref.t) / 1000f
        if (span < 0.4f) return
        val accel = (newest.v - ref.v) / span

        // 한 번의 가속·감속을 여러 번 세지 않도록: 움직임이 잦아들 때까지 다음 감지를 막는다
        if (inEvent) {
            if (accel > decelLimit / 2 && accel < accelLimit / 2) inEvent = false
            return
        }
        if (t - lastEventAt < 1000) return

        // 최근 1초 안에 한 번이라도 페달을 밟았으면 일반 제동으로 본다
        val pedalInWindow = samples.any { it.pedal }
        when {
            accel < decelLimit && !pedalInWindow -> {
                // 브레이크를 밟지 않았는데 강하게 감속 = 회생제동 '울컥'
                jerkCount.value++; recentJerks.addLast(t); pendingMark = 1
                addRisk(14f)
                event(t, "방금 회생제동 울컥이 있었어요", "울컥 %.1f m/s²".format(accel))
            }
            accel < decelLimit -> {
                brakeCount.value++; recentRough.addLast(t); pendingMark = 3
                addRisk(7f)
                event(t, "방금 급제동이 있었어요", "급제동 %.1f m/s²".format(accel))
            }
            accel > accelLimit -> {
                accelCount.value++; recentRough.addLast(t); pendingMark = 2
                addRisk(9f)
                event(t, "방금 급가속이 있었어요", "급가속 +%.1f m/s²".format(accel))
            }
        }
    }

    // 화면에는 운전자가 바로 이해하는 말로, 기록에는 수치로 남긴다
    // 기록 시간은 그래프와 같은 기준(그래프 한 점 = 0.2초)으로 센다.
    // (느린 기기에서 실제 시계로 세면 그래프 눈금과 기록 시간이 어긋남)
    internal fun addTrip(kind: Int, text: String) {
        val sec = chartSpeeds.size * tickMs / 1000
        tripLog.add(TripItem("%d:%02d".format(sec / 60, sec % 60), text, kind))
    }

    internal fun event(t: Long, text: String, detail: String) {
        lastCause = if (text.contains("울컥")) "JERK" else "ROUGH"
        lastEvent.value = text
        addTrip(when { text.contains("울컥") -> 1; text.contains("급가속") -> 2; else -> 3 },
            text.removePrefix("방금 ").removeSuffix("이 있었어요").removeSuffix("가 있었어요"))
        lastEventAt = t
        inEvent = true
        log(detail)
    }

    // ---------------- 옆 방향: 급커브 ----------------
    internal fun addHeading(t: Long, deg: Float) {
        headings.addLast(HeadingSample(t, deg))
        while (headings.isNotEmpty() && t - headings.first().t > 3000) headings.removeFirst()
    }

    internal fun detectCurve(t: Long, speed: Float) {
        val newest = headings.lastOrNull() ?: return
        if (t - newest.t > 3000) return                     // 위치 갱신이 끊긴 경우
        val ref = headings.firstOrNull { newest.t - it.t <= 1500 } ?: return
        val span = (newest.t - ref.t) / 1000f
        if (span < 0.8f) return
        var diff = newest.deg - ref.deg
        diff = ((diff + 540f) % 360f) - 180f                // -180~180도로 정리 (359→1도 같은 경우)
        val yawRate = Math.toRadians(diff.toDouble()).toFloat() / span   // rad/s
        val lateral = kotlin.math.abs(speed * yawRate)       // 옆 방향 가속도 ≈ 속도 × 회전 속도

        if (inCurve) {
            if (lateral < lateralLimit / 2) inCurve = false
            return
        }
        if (lateral > lateralLimit) {
            curveCount.value++; recentCurves.addLast(t); pendingMark = 4
            lastCause = "CURVE"
            addRisk(10f)
            inCurve = true
            lastEvent.value = "방금 급커브가 있었어요"
            addTrip(4, "급커브")
            log("급커브 옆 방향 %.1f m/s²".format(lateral))
        }
    }

    internal fun sensitivityFactor(): Float =
        if (!passengerOn.value) 0.5f else when (sensitivity.value) { 0 -> 0.7f; 2 -> 1.4f; else -> 1.0f }

    internal fun addRisk(points: Float) {
        risk.value = min(100f, risk.value + points * sensitivityFactor())
        peakRisk.value = max(peakRisk.value, risk.value)
    }

    // 멀미를 잘 하는 동승자일수록 더 낮은 위험도에서 듣기 시작
    internal fun listenThreshold(): Float = when (sensitivity.value) { 0 -> 60f; 2 -> 30f; else -> 45f }

    // =================================================================
    // 위험도 구간별 단계 대응 (기획서 5번)
    //   50 이상: 실내 환경 케어(외기·조명) → 70 이상: 승차감 안내 → 90 이상·멀미 신호 반복: 휴식 제안
    // =================================================================
    internal fun respondByStage(now: Long) {
        if (gearLabel.value == "P") return          // 주차 중에는 운전 안내를 하지 않는다
        val r = risk.value
        if (r >= 50f) careOnce(now)
        if (recentJerks.size >= 3) {
            guide("JERK", listOf(
                "회생제동 울컥임이 반복되고 있어요. 페달에서 발을 조금 더 천천히 떼 보세요.",
                "회생제동 울컥임이 잦아요. 회생제동 단계를 한 단계 낮춰 보세요."
            ), now)
        } else if (r >= 70f) {
            // 위험도를 올린 주된 원인에 맞춰 안내한다
            if (lastCause == "CURVE") {
                guide("CURVE", listOf(
                    "굽은 길이 이어지고 있어요. 커브에 들어가기 전에 미리 속도를 줄여 주세요.",
                    "커브에서 몸이 많이 흔들려요. 조금 더 천천히 돌아 주세요."
                ), now)
            } else {
                guide("ROUGH", listOf(
                    "가다 서다가 이어지고 있어요. 앞차와 간격을 넓히면 승차감이 좋아져요.",
                    "가속과 감속이 커요. 페달을 조금 더 부드럽게 다뤄 주세요."
                ), now)
            }
        }
        // 휴식 제안: 위험도 90 이상이 1분 넘게 이어질 때 (멀미 신호 2회는 onPassengerSick에서 처리)
        if (r >= 90f) { if (highSince == 0L) highSince = now } else highSince = 0L
        if (highSince != 0L && now - highSince >= 60_000) suggestRest(now)
    }

    internal fun careOnce(now: Long) {
        val last = lastGuideAt["CARE"]
        if (last != null && now - last < guideIntervalMs) return
        lastGuideAt["CARE"] = now
        cabinCare()
    }

    internal fun suggestRest(now: Long, urgent: Boolean = false) {
        guide("REST", urgent = urgent, now = now, sentences = listOf(
            "동승자가 많이 힘들어 보여요. 가까운 휴게소에서 잠깐 쉬어 가는 건 어떨까요?",
            "흔들림이 계속 쌓이고 있어요. 다음 휴게소에서 잠시 쉬어 가면 좋겠어요."
        ))
    }

    // =================================================================
    // 조건부 듣기 (STT)
    // =================================================================
    internal fun initStt() {
        try {
            val s = SpeechToText(ctx, SttMode.ON_DEVICE)
            stt = s
            s.addListener(object : ResultListener {
                override fun onUpdated(text: String, isFinal: Boolean) {
                    // 대화 원문은 화면에 띄우지도, 기록하지도 않는다. 완성된 문장에서 정해진 단어만 확인
                    if (isFinal) ui { onHeard(text) }
                }
                override fun onUpdatedEpdData(epdOn: Long, epdOff: Long) {}
                override fun onStartedRecognition() {}
                override fun onEndedRecognition() {
                    // 한 문장을 들으면 SDK가 스스로 멈추므로, 듣기 창이 남아 있으면 다시 듣는다
                    main.postDelayed({ if (listening.value) sttRequest() }, 300)
                }
                override fun onError(type: SttErrorType) {
                    log("음성 인식 오류: $type")
                    main.postDelayed({ if (listening.value) sttRequest() }, 1000)
                }
                override fun onReady() {}
                override fun onUpdatedRms(rms: Double) {}
            })
            s.initialize(object : SttInitListener {
                override fun onSuccess() {
                    runCatching { s.setLocale(Locale.KOREA) }
                    sttReady = true
                    log("음성 인식 준비 완료")
                }
                override fun onFailure(type: SttErrorType) { log("음성 인식 초기화 실패: $type") }
            })
        } catch (e: Exception) {
            log("음성 인식 예외: $e")
        }
    }

    internal fun updateListening(now: Long) {
        if (!passengerOn.value) {
            if (listening.value) stopListening(now)
            return
        }
        if (listening.value && (now > listenUntil || gearLabel.value == "P")) {
            stopListening(now)
            return
        }
        if (!listening.value && sttReady && now > speakingUntil && gearLabel.value != "P" &&
            now > listenCooldownUntil && risk.value >= listenThreshold()
        ) {
            listening.value = true
            listenUntil = now + 30_000
            heardKeyword.value = ""
            log("듣기 창 열림 (30초)")
            sttRequest()
        }
    }

    internal fun sttRequest() {
        bgVoice { stt?.request() }
    }

    internal fun stopListening(now: Long) {
        listening.value = false
        listenCooldownUntil = now + 20_000
        bgVoice { stt?.stop() }
        log("듣기 창 닫힘")
    }

    // "멀미 안 나", "멀미는 없어" 같은 부정 표현은 멀미 신호로 보지 않는다
    internal val sickNegation = Regex("멀미\\s*(는|가|도)?\\s*(안|않|없|별로)")

    internal fun onHeard(text: String) {
        if (text.isBlank()) return
        when {
            text.contains("멀미") && !sickNegation.containsMatchIn(text) -> {
                heardKeyword.value = "멀미"
                log("단어 감지: 멀미")
                onPassengerSick("음성")
            }
            text.contains("휴게소") -> {
                heardKeyword.value = "휴게소"
                log("단어 감지: 휴게소")
                lastGuideAt.remove("REST")
                suggestRest(SystemClock.elapsedRealtime(), urgent = true)
            }
            text.contains("괜찮") || sickNegation.containsMatchIn(text) -> {
                heardKeyword.value = "괜찮아"
                log("단어 감지: 괜찮아")
                risk.value = max(0f, risk.value - 20f)
                lastEvent.value = "동승자가 괜찮다고 했어요"
            }
        }
    }

    // =================================================================
    // 동승자 신호 → 안내 + 실내 환경 케어
    // =================================================================
    internal fun onPassengerSick(source: String) {
        val now = SystemClock.elapsedRealtime()
        // 멀미 신호가 오면 위험도를 '승차감 안내' 단계(70) 이상으로 바로 올린다
        risk.value = min(100f, max(risk.value + 25f, 70f))
        peakRisk.value = max(peakRisk.value, risk.value)
        sickTotal.value++
        sickSignals.addLast(now)
        lastEvent.value = "동승자 멀미 신호 ($source)"
        addTrip(6, "동승자 멀미 신호 ($source)")
        log(lastEvent.value)
        if (listening.value) stopListening(now)
        lastGuideAt.remove("CARE")
        careOnce(now)
        if (sickSignals.size >= 2) {
            lastGuideAt.remove("REST")
            suggestRest(now, urgent = true)
            return
        }
        guide(
            "SICK",
            listOf(
                "동승자가 멀미를 느끼고 있어요. 앞차와 간격을 조금 넓히고, 페달에서 천천히 발을 떼 보세요.",
                "동승자가 불편해해요. 회생제동 단계를 한 단계 낮추고, 부드럽게 속도를 줄여 보세요.",
                "동승자가 멀미 중이에요. 외기 유입으로 바꿀게요. 조금만 더 부드럽게 운전해 주세요."
            ),
            now,
            urgent = true
        )
    }

    // urgent: 동승자가 직접 보낸 신호에 대한 안내 → 간격 제한 없이 바로 말한다
    internal fun guide(type: String, sentences: List<String>, now: Long, urgent: Boolean = false) {
        val last = lastGuideAt[type]
        if (last != null && now - last < guideIntervalMs) return
        // 그 밖의 안내는 직전 안내와 최소 60초 간격을 둔다 (잔소리 방지, 멀미 신호 직후 1분도 포함)
        if (!urgent && now - lastSpokeAt < 60_000) return
        lastGuideAt[type] = now
        lastSpokeAt = now
        val text = sentences[(guideHistory.size + jerkCount.value) % sentences.size]
        guideText.value = text
        guideHistory.add(SimpleDateFormat("HH:mm", Locale.KOREA).format(Date()) + "  " + text)
        addTrip(5, text)
        if (gearLabel.value != "P") chartGuides.add(chartSpeeds.size)
        speak(text)
    }

    internal fun cabinCare() {
        val v = vehicle ?: run { log("실내 케어: 차량 미연결"); return }
        bg { cabinCareWork(v) }
    }

    internal fun cabinCareWork(v: Vehicle) {
        log("실내 케어 요청")
        runCatching {
            val hvac = v.getHVAC()
            hvac.checkHvacRecirculationCapabilities(
                { _, areas ->
                    log("공조 확인 응답: 영역 ${areas.size}개")
                    val area = areas.firstOrNull() ?: SeatArea(SeatValue.ROW_1_LEFT)
                    hvac.setHvacRecirculation(area, false,
                        { _, _ -> log("공조 외기 전환 성공"); ui { addCabin("외기 전환") } },
                        { e -> log("공조 오류: $e") })
                },
                { e -> log("공조 확인 오류: $e") }
            )
        }.onFailure { log("공조 예외: $it") }
        runCatching {
            // 안전 원칙: 밝기는 올리지 않고 색만 차분하게 바꾼다
            val light = v.getLight()
            light.checkAmbientLightBrightnessColorCapabilities(
                { _, areas ->
                    log("조명 확인 응답: 영역 ${areas.size}개")
                    val area = areas.firstOrNull() ?: SeatArea(SeatValue.ROW_1_LEFT)
                    light.setAmbientLightBrightnessColor(area, 0x3DD6B5,
                        { _, _ -> log("조명 변경 성공"); ui { addCabin("무드 조명") } },
                        { e -> log("조명 오류: $e") })
                },
                { e -> log("조명 확인 오류: $e") }
            )
        }.onFailure { log("조명 예외: $it") }
        log("실내 케어 요청 보냄")
    }

    internal fun addCabin(item: String) {
        cabinText.value = if (cabinText.value.isEmpty() || !cabinText.value.startsWith("실내 환경")) {
            "실내 환경: $item"
        } else if (!cabinText.value.contains(item)) {
            cabinText.value + " · " + item
        } else cabinText.value
    }

    // =================================================================
    // 음성 안내 (TTS)
    // =================================================================
    internal fun initTts() {
        try {
            val t = TextToSpeech(ctx, TtsMode.ON_DEVICE)
            t.initialize(object : TtsInitListener {
                override fun onSuccess() {
                    tts = t
                    runCatching {
                        t.addEventListener(object : EventListener {
                            override fun onStart() {}
                            override fun onReady() {}
                            override fun onDone() { speakingUntil = 0L }
                            override fun onError(type: TtsErrorType) { speakingUntil = 0L; log("음성 안내 오류: $type") }
                            override fun onStop() { speakingUntil = 0L }
                            override fun onUpdatedRms(rms: Double) {}
                        })
                    }
                    runCatching { t.setLocale(Locale.KOREA) }
                    ttsReady = true
                    log("음성 안내 준비 완료")
                }
                override fun onFailure(type: TtsErrorType) { log("음성 안내 초기화 실패: $type") }
            })
        } catch (e: Exception) {
            log("음성 안내 예외: $e")
        }
    }

    internal fun speak(text: String) {
        log("안내: $text")
        if (!ttsReady) return
        // 안내 소리를 마이크가 듣지 않도록 잠시 듣기를 멈추고, 말이 끝나면 다시 열 수 있게 한다
        speakingUntil = SystemClock.elapsedRealtime() + 15_000
        if (listening.value) {
            listening.value = false
            bgVoice { stt?.stop() }
        }
        bgVoice { tts?.speak("승차감 안내입니다. $text") }
    }

    // =================================================================
    // 시연용 주행 재생: 고속도로 정체 구간(가다 서다) → 나들목 굽은 길 → 정차
    // =================================================================
    internal fun buildDemo(): List<Frame> {
        val f = mutableListOf<Frame>()
        val step = tickMs / 1000f
        var heading = 90f
        var speedNow = 0f
        fun hold(kmh: Float, sec: Float, gear: String = "D", pedal: Boolean = false) {
            speedNow = kmh
            repeat((sec / step).toInt()) { f.add(Frame(kmh / 3.6f, pedal, gear, heading)) }
        }
        fun ramp(from: Float, to: Float, sec: Float, pedal: Boolean = false) {
            val n = (sec / step).toInt()
            for (i in 1..n) f.add(Frame((from + (to - from) * i / n) / 3.6f, pedal, "D", heading))
            speedNow = to
        }
        fun turn(deg: Float, sec: Float) {          // 같은 속도로 deg만큼 방향 전환
            val n = (sec / step).toInt()
            for (i in 1..n) {
                heading = (heading + deg / n + 360f) % 360f
                f.add(Frame(speedNow / 3.6f, false, "D", heading))
            }
        }
        hold(0f, 1.5f, gear = "P", pedal = true)
        hold(0f, 1.5f, pedal = true)
        // 1회차: 부드럽게 출발 → 회생제동 울컥
        ramp(0f, 35f, 5f); hold(35f, 2f); ramp(35f, 8f, 1.6f); ramp(8f, 5f, 1f)
        // 2회차: 급가속 → 회생제동 울컥
        ramp(5f, 40f, 2.6f); hold(40f, 2f); ramp(40f, 10f, 1.6f); ramp(10f, 6f, 1f)
        // 3회차: 브레이크를 밟은 급제동 (울컥과 구분됨)
        ramp(6f, 35f, 5f); hold(35f, 2f); ramp(35f, 8f, 1.6f, pedal = true); ramp(8f, 5f, 1f)
        // 4·5회차: 회생제동 울컥 반복
        ramp(5f, 35f, 5f); hold(35f, 2f); ramp(35f, 8f, 1.6f); ramp(8f, 5f, 1f)
        ramp(5f, 30f, 4f); hold(30f, 2f); ramp(30f, 6f, 1.4f); ramp(6f, 4f, 1f)
        // 나들목 굽은 길: 왼쪽 90도 → 직진 → 오른쪽 90도
        ramp(4f, 30f, 5f); hold(30f, 2f)
        turn(-90f, 3.5f); hold(30f, 2f)
        ramp(30f, 28f, 1f); turn(90f, 3f); hold(28f, 2f)
        // 천천히 정차
        ramp(28f, 0f, 9f, pedal = true)
        hold(0f, 2f, pedal = true)
        hold(0f, 2f, gear = "P", pedal = true)
        return f
    }

    internal fun nextDemoFrame(): Frame {
        if (demoIndex >= demoFrames.size) {
            demoRunning.value = false
            lastTickT = -1L; samples.clear(); headings.clear()
            log("주행 재생 끝")
            return demoFrames.lastOrNull() ?: Frame(0f, false, "P", Float.NaN)
        }
        return demoFrames[demoIndex++]
    }

    internal fun toggleDemo() {
        lastTickT = -1L; samples.clear(); headings.clear(); filteredSpeed = 0f
        inEvent = false; inCurve = false
        if (demoRunning.value) {
            demoRunning.value = false
            log("주행 재생 정지")
        } else {
            resetAll()                         // 새 주행으로 시작 (지난 주행 요약 지우기)
            demoFrames = buildDemo()
            demoIndex = 0
            simClock = 0L; lastEventAt = -10_000L
            recentJerks.clear(); recentCurves.clear(); recentRough.clear()
            demoRunning.value = true
            log("주행 재생 시작 (약 ${demoFrames.size * tickMs / 1000}초)")
        }
    }

    internal fun resetAll() {
        demoRunning.value = false
        risk.value = 0f; peakRisk.value = 0f; sickTotal.value = 0
        jerkCount.value = 0; accelCount.value = 0; brakeCount.value = 0; curveCount.value = 0
        recentJerks.clear(); recentCurves.clear(); recentRough.clear(); sickSignals.clear()
        lastGuideAt.clear(); samples.clear(); headings.clear(); lastSpokeAt = -120_000L; highSince = 0L; lastCause = ""
        inEvent = false; inCurve = false; lastTickT = -1L; filteredSpeed = 0f
        if (listening.value) stopListening(SystemClock.elapsedRealtime())
        listenCooldownUntil = 0L
        guideText.value = "아직 알린 내용이 없어요."
        lastEvent.value = ""; cabinText.value = ""; heardKeyword.value = ""
        chartSpeeds.clear(); chartMarks.clear(); chartGuides.clear(); guideHistory.clear(); tripLog.clear(); tripStart = 0L
        log("초기화")
    }

    // =================================================================
    // 도우미
    // =================================================================
    internal fun ui(block: () -> Unit) = main.post(block)

    // 차량·음성 SDK 호출은 화면 스레드가 아닌 작업 스레드에서 한다.
    // (화면 스레드에서 호출하면 응답이 늦을 때 앱이 5초 이상 멈춰 '응답 없음'으로 종료됨)
    // Looper가 있는 작업 스레드 (SDK가 내부에서 Handler를 만들어도 안전)
    internal val workerThread = android.os.HandlerThread("sokpyeon-sdk").apply { start() }
    internal val workerHandler = Handler(workerThread.looper)

    internal fun bg(block: () -> Unit) {
        workerHandler.post { runCatching(block).onFailure { log("SDK 호출 오류: $it") } }
    }

    // 음성(STT·TTS) 호출은 별도 스레드: 음성 쪽이 느려도 차량 제어가 막히지 않게
    internal val voiceThread = android.os.HandlerThread("sokpyeon-voice").apply { start() }
    internal val voiceHandler = Handler(voiceThread.looper)

    internal fun bgVoice(block: () -> Unit) {
        voiceHandler.post { runCatching(block).onFailure { log("음성 호출 오류: $it") } }
    }

    internal fun log(msg: String) {
        Log.d(tag, msg)
    }

    internal fun gearName(g: VehicleGear?) = when (g) {
        VehicleGear.GEAR_PARK -> "P"; VehicleGear.GEAR_REVERSE -> "R"
        VehicleGear.GEAR_NEUTRAL -> "N"; VehicleGear.GEAR_DRIVE -> "D"
        null -> "-"; else -> g.name
    }

    companion object {
        const val PERM_SPEED = "android.car.permission.CAR_SPEED"
        const val PERM_LOCATION = "pleos.car.permission.FUSED_LOCATION"
        private const val PERF_VEHICLE_SPEED = 291504647

        // 앱 전체에서 하나만 (서비스와 화면이 같은 엔진을 공유)
        @Volatile private var instance: SokpyeonEngine? = null
        fun get(context: Context): SokpyeonEngine =
            instance ?: synchronized(this) {
                instance ?: SokpyeonEngine(context.applicationContext).also { instance = it }
            }
    }
}

// =====================================================================
// 백그라운드 서비스: 화면이 내비로 바뀌어도 판단 엔진을 계속 돌린다
//   - 상시 알림에 "멀미 나요" 버튼 → 앱을 열지 않고도 동승자 신호 전달
// =====================================================================
class SokpyeonService : Service() {

    override fun onCreate() {
        super.onCreate()
        startAsForeground()
        SokpyeonEngine.get(this).start()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_SICK) {
            SokpyeonEngine.get(this).onPassengerSick("알림")
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startAsForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "속편한 승차감 케어", NotificationManager.IMPORTANCE_LOW)
        )
        val sick = PendingIntent.getService(
            this, 1, Intent(this, SokpyeonService::class.java).setAction(ACTION_SICK),
            PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("속편한이 승차감을 살피고 있어요")
            .setContentText("동승자가 불편하면 '멀미 나요'를 눌러 주세요")
            .addAction(0, "멀미 나요", sick)
            .setOngoing(true)
            .build()
        // Android 14(API 34)부터는 서비스 종류(specialUse)를 함께 알려야 한다
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTI_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTI_ID, notification)
        }
    }

    companion object {
        const val ACTION_SICK = "com.sokpyeon.app.SICK"
        private const val CHANNEL = "sokpyeon_care"
        private const val NOTI_ID = 1
    }
}

// =====================================================================
// 화면: 엔진의 상태를 보여 주기만 한다 (화면을 닫아도 엔진은 서비스에서 계속 동작)
// =====================================================================
class MainActivity : ComponentActivity() {

    private val engine by lazy { SokpyeonEngine.get(this) }

    // 화면 코드가 엔진 상태를 그대로 읽도록 연결
    private val speedKmh get() = engine.speedKmh
    private val gearLabel get() = engine.gearLabel
    private val risk get() = engine.risk
    private val jerkCount get() = engine.jerkCount
    private val accelCount get() = engine.accelCount
    private val brakeCount get() = engine.brakeCount
    private val curveCount get() = engine.curveCount
    private val peakRisk get() = engine.peakRisk
    private val sickTotal get() = engine.sickTotal
    private val passengerOn get() = engine.passengerOn
    private val sensitivity get() = engine.sensitivity
    private val listening get() = engine.listening
    private val heardKeyword get() = engine.heardKeyword
    private val guideText get() = engine.guideText
    private val lastEvent get() = engine.lastEvent
    private val guideHistory get() = engine.guideHistory
    private val cabinText get() = engine.cabinText
    private val demoRunning get() = engine.demoRunning
    private val chartSpeeds get() = engine.chartSpeeds
    private val chartMarks get() = engine.chartMarks
    private val chartGuides get() = engine.chartGuides
    private val tripLog get() = engine.tripLog
    private fun toggleDemo() = engine.toggleDemo()
    private fun resetAll() = engine.resetAll()
    private fun onPassengerSick(source: String) = engine.onPassengerSick(source)
    private fun saveSettings() = engine.saveSettings()

    // 화면 부담 줄이기: 결과(어느 화면인지, 주행 중인지)가 바뀔 때만 다시 그린다
    private val movingState by lazy { derivedStateOf { speedKmh.value > 5f && gearLabel.value != "P" } }
    private fun isMoving() = movingState.value
    private val screenState by lazy { derivedStateOf { currentView() } }

    private val requestPermissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            result.forEach { (p, ok) -> Log.d("SOKPYEON", "권한 ${p.substringAfterLast('.')}: ${if (ok) "허용" else "거부"}") }
            engine.connectPermittedSignals()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AppRoot() }
        Log.d("SOKPYEON", "화면 시작")
        // 엔진은 서비스가 돌린다 (화면이 내비로 바뀌어도 계속)
        main.postDelayed({
            runCatching { ContextCompat.startForegroundService(this, Intent(this, SokpyeonService::class.java)) }
                .onFailure { Log.d("SOKPYEON", "서비스 시작 오류: $it"); engine.start() }
            val need = listOf(SokpyeonEngine.PERM_SPEED, SokpyeonEngine.PERM_LOCATION, PERM_NOTIFICATION).filter {
                ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
            }
            if (need.isNotEmpty()) requestPermissions.launch(need.toTypedArray())
        }, 300)
    }

    private val main = Handler(Looper.getMainLooper())

    companion object {
        private const val PERM_NOTIFICATION = "android.permission.POST_NOTIFICATIONS"
    }

    // =================================================================
    // 화면
    //   준비(주차·주행 전) → 주행 중(최소 정보) → 주행 후(돌아보기) 세 화면
    //   시연: 로고를 길게 누르면 주행 재생, 두 번 누르면 초기화
    // =================================================================
    private enum class Screen { READY, DRIVING, SUMMARY }

    private fun currentView(): Screen {
        val parked = gearLabel.value == "P" || (gearLabel.value == "-" && speedKmh.value < 1f)
        return when {
            !parked -> Screen.DRIVING
            chartSpeeds.isNotEmpty() -> Screen.SUMMARY
            else -> Screen.READY
        }
    }

    @Composable
    private fun AppRoot() {
        val pal = if (isSystemInDarkTheme()) DarkPal else LightPal
        CompositionLocalProvider(LocalPal provides pal) {
            ProvideTextStyle(TextStyle(fontFamily = AstaSans, color = pal.text1)) {
                Surface(modifier = Modifier.fillMaxSize(), color = pal.bg, contentColor = pal.text1) {
                    AppScreen()
                }
            }
        }
    }

    @Composable
    private fun AppScreen() {
        BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 20.dp)) {
            val wide = maxWidth >= 900.dp       // 화면을 반으로 나눠 쓰면 세로로 쌓는다
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Header()
                // 화면이 바뀔 때 부드럽게 겹쳐 전환
                Crossfade(
                    targetState = screenState.value,
                    animationSpec = tween(450),
                    modifier = Modifier.weight(1f).fillMaxWidth()
                ) { screen ->
                    when (screen) {
                        Screen.READY -> ReadyView(wide)
                        Screen.DRIVING -> DrivingView(wide)
                        Screen.SUMMARY -> SummaryView(wide)
                    }
                }
            }
        }
    }

    // 넓으면 좌우 두 칸, 좁으면 위아래로 쌓고 스크롤
    @Composable
    private fun TwoPane(
        wide: Boolean,
        leftWeight: Float = 1f,
        left: @Composable ColumnScope.() -> Unit,
        right: @Composable ColumnScope.() -> Unit
    ) {
        if (wide) {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                Column(Modifier.weight(leftWeight).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(20.dp)) { left() }
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(20.dp)) { right() }
            }
        } else {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) { left(); right() }
        }
    }

    // 넓은 화면에서는 남은 높이를 채우고, 좁은 화면(스크롤)에서는 정해진 높이 또는 내용 높이만큼
    private fun ColumnScope.fill(wide: Boolean, weight: Float = 1f, narrowHeight: Int = 0): Modifier =
        if (wide) Modifier.weight(weight).fillMaxWidth()
        else if (narrowHeight > 0) Modifier.fillMaxWidth().height(narrowHeight.dp)
        else Modifier.fillMaxWidth()

    // ---------------- 머리글 ----------------
    @Composable
    private fun Header() {
        val p = LocalPal.current
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.pointerInput(Unit) {
                    detectTapGestures(
                        onLongPress = { toggleDemo() },   // 시연용: 주행 재생 시작/정지
                        onDoubleTap = { resetAll() }      // 시연용: 초기화
                    )
                }
            ) {
                LogoMark(Modifier.size(56.dp))
                Spacer(Modifier.width(16.dp))
                Column {
                    Text("속편한", fontSize = TS.bodyL, lineHeight = TS.bodyLLH, fontWeight = FontWeight.ExtraBold)
                    Text("동승자의 멀미를 미리 읽고, 대신 전하고, 줄여요", fontSize = TS.labelS, lineHeight = TS.labelSLH, color = p.text3)
                }
            }
            Spacer(Modifier.weight(1f))
            if (demoRunning.value) {
                Pill("주행 데이터 재생 중", p.warn)
                Spacer(Modifier.width(10.dp))
            }
            Pill(
                if (passengerOn.value) "동승자 멀미 " + sensitivityName(sensitivity.value) else "운전자 혼자",
                if (passengerOn.value) p.brand else p.text3
            )
        }
    }

    @Composable
    private fun LogoMark(modifier: Modifier) {
        Canvas(modifier.clip(RoundedCornerShape(14.dp))) {
            val s = size.width / 108f
            drawRect(Brush.linearGradient(listOf(Color(0xFF2FE0B8), Color(0xFF0A5F80)), Offset.Zero, Offset(size.width, size.height)))
            drawCircle(Color.White.copy(alpha = 0.12f), radius = 30f * s, center = Offset(54f * s, 54f * s))
            drawCircle(Color.White, radius = 6f * s, center = Offset(54f * s, 39f * s))
            val wave = Path().apply {
                moveTo(30f * s, 60f * s)
                cubicTo(38f * s, 50f * s, 46f * s, 50f * s, 54f * s, 58f * s)
                cubicTo(62f * s, 66f * s, 70f * s, 66f * s, 78f * s, 56f * s)
            }
            drawPath(wave, Color.White, style = Stroke(6.5f * s, cap = StrokeCap.Round))
            val wave2 = Path().apply {
                moveTo(34f * s, 71f * s)
                cubicTo(41f * s, 66f * s, 47f * s, 66f * s, 54f * s, 70f * s)
                cubicTo(61f * s, 74f * s, 67f * s, 74f * s, 74f * s, 69f * s)
            }
            drawPath(wave2, Color.White.copy(alpha = 0.55f), style = Stroke(4f * s, cap = StrokeCap.Round))
        }
    }

    // =================================================================
    // 1) 준비 화면: 출발 전 동승자 설정 + 앱이 하는 일
    // =================================================================
    @Composable
    private fun ReadyView(wide: Boolean) {
        val p = LocalPal.current
        TwoPane(wide, leftWeight = 1.15f,
            left = {
                Panel(fill(wide)) {
                    Text("출발 전 준비", fontSize = TS.labelM, color = p.brand, fontWeight = FontWeight.SemiBold)
                    Text("오늘 동승자가 있나요?", fontSize = TS.headlineS, lineHeight = TS.headlineLH, fontWeight = FontWeight.ExtraBold)
                    Text("동승자 모드를 켜고 멀미를 얼마나 하는지 알려 주세요. 주행이 시작되면 나머지는 앱이 알아서 해요.",
                        fontSize = TS.bodyS, lineHeight = TS.bodySLH, color = p.text2)
                    Spacer(Modifier.height(16.dp))
                    PassengerControls(large = true, wide = wide)
                }
            },
            right = {
                Panel(fill(wide)) {
                    Text("주행 중에는 이렇게 도와요", fontSize = TS.bodyM, lineHeight = TS.bodyMLH, fontWeight = FontWeight.ExtraBold)
                    Spacer(Modifier.height(4.dp))
                    Column(
                        if (wide) Modifier.weight(1f) else Modifier,
                        verticalArrangement = if (wide) Arrangement.SpaceEvenly else Arrangement.spacedBy(12.dp)
                    ) {
                        Step(1, "흔들림을 살펴요", "차의 속도·브레이크·진행 방향으로 회생제동 울컥임과 급커브를 읽어요.")
                        Step(2, "필요할 때만 들어요", "흔들림이 쌓일 때만 잠깐 '멀미'라는 말을 들어요. 대화는 보여 주거나 저장하지 않아요.")
                        Step(3, "대신 전하고 줄여요", "운전자에게는 음성으로 부드럽게 알리고, 외기 유입을 켜고 무드 조명을 차분한 색으로 바꿔요.")
                        Step(4, "내비와 함께 써요", "화면에 내비를 띄워 둬도 뒤에서 계속 살피고 음성으로 알려요.")
                    }
                    Box(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(p.surfaceHigh)
                            .padding(horizontal = 20.dp, vertical = 14.dp)
                    ) {
                        Text("위치·주행 기록과 음성은 차 안에서만 처리하고 밖으로 보내지 않아요.",
                            fontSize = TS.labelS, lineHeight = TS.labelSLH, color = p.text2)
                    }
                    Text("시연: 왼쪽 위 로고를 길게 누르면 정체 구간 주행을 재생해요",
                        fontSize = TS.labelS, lineHeight = TS.labelSLH, color = p.text3)
                }
            }
        )
    }

    @Composable
    private fun Step(n: Int, title: String, body: String) {
        val p = LocalPal.current
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(56.dp).clip(CircleShape).background(p.brandSoft),
                contentAlignment = Alignment.Center
            ) { Text("$n", fontSize = TS.bodyS, color = p.brand, fontWeight = FontWeight.ExtraBold) }
            Spacer(Modifier.width(20.dp))
            Column {
                Text(title, fontSize = TS.bodyS, lineHeight = TS.bodySLH, fontWeight = FontWeight.SemiBold)
                Text(body, fontSize = TS.labelS, lineHeight = 30.sp, color = p.text2)
            }
        }
    }

    // =================================================================
    // 2) 주행 중 화면: 운전자 시선을 끌지 않도록 최소 정보만, 움직이는 그래프 없음
    // =================================================================
    @Composable
    private fun DrivingView(wide: Boolean) {
        TwoPane(wide, leftWeight = 1.1f,
            left = {
                StatusCard(fill(wide, 1f, 420))
            },
            right = {
                GuideCard()
                CabinCard()
                ListeningCard(fill(wide, 1f, 0), big = wide)
                SickButton(Modifier.fillMaxWidth().height(120.dp))
                Panel(Modifier.fillMaxWidth()) { PassengerControls(large = false, wide = wide) }
            }
        )
    }

    @Composable
    private fun StatusCard(modifier: Modifier) {
        val p = LocalPal.current
        val shown = animateFloatAsState(targetValue = risk.value, animationSpec = tween(600)).value
        val color = riskColor(shown, p)
        Panel(modifier) {
            Text("지금 동승자는", fontSize = TS.labelM, color = p.text3)
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize().padding(8.dp)) {
                    val d = min(size.width, size.height)
                    val stroke = d * 0.07f
                    val arc = Size(d - stroke, d - stroke)
                    val tl = Offset((size.width - d) / 2 + stroke / 2, (size.height - d) / 2 + stroke / 2)
                    drawArc(p.surfaceHigh, 140f, 260f, false, tl, arc, style = Stroke(stroke, cap = StrokeCap.Round))
                    drawArc(color, 140f, 260f * shown / 100f, false, tl, arc, style = Stroke(stroke, cap = StrokeCap.Round))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stateWord(shown), fontSize = TS.headlineS, lineHeight = TS.headlineLH,
                        fontWeight = FontWeight.ExtraBold, color = p.text1)
                    Text("멀미 위험도 %.0f".format(shown), fontSize = TS.labelM, lineHeight = TS.labelMLH, color = color,
                        fontWeight = FontWeight.SemiBold)
                }
            }
            Text(
                if (lastEvent.value.isEmpty()) "흔들림을 살펴보고 있어요" else lastEvent.value,
                fontSize = TS.labelS, lineHeight = TS.labelSLH, color = p.text3,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
    }

    @Composable
    private fun CheckMark(color: Color) {
        Canvas(Modifier.size(18.dp)) {
            val path = Path().apply {
                moveTo(size.width * 0.15f, size.height * 0.55f)
                lineTo(size.width * 0.42f, size.height * 0.8f)
                lineTo(size.width * 0.88f, size.height * 0.22f)
            }
            drawPath(path, color, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round))
        }
    }

    // 운전자에게 전한 안내
    @Composable
    private fun GuideCard() {
        val p = LocalPal.current
        Panel(Modifier.fillMaxWidth()) {
            Text("방금 운전자에게 음성으로 알린 내용", fontSize = TS.labelS, lineHeight = TS.labelSLH, color = p.text3)
            Text(guideText.value, fontSize = TS.bodyM, lineHeight = TS.bodyMLH, fontWeight = FontWeight.SemiBold)
        }
    }

    // 실내 환경 상태 (조치 전에는 '필요할 때 바꿔요')
    @Composable
    private fun CabinCard() {
        val p = LocalPal.current
        Panel(Modifier.fillMaxWidth()) {
            Text("실내 환경", fontSize = TS.labelS, color = p.text3)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CabinState("외기 유입", "켰어요", "필요할 때 켜요", cabinText.value.contains("외기"), Modifier.weight(1f))
                CabinState("무드 조명", "차분한 색으로 바꿨어요", "필요할 때 바꿔요", cabinText.value.contains("조명"), Modifier.weight(1f))
            }
        }
    }

    @Composable
    private fun CabinState(label: String, onText: String, offText: String, on: Boolean, modifier: Modifier) {
        val p = LocalPal.current
        Row(
            modifier.heightIn(min = 64.dp).clip(RoundedCornerShape(16.dp))
                .background(if (on) p.brandSoft else p.surfaceHigh).padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (on) { CheckMark(p.brand); Spacer(Modifier.width(10.dp)) }
            Column {
                Text(label, fontSize = TS.labelS, lineHeight = TS.labelSLH,
                    color = if (on) p.brand else p.text2, fontWeight = FontWeight.SemiBold)
                Text(if (on) onText else offText, fontSize = 18.sp, lineHeight = 22.sp, color = p.text3)
            }
        }
    }

    // 조건부 듣기 상태. 넓은 화면에서는 남은 높이를 채우는 큰 형태로 (이 앱의 핵심 기능)
    @Composable
    private fun ListeningCard(modifier: Modifier, big: Boolean) {
        val p = LocalPal.current
        val on = listening.value
        // 깜빡임 애니메이션은 듣는 중일 때만 돌린다 (평소에는 화면을 계속 다시 그리지 않음)
        val pulse = if (on) rememberInfiniteTransition().animateFloat(
            initialValue = 0.4f, targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse)
        ).value else 1f
        val title = when {
            heardKeyword.value.isNotEmpty() -> "'${heardKeyword.value}'라는 말을 알아들었어요"
            on -> "듣는 중이에요"
            passengerOn.value -> "흔들림이 쌓일 때만 잠깐 들어요"
            else -> "동승자 모드가 꺼져 있어요"
        }
        val sub = if (on) "'멀미'라고 말하면 차가 알아서 조치해요" else "대화 내용은 보여 주거나 저장하지 않아요"
        Panel(modifier, border = if (on) p.brand else null) {
            if (big) {
                Column(
                    Modifier.weight(1f).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(96.dp)) {
                        Box(Modifier.size(96.dp).clip(CircleShape)
                            .background(if (on) p.brand.copy(alpha = 0.12f + 0.10f * pulse) else p.surfaceHigh))
                        MicIcon(if (on) p.brand else p.text3, 44)
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(title, fontSize = TS.bodyS, lineHeight = TS.bodySLH, fontWeight = FontWeight.SemiBold)
                    Text(sub, fontSize = TS.labelS, lineHeight = TS.labelSLH, color = p.text3)
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MicIcon(if (on) p.brand.copy(alpha = pulse) else p.text3, 32)
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(title, fontSize = TS.labelM, lineHeight = TS.labelMLH, fontWeight = FontWeight.SemiBold)
                        Text(sub, fontSize = TS.labelS, lineHeight = TS.labelSLH, color = p.text3)
                    }
                }
            }
        }
    }

    @Composable
    private fun MicIcon(color: Color, sizeDp: Int) {
        Canvas(Modifier.size(sizeDp.dp)) {
            val w = size.width; val h = size.height
            drawRoundRect(color, topLeft = Offset(w * 0.34f, h * 0.06f), size = Size(w * 0.32f, h * 0.52f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.16f, w * 0.16f))
            val cup = Path().apply {
                moveTo(w * 0.2f, h * 0.42f)
                cubicTo(w * 0.2f, h * 0.78f, w * 0.8f, h * 0.78f, w * 0.8f, h * 0.42f)
            }
            drawPath(cup, color, style = Stroke(w * 0.07f, cap = StrokeCap.Round))
            drawLine(color, Offset(w * 0.5f, h * 0.74f), Offset(w * 0.5f, h * 0.92f), strokeWidth = w * 0.07f, cap = StrokeCap.Round)
        }
    }

    @Composable
    private fun SickButton(modifier: Modifier) {
        val p = LocalPal.current
        val enabled = passengerOn.value
        Box(
            modifier = modifier
                .clip(RoundedCornerShape(24.dp))
                .background(if (enabled) p.sickButton else p.surfaceHigh)
                .clickable(enabled = enabled) { onPassengerSick("버튼") },
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("멀미 나요", fontSize = TS.bodyL, lineHeight = TS.bodyLLH, fontWeight = FontWeight.ExtraBold,
                    color = if (enabled) Color.White else p.text3)
                Text(
                    if (enabled) "누르면 차가 알아서 조치하고, 운전자에게 부드럽게 전해요" else "동승자 모드를 켜면 쓸 수 있어요",
                    fontSize = TS.labelS, lineHeight = TS.labelSLH,
                    color = if (enabled) Color.White.copy(alpha = 0.9f) else p.text3
                )
            }
        }
    }

    // ---------------- 동승자 모드 (준비 화면: 크게 / 주행 중: 작게) ----------------
    @Composable
    private fun ColumnScope.PassengerControls(large: Boolean, wide: Boolean) {
        val p = LocalPal.current
        val moving = isMoving()
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().heightIn(min = TouchMin)) {
            Column(Modifier.weight(1f)) {
                Text("동승자 모드", fontSize = if (large) TS.bodyM else TS.labelM, fontWeight = FontWeight.SemiBold)
                Text(
                    if (moving) "주행 중에는 동승자가 조작해 주세요" else "동승자가 멀미를 얼마나 하나요?",
                    fontSize = TS.labelS, lineHeight = TS.labelSLH, color = if (moving) p.warn else p.text3
                )
            }
            Switch(
                checked = passengerOn.value,
                onCheckedChange = { passengerOn.value = it; saveSettings() },
                modifier = Modifier.scale(1.4f).padding(end = 12.dp),
                colors = SwitchDefaults.colors(
                    checkedTrackColor = p.calm, checkedThumbColor = Color.White,
                    uncheckedTrackColor = p.surfaceHigh, uncheckedThumbColor = p.text3, uncheckedBorderColor = p.line
                )
            )
        }
        Spacer(Modifier.height(12.dp))
        if (large) {
            // 출발 전: 선택지마다 설명을 붙여 크게 (동승자가 직접 고르기 쉽게)
            val desc = listOf("먼 길도 괜찮아요", "막히는 길·굽은 길에서 힘들어요", "차만 타면 속이 불편해요")
            // 넓은 화면에서는 선택지 3개가 남은 높이를 나눠 채운다 (큰 터치 영역)
            Column(
                if (wide) Modifier.weight(1f).fillMaxWidth() else Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                for (i in 0..2) {
                    val selected = sensitivity.value == i && passengerOn.value
                    Row(
                        (if (wide) Modifier.weight(1f) else Modifier).fillMaxWidth().heightIn(min = 96.dp, max = 160.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(if (selected) p.brandSoft else p.surfaceHigh)
                            .border(2.dp, if (selected) p.brand else Color.Transparent, RoundedCornerShape(18.dp))
                            .clickable(enabled = passengerOn.value) { sensitivity.value = i; saveSettings() }
                            .padding(horizontal = 24.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        WaveGlyph(i + 1, if (selected) p.brand else if (passengerOn.value) p.text3 else p.line)
                        Spacer(Modifier.width(20.dp))
                        Text(sensitivityName(i), fontSize = TS.bodyS, fontWeight = FontWeight.ExtraBold,
                            color = if (selected) p.brand else if (passengerOn.value) p.text1 else p.text3,
                            modifier = Modifier.width(170.dp))
                        Text(desc[i], fontSize = TS.labelM, color = if (passengerOn.value) p.text2 else p.text3)
                    }
                }
            }
        } else {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(p.surfaceHigh).padding(6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                for (i in 0..2) {
                    val selected = sensitivity.value == i && passengerOn.value
                    Box(
                        Modifier.weight(1f).heightIn(min = TouchMin).clip(RoundedCornerShape(14.dp))
                            .background(if (selected) p.surface else Color.Transparent)
                            .clickable(enabled = passengerOn.value) { sensitivity.value = i; saveSettings() },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            sensitivityName(i), fontSize = TS.labelM,
                            fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Normal,
                            color = if (selected) p.brand else if (passengerOn.value) p.text2 else p.text3
                        )
                    }
                }
            }
        }
    }

    private fun sensitivityName(i: Int) = when (i) { 0 -> "거의 안 해요"; 2 -> "자주 해요"; else -> "가끔 해요" }

    // =================================================================
    // 3) 주행 후 화면: 이번 주행 돌아보기 (정차 중에만 표시)
    // =================================================================
    @Composable
    private fun SummaryView(wide: Boolean) {
        val p = LocalPal.current
        val (headline, tip) = summaryTip()
        TwoPane(wide, leftWeight = 1.3f,
            left = {
                Panel(fill(wide, 1f, 380)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("이번 주행 흐름", fontSize = TS.labelM, color = p.text3)
                        Spacer(Modifier.weight(1f))
                        Text("속도 (최고 %.0f km/h)".format(chartSpeeds.maxOrNull() ?: 0f), fontSize = TS.labelS, color = p.text3)
                    }
                    SpeedChart(Modifier.weight(1f).fillMaxWidth())
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    CountTile("회생제동 울컥", jerkCount.value, p.high, Modifier.weight(1f))
                    CountTile("급커브", curveCount.value, p.curve, Modifier.weight(1f))
                    CountTile("급가속", accelCount.value, p.warn, Modifier.weight(1f))
                    CountTile("급제동", brakeCount.value, p.brake, Modifier.weight(1f))
                }
            },
            right = {
                Panel(Modifier.fillMaxWidth()) {
                    Text("이번 주행 돌아보기", fontSize = TS.labelM, color = p.brand, fontWeight = FontWeight.SemiBold)
                    Text(headline, fontSize = TS.bodyL, lineHeight = TS.bodyLLH, fontWeight = FontWeight.ExtraBold)
                    Text(tip, fontSize = TS.bodyS, lineHeight = TS.bodySLH, color = p.text2)
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth()) {
                        MiniStat("최고 멀미 위험도", "%.0f".format(peakRisk.value), Modifier.weight(1f))
                        MiniStat("동승자 멀미 신호", "${sickTotal.value}회", Modifier.weight(1f))
                        MiniStat("음성 안내", "${guideHistory.size}회", Modifier.weight(1f))
                    }
                }
                Panel(fill(wide, 1f, 0)) {
                    Text("이번 주행 기록", fontSize = TS.labelM, color = p.text3)
                    if (tripLog.isEmpty()) {
                        Text("이번 주행에서는 큰 흔들림이 없었어요.", fontSize = TS.labelM, color = p.text2)
                    } else {
                        // 흔들림과 음성 안내를 시간 순으로 (넓은 화면에서는 칸 안에서 스크롤)
                        Column(
                            (if (wide) Modifier.weight(1f) else Modifier.heightIn(max = 420.dp))
                                .fillMaxWidth().verticalScroll(rememberScrollState())
                        ) {
                            tripLog.forEach { item -> TripRow(item) }
                        }
                    }
                }
                Box(
                    Modifier.fillMaxWidth().heightIn(min = TouchMin).clip(RoundedCornerShape(20.dp))
                        .background(p.surfaceHigh).clickable { resetAll() },
                    contentAlignment = Alignment.Center
                ) { Text("새 주행 준비", fontSize = TS.labelM, fontWeight = FontWeight.SemiBold, color = p.text1) }
            }
        )
    }

    @Composable
    private fun TripRow(item: SokpyeonEngine.TripItem) {
        val p = LocalPal.current
        val color = when (item.kind) { 1 -> p.high; 2 -> p.warn; 3 -> p.brake; 4 -> p.curve; 5 -> p.brand; else -> p.sickButton }
        val voice = item.kind == 5
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.Top) {
            Text(item.time, fontSize = TS.labelS, lineHeight = TS.labelSLH, color = p.text3, modifier = Modifier.width(64.dp))
            Box(Modifier.padding(top = 7.dp).size(10.dp).clip(CircleShape).background(color))
            Spacer(Modifier.width(12.dp))
            Text(
                if (voice) "음성 안내: ${item.text}" else item.text,
                fontSize = TS.labelS, lineHeight = TS.labelSLH,
                color = if (voice) p.text1 else p.text2,
                fontWeight = if (voice) FontWeight.SemiBold else FontWeight.Normal
            )
        }
    }

    private fun summaryTip(): Pair<String, String> {
        val rough = accelCount.value + brakeCount.value
        val top = maxOf(jerkCount.value, curveCount.value, rough)
        return when {
            top == 0 -> "이번 주행은 편안했어요" to "동승자가 편안하게 탈 수 있는 운전이었어요."
            jerkCount.value == top -> "회생제동 '울컥'이 ${jerkCount.value}번 있었어요" to
                    "다음에는 회생제동 단계를 한 단계 낮추거나, 페달에서 발을 조금 더 천천히 떼 보세요."
            curveCount.value == top -> "급커브가 ${curveCount.value}번 있었어요" to
                    "커브에 들어가기 전에 미리 속도를 줄이면 동승자의 몸이 덜 흔들려요."
            else -> "급가속·급제동이 ${rough}번 있었어요" to
                    "앞차와 간격을 넓히고 페달을 부드럽게 다루면 승차감이 좋아져요."
        }
    }

    @Composable
    private fun MiniStat(label: String, value: String, modifier: Modifier = Modifier) {
        val p = LocalPal.current
        Column(modifier) {
            Text(label, fontSize = TS.labelS, color = p.text3)
            Text(value, fontSize = TS.bodyL, lineHeight = TS.bodyLLH, fontWeight = FontWeight.ExtraBold)
        }
    }

    @Composable
    private fun CountTile(label: String, count: Int, color: Color, modifier: Modifier) {
        val p = LocalPal.current
        Panel(modifier) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(12.dp).clip(CircleShape).background(color))
                Spacer(Modifier.width(8.dp))
                Text(label, fontSize = TS.labelS, color = p.text2, maxLines = 1)
            }
            Row(verticalAlignment = Alignment.Bottom) {
                Text("$count", fontSize = TS.headlineS, lineHeight = TS.headlineLH, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.width(4.dp))
                Text("회", fontSize = TS.labelM, color = p.text3, modifier = Modifier.padding(bottom = 10.dp))
            }
        }
    }

    // 멀미 민감도 그림: 물결 개수(1~3)로 정도를 표시
    @Composable
    private fun WaveGlyph(level: Int, color: Color) {
        Canvas(Modifier.size(56.dp)) {
            val w = size.width
            val gap = w * 0.2f
            val top = size.height / 2 - gap * (level - 1) / 2
            for (k in 0 until level) {
                val y = top + gap * k
                val path = Path().apply {
                    moveTo(w * 0.12f, y)
                    cubicTo(w * 0.3f, y - w * 0.12f, w * 0.38f, y - w * 0.12f, w * 0.5f, y)
                    cubicTo(w * 0.62f, y + w * 0.12f, w * 0.7f, y + w * 0.12f, w * 0.88f, y)
                }
                drawPath(path, color, style = Stroke(w * 0.07f, cap = StrokeCap.Round))
            }
        }
    }

    @Composable
    private fun SpeedChart(modifier: Modifier) {
        val p = LocalPal.current
        val speeds = chartSpeeds.toList()
        val marks = chartMarks.toList()
        val guides = chartGuides.toList()
        val measurer = rememberTextMeasurer()
        val axisStyle = TextStyle(fontFamily = AstaSans, fontSize = TS.labelS, color = p.text3)
        val guideStyle = TextStyle(fontFamily = AstaSans, fontSize = 18.sp, color = p.brand, fontWeight = FontWeight.SemiBold)
        Canvas(modifier.padding(top = 8.dp)) {
            val axisH = 36.dp.toPx()                        // 아래쪽 시간 눈금 자리
            val plotH = size.height - axisH
            val maxKmh = max(40f, (speeds.maxOrNull() ?: 0f) * 1.15f)
            for (i in 0..3) {
                val y = plotH * i / 3f
                drawLine(p.line, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
            }
            if (speeds.size < 2) return@Canvas
            val dx = size.width / (speeds.size - 1)          // 이번 주행 전체를 화면 너비에 맞춤
            fun pt(i: Int) = Offset(dx * i, plotH * (1f - min(speeds[i], maxKmh) / maxKmh))

            // 시간 눈금 (한 점 = 0.2초)
            val totalSec = (speeds.size - 1) * 0.2f
            val stepSec = when { totalSec <= 40f -> 10; totalSec <= 150f -> 30; else -> 60 }
            var sec = 0
            while (sec <= totalSec) {
                val x = size.width * sec / totalSec
                drawLine(p.line, Offset(x, plotH), Offset(x, plotH + 6.dp.toPx()), strokeWidth = 1.dp.toPx())
                val label = "%d:%02d".format(sec / 60, sec % 60)
                val layout = measurer.measure(label, axisStyle)
                val lx = (x - layout.size.width / 2f).coerceIn(0f, size.width - layout.size.width)
                drawText(layout, topLeft = Offset(lx, plotH + 10.dp.toPx()))
                sec += stepSec
            }

            // 음성 안내가 나간 시점: 세로 점선 + '안내'
            val dash = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 6.dp.toPx()))
            val guideLabel = measurer.measure("안내", guideStyle)
            for (g in guides) {
                if (g !in speeds.indices) continue
                val x = dx * g
                drawLine(p.brand.copy(alpha = 0.7f), Offset(x, guideLabel.size.height + 6.dp.toPx()), Offset(x, plotH),
                    strokeWidth = 2.dp.toPx(), pathEffect = dash)
                val lx = (x - guideLabel.size.width / 2f).coerceIn(0f, size.width - guideLabel.size.width)
                drawText(guideLabel, topLeft = Offset(lx, 0f))
            }
            val line = Path().apply {
                moveTo(pt(0).x, pt(0).y)
                for (i in 1 until speeds.size) lineTo(pt(i).x, pt(i).y)
            }
            val area = Path().apply {
                addPath(line)
                lineTo(pt(speeds.size - 1).x, plotH)
                lineTo(0f, plotH)
                close()
            }
            drawPath(area, Brush.verticalGradient(listOf(p.brand.copy(alpha = 0.22f), Color.Transparent)))
            drawPath(line, p.brand, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
            for (i in marks.indices) {
                val c = when (marks[i]) { 1 -> p.high; 2 -> p.warn; 3 -> p.brake; 4 -> p.curve; else -> null } ?: continue
                drawCircle(p.surface, radius = 11.dp.toPx(), center = pt(i))
                drawCircle(c, radius = 8.dp.toPx(), center = pt(i))
            }
        }
    }

    // ---------------- 공통 ----------------
    private fun riskColor(r: Float, p: Pal) = when {
        r >= 70f -> p.high; r >= 40f -> p.warn; else -> p.calm
    }

    private fun stateWord(r: Float) = when {
        r >= 70f -> "많이 흔들려요"; r >= 40f -> "조금 흔들려요"; else -> "편안해요"
    }

    @Composable
    private fun Panel(
        modifier: Modifier,
        border: Color? = null,
        content: @Composable ColumnScope.() -> Unit
    ) {
        val p = LocalPal.current
        Column(
            modifier = modifier
                .clip(RoundedCornerShape(24.dp))
                .background(p.surface)
                .border(if (border != null) 2.dp else 1.dp, border ?: p.line, RoundedCornerShape(24.dp))
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) { content() }
    }

    @Composable
    private fun Pill(text: String, color: Color) {
        Box(
            Modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.12f))
                .padding(horizontal = 18.dp, vertical = 10.dp)
        ) { Text(text, fontSize = TS.labelS, color = color, fontWeight = FontWeight.SemiBold) }
    }

}