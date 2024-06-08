package com.example.aplikacja.timetoshot.kotlin

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.navigation.fragment.findNavController
import com.example.aplikacja.timetoshot.R
import com.example.aplikacja.timetoshot.databinding.FragmentMainBinding
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser

import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

private val PERMISSIONS_REQUIRED = arrayOf(Manifest.permission.RECORD_AUDIO)

class MainFragment : BaseFragment() {

    companion object {
        const val mainTAG = "MainFragment"
        const val SAMPLE_RATE = 8000
    }

    private var audioRecord: AudioRecord? = null
    private var isRecording = false

    private lateinit var auth: FirebaseAuth
    val db = Firebase.firestore
    data class Shots(
        val data: Timestamp? = null,
        val firstShotTime: Long? = null,
        val recordTime: Long? = null,
        val numberOfShots: Int? = null,
        val avgShotsPerTime: Long? = null
    )

    private var _binding: FragmentMainBinding? = null

    private var mediaPlayer: MediaPlayer? = null
    private val binding: FragmentMainBinding
        get() = _binding!!

    private var startTime = 0L
    private var pauseStartTime = 0L
    private var totalPauseTime = 0L

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
        auth = Firebase.auth

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
                        pauseStartTime = System.currentTimeMillis()
                        showStopRecordingConfirmation()
                    } else {
                        startRecording()
                    }
                } else {
                    permReqLauncher.launch(PERMISSIONS_REQUIRED)
                }
            }


            signOutButton.setOnClickListener { signOut() }
            infoIcon.setOnClickListener {
                showInformationDialog()
            }

            exitAppButton.setOnClickListener {
                showExitConfirmationDialog()
            }
        }
    }


    private fun hasPermissions(context: Context) = PERMISSIONS_REQUIRED.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("SetTextI18n")
    private fun startRecording() {
        binding.recordingButton.isVisible=false
        var stopC=4
        CoroutineScope(Dispatchers.Main).launch {
            while (stopC >= 0) {
                binding.result.text = "Strzelanie rozpocznie się za $stopC"

                playCountdownBeep()

                delay(1000)
                stopC--
            }
            binding.result.text = "Recording started"
            binding.recordingButton.isVisible=true
            binding.recordingButton.text="Stop recording"

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
            ) return@launch
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )

            if (audioRecord!!.state == AudioRecord.STATE_INITIALIZED) {
                isRecording = true
                startTime = System.currentTimeMillis()
                audioRecord!!.startRecording()
                Thread {
                    recordAudio(bufferSize)
                }.start()
            } else {
                Log.e(mainTAG, "Failed to initialize AudioRecord.")
            }
        }
    }

    private fun showStopRecordingConfirmation() {
        val builder = AlertDialog.Builder(requireContext())
        val dialogView = layoutInflater.inflate(R.layout.dialog_buttons, null)
        builder.setView(dialogView)
            .setTitle("Confirmation")
            .setMessage("Are you sure you want to stop shooting?")

        val dialog = builder.create()

        val positiveButton: Button = dialogView.findViewById(R.id.positiveButton)
        val negativeButton: Button = dialogView.findViewById(R.id.negativeButton)

        positiveButton.setOnClickListener {
            totalPauseTime += System.currentTimeMillis() - pauseStartTime
            stopRecording()
            dialog.dismiss()
        }

        negativeButton.setOnClickListener {
            totalPauseTime += System.currentTimeMillis() - pauseStartTime
            dialog.dismiss()
        }

        dialog.show()
    }


    private fun stopRecording() {
        if (isRecording) {
            isRecording = false
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null
            Log.d(mainTAG, "Recording stopped.")
            binding.recordingButton.text="Start recording"

            val stopTime = System.currentTimeMillis()
            val recordTime = stopTime - startTime - totalPauseTime
            if(firstShotTime!=0L){
                firstShotTime-=startTime}
            else firstShotTime=0L
            binding.result.text = "Record time: $recordTime ms\n First shot time: $firstShotTime ms\n Shot counter: $shotCounter"
            val avgShotPerTime=shotCounter/recordTime
            val shot=Shots(Timestamp.now(),firstShotTime,recordTime,shotCounter,avgShotPerTime)
            db.collection("shots")
                .add(shot).addOnSuccessListener { documentReference ->
                    println("DocumentSnapshot added with ID: ${documentReference.id}")
                }
                .addOnFailureListener { e ->
                    println("Error adding document $e")
                }
            shotCounter = 0
            firstShotTime = 0L
            totalPauseTime = 0L
            binding.saveDataButton.visibility = View.VISIBLE

        } else {
            Log.d(mainTAG, "Recording already stopped.")
        }
    }

    private var firstShotTime = 0L
    private var shotCounter = 0
    private fun recordAudio(bufferSize: Int) {
        val buffer = ShortArray(bufferSize / 2)
        while (isRecording) {
            val read = audioRecord!!.read(buffer, 0, buffer.size, AudioRecord.READ_BLOCKING)
            if (read < 0) {
                Log.e(mainTAG, "Error reading audio.")
                return
            }
            val pair = calculateRMS(buffer)
            firstShotTime = pair.first
            shotCounter = pair.second
        }
    }

    private fun calculateRMS(buffer: ShortArray): Pair<Long, Int> {
        var sum = 0.0
        for (sample in buffer) {
            sum += sample * sample
        }
        val rms = Math.sqrt(sum / buffer.size)
        Log.d(mainTAG, "loudness:$rms")
        if (rms > 3000) {
            shotCounter++
            if (firstShotTime == 0L) {
                firstShotTime = System.currentTimeMillis()
            }
        }
        return Pair(firstShotTime, shotCounter)
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
        updateUI(currentUser)
    }

    private fun signOut() {
        auth.signOut()
        findNavController().navigate(R.id.action_emailpassword)
    }

    private fun showExitConfirmationDialog() {
        val builder = AlertDialog.Builder(requireContext())
        builder.setTitle("Exit Confirmation")
            .setMessage("Are you sure you want to exit the application?")
            .setPositiveButton("Yes") { dialog, which ->
                // Zamknięcie aplikacji po kliknięciu "Yes"
                requireActivity().finish()
            }
            .setNegativeButton("No") { dialog, which ->
                // Anulowanie zamknięcia aplikacji po kliknięciu "No"
                dialog.dismiss()
            }
        builder.show()
    }
    private fun showInformationDialog() {
        val dialogMessage = "You can try your shooting skills with us. Click start, when you are ready to shoot. We can track your first shot and show you when you got your first target down"
        val builder = AlertDialog.Builder(requireContext())
        builder.setTitle("How it works?")
            .setMessage(dialogMessage)
            .setPositiveButton("OK", null)
        val dialog = builder.create()
        dialog.show()
    }
    private fun playCountdownBeep() {
        mediaPlayer?.release() // Zwolnij poprzedni MediaPlayer, jeśli istnieje
        mediaPlayer = MediaPlayer.create(requireContext(), R.raw.countdown_beep)
        mediaPlayer?.start()
        mediaPlayer?.setOnCompletionListener {
            it.release() // Zwolnij zasoby po zakończeniu odtwarzania
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
}
