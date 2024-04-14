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

    private val recordingThread: Thread? = null
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        _binding = FragmentMainBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setProgressBar(binding.progressBar)

        var startTime:Long=0
        var status =0
        // Buttons
        with(binding) {
            recordingButton.setOnClickListener {
                if (hasPermissions(requireContext())) {
                    if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                        return@setOnClickListener
                    }
                    if (status==0){
                        startTime=System.currentTimeMillis()
                        recorder = AudioRecord(MediaRecorder.AudioSource.DEFAULT, SAMPLING_RATE_IN_HZ, CHANNEL_CONFIG, AUDIO_FORMAT, BUFFER_SIZE)
                        recorder?.startRecording()
                        recordingButton.setText("Stop recording")
                        status=1
                    }
                    else if(status==1){
                        recorder?.stop()
                        var stopTime=System.currentTimeMillis()
                        var recordTime=(stopTime-startTime)/1000
                        recordingButton.setText("Start recording")
                        result.text="Czas nagrania ${recordTime}s"
                        status=0
                    }
                }else{
                    permReqLauncher.launch(
                        PERMISSIONS_REQUIRED
                    )
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
    private val permReqLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val granted = permissions.entries.all {
                it.value
            }
            if (granted) {
                displayMainFragment()
            }
        }
    private fun displayMainFragment() {

    }

    public override fun onStart() {
        super.onStart()
        // Check if user is signed in (non-null) and update UI accordingly.
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val TAG = "EmailPassword"
    }

/*
    class NoiseRecorder {
        private val TAG: String = SoundOfTheCityConstants.TAG

        @get:Throws(NoValidNoiseLevelException::class)
        val noiseLevel: Double
            get() {
                Logging.e(TAG, "start new recording process")
                var bufferSize = AudioRecord.getMinBufferSize(44100, AudioFormat.CHANNEL_IN_DEFAULT, AudioFormat.ENCODING_PCM_16BIT)
                //making the buffer bigger....
                bufferSize = bufferSize * 4
                val recorder = AudioRecord(MediaRecorder.AudioSource.MIC, 44100, AudioFormat.CHANNEL_IN_DEFAULT, AudioFormat.ENCODING_PCM_16BIT, bufferSize)
                val data = ShortArray(bufferSize)
                var average = 0.0
                recorder.startRecording()
                //recording data;
                recorder.read(data, 0, bufferSize)
                recorder.stop()
                Logging.e(TAG, "stop")
                for (s in data) {
                    if (s > 0) {
                        average += abs(s.toDouble())
                    } else {
                        bufferSize--
                    }
                }
                //x=max;
                val x = average / bufferSize
                Logging.e(TAG, "" + x)
                recorder.release()
                Logging.d(TAG, "getNoiseLevel() ")
                var db = 0.0
                if (x == 0.0) {
                    val e = NoValidNoiseLevelException(x)
                    throw e
                }
                val pressure =
                    x / 51805.5336
                Logging.d(TAG, "x=$pressure Pa")
                db = 20 * log10(pressure / REFERENCE)
                Logging.d(TAG, "db=$db")
                if (db > 0) {
                    return db
                }
                val e = NoValidNoiseLevelException(x)
                throw e
            }

        companion object {
            var REFERENCE = 0.00002
        }
    }
*/
}
