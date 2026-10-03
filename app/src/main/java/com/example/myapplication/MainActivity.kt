package com.example.myapplication


import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import com.antonkarpenko.ffmpegkit.FFmpegKit
import com.antonkarpenko.ffmpegkit.ReturnCode
import com.example.myapplication.ml.Nmp
import com.example.myapplication.ui.theme.MyApplicationTheme
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.leff.midi.MidiFile
import com.leff.midi.MidiTrack
import com.leff.midi.event.meta.Tempo
import com.leff.midi.event.meta.TimeSignature
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.tensorflow.lite.DataType
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import java.io.RandomAccessFile
import androidx.lifecycle.viewModelScope
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.offset
import java.util.stream.IntStream
import androidx.compose.ui.zIndex
import com.leff.midi.util.MidiProcessor
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.Slider

import androidx.compose.ui.zIndex
import androidx.compose.material3.SegmentedButtonDefaults.Icon
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearEasing
import androidx.compose.material3.Icon
import com.example.myapplication.R
import androidx.compose.material3.FilledIconButton
import androidx.compose.ui.res.painterResource
import androidx.compose.runtime.key
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.util.concurrent.TimeUnit
import org.json.JSONArray
import org.json.JSONObject
import okhttp3.RequestBody
import androidx.lifecycle.lifecycleScope
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import kotlin.Boolean
import kotlinx.coroutines.delay
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.collectLatest
fun main(cpp: cpp){
    cpp.sequencer(false); Int
}
val size =43844 * 4   // 43844 samples × 4 bytes/float = 175376 bytes (your "30 bytes")
val step = 36164 * 4
val padBytes = 3840
val speed=10
@JsonIgnoreProperties(ignoreUnknown = true)
data class TikWmResponse(

    val data: TikWmData?
)
@JsonIgnoreProperties(ignoreUnknown = true)
data class TikWmData(
    val play: String?,
    val music: String?
)
data class Note(
    var play: Boolean?,
    val music: Boolean?
)
data class pianoNote( //x and y trandlation
    var isWhite: Boolean?,
    var offset: Int?,
    var time: Int?,
    var play: Boolean?,
)
data class fallingNote(
    var note: Int?,
    var time: Long?,
    var level: Int?,


    )
val pianoNotes =mutableStateListOf<Note>().apply {
    addAll(List(88) { Note(
        play = false,
        music = false
    ) })}
fun dumpMidi(file: File) {
    val midi = file.inputStream().use { MidiFile(it) }
    Log.d("midiifo","PPQN=${midi.resolution}  format=${midi.type}  tracks=${midi.tracks.size}")
    for (track in midi.tracks) {
        for (e in track.events) {
            if (e is Tempo) Log.d("midiifo", "tick=${e.tick}  tempo=${e.bpm} bpm")
        }
    }
}
class advancedCon : ViewModel(){
    private val spaceUrl:String="https://qx32account-midiapp.hf.space"
    private val hfToken: String?=
    private val client= OkHttpClient.Builder()
        .readTimeout(0,TimeUnit.SECONDS)
        .build()
    private fun Request.Builder.auth()=apply{hfToken?.let{header("Authorization", "Bearer $it")}}

    suspend fun uploadAudio(file: File): String = withContext(Dispatchers.IO){

        val body= MultipartBody.Builder().setType(MultipartBody.FORM).addFormDataPart("files",file.name, file.asRequestBody("audio/wav".toMediaType())).build()
        val req =Request.Builder()
            .url("$spaceUrl/upload")
            .post(body).auth().build()

        client.newCall(req).execute().use{ resp ->check(resp.isSuccessful){"uplo failed: ${resp.code}"}
            JSONArray(resp.body!!.string()).getString(0) }// server-side path

    }
    suspend fun startJob(serverPath: String ): String= withContext(Dispatchers.IO){
        val payload=JSONObject().put("data", JSONArray().put(JSONObject()
            .put("path",serverPath)
            .put("meta", JSONObject().put("_type", "gradio.FileData"))
        ))
        val req = Request.Builder()
            .url("$spaceUrl/call/transcribe_midi") // match your Space's api_name
            .post(RequestBody.create("application/json".toMediaType(), payload.toString()))
            .auth().build()
        client.newCall(req).execute().use { resp ->
            check(resp.isSuccessful) { "Calls failed: ${resp.code}" }
            JSONObject(resp.body!!.string()).getString("event_id")
        }

    }
    suspend fun awaitResult(eventId: String): String = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url("$spaceUrl/call/transcribe_midi/$eventId")
            .auth().build()
        client.newCall(req).execute().use { resp ->
            val source = resp.body!!.source()
            var event = ""
            while (!source.exhausted()) {
                val line = source.readUtf8Line() ?: break
                when {
                    line.startsWith("event:") -> event = line.removePrefix("event:").trim()
                    line.startsWith("data:") && event == "complete" -> {
                        val obj = JSONArray(line.removePrefix("data:").trim()).getJSONObject(0)
                        return@withContext "$spaceUrl/file=${obj.getString("path")}"
                    }
                    line.startsWith("data:") && event == "error" ->
                        error("Space error: $line") // e.g. quota exceeded
                }
            }
            error("Stream ended without result")

        }
    }
    suspend fun downloadFile(url: String, dest: File): File = withContext(Dispatchers.IO){

        val req=Request.Builder().url(url).auth().build()

        client.newCall(req).execute().use {resp -> check(resp.isSuccessful
        ){"download fail:${resp.code}"}
            dest.outputStream().use{ resp.body!!.byteStream().copyTo(it)}
        }
        dest
    }
}
class Transcriber(
    private val client: advancedCon,
    private val cacheDir: File
) {
    /** One call: audio in, MIDI file out. Throws on failure. */
    suspend fun transcribe(audio: File,cacheDir: File): File {

        val serverPath = client.uploadAudio(audio)
        val eventId = client.startJob(serverPath)
        val midiUrl = client.awaitResult(eventId)

        return client.downloadFile(midiUrl, File(cacheDir, "info.mid"))
    }
}
class cpp(): ViewModel(){
    external fun sequencer(isPlaying: Boolean): Int
    external fun play(freq: Int, level: Int): Int

    companion object {
        init {
            System.loadLibrary("sequencer") // Matches library name in CMakeLists.txt
        }
    }
}
class converter(application: Application) : AndroidViewModel(application) {
    var url by mutableStateOf("")
    var isAdvanced by mutableStateOf(false)

    // 2. Android safely encodes the symbols so the API can read it seamlessly
    var isMidiReady by mutableStateOf(false)
    val encodedUrl by derivedStateOf { Uri.encode(url) }
    val finalApiUrl by derivedStateOf { "https://www.tikwm.com/api/?url=$encodedUrl" }
    var input = "/data/data/com.example.myapplication/files/Folder/pikachu.mp3"

    var output = "/data/data/com.example.myapplication/files/Folder/pikachu.wav"
    var rawFilePath = "/data/data/com.example.myapplication/files/Folder/rawFile.raw"

    val file = File(input)
    val midiFile = File(getApplication<Application>().cacheDir, "info.mid")
    var rawFile = File(rawFilePath)
    val itemCount = mutableStateListOf<pianoNote>() //piano roll
    val currentlyPlayedId = mutableListOf<Int>()
    var noteFrames = floatArrayOf()
    var onsetFrames = floatArrayOf()
    var convertedLastVal = 0
    var everythingReady by mutableStateOf(false)
    var milli=0

    val fallingNoteCount = mutableStateListOf<fallingNote>()
    fun drawPiano() {
        var currentId = 0
        var offset =0
        itemCount.add(pianoNote(
            isWhite = true,
            offset = offset,
            time = 10,
            play = false
        ))
        currentId += 1
        offset += 5
        itemCount.add(pianoNote(
            isWhite = false,
            offset = offset,
            time = 10,
            play = false
        ))
        currentId += 1
        offset += 5
        itemCount.add(pianoNote(
            isWhite = true,
            offset = offset,
            time = 10,
            play = false
        ))
        for (o in 1..7) { //octave
            for (k in 1..2) { //key groups
                currentId += 1
                offset += 5
                itemCount.add(
                    pianoNote(
                        isWhite = true,
                        offset = offset,
                        time = 10,
                        play = false
                    )
                )
                currentId += 1
                offset += 5
                itemCount.add(
                    pianoNote(
                        isWhite = false,
                        offset = offset,
                        time = 10,
                        play = false
                    )
                )
            }
            currentId += 1
            offset += 5
            itemCount.add(
                pianoNote(
                    isWhite = true,
                    offset = offset,
                    time = 10,
                    play = false
                )
            )




            for (k in 0..2) {
                currentId += 1
                offset += 5
                itemCount.add(
                    pianoNote(
                        isWhite = true,
                        offset = offset,
                        time = 10,
                        play = false
                    )
                )
                currentId += 1
                offset += 5
                itemCount.add(
                    pianoNote(
                        isWhite = false,
                        offset = offset,
                        time = 10,
                        play = false
                    )
                )
            }
            currentId += 1
            offset += 5
            itemCount.add(
                pianoNote(
                    isWhite = true,
                    offset = offset,
                    time = 10,
                    play = false
                )
            )
        }
        currentId += 1
        offset += 5
        itemCount.add(pianoNote(
            isWhite = true,
            offset = 435,
            time = 10,
            play = false
        ))


    }




    suspend fun saveFileData(context: Context,myViewModel: converter) {
        Log.d("savings", "$url")
        Log.d("savings", "$finalApiUrl")
        val mapper = jacksonObjectMapper()
        val okHttpClient = OkHttpClient() //core class

        val request = Request.Builder()
            .url(finalApiUrl)
            .build()
        Log.d("saving", request.toString())

        return withContext(Dispatchers.IO) { //executes the code block on a different thread, and returns right back to the original thread when finished

            val response: Response = okHttpClient.newCall(request)
                .execute() //engine client prepares a methog that prepares my request then executes it


            val jsonStream = response.body?.string() ?: "" //gets link
            val readJson: TikWmResponse = mapper.readValue(jsonStream)
            val trueLink = readJson.data?.music

            if (!trueLink.isNullOrEmpty()) {
                Log.d("https", "link recieved")
                val requestVideo = Request.Builder()
                    .url(trueLink)
                    .build()

                val path = File(context.filesDir.toString() + "/Folder")

                if (!path.exists())
                    path.mkdirs()

                val imageFile = File(path, "pikachu.mp3")
                if (imageFile.exists()) {

                }
                val responseVideo: Response = okHttpClient.newCall(requestVideo).execute()

                val inputStream = responseVideo.body?.byteStream()
                val imageFilePath = imageFile.toURI().toString()
                val fos = FileOutputStream(imageFile)
                Log.d("https", "downloaded")
                fos.write(inputStream?.readBytes())
                fos.flush()
                fos.close() //dnc
                FFmpegKit.executeAsync("-y -i  $input $output") { session ->

                    if (ReturnCode.isSuccess(session.returnCode)) {
                        viewModelScope.launch {
                            if (!isAdvanced) {

                                simpleConvert(context, myViewModel)
                            } else {
                                Log.d("debugh", "done")
                                advancedConvert(context.cacheDir, myViewModel)
                            }
                        }
                    }
                }}
        }

    }}
suspend fun advancedConvert(cacheDir: File,myViewModel: converter) = withContext(Dispatchers.IO) {
    Log.d("debugh", "done")


    try {
        val client= advancedCon()

        val transcriber by lazy { Transcriber(client,cacheDir) }
        // Now you can call suspend functions directly!
        val midi = transcriber.transcribe(File(myViewModel.output), cacheDir)
        Log.d("debugh", "ready1")

        withContext(Dispatchers.Main) {

            myViewModel.isMidiReady = true
            myViewModel.everythingReady = true
            Log.d("debugh", "ready")
            dumpMidi(myViewModel.midiFile)
        }
    } catch (e: Exception) {
        Log.e("MIDI_CONVERT", "Advanced conversion failed", e)
    }
}
fun simpleConvert(context: Context,myViewModel: converter) {


    if (myViewModel.file.exists()) {
        Log.d("MIDI", "Actual model array size is")

        FFmpegKit.execute("-y -i ${myViewModel.output} -ac 1 -ar 22050 -f f32le ${myViewModel.rawFilePath}")
        val bufferSize = 43844 * 4
        val model = Nmp.newInstance(context)

        try {
            RandomAccessFile(myViewModel.rawFilePath, "r").use { stream ->
                Log.d("MIDI", "Actual model array size is")
                var pointer = -padBytes * 4L

                var bufferOffset = 0
                bufferOffset += bufferSize
                var timeframe = 0
                val fileLength = myViewModel.rawFile.length()
                var globalFrameIndex = 0

                val tempoTrack = MidiTrack()
                val noteTrack = MidiTrack()

                while (pointer < fileLength) {
                    val bytes = ByteArray(175376)
                    val readStart = maxOf(pointer, 0L)
                    val skipInBuffer = (readStart - pointer).toInt()
                    timeframe += 1
                    if (readStart < fileLength) {
                        stream.seek(readStart)
                        val maxReadable =
                            minOf(
                                175376 - skipInBuffer,
                                (fileLength - readStart).toInt()
                            )
                        stream.read(bytes, skipInBuffer, maxReadable)
                    }
                    val ByteBuffer =
                        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

                    val inputFeature0 = TensorBuffer.createFixedSize(
                        intArrayOf(1, 43844, 1),
                        DataType.FLOAT32
                    )
                    inputFeature0.loadBuffer(ByteBuffer)
                    val outputs = model.process(inputFeature0)

                    val noteFrames = outputs.outputFeature0AsTensorBuffer
                    val rawDataNote = noteFrames.floatArray
                    myViewModel.noteFrames = rawDataNote
                    val onsetFrames = outputs.outputFeature1AsTensorBuffer
                    val rawDataonset = onsetFrames.floatArray
                    myViewModel.onsetFrames = rawDataonset
                    val contourFrames = outputs.outputFeature2AsTensorBuffer
                    val outputArray = noteFrames.floatArray
                    val onsetArray = onsetFrames.floatArray
                    Log.d("SECOND", "$timeframe")

                    Log.d(
                        "MIDI_DEBUG",
                        "Actual model array size is: ${outputArray.size}"
                    )


                    val ts = TimeSignature()
                    ts.setTimeSignature(
                        4,
                        4,
                        TimeSignature.DEFAULT_METER,
                        TimeSignature.DEFAULT_DIVISION
                    )
                    val tempo = Tempo()
                    tempo.setBpm(120f)
                    tempoTrack.insertEvent(ts);
                    tempoTrack.insertEvent(tempo);
                    var seconds = 0
                    seconds += 1
                    val channel = 0

                    for (timeIndex in 15 until 157) {
                        for (pianoKeyIndex in 0 until 88) {
                            val noteLocation = (timeIndex * 88) + pianoKeyIndex

                            val pitch = pianoKeyIndex + 22
                            val velocity = 100
                            val tick = (globalFrameIndex.toLong() * 11L)
                            val duration: Long = 120
                            // FIXED: Use noteLocation instead of just pianoKeyIndex!
                            val confidence = outputArray[noteLocation]
                            val onset = onsetArray[noteLocation]
                            val Level = onset * 127

                            // 1. Ensure we aren't on the very first time frame to avoid out-of-bounds errors
                            val isPeak = if (timeIndex > 15) {
                                val lastFrameLocation = noteLocation - 88
                                confidence - outputArray[lastFrameLocation].toDouble() > 0.16


                            } else {
                                true // On the first frame, treat it as a peak if it clears the threshold
                            }
                            val isNotPeak = if (timeIndex > 15) {
                                val lastFrameLocation = noteLocation - 88
                                confidence - outputArray[lastFrameLocation].toDouble() < -0.16


                            } else {
                                true // On the first frame, treat it as a peak if it clears the threshold
                            }
                            if (confidence > 0.5f && isPeak) {

                                if (pianoNotes[pianoKeyIndex].play == false) {
                                    pianoNotes[pianoKeyIndex].play = true
                                    noteTrack.insertEvent(
                                        com.leff.midi.event.NoteOn(
                                            tick,
                                            channel,
                                            pitch,
                                            100
                                        )
                                    )
                                    Log.d(
                                        "MIDI",
                                        " $pianoKeyIndex,$timeIndex,$onset,$confidence,$timeframe"
                                    )
                                }

                            } else if (onset > 0.9f) {
                                // Scenario B: TRUE NOTE SEGMENTATION
                                // The note was already playing, but the onset head detected a sharp new hammer strike!
                                // Cut the old note instantly and strike it again.
                                noteTrack.insertEvent(
                                    com.leff.midi.event.NoteOff(
                                        tick,
                                        channel,
                                        pitch,
                                        0
                                    )
                                )
                                noteTrack.insertEvent(
                                    com.leff.midi.event.NoteOn(
                                        tick + 1,
                                        channel,
                                        pitch,
                                        100
                                    )
                                )
                                Log.d(
                                    "MIDI_SEGMENT",
                                    "Segmented Re-strike Key $pianoKeyIndex at Tick $tick"
                                )
                            } else {
                                if (pianoNotes[pianoKeyIndex].play == true && isNotPeak) {
                                    pianoNotes[pianoKeyIndex].play = false
                                    noteTrack.insertEvent(
                                        com.leff.midi.event.NoteOff(
                                            tick,
                                            channel,
                                            pitch,
                                            0
                                        )
                                    )
                                }
                            }

                        }
                        globalFrameIndex++
                    }


                    pointer += step
                }
                val tracks: MutableList<MidiTrack?> = ArrayList<MidiTrack?>()
                tracks.add(tempoTrack)
                tracks.add(noteTrack)

                val midi = MidiFile(MidiFile.DEFAULT_RESOLUTION, tracks)
                try {
                    midi.writeToFile(myViewModel.midiFile)
                    // SWITCH TO MAIN THREAD TO UPDATE UI SAFELY
                    myViewModel.viewModelScope.launch(Dispatchers.Main) {
                        myViewModel.isMidiReady = true
                    }
                } catch (e: IOException) {
                    System.err.println(e)

                }
            }
            model.close()

            myViewModel.everythingReady = true



        } catch (e: IOException) {
            Log.e("MIDI_CONVERT", "Processing error", e)
        }

    }


}
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val myViewModel: converter by viewModels()
        val cppviewmodel: cpp by viewModels()

        setContent {

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding()
                    .padding(16.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                main(cpp = cppviewmodel)
                myViewModel.drawPiano()
                Greeting(viewModel = myViewModel)
                convertButton(viewModel = myViewModel,cpp = cppviewmodel)
                createMidi(viewModel = myViewModel)
                TransformScale(viewModel = myViewModel,cpp = cppviewmodel)
                try {


                    // play it, save it, show it
                } catch (e: Exception) {



                }

            }
        }
    }


    @Composable
    fun Greeting(viewModel: converter) {
        if(!viewModel.everythingReady) {

            viewModel.fallingNoteCount.clear()
            val state = rememberTextFieldState()
            OutlinedTextField(

                value = viewModel.url,
                onValueChange = { newValue ->
                    viewModel.url = newValue // Update the ViewModel when the user types
                },
                singleLine = true,
                label = { Text("Paste a TikTok link") })

        } else {viewModel.url=""}
    }

    @Composable
    fun convertButton(viewModel: converter,cpp: cpp) {
        if (!viewModel.everythingReady) {


            val scope = rememberCoroutineScope()
            val context = LocalContext.current
            Button(onClick = {
                scope.launch {
                    cpp.play(900,10)
                    viewModel.isAdvanced=true
                    viewModel.saveFileData(context,viewModel)



                }
            }
            ) {
                Text(text = "Convert")
            }
            Text(text = "here be debug")
        }
    }

    @Composable
    fun advancedButton(viewModel: converter) {
        if (!viewModel.everythingReady) {
            val scope = rememberCoroutineScope()
            val context = LocalContext.current
            Button(onClick = {
                scope.launch {
                    viewModel.isAdvanced=true

                    viewModel.saveFileData(context,viewModel)

                }
            }
            ) {
                Text(text = "Convert")
            }
        }
    }

    fun createMidi(viewModel: converter) {


    }
    // Delta time is 0, so it starts immediately
    @Composable
    fun TransformScale(viewModel: converter,cpp: cpp

    ) {


        var savedScale by remember { mutableStateOf(true) }
        var scaleX by remember { mutableFloatStateOf(1.0f) }

        var scaleY by remember { mutableFloatStateOf(1.0f) }
        val currentDensity = LocalDensity.current

        var offset by remember { mutableStateOf(Offset.Zero) }
        var finger1Position by remember { mutableStateOf<Offset?>(null) }
        var finger2Position by remember { mutableStateOf<Offset?>(null) }
        var fingerVector by remember { mutableStateOf(Offset.Zero) }

        val state = rememberTransformableState { zoomChange, offsetChange, rotationChange ->
            if (savedScale) {
                scaleX = (scaleX * zoomChange).coerceIn(0.1f, 10.0f)
            } else {
                scaleY = (scaleY * zoomChange).coerceIn(0.5f, 5.0f)
            }
            offset += offsetChange
        }

        var currentSize by remember { mutableStateOf(0) }
        val horizontalBrush = Brush.linearGradient(
            0.0f to Color.Black,
            0.5f to Color.Black, // Sharp cut-off right at the middle
            0.5f to Color.Gray,
            1.0f to Color.Gray,
            start = Offset(0f, 0f),
            end = Offset(Float.POSITIVE_INFINITY, 0f) // Forces the flow across the X-axis (left to right)
        )
        val playedBrush = Brush.linearGradient(
            0.0f to Color(0xFF1565C0),
            0.5f to Color(0xFF1565C0), // dark blue, Sharp cut-off right at the middle
            0.5f to Color.Gray,
            1.0f to Color.Gray,
            start = Offset(0f, 0f),
            end = Offset(Float.POSITIVE_INFINITY, 0f) // Forces the flow across the X-axis (left to right)
        )

        var touchX by remember { mutableStateOf(currentSize.toFloat()) }
        var touchY by remember { mutableStateOf(currentSize.toFloat()) }
        val density = LocalDensity.current
        var isPlaying by remember { mutableStateOf(false) }
        val animate = remember { Animatable(0f) }
        var sliderVal by remember { mutableFloatStateOf(animate.value) }
        var initialLastVal = viewModel.convertedLastVal
        LaunchedEffect(!isPlaying) {
            initialLastVal =
                ((viewModel.convertedLastVal + 1000) * (1 - sliderVal)).toInt() //relative time
        }
        if (viewModel.everythingReady) {
            LaunchedEffect(animate.value) {
                sliderVal = animate.value
            }
            LaunchedEffect(isPlaying) {
                if (isPlaying) {
                    animate.snapTo(sliderVal)
                    animate.animateTo(
                        targetValue = 1f,
                        animationSpec = tween(durationMillis = ((initialLastVal+1000)/960)*1000, easing = LinearEasing) //divides 960 into 1 and times 1000 also the other 1000 is the silent headroom
                    )
                }
            }

            Slider(
                value = sliderVal,
                onValueChange = { sliderVal = it },
                modifier = Modifier.size(400.dp, 10.dp)
                    .zIndex(1f)
            )
            FilledIconButton(
                onClick = { isPlaying = !isPlaying },
                modifier = Modifier.size(64.dp).zIndex(1f)
            ) {
                if (!isPlaying) {
                    isPlaying = isPlaying

                    Icon(painter = painterResource(id = R.drawable.baseline_play_arrow_24), "hi")
                } else {

                    Icon(painter = painterResource(id = R.drawable.baseline_pause_24), "hi")
                }
            }
            FilledIconButton(
                onClick = { viewModel.everythingReady = false },
                modifier = Modifier.size(64.dp).zIndex(1f)
            ) {
                Icon(painter = painterResource(id = R.drawable.baseline_cancel_24), "hi")

            }

            var convertedVal = (viewModel.convertedLastVal+1000) * sliderVal//decides how long to reach the final note based on slider 0 to 1 speed
            Log.d("converted",viewModel.convertedLastVal.toString())

            Box(
                modifier = Modifier
                    .pointerInput(Unit)
                    {
                        awaitEachGesture {
                            var prevPositions: List<Offset>? = null
                            while (true) {
                                val event = awaitPointerEvent()

                                val activePointers = event.changes.filter { it.pressed }

                                if (activePointers.size > 1) {

                                    val x1 = activePointers[0].position.x
                                    val x2 = activePointers[1].position.x

                                    val y1 = activePointers[0].position.y
                                    val y2 = activePointers[1].position.y
                                    fingerVector = Offset(
                                        x = kotlin.math.abs(x2 - x1),
                                        y = kotlin.math.abs(y2 - y1)
                                    )
                                    val prev = prevPositions
                                } else if (activePointers.size == 1) {
                                    // 2. SAFE SINGLE-TOUCH MODE (Only fires if exactly one finger is down)
                                    val x1 = activePointers[0].position.x
                                    val y1 = activePointers[0].position.y

                                    // Put your playhead dragging/scrolling math here
                                } else {
                                    // 3. SAFE IDLE MODE (Fires when fingers are lifted completely)
                                    // Do nothing or reset your touch flags here so it doesn't crash!
                                }
                                if (fingerVector.x > fingerVector.y) {
                                    savedScale = true

                                } else {
                                    savedScale = false


                                }
                            }
                        }
                    }

                    .graphicsLayer {

                        this.scaleX = scaleX
                        this.scaleY = scaleY

                        // 2. Set your translation shifts cleanly inside the block boundary
                        this.translationX = offset.x
                        this.translationY = offset.y
                    }
                    .transformable(state = state)
                    .requiredSize(440.dp, 600.dp)
                    .background(Color.Black) // Dark background for a DAW look

            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)

                ) {
                    for (o in viewModel.itemCount.indices) { //work
                        key(o) {

                            Box(
                                Modifier
                                    .size(5.dp, 50.dp)
                                    .offset(x = viewModel.itemCount[o].offset!!.dp)
                                    .background(
                                        if (viewModel.itemCount[o].isWhite!! == true) {
                                            if (viewModel.itemCount[o].play!! == false) {
                                                SolidColor(Color.White)
                                            } else {
                                                SolidColor(Color(0xFF4FC3F7))

                                            }
                                        } else {
                                            if (viewModel.itemCount[o].play!! == false) {
                                                horizontalBrush
                                            } else {
                                                playedBrush
                                            }

                                        }
                                    )
                                    .zIndex(2f)

                            )
                        }
                    }

                    LaunchedEffect(viewModel.midiFile) { //turns on once
                        withContext(Dispatchers.IO) {
                            Log.e("check2", sliderVal.toString())
                            val midi = MidiFile(viewModel.midiFile)
                            val unsortedNotes = mutableListOf<fallingNote>()
                            midi.tracks.forEach { track ->

                                track.events.forEach { event ->
                                    when (event) {
                                        is com.leff.midi.event.NoteOn -> {
                                            withContext(Dispatchers.Main) {
                                                val level = event.velocity
                                                val pitch = event.getNoteValue()
                                                val tick = event.getTick()

                                                Log.d("etet", "$tick")
                                                unsortedNotes.add(
                                                    fallingNote(
                                                        note=pitch,
                                                        time=tick,
                                                        level=level
                                                    )
                                                )
                                            }
                                        }

                                    }

                                }
                            }
                            unsortedNotes.sortBy { it.time }
                            viewModel.fallingNoteCount.addAll(unsortedNotes)

                            val f = viewModel.fallingNoteCount
                            Log.d(
                                "soo",
                                " $f"
                            )
                            viewModel.convertedLastVal =
                                viewModel.fallingNoteCount[viewModel.fallingNoteCount.size - 1].time!!.toInt() //calculates total time from last notes time
                            Log.d(
                                "soo",
                                " ${viewModel.convertedLastVal}"
                            )
                        }

                    }
                    //wotks
                    val seen = mutableSetOf<Int>(
                        2,
                        5,
                        9,
                        12,
                        16,
                        19,
                        23,
                        26,
                        30,
                        33,
                        37,
                        40,
                        44,
                        47,
                        51,
                        54,
                        58,
                        61,
                        65,
                        68,
                        72
                    )
                    LaunchedEffect(isPlaying) {
                        snapshotFlow { sliderVal }
                            .collectLatest { currentSliderVal ->
                                Log.e("check2", currentSliderVal.toString())
                                val currentPlayhead = (viewModel.convertedLastVal + 1000) * currentSliderVal / speed.toFloat() //playhead location
                                val keysThatShouldBePlaying = BooleanArray(88)
                                for (n in viewModel.fallingNoteCount.indices) { //redraws every note then checks
                                    val note = viewModel.fallingNoteCount[n] //

                                    val pianoKeyIndex = note.note!! - 21
                                    if (note.note == null) continue
                                    if (pianoKeyIndex < 0 || pianoKeyIndex >= viewModel.itemCount.size) {
                                        continue // Skip this note, it's too high or too low for the keyboard
                                    }
                                    val trueOffsetY =
                                        viewModel.fallingNoteCount[n].time!! / speed // note offset nothing with speed


                                    val playChecker = trueOffsetY - currentPlayhead


                                    if (playChecker < -10f) {

                                        continue
                                    }

                                    if (playChecker > 10f) {

                                        break
                                    }
                                    if (playChecker in -5f..5f) {

                                        keysThatShouldBePlaying[pianoKeyIndex] = true //data note
                                    }
                                        else { keysThatShouldBePlaying[pianoKeyIndex] = false }


                                }
                                for(i in keysThatShouldBePlaying.indices){ //88
                                    if ( keysThatShouldBePlaying[i] == true) { //check if played
                                            //plays/changes color only if false
                                            if  (viewModel.itemCount[i].play == false)
                                            {
                                                viewModel.itemCount[i] = viewModel.itemCount[i].copy(play = true) //color
                                            cpp.play(
                                                i!!,
                                                1!! //not supported currently
                                            );

                                            }

                                        } else {
                                            viewModel.itemCount[i] = viewModel.itemCount[i].copy(play = false) //turn the color off
                                        }




                                }

                            }
                    }
                    Box( //box filled with notes speed
                        Modifier.offset(y = (convertedVal!!/speed).toInt().dp)


                    ) {

                        for (i in 1..viewModel.fallingNoteCount.size) { //falling notes
                            key(i) {
                                val trueOffset =
                                    viewModel.fallingNoteCount[i - 1].note!! * 5 - 21 * 5
                                val trueOffsetY =
                                    viewModel.fallingNoteCount[i - 1].time!! / speed // note offset nothing with speed

                                val endVal = trueOffsetY + 9
                                val endChecker = endVal - convertedVal / speed //speed


                                Log.d("slideryy", "${trueOffsetY - convertedVal / 10}")

                                Log.d("converted1", sliderVal.toString())
                                Box(
                                    Modifier
                                        .size(5.dp, 5.dp)
                                        .offset(x = trueOffset.dp, y = -trueOffsetY.toInt().dp)
                                        .background(
                                            if (viewModel.itemCount[viewModel.fallingNoteCount[i - 1].note!! - 21].isWhite == true) {
                                                Color(0xFF4FC3F7)
                                            } else {
                                                Color(0xFF1565C0)
                                            }
                                        )
                                        .zIndex(0f)
                                )

                            }
                        }
                        }

                    }


                }
            }
        }
    }
