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

    private lateinit var auth: FirebaseAuth

    private var _binding: FragmentMainBinding? = null
    private val binding: FragmentMainBinding
        get() = _binding!!

    private val SAMPLING_RATE_IN_HZ = 44100
    private val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
    private val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    private val BUFFER_SIZE_FACTOR = 2
    private val BUFFER_SIZE = AudioRecord.getMinBufferSize(SAMPLING_RATE_IN_HZ, CHANNEL_CONFIG, AUDIO_FORMAT) * BUFFER_SIZE_FACTOR
    private val recordingInProgress = AtomicBoolean(false)
    private var recorder: AudioRecord? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        _binding = FragmentMainBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setProgressBar(binding.progressBar)

        var startTime: Long = 0
        var lastShotTime: Long = 0  // Czas ostatniego strzału
        var firstShotTime: Long = -1 // Czas pierwszego strzału
        var status = 0

        with(binding) {
            recordingButton.setOnClickListener {
                if (hasPermissions(requireContext())) {
                    if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                        return@setOnClickListener
                    }
                    if (status == 0) {
                        startTime = System.currentTimeMillis()
                        recorder = AudioRecord(MediaRecorder.AudioSource.DEFAULT, SAMPLING_RATE_IN_HZ, CHANNEL_CONFIG, AUDIO_FORMAT, BUFFER_SIZE)
                        recorder?.startRecording()
                        requireActivity().runOnUiThread {
                            result.text = "Trwa strzelanie!"
                            recordingButton.text = "Stop recording"
                        }
                        status = 1
                    } else if (status == 1) {
                        val audioData = ShortArray(BUFFER_SIZE)
                        val bytesRead = recorder?.read(audioData, 0, BUFFER_SIZE)

                        if (bytesRead != AudioRecord.ERROR_BAD_VALUE && bytesRead != AudioRecord.ERROR_INVALID_OPERATION) {
                            val detectionResult = detectClaps(audioData)

                            val stopTime = System.currentTimeMillis()
                            val recordTime = (stopTime - startTime) / 1000

                            requireActivity().runOnUiThread {
                                recordingButton.text = "Start recording"
                                result.text = "Czas nagrania ${recordTime}s, liczba strzałów: ${detectionResult.clapsCount}"

                                if (detectionResult.firstShotTime != -1L) {
                                    firstShotTime = detectionResult.firstShotTime
                                    result.text = "Czas nagrania ${recordTime}s, liczba strzałów: ${detectionResult.clapsCount}, pierwszy strzał po ${firstShotTime - startTime}ms"
                                }
                            }
                            recorder?.stop()
                            recorder?.release()  // Zwolnienie zasobów AudioRecord
                            status = 0
                        } else {
                            Log.e(TAG, "Error reading audio data")
                        }
                    }
                } else {
                    permReqLauncher.launch(PERMISSIONS_REQUIRED)
                }
            }
            signOutButton.setOnClickListener { signOut() }
            reloadButton.setOnClickListener { reload() }
        }
        // Initialize Firebase Auth
        auth = Firebase.auth
    }

    private fun hasPermissions(context: Context) = PERMISSIONS_REQUIRED.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
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
        if (currentUser != null) {
            reload()
        }
    }

    private fun signOut() {
        auth.signOut()
        findNavController().navigate(R.id.action_emailpassword)
    }

    private fun reload() {
        auth.currentUser!!.reload().addOnCompleteListener { task ->
            if (task.isSuccessful) {
                updateUI(auth.currentUser)
                Toast.makeText(context, "Reload successful!", Toast.LENGTH_SHORT).show()
            } else {
                Log.e(TAG, "reload", task.exception)
                Toast.makeText(context, "Failed to reload user.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateUI(user: FirebaseUser?) {
        hideProgressBar()
        if (user != null) {
            binding.signedInButtons.visibility = View.VISIBLE
        } else {
            binding.signedInButtons.visibility = View.GONE
        }
    }

    private fun detectClaps(data: ShortArray): DetectionResult {
        // Parametry analizy ramkowej
        val frameSize = 1024  // Rozmiar ramki
        val overlap = frameSize / 2  // Przesunięcie ramki
        val frames = data.size / overlap - 1  // Ilość ramek

        // Prog do wykrywania klaśnięcia
        val energyThreshold = 100000  // Próg energii dla klaśnięcia

        var clapsCount = 0
        var firstShotTime: Long = -1

        for (i in 0 until frames) {
            var energy = 0L
            for (j in 0 until frameSize) {
                val index = i * overlap + j
                if (index < data.size) {
                    val sample = data[index].toInt()
                    energy += sample * sample
                }
            }
            if (energy > energyThreshold && firstShotTime == -1L) {
                firstShotTime = System.currentTimeMillis()
            }

            if (energy > energyThreshold) {
                clapsCount++
            }
        }

        return DetectionResult(clapsCount, firstShotTime)
    }

    private data class DetectionResult(val clapsCount: Int, val firstShotTime: Long)

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val TAG = "EmailPassword"
    }
}
