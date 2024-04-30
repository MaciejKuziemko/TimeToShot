package com.example.aplikacja.timetoshot.kotlin

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
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

    private fun startRecording() {
        if (isRecording) {
            Log.d(mainTAG, "Already recording.")
            return
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
            Log.d(mainTAG, "Recording started.")
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

    private fun recordAudio(bufferSize: Int) {
        val buffer = ShortArray(bufferSize / 2)

        while (isRecording) {
            val read = audioRecord!!.read(buffer, 0, buffer.size, AudioRecord.READ_BLOCKING)
            if (read < 0) {
                Log.e(mainTAG, "Error reading audio.")
                return
            }
            val rms = calculateRMS(buffer)
            Log.d(mainTAG, "Loudness: $rms")
        }
    }

    private fun calculateRMS(buffer: ShortArray): Double {
        var sum = 0.0
        for (sample in buffer) {
            sum += sample * sample
        }
        val rms = Math.sqrt(sum / buffer.size)
        return rms
    }

    private fun updateButtonLabel() {
        val label = if (isRecording) "Stop Recording" else "Start Recording"
        with(binding) {
            recordingButton.text = label
        }
    }
    /*



    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_main, container, false)

        val startButton = view.findViewById<Button>(R.id.start_button)
        val stopButton = view.findViewById<Button>(R.id.stop_button)

        startButton.setOnClickListener {
            startRecording()
        }

        stopButton.setOnClickListener {
            stopRecording()
        }

        return view
    }
}

     */

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
