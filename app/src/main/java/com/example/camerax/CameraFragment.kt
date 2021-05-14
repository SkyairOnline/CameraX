package com.example.camerax

import android.content.Context
import android.content.res.Configuration
import android.hardware.display.DisplayManager
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Bundle
import android.util.DisplayMetrics
import android.view.View
import android.webkit.MimeTypeMap
import android.widget.ImageView
import androidx.camera.core.AspectRatio
import androidx.camera.core.Camera
import androidx.camera.core.CameraInfoUnavailableException
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.core.net.toFile
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.example.camerax.ImageFile.createImageFile
import com.example.camerax.ImageFile.fixConfigureImage
import com.example.camerax.ImageFile.getOutputDirectory
import com.example.camerax.databinding.FragmentCameraBinding
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class CameraFragment: BaseFragment<FragmentCameraBinding>(FragmentCameraBinding::inflate) {

    private var displayId: Int = -1
    private var imageCapture: ImageCapture? = null
    private var imageAnalyzer: ImageAnalysis? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var preview: Preview? = null
    private var camera: Camera? = null
    private var savedUri: Uri? = null
    private var lensFacing: Int = CameraSelector.LENS_FACING_BACK
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var outputDirectory: File

    private val displayManager by lazy {
        requireContext().getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
    }

    /**
     * We need a display listener for orientation changes that do not trigger a configuration
     * change, for example if we choose to override config change in manifest or for 180-degree
     * orientation changes.
     */
    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = Unit
        override fun onDisplayRemoved(displayId: Int) = Unit
        override fun onDisplayChanged(displayId: Int) = view?.let { view ->
            if (displayId == this@CameraFragment.displayId) {
                imageCapture?.targetRotation = view.display.rotation
                imageAnalyzer?.targetRotation = view.display.rotation
            }
        } ?: Unit
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Initialize our background executor
        cameraExecutor = Executors.newSingleThreadExecutor()
        // Every time the orientation of device changes, update rotation for use cases
        displayManager.registerDisplayListener(displayListener, null)
        // Determine the output directory
        outputDirectory = getOutputDirectory(requireContext())
        // Wait for the views to be properly laid out
        binding.viewFinderCameraCapture.post {

            // Keep track of the display in which this view is attached
            displayId = binding.viewFinderCameraCapture.display.displayId

            // Build UI controls
            updateCameraUi()

            // Set up the camera and its use cases
            setUpCamera()
        }
        getListener()
        takePictureDone(false)
    }

    private fun getListener(){
        with(binding){
            btnRetakeImage.setOnClickListener {
                takePictureDone(false)
            }

            btnCancelCaptureImage.setOnClickListener{
                takePictureDone(true)
            }

            btnCheck.setOnClickListener {

            }
            btnBack.setOnClickListener {

            }
        }
    }

    /** Method used to re-draw the camera UI controls, called every time configuration changes. */
    private fun updateCameraUi() {
        with(binding){
            // Listener for button used to capture photo
            btnCaptureImage.setOnClickListener {

                // Get a stable reference of the modifiable image capture use case
                imageCapture?.let { imageCapture ->

                    // Create output file to hold the image
                    val photoFile = createImageFile(outputDirectory)

                    // Setup image capture metadata
                    val metadata = ImageCapture.Metadata().apply {

                        // Mirror image when using the front camera
                        isReversedHorizontal = lensFacing == CameraSelector.LENS_FACING_FRONT
                    }

                    // Create output options object which contains file + metadata
                    val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile)
                        .setMetadata(metadata)
                        .build()

                    // Setup image capture listener which is triggered after photo has been taken
                    imageCapture.takePicture(
                        outputOptions, cameraExecutor, object : ImageCapture.OnImageSavedCallback {
                            override fun onError(e: ImageCaptureException) {
                                e.printStackTrace()
                            }

                            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                                savedUri = output.savedUri ?: Uri.fromFile(photoFile)
                                // If the folder selected is an external media directory, this is
                                // unnecessary but otherwise other apps will not be able to access our
                                // images unless we scan them using [MediaScannerConnection]
                                fixConfigureImage(
                                    photoFile,
                                    requireContext(),
                                    savedUri!!
                                )
                                val mimeType = MimeTypeMap.getSingleton()
                                    .getMimeTypeFromExtension(savedUri?.toFile()?.extension)
                                MediaScannerConnection.scanFile(
                                    context,
                                    savedUri?.toFile()?.let { it1 -> arrayOf(it1.absolutePath) },
                                    arrayOf(mimeType)
                                ) { _, _ -> }
                                imgCameraCapture.post{
                                    Glide.with(imgCameraCapture)
                                        .load(savedUri)
                                        .centerCrop()
                                        .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
                                        .into(imgCameraCapture)
                                    takePictureDone(true)
                                }
                            }
                        })
                }
            }

            // Setup for button used to switch cameras
            btnSwitchCaptureImage.let {

                // Disable the button until the camera is set up
                it.isEnabled = false

                // Listener for button used to switch cameras. Only called if the button is enabled
                it.setOnClickListener {
                    lensFacing = if (CameraSelector.LENS_FACING_FRONT == lensFacing) {
                        CameraSelector.LENS_FACING_BACK
                    } else {
                        CameraSelector.LENS_FACING_FRONT
                    }
                    // Re-bind use cases to update selected camera
                    bindCameraUseCases()
                }
            }
        }
    }

    private fun takePictureDone(status: Boolean){
        with(binding){
            if(status){
                enableView(imgCameraCapture, true)
                viewFinderCameraCapture.gone()
                enableView(btnCaptureImage, false)
                enableView(btnSwitchCaptureImage, false)
                enableView(btnRetakeImage, true)
                enableView(btnCheck, true)
            }
            else{
                enableView(imgCameraCapture, false)
                viewFinderCameraCapture.visible()
                enableView(btnCaptureImage, true)
                enableView(btnSwitchCaptureImage, true)
                enableView(btnRetakeImage, false)
                enableView(btnCheck, false)
            }
        }
    }

    private fun enableView(view: ImageView, status: Boolean){
        if(status){
            view.enable()
            view.visible()
        }
        else{
            view.disable()
            view.gone()
        }
    }

    /**
     *  androidx.camera.core.ImageAnalysisConfig requires enum value of
     *  androidx.camera.core.AspectRatio. Currently it has values of 4:3 & 16:9.
     *
     *  Detecting the most suitable ratio for dimensions provided in @params by counting absolute
     *  of preview ratio to one of the provided values.
     *
     *  @param width - preview width
     *  @param height - preview height
     *  @return suitable aspect ratio
     */
    private fun aspectRatio(width: Int, height: Int): Int {
        val previewRatio = max(width, height).toDouble() / min(width, height)
        if (abs(previewRatio - (4.0 / 3.0)) <= abs(previewRatio - (16.0 / 9.0))) {
            return AspectRatio.RATIO_4_3
        }
        return AspectRatio.RATIO_16_9
    }

    /** Declare and bind preview, capture and analysis use cases */
    private fun bindCameraUseCases() {

        // Get screen metrics used to setup camera for full screen resolution
        val metrics = DisplayMetrics().also { binding.viewFinderCameraCapture.display.getRealMetrics(it) }

        val screenAspectRatio = aspectRatio(metrics.widthPixels, metrics.heightPixels)

        val rotation = binding.viewFinderCameraCapture.display.rotation

        // CameraProvider
        val cameraProvider = cameraProvider
            ?: throw IllegalStateException("Camera initialization failed.")

        // CameraSelector
        val cameraSelector = CameraSelector.Builder().requireLensFacing(lensFacing).build()

        // Preview
        preview = Preview.Builder()
            // We request aspect ratio but no resolution
            .setTargetAspectRatio(screenAspectRatio)
            // Set initial target rotation
            .setTargetRotation(rotation)
            .build()

        // ImageCapture
        imageCapture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            // We request aspect ratio but no resolution to match preview config, but letting
            // CameraX optimize for whatever specific resolution best fits our use cases
            .setTargetAspectRatio(screenAspectRatio)
            // Set initial target rotation, we will have to call this again if rotation changes
            // during the lifecycle of this use case
            .setTargetRotation(rotation)
            .build()

        // ImageAnalysis
        imageAnalyzer = ImageAnalysis.Builder()
            // We request aspect ratio but no resolution
            .setTargetAspectRatio(screenAspectRatio)
            // Set initial target rotation, we will have to call this again if rotation changes
            // during the lifecycle of this use case
            .setTargetRotation(rotation)
            .build()
            // The analyzer can then be assigned to the instance
            .also {
                it.setAnalyzer(cameraExecutor, LuminosityAnalyzer {
                    // Values returned from our analyzer are passed to the attached listener
                })
            }

        // Must unbind the use-cases before rebinding them
        cameraProvider.unbindAll()

        try {
            // A variable number of use-cases can be passed here -
            // camera provides access to CameraControl & CameraInfo
            camera = cameraProvider.bindToLifecycle(
                this, cameraSelector, preview, imageCapture, imageAnalyzer)

            // Attach the viewfinder's surface provider to preview use case
            preview?.setSurfaceProvider(binding.viewFinderCameraCapture.surfaceProvider)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /** Initialize CameraX, and prepare to bind the camera use cases  */
    private fun setUpCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())
        cameraProviderFuture.addListener({

            // CameraProvider
            cameraProvider = cameraProviderFuture.get()

            // Select lensFacing depending on the available cameras
            lensFacing = when {
                hasBackCamera() -> CameraSelector.LENS_FACING_BACK
                hasFrontCamera() -> CameraSelector.LENS_FACING_FRONT
                else -> throw IllegalStateException("Back and front camera are unavailable")
            }

            // Enable or disable switching between cameras
            updateCameraSwitchButton()

            // Build and bind the camera use cases
            bindCameraUseCases()
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    /** Returns true if the device has an available back camera. False otherwise */
    private fun hasBackCamera(): Boolean {
        return cameraProvider?.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) ?: false
    }

    /** Returns true if the device has an available front camera. False otherwise */
    private fun hasFrontCamera(): Boolean {
        return cameraProvider?.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) ?: false
    }

    /** Enabled or disabled a button to switch cameras depending on the available cameras */
    private fun updateCameraSwitchButton() {
        with(binding.btnSwitchCaptureImage){
            isEnabled = try {
                hasBackCamera() && hasFrontCamera()
            } catch (exception: CameraInfoUnavailableException) {
                false
            }
        }
    }

    /**
     * Inflate camera controls and update the UI manually upon config changes to avoid removing
     * and re-adding the view finder from the view hierarchy; this provides a seamless rotation
     * transition on devices that support it.
     *
     * NOTE: The flag is supported starting in Android 8 but there still is a small flash on the
     * screen for devices that run Android 9 or below.
     */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        // Redraw the camera UI controls
        updateCameraUi()

        // Enable or disable switching between cameras
        updateCameraSwitchButton()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        cameraExecutor.shutdown()
        displayManager.unregisterDisplayListener(displayListener)
        imageCapture = null
        imageAnalyzer = null
        cameraProvider = null
        preview = null
        camera = null
        savedUri = null
    }
}
