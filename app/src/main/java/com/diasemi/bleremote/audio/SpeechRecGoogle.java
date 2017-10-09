/*-----------------------------------------------------------------------------
 *                 @@@           @@                  @@
 *                @@@@@          @@   @@             @@
 *                @@@@@          @@                  @@
 *       .@@@@@.  @@@@@      @@@ @@   @@     @@@@    @@     @@@       @@@
 *     @@@@@@   @@@@@@@    @@   @@@   @@        @@   @@   @@   @@   @@   @@
 *    @@@@@    @@@@@@@@    @@    @@   @@    @@@@@@   @@   @@   @@   @@   @@
 *   @@@@@@     @@@@@@@    @@    @@   @@   @@   @@   @@   @@   @@   @@   @@
 *   @@@@@@@@     @@@@@    @@   @@@   @@   @@   @@   @@   @@   @@   @@   @@
 *   @@@@@@@@@@@    @@@     @@@@ @@   @@    @@@@@    @@     @@@       @@@@@
 *    @@@@@@@@@@@  @@@@                                                  @@
 *     @@@@@@@@@@@@@@@@                                                  @@
 *       "@@@@@"  @@@@@    S  E  M  I  C  O  N  D  U  C  T  O  R     @@@@@
 *
 *
 * Copyright (C) 2014 Dialog Semiconductor GmbH and its Affiliates, unpublished
 * work. This computer program includes Confidential, Proprietary Information
 * and is a Trade Secret of Dialog Semiconductor GmbH and its Affiliates. All
 * use, disclosure, and/or  reproduction is prohibited unless authorized in
 * writing. All Rights Reserved.
 *
 * Filename: AudioCodec.java
 * Purpose : IMA decoder
 * Created : 08-2014
 * By      : Johannes Steensma, Taronga Technology Inc.
 * Country : USA
 *
 *-----------------------------------------------------------------------------
 *
 * Speech Recognition class
 *
 *-----------------------------------------------------------------------------
 */

package com.diasemi.bleremote.audio;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.os.Build;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.diasemi.bleremote.Constants;
import com.diasemi.bleremote.R;

import org.apache.http.util.ByteArrayBuffer;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.nio.ByteBuffer;
import java.util.Arrays;

import javax.net.ssl.HttpsURLConnection;

/**
 * Created by johannes on 10/13/2014.
 * <p/>
 * Some more info from:
 * https://github.com/lkuza2/java-speech-api/tree/master/src/main/java/com/darkprograms/speech/recognizer
 * http://bernaerts.dyndns.org/263-asterisk-voicemail-speech-recognition-google
 * Python implementation: https://pypi.python.org/pypi/SpeechRecognition/
 * https://github.com/gillesdemey/google-speech-v2
 * <p/>
 * Flac Encoding: Currently using built-in Android MediaCodec
 * Other options:
 * - ffmpeg
 * - Java implementation of flac encoder: http://javaflacencoder.sourceforge.net/
 * - http://stackoverflow.com/questions/21804390/android-mediacodec-pcm-aac-encoder-pcmdecoder-in-real-time-with-correc
 */
@SuppressWarnings("deprecation")
public class SpeechRecGoogle {
    private static final String TAG = "SpeechRec";

    private static final String API_URL = "https://www.google.com/speech-api/v2/recognize?client=chromium";
    private static final String API_KEY = "AIzaSyBOti4mM-6x9WDnZIjIeyEU21OpBXqWBgw";
    private static final boolean SAVE_FLAC_OUTPUT = false;
    private static final String SAVE_FLAC_OUTPUT_NAME = "/bleaudio.flac";

    private Context context;
    private MediaCodec flacEncoder;
    private ByteBuffer[] inputBuffers;
    private ByteBuffer[] outputBuffers;
    private MediaCodec.BufferInfo bufferInfo;
    private short[] samplesBuffer;
    private ByteArrayBuffer flacBuffer;
    private String speechRecResult;
    private String speechRecConfidence;

    /**
     * Constructor
     */
    public SpeechRecGoogle(Context context) {
        this.context = context;
        flacBuffer = new ByteArrayBuffer(100000);
    }

    /**
     * Send new samples to FLAC encoder. Get encoded data.
     *
     * @param samples samples
     */
    @SuppressLint("NewApi")
    public void addSampleData(short[] samples) {
        if (flacEncoder == null)
            return;
        boolean endOfStream = samples == null;
        boolean endOfStreamFlag = false;
        ByteBuffer inputBuffer;
        ByteBuffer outputBuffer;

        // Prepend any previous samples
        if (samplesBuffer != null) {
            short[] newSamples = null;
            if (samples != null) {
                newSamples = Arrays.copyOf(samplesBuffer, samplesBuffer.length + samples.length);
                System.arraycopy(samples, 0, newSamples, samplesBuffer.length, samples.length);
            }
            samples = newSamples != null ? newSamples : samplesBuffer;
            samplesBuffer = null;
        }

        // Write raw data to encoder
        int inputBufferIndex = -1;
        if (samples != null) {
            inputBufferIndex = flacEncoder.dequeueInputBuffer(1000);
            // Keep data if no available buffer
            if (inputBufferIndex < 0)
                samplesBuffer = samples.clone();
        }
        while (inputBufferIndex >= 0) {
            inputBuffer = Build.VERSION.SDK_INT < 21 ? inputBuffers[inputBufferIndex] : flacEncoder.getInputBuffer(inputBufferIndex);
            inputBuffer.clear();
            if (samples != null && samples.length > 0) {
                // Add data to buffer
                int length = Math.min(samples.length, inputBuffer.limit() / 2);
                for (int i = 0; i < length; i++)
                    inputBuffer.putShort(samples[i]);
                flacEncoder.queueInputBuffer(inputBufferIndex, 0, length * 2, 0, 0);
                if (length == samples.length)
                    break;
                // Send/Keep excess data
                samples = samplesBuffer = Arrays.copyOfRange(samples, length, samples.length);
                inputBufferIndex = flacEncoder.dequeueInputBuffer(1000);
            }
        }

        // Read encoded data from encoder
        // After setting the BUFFER_FLAG_END_OF_STREAM flag in the input buffer, the encoder truncates
        // the stream and we don't get an output buffer with the last data. In order to mitigate this, we
        // add some silence at the end of the stream until we get an encoded output buffer.
        int attempts = 0;
        int silence = 0;
        int outputBufferIndex = flacEncoder.dequeueOutputBuffer(bufferInfo, 0);
        while (outputBufferIndex >= 0 || endOfStream) {
            // Add silence at the end of the stream (or any remaining data)
            if (endOfStream && outputBufferIndex < 0) {
                if (!endOfStreamFlag) { // don't add anything after the end of stream flag
                    inputBufferIndex = flacEncoder.dequeueInputBuffer(1000);
                    if (inputBufferIndex >= 0) {
                        inputBuffer = Build.VERSION.SDK_INT < 21 ? inputBuffers[inputBufferIndex] : flacEncoder.getInputBuffer(inputBufferIndex);
                        int length = inputBuffer.limit() / 2;
                        for (int i = 0; i < length; i++) {
                            if (samplesBuffer != null && i < samplesBuffer.length) {
                                inputBuffer.putShort(samplesBuffer[i]);
                            } else {
                                ++silence;
                                inputBuffer.putShort((short) 0);
                            }
                        }
                        samplesBuffer = null;
                        int flags = silence > 8000 ? MediaCodec.BUFFER_FLAG_END_OF_STREAM : 0;
                        flacEncoder.queueInputBuffer(inputBufferIndex, 0, length * 2, 0, flags);
                        if (flags != 0) {
                            Log.d(TAG, "End of input stream (" + silence + " silence samples)");
                            endOfStreamFlag = true;
                        }
                    }
                }
                // Retry getting an output buffer
                outputBufferIndex = flacEncoder.dequeueOutputBuffer(bufferInfo, 1000);
                if (attempts++ < 10)
                    continue;
                else
                    break;
            }

            // Read data from buffer
            Log.d(TAG, "FLAC data: " + bufferInfo.size);
            outputBuffer = Build.VERSION.SDK_INT < 21 ? outputBuffers[outputBufferIndex] : flacEncoder.getOutputBuffer(outputBufferIndex);
            outputBuffer.position(bufferInfo.offset);
            outputBuffer.limit(bufferInfo.offset + bufferInfo.size);
            byte[] outData = new byte[bufferInfo.size];
            outputBuffer.get(outData);
            flacBuffer.append(outData, 0, outData.length);
            flacEncoder.releaseOutputBuffer(outputBufferIndex, false);

            // Check for end of stream
            if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                Log.d(TAG, "End of output stream");
                break;
            }

            // Signal end of stream (ensure some silence has been added)
            if (endOfStream && silence > 0) {
                inputBufferIndex = flacEncoder.dequeueInputBuffer(1000);
                if (inputBufferIndex < 0)
                    break;
                flacEncoder.queueInputBuffer(inputBufferIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                Log.d(TAG, "End of input stream (" + silence + " silence samples)");
                endOfStreamFlag = true;
            }

            outputBufferIndex = flacEncoder.dequeueOutputBuffer(bufferInfo, 0);
        }
    }

    /**
     * Start the speech recognition. Initialize the FLAC Encoder.
     */
    public void start() {
        try {
            flacBuffer.clear();
            speechRecResult = "";
            samplesBuffer = null;

            // Initialize the Codec
            flacEncoder = MediaCodec.createEncoderByType("audio/flac");
            MediaFormat format = new MediaFormat();
            format.setString(MediaFormat.KEY_MIME, "audio/flac");
            format.setInteger(MediaFormat.KEY_CHANNEL_COUNT, 1);
            format.setInteger(MediaFormat.KEY_SAMPLE_RATE, 16000);
            //format.setInteger(MediaFormat.KEY_FLAC_COMPRESSION_LEVEL, 8);
            flacEncoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            flacEncoder.start();
            Log.d(TAG, "FLAC codec started");
            if (Build.VERSION.SDK_INT < 21) {
                inputBuffers = flacEncoder.getInputBuffers();
                outputBuffers = flacEncoder.getOutputBuffers();
            }
            bufferInfo = new MediaCodec.BufferInfo();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * End of speech recognition. Flush the FLAC encoder and send speech API HTTP request.
     */
    public void stop() {
        addSampleData(null); // flush the encoder
        if (flacEncoder != null) {
            flacEncoder.stop();
            flacEncoder.release();
            flacEncoder = null;
        }
        byte[] flacData = flacBuffer.toByteArray();
        Log.d(TAG, "FLAC data size: " + flacData.length);
        sendRequest(prepareGoogleSpeechRecUrl(), flacData);

        if (SAVE_FLAC_OUTPUT) {
            try {
                FileOutputStream flacFile = new FileOutputStream(context.getExternalFilesDir(null).getAbsolutePath() + SAVE_FLAC_OUTPUT_NAME);
                flacFile.write(flacData);
                flacFile.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private String prepareGoogleSpeechRecUrl() {
        StringBuilder url = new StringBuilder(API_URL);
        String defaultLanguage = context.getResources().getStringArray(R.array.voice_rec_lang_codes)[0];
        String language = context.getSharedPreferences(Constants.PREFERENCES_NAME, Context.MODE_PRIVATE).getString(Constants.PREF_VOICE_REC_LANG, defaultLanguage);
        url.append("&lang=").append(language);
        url.append("&key=").append(API_KEY);
        return url.toString();
    }

    /**
     * Parse the JSON result struct, and get the first result returned.
     * Results are in the form:
     * {"result":[{"alternative":[{"transcript":"hello","confidence":0.98762906}],"final":true}],"result_index":0}
     * {"result":[{"alternative":[{"transcript":"123","confidence":0.92055374},{"transcript":"1 2 3"},
     *      {"transcript":"one two three"},{"transcript":"1 2 /3"},{"transcript":"12 /3"}],"final":true}],"result_index":0}
     */
    void processResult(final String result) {
        Log.d(TAG, "Google speech API response: " + (result.isEmpty() ? "EMPTY" : result));
        if (result.isEmpty()) {
            // If no result is returned, send the broadcast anyway with no data
            Intent intent = new Intent(Constants.ACTION_SPEECHREC_RESULT);
            LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
            return;
        }
        try {
            JSONObject jObject = new JSONObject(result);
            // First result
            JSONArray resultArray = jObject.getJSONArray("result");
            if (resultArray.length() > 0) {
                JSONArray alternativeArray = resultArray.getJSONObject(0).getJSONArray("alternative");
                if (alternativeArray.length() > 0) {
                    JSONObject transcript = alternativeArray.getJSONObject(0);
                    speechRecResult = transcript.getString("transcript");
                    speechRecConfidence = transcript.has("confidence") ? transcript.getString("confidence") : "";
                }
            }
            Log.i(TAG, "Speech recognition result: " + speechRecResult);
        } catch (JSONException e) {
            Log.e(TAG, "JSONException", e);
        }

        Intent intent = new Intent(Constants.ACTION_SPEECHREC_RESULT);
        intent.putExtra(Constants.EXTRA_DATA, speechRecResult);
        intent.putExtra(Constants.EXTRA_VALUE, speechRecConfidence);
        LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
    }

    /**
     * Opens a chunked response HTTPS line to the specified URL
     *
     * @param urlString The URL string to connect for chunking
     * @param data The data you want to send to Google. Speech files under 15
     *            seconds long recommended.
     */
    private void sendRequest(final String urlString, final byte[] data) {
        new Thread() {

            @SuppressWarnings({
                    "null", "resource"
            })
            @Override
            public void run() {
                HttpsURLConnection httpConn = null;
                ByteBuffer buff = ByteBuffer.wrap(data);
                byte[] destdata = new byte[2048];
                OutputStream out;
                try {
                    Log.d(TAG, "Sending request to: " + urlString);
                    URL url = new URL(urlString);
                    URLConnection urlConn = url.openConnection();
                    httpConn = (HttpsURLConnection) urlConn;
                    httpConn.setAllowUserInteraction(false);
                    httpConn.setInstanceFollowRedirects(true);
                    httpConn.setRequestMethod("POST");
                    httpConn.setDoOutput(true);
                    httpConn.setChunkedStreamingMode(0); // TransferType: chunked
                    httpConn.setRequestProperty("Content-Type", "audio/x-flac; rate=16000");

                    // This opens a connection, then sends POST & headers.
                    out = httpConn.getOutputStream();
                    // Beyond 15 sec duration just simply writing the file
                    // does not seem to work. So buffer it and delay to simulate
                    // buffered microphone delivering stream of speech
                    // re: net.http.ChunkedOutputStream.java
                    while (buff.hasRemaining()) {
                        int length = Math.min(buff.remaining(), destdata.length);
                        buff.get(destdata, 0, length);
                        out.write(destdata, 0, length);
                    }
                    out.close();

                    int resCode = httpConn.getResponseCode();
                    Log.d(TAG, "Google speech API response code: " + resCode);
                    if (resCode != HttpURLConnection.HTTP_OK) {
                        Log.e(TAG, "HTTP error response");
                        return;
                    }

                    BufferedReader br = new BufferedReader(new InputStreamReader(httpConn.getInputStream()));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    for (int n = 1; (line = br.readLine()) != null; n++) {
                        Log.d(TAG, "Response line " + n + ": " + line);
                        // First line contains an empty result
                        if (n != 1)
                            sb.append(line);
                    }
                    processResult(sb.toString());
                } catch (IOException e) {
                    Log.e(TAG, "HTTP request: " + e.getMessage());
                } finally {
                    if (httpConn != null)
                        httpConn.disconnect();
                }
            }
        }.start();
    }
}
