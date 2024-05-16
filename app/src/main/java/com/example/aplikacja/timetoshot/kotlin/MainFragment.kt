package com.example.aplikacja.timetoshot.kotlin

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.navigation.fragment.findNavController
import com.example.aplikacja.timetoshot.R
import com.example.aplikacja.timetoshot.databinding.FragmentMainBinding
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

private val PERMISSIONS_REQUIRED = arrayOf(Manifest.permission.RECORD_AUDIO)

class MainFragment : BaseFragment() {

    companion object {
        private const val emailPasswordTAG = "EmailPassword"
        const val mainTAG = "MainFragment"
        const val SAMPLE_RATE = 8000
    }

    private var audioRecord: AudioRecord? = null
    private var isRecording = false

    private lateinit var auth: FirebaseAuth

    private var _binding: FragmentMainBinding? = null
    private val binding: FragmentMainBinding
        get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentMainBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setProgressBar(binding.progressBar)

        with(binding) {
            recordingButton.setOnClickListener {
                if (hasPermissions(requireContext())) {
                    if (ActivityCompat.checkSelfPermission(
                            requireContext(),
                            Manifest.permission.RECORD_AUDIO
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        return@setOnClickListener
                    }
                    if (isRecording) {
                        stopRecording()
                    } else {
                        startRecording()
                    }
                    updateButtonLabel()
                } else {
                    permReqLauncher.launch(PERMISSIONS_REQUIRED)
                }
                signOutButton.setOnClickListener { signOut() }
                // Initialize Firebase Auth
            }
            auth = Firebase.auth
            }
        }

    private fun hasPermissions(context: Context) = PERMISSIONS_REQUIRED.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("SetTextI18n")
    private fun startRecording() {
        if (isRecording) {
            Log.d(mainTAG, "Already recording.")
            return
        }

        var stopC=4
        CoroutineScope(Dispatchers.Main).launch {
            while (stopC>=0) {
                binding.result.text = "Strzelanie rozpocznie się za $stopC"
                delay(1000)
                stopC--
            }
            binding.result.text = "Recording started"
        }
        val bufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        Log.d(mainTAG, "$bufferSize.")
        if (ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize
        )

        if (audioRecord!!.state == AudioRecord.STATE_INITIALIZED) {
            isRecording = true
            audioRecord!!.startRecording()
            Thread {
                recordAudio(bufferSize)
            }.start()
        } else {
            Log.e(mainTAG, "Failed to initialize AudioRecord.")
        }
    }

    private fun stopRecording() {
        isRecording = false
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
        Log.d(mainTAG, "Recording stopped.")
    }

    var firstShotTime=0L
    var shotCounter=0
    private fun recordAudio(bufferSize: Int) {
        val buffer = ShortArray(bufferSize / 2)
        val startTime=System.currentTimeMillis()
        while (isRecording) {
            val read = audioRecord!!.read(buffer, 0, buffer.size, AudioRecord.READ_BLOCKING)
            if (read < 0) {
                Log.e(mainTAG, "Error reading audio.")
                return
            }
            val pair = calculateRMS(buffer)
            firstShotTime=pair.first
            shotCounter=pair.second
        }
        if(firstShotTime!=0L){
            firstShotTime-=startTime
        }
        val stopTime=System.currentTimeMillis()
        val recordTime=stopTime-startTime
        Handler(Looper.getMainLooper()).post {
            binding.result.text =
                "Record time: $recordTime ms\n First shot time: $firstShotTime ms\n Shot counter: $shotCounter"
            shotCounter = 0
            firstShotTime = 0L
        }
    }

    private fun calculateRMS(buffer: ShortArray): Pair<Long,Int> {
        var sum = 0.0
        for (sample in buffer) {
            sum += sample * sample
        }
        val rms = Math.sqrt(sum / buffer.size)
        Log.d(mainTAG,"loudness:$rms")
        if(rms>3000){
            shotCounter++
            if(firstShotTime==0L){
            firstShotTime=System.currentTimeMillis()
            }
        }
        return Pair(firstShotTime,shotCounter)
    }

    private fun updateButtonLabel() {
        val label = if (isRecording) "Stop Recording" else "Start Recording"
        with(binding) {
            recordingButton.text = label
        }
    }

    private val permReqLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        val granted = permissions.entries.all {
            it.value
        }
        if (granted) {
            displayMainFragment()
        }
    }

    private fun displayMainFragment() {
        // Możesz dodać tutaj dodatkową logikę po uzyskaniu wszystkich uprawnień
    }

    public override fun onStart() {
        super.onStart()
        val currentUser = auth.currentUser
    }

    private fun signOut() {
        auth.signOut()
        findNavController().navigate(R.id.action_emailpassword)
    }

    private fun updateUI(user: FirebaseUser?) {
        hideProgressBar()
        if (user != null) {
            binding.signedInButtons.visibility = View.VISIBLE
        } else {
            binding.signedInButtons.visibility = View.GONE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
