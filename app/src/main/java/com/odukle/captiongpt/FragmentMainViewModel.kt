package com.odukle.captiongpt

import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.amazonaws.AmazonWebServiceRequest
import com.amazonaws.auth.AWSCredentials
import com.amazonaws.auth.AWSCredentialsProvider
import com.amazonaws.event.ProgressListener
import com.amazonaws.services.rekognition.AmazonRekognitionClient
import com.amazonaws.services.rekognition.model.DetectLabelsRequest
import com.amazonaws.services.rekognition.model.Image
import com.amazonaws.services.rekognition.model.Label
import com.amazonaws.services.rekognition.model.StartLabelDetectionRequest
import com.theokanning.openai.completion.CompletionRequest
import com.theokanning.openai.completion.CompletionResult
import com.theokanning.openai.completion.chat.ChatCompletionRequest
import com.theokanning.openai.completion.chat.ChatMessage
import com.theokanning.openai.service.OpenAiService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.launch
import software.amazon.awssdk.regions.Region
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.time.Duration
import java.util.Locale
import kotlin.math.sqrt

private const val TAG = "FragmentMainViewModel"

class FragmentMainViewModel : ViewModel() {
    private val descriptionLiveData = MutableLiveData<String?>()
    private val captionLiveData = MutableLiveData<String>()

    fun generateImageDescription(imageUri: Uri, activity: FragmentActivity, OPENAI_API_KEY: String, AWS_KEY: String, AWS_SECRET_KEY: String) {

        val rekognitionClient = getAWSRecognitionClient(AWS_KEY, AWS_SECRET_KEY)
        // Read the image file into bytes
        val inputStream = activity.contentResolver.openInputStream(imageUri)
        var imageBytes = ByteArray(inputStream!!.available())
        if (imageBytes.size > 500) imageBytes = compressImageTo500KB(inputStream)
        inputStream.close()

        // Detect labels using Amazon Rekognition
        val image = Image()
        image.bytes = ByteBuffer.wrap(imageBytes)
        val request = DetectLabelsRequest()
        request.apply {
            this.image = image
            maxLabels = 20
            minConfidence = 70f
        }
        val response = rekognitionClient.detectLabels(request)
        val imageLabels = mutableListOf<String>()
        for (label: Label in response.labels) {
            imageLabels.add(label.name.lowercase(Locale.ROOT))
        }

        // Generate a prompt by concatenating the image labels
        val prompt = "Generate an image caption for the following image labels: ${imageLabels.joinToString(", ")}"
        Log.d(TAG, "generateImageDescription: $prompt")

        // Use the OpenAI API to generate image captions
        val openaiApi = OpenAiService(OPENAI_API_KEY)
        val openaiCompletionRequest = CompletionRequest.builder()
            .model("text-davinci-003")
            .prompt(prompt)
            .temperature(0.5)
            .maxTokens(50)
            .build()
        val openaiCompletionResponse: CompletionResult = openaiApi.createCompletion(openaiCompletionRequest)

        // Extract the generated image captions from the API response
        activity.runOnUiThread {
            descriptionLiveData.value = openaiCompletionResponse.choices[0].text
        }
        Log.d(TAG, "generateImageCaption: ${descriptionLiveData.value}")
    }

    private fun compressImageTo500KB(inputStream: InputStream): ByteArray {
        // Load the original image from file
        val bitmap = BitmapFactory.decodeStream(inputStream)

        // Calculate the target compression quality
        val targetSize = 500 * 1024 // 500 KB
        var quality = 100
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
        while (outputStream.toByteArray().size > targetSize && quality >= 10) {
            outputStream.reset()
            quality -= 10
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
        }

        return outputStream.toByteArray()
    }

    fun generateCaptions(description: String,noOfCaptions: String,tone: String, activity: FragmentActivity, OPENAI_API_KEY: String) {
        val openaiApi = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            OpenAiService(OPENAI_API_KEY, Duration.ofSeconds(30))
        } else {
            OpenAiService(OPENAI_API_KEY)
        }

        var prompt = "generate $noOfCaptions $tone captions for an instagram post that contains $description"
        val chatCompletionReq = ChatCompletionRequest.builder()
            .model("gpt-3.5-turbo")
            .messages(mutableListOf(ChatMessage("user", prompt)))
            .temperature(0.5)
//            .maxTokens(100)
            .build()

        prompt = "generate 4 captions for an instagram post that contains $description"
        val completionRequest = CompletionRequest.builder()
            .model("curie")
            .prompt(prompt)
            .temperature(0.5)
            .build()

        CoroutineScope(IO).launch {
            try {
                val chatResp = openaiApi.createChatCompletion(chatCompletionReq)
//                val chatResp = openaiApi.createCompletion(completionRequest)
                val stringBuilder = StringBuilder()
                chatResp.choices.forEach {
                    stringBuilder.append("\n\n${it.message.content}")
                }
                activity.runOnUiThread {
                    captionLiveData.value = stringBuilder.toString()
                    Log.d(TAG, "generateCaptions: ${stringBuilder.toString()}")
                }
            } catch (e: Exception) {
                activity.runOnUiThread {
                    Toast.makeText(activity, "${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }

    }

    fun getDescription(): MutableLiveData<String?> {
        return descriptionLiveData
    }

    fun getCaptions(): MutableLiveData<String> {
        return captionLiveData
    }

    private fun getAWSRecognitionClient(AWS_KEY: String, AWS_SECRET_KEY: String): AmazonRekognitionClient {
        val region = Region.AP_SOUTH_1
        //AWS credentials provider
        val credProvider = object : AWSCredentialsProvider {
            override fun getCredentials(): AWSCredentials {
                return object : AWSCredentials {
                    override fun getAWSAccessKeyId(): String = AWS_KEY
                    override fun getAWSSecretKey(): String = AWS_SECRET_KEY
                }
            }

            override fun refresh() {
                //TODO
            }
        }

        return AmazonRekognitionClient(credProvider)
    }
}