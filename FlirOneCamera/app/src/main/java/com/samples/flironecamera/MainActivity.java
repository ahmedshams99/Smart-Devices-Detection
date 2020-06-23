/*
 * ******************************************************************
 * @title FLIR THERMAL SDK
 * @file MainActivity.java
 * @Author FLIR Systems AB
 *
 * @brief  Main UI of test application
 *
 * Copyright 2019:    FLIR Systems
 * ******************************************************************/
package com.samples.flironecamera;

import android.content.pm.PackageManager;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.ColorSpace;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import com.flir.thermalsdk.ErrorCode;
import com.flir.thermalsdk.androidsdk.ThermalSdkAndroid;
import com.flir.thermalsdk.androidsdk.live.connectivity.UsbPermissionHandler;
import com.flir.thermalsdk.live.CommunicationInterface;
import com.flir.thermalsdk.live.Identity;
import com.flir.thermalsdk.live.connectivity.ConnectionStatusListener;
import com.flir.thermalsdk.live.discovery.DiscoveryEventListener;
import com.flir.thermalsdk.log.ThermalLog;
import org.jetbrains.annotations.NotNull;
import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfFloat;
import org.opencv.core.MatOfInt;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.MatOfRect;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.dnn.Dnn;
import org.opencv.dnn.Net;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.opencv.utils.Converters;

import org.tensorflow.lite.Interpreter;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import static android.graphics.Bitmap.Config.RGBA_F16;
import static android.graphics.ColorSpace.Named.SRGB;
import static org.opencv.core.CvType.CV_8UC1;

/**
 * Sample application for scanning a FLIR ONE or a built in emulator
 * <p>
 * See the {@link CameraHandler} for how to preform discovery of a FLIR ONE camera, connecting to it and start streaming images
 * <p>
 * The MainActivity is primarily focused to "glue" different helper classes together and updating the UI components
 * <p/>
 * Please note, this is <b>NOT</b> production quality code, error handling has been kept to a minimum to keep the code as clear and concise as possible
 */
public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    Net tinyYolo;
    Interpreter onOffModel;
    boolean isOpencvOn = false;
    boolean isModelLoaded = false;
    boolean showedMessage = false;
    SeekBar seekBar;
    int seekBarValue = 0;
    //Handles Android permission for eg Network
    private PermissionHandler permissionHandler;

    //Handles network camera operations
    private CameraHandler cameraHandler;

    private Identity connectedIdentity = null;
    private TextView connectionStatus;
    private TextView discoveryStatus;

    private ImageView msxImage;
    private ImageView photoImage;

    private LinkedBlockingQueue<FrameDataHolder> framesBuffer = new LinkedBlockingQueue(21);
    private UsbPermissionHandler usbPermissionHandler = new UsbPermissionHandler();

    /**
     * Show message on the screen
     */
    public interface ShowMessage {
        void show(String message);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ThermalLog.LogLevel enableLoggingInDebug = BuildConfig.DEBUG ? ThermalLog.LogLevel.DEBUG : ThermalLog.LogLevel.NONE;

        //ThermalSdkAndroid has to be initiated from a Activity with the Application Context to prevent leaking Context,
        // and before ANY using any ThermalSdkAndroid functions
        //ThermalLog will show log from the Thermal SDK in standards android log framework
        ThermalSdkAndroid.init(getApplicationContext(), enableLoggingInDebug);

        permissionHandler = new PermissionHandler(showMessage, MainActivity.this);

        cameraHandler = new CameraHandler();

        setupViews();

        showSDKversion(ThermalSdkAndroid.getVersion());
        seekBar=(SeekBar) findViewById(R.id.seekBar);
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {

            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                seekBarValue = progress;
                TextView displayVal = (TextView)findViewById(R.id.confThresold);
                displayVal.setText(progress+"%");
            }
            public void onStartTrackingTouch(SeekBar seekBar) {}

            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
    }

    public void startDiscovery(View view) {
        startDiscovery();
    }

    public void stopDiscovery(View view) {
        stopDiscovery();
    }


    public void connectFlirOne(View view) {
        connect(cameraHandler.getFlirOne());
    }

    public void connectSimulatorOne(View view) {
        connect(cameraHandler.getCppEmulator());
    }

    public void connectSimulatorTwo(View view) {
        connect(cameraHandler.getFlirOneEmulator());
    }

    public void disconnect(View view) {
        disconnect();
    }

    /**
     * Handle Android permission request response for Bluetooth permissions
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        Log.d(TAG, "onRequestPermissionsResult() called with: requestCode = [" + requestCode + "], permissions = [" + permissions + "], grantResults = [" + grantResults + "]");
        permissionHandler.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    /**
     * Connect to a Camera
     */
    private void connect(Identity identity) {
        //We don't have to stop a discovery but it's nice to do if we have found the camera that we are looking for
        cameraHandler.stopDiscovery(discoveryStatusListener);

        if (connectedIdentity != null) {
            Log.d(TAG, "connect(), in *this* code sample we only support one camera connection at the time");
            showMessage.show("connect(), in *this* code sample we only support one camera connection at the time");
            return;
        }

        if (identity == null) {
            Log.d(TAG, "connect(), can't connect, no camera available");
            showMessage.show("connect(), can't connect, no camera available");
            return;
        }

        connectedIdentity = identity;

        updateConnectionText(identity, "CONNECTING");
        //IF your using "USB_DEVICE_ATTACHED" and "usb-device vendor-id" in the Android Manifest
        // you don't need to request permission, see documentation for more information
        if (UsbPermissionHandler.isFlirOne(identity)) {
            usbPermissionHandler.requestFlirOnePermisson(identity, this, permissionListener);
        } else {
            doConnect(identity);
        }

    }

    private UsbPermissionHandler.UsbPermissionListener permissionListener = new UsbPermissionHandler.UsbPermissionListener() {
        @Override
        public void permissionGranted(Identity identity) {
            doConnect(identity);
        }

        @Override
        public void permissionDenied(Identity identity) {
            MainActivity.this.showMessage.show("Permission was denied for identity ");
        }

        @Override
        public void error(UsbPermissionHandler.UsbPermissionListener.ErrorType errorType, final Identity identity) {
            MainActivity.this.showMessage.show("Error when asking for permission for FLIR ONE, error:"+errorType+ " identity:" +identity);
        }
    };

    private void doConnect(Identity identity) {
        new Thread(() -> {
            try {
                cameraHandler.connect(identity, connectionStatusListener);
                runOnUiThread(() -> {
                    updateConnectionText(identity, "CONNECTED");
                    cameraHandler.startStream(streamDataListener);
                });
            } catch (IOException e) {
                runOnUiThread(() -> {
                    Log.d(TAG, "Could not connect: " + e);
                    updateConnectionText(identity, "DISCONNECTED");
                });
            }
        }).start();
    }

    /**
     * Disconnect to a camera
     */
    private void disconnect() {
        updateConnectionText(connectedIdentity, "DISCONNECTING");
        connectedIdentity = null;
        Log.d(TAG, "disconnect() called with: connectedIdentity = [" + connectedIdentity + "]");
        new Thread(() -> {
            cameraHandler.disconnect();
            runOnUiThread(() -> {
                    updateConnectionText(null, "DISCONNECTED");
            });
        }).start();
    }

    /**
     * Update the UI text for connection status
     */
    private void updateConnectionText(Identity identity, String status) {
        String deviceId = identity != null ? identity.deviceId : "";
        connectionStatus.setText(getString(R.string.connection_status_text, deviceId + " " + status));
    }

    /**
     * Start camera discovery
     */
    private void startDiscovery() {
        cameraHandler.startDiscovery(cameraDiscoveryListener, discoveryStatusListener);
    }

    /**
     * Stop camera discovery
     */
    private void stopDiscovery() {
        cameraHandler.stopDiscovery(discoveryStatusListener);
    }

    /**
     * Callback for discovery status, using it to update UI
     */
    private CameraHandler.DiscoveryStatus discoveryStatusListener = new CameraHandler.DiscoveryStatus() {
        @Override
        public void started() {
            discoveryStatus.setText(getString(R.string.connection_status_text, "discovering"));
        }

        @Override
        public void stopped() {
            discoveryStatus.setText(getString(R.string.connection_status_text, "not discovering"));
        }
    };

    /**
     * Camera connecting state thermalImageStreamListener, keeps track of if the camera is connected or not
     * <p>
     * Note that callbacks are received on a non-ui thread so have to eg use {@link #runOnUiThread(Runnable)} to interact view UI components
     */
    private ConnectionStatusListener connectionStatusListener = new ConnectionStatusListener() {
        @Override
        public void onDisconnected(@org.jetbrains.annotations.Nullable ErrorCode errorCode) {
            Log.d(TAG, "onDisconnected errorCode:" + errorCode);

            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    updateConnectionText(connectedIdentity, "DISCONNECTED");
                }
            });
        }
    };

    private final CameraHandler.StreamDataListener streamDataListener = new CameraHandler.StreamDataListener() {

        @Override
        public void images(FrameDataHolder dataHolder) {

            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    msxImage.setImageBitmap(dataHolder.msxBitmap);
                    photoImage.setImageBitmap(dataHolder.dcBitmap);
                }
            });
        }
        @RequiresApi(api = Build.VERSION_CODES.O)
        @Override
        public void images(Bitmap msxBitmap, Bitmap dcBitmap) {
            if(OpenCVLoader.initDebug())
                isOpencvOn = true;
            if(isOpencvOn) {
                if(!isModelLoaded)
                {
                    String tinyYoloCfg = Environment.getExternalStorageDirectory() + "/Documents/yolov3tiny-custom.cfg" ;
                    String tinyYoloWeights = Environment.getExternalStorageDirectory() + "/Documents/yolov3tiny-custom_10000.weights";
                    tinyYolo = Dnn.readNetFromDarknet(tinyYoloCfg, tinyYoloWeights);
                    try{
                        AssetFileDescriptor fileDescriptor = getAssets().openFd("model.tflite");
                        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
                        FileChannel fileChannel = inputStream.getChannel();
                        long startOffset = fileDescriptor.getStartOffset();
                        long declaredLength = fileDescriptor.getDeclaredLength();
                        ByteBuffer tfLifeFile = fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
                        onOffModel = new Interpreter(tfLifeFile);
                    }catch (IOException e)
                    {
                        Log.e("Tag cannot load", e.toString());
//                        e.printStackTrace();
                    }
                    isModelLoaded = true;
                }
                Mat frameRGB = new Mat();
                Mat frameThermal = new Mat();
                Utils.bitmapToMat(dcBitmap, frameRGB);
                Utils.bitmapToMat(msxBitmap, frameThermal);
                //Preprocessing
                Imgproc.cvtColor(frameThermal, frameThermal, Imgproc.COLOR_RGBA2BGR);
                Imgproc.cvtColor(frameRGB, frameRGB, Imgproc.COLOR_RGBA2RGB);
                //Gamma Correction
//                for(int i = 0; i<frameRGB.height();i++)
//                    for(int j = 0; j<frameRGB.width();j++)
//                    {
//                        double [] pixelValue = frameRGB.get(i,j);
//                        for(int k = 0; k<frameRGB.channels(); k++)
//                            pixelValue[k] = Math.pow(pixelValue[k]/255.0, 0.5)*255;
//                        frameRGB.put(i, j, pixelValue);
//                    }
                // Resize the image 0.81%
                Imgproc.resize(frameThermal,frameThermal, new Size(frameRGB.width()*0.81, frameRGB.height()*0.81));
                // Pad the image and shift 180, 85
                Mat warpMat = new Mat( 2, 3, CvType.CV_64FC1 );
                warpMat.put(0 ,0, 1, 0, 120, 0, 1, 185);

                Imgproc.warpAffine(frameThermal, frameThermal, warpMat, new Size(frameRGB.width(), frameRGB.height()));
                Mat imageBlob = Dnn.blobFromImage(frameRGB, 0.00392, new Size(416,416),new Scalar(0, 0, 0),/*swapRB*/false, /*crop*/false);
                tinyYolo.setInput(imageBlob);
                java.util.List<Mat> result = new java.util.ArrayList<Mat>(2);
                List<String> outBlobNames = new java.util.ArrayList<>();
                outBlobNames.add(0, "yolo_16");
                outBlobNames.add(1, "yolo_23");
                tinyYolo.forward(result,outBlobNames);
                float confThreshold = (float)(seekBarValue/100.0);
                List<Integer> clsIds = new ArrayList<>();
                List<Float> confs = new ArrayList<>();
                List<Rect> rects = new ArrayList<>();

                for (int i = 0; i < result.size(); ++i)
                {
                    Mat level = result.get(i);
                    for (int j = 0; j < level.rows(); ++j)
                    {
                        Mat row = level.row(j);
                        Mat scores = row.colRange(5, level.cols());
                        Core.MinMaxLocResult mm = Core.minMaxLoc(scores);
                        float confidence = (float)mm.maxVal;
                        Point classIdPoint = mm.maxLoc;
                        if (confidence > confThreshold)
                        {
                            int centerX = (int)(row.get(0,0)[0] * frameRGB.cols());
                            int centerY = (int)(row.get(0,1)[0] * frameRGB.rows());
                            int width   = (int)(row.get(0,2)[0] * frameRGB.cols());
                            int height  = (int)(row.get(0,3)[0] * frameRGB.rows());
                            int left    = centerX - width  / 2;
                            int top     = centerY - height / 2;
                            int curClass = (int)classIdPoint.x;
                            left = left > 1080? 1080:left<0? 0:left;
                            top = top > 1440? 1440:top<0? 0:top;
                            width = width > 1440? 1440:width<0? 0:width;
                            height = height > 1440? 1440:height<0? 0:height;
                            width = width+left > 1080 ? 1080-left:width;
                            height = height+top > 1440 ? 1440-top:height;
                            confs.add((float)confidence);
                            Rect rectCrop = new Rect(left, top, width, height);
                            rects.add(rectCrop);

                            // Add to onOffModel
                            float [][] nameLabels = new float[1][5];
                            nameLabels[0][curClass] = 1;
                            Mat modelInput= frameThermal.submat(rectCrop);
                            Imgproc.resize(modelInput, modelInput, new Size(152,145));
                            float[][][][] inputFloats = new float[1][modelInput.height()][modelInput.width()][1];

                            for (int k = 0; k < modelInput.height(); k++)
                                for (int l = 0; l < modelInput.width(); l++)
                                {
                                    float red = (float) (((float)modelInput.get(k,l)[0])/255.0);
                                    float green = (float) (((float)modelInput.get(k,l)[1])/255.0);
                                    float blue = (float) (((float)modelInput.get(k,l)[2])/255.0);
                                    inputFloats[0][k][l][0] = (red+green+blue)/3;
                                }

                            Object [] finalInput = {nameLabels, inputFloats};
                            float[][] output = {{0}};
                            Map<Integer, Object> outputs = new HashMap<>();
                            outputs.put(0, output);
                            onOffModel.runForMultipleInputsOutputs(finalInput, outputs);
                            float [] [] finalAnswer = (float[][])outputs.get(0);
                            if (finalAnswer[0][0] > 0.5)
                                finalAnswer[0][0] = 1;
                            else
                                finalAnswer[0][0] = 0;
                            curClass += 5*finalAnswer[0][0];
                            clsIds.add(curClass);
                        }
                    }
                }
                ArrayList<Mat> bgrThermal = new ArrayList<Mat>();
                ArrayList<Mat> bgrColor = new ArrayList<Mat>();
                Core.split(frameThermal,bgrThermal);
                Core.split(frameRGB,bgrColor);
                bgrThermal.set(1, bgrColor.get(1));
                bgrThermal.set(2, bgrColor.get(2));
                Core.merge(bgrThermal, frameThermal);
                // Resize image back for debugging
                Imgproc.resize(frameThermal,frameThermal, new Size(480, 640));

                int ArrayLength = confs.size();
                if (ArrayLength>=1) {
                    // Apply non-maximum suppression procedure.
                    float nmsThresh = 0.2f;
                    MatOfFloat confidences = new MatOfFloat(Converters.vector_float_to_Mat(confs));
                    Rect[] boxesArray = rects.toArray(new Rect[0]);
                    MatOfRect boxes = new MatOfRect(boxesArray);
                    MatOfInt indices = new MatOfInt();
                    Dnn.NMSBoxes(boxes, confidences, confThreshold, nmsThresh, indices);
                    // Draw result boxes:
                    int[] ind = indices.toArray();
                    for (int i = 0; i < ind.length; ++i) {
                        int idx = ind[i];
                        Rect box = boxesArray[idx];
                        int idGuy = clsIds.get(idx);
                        float conf = confs.get(idx);
                        List<String> cocoNames = Arrays.asList("Mobile Off", "Laptop Off" , "Speaker Off", "Alexa Off", "Screen Off", "Mobile On", "Laptop On" , "Speaker On", "Alexa On", "Screen On");
                        int intConf = (int) (conf * 100);
                        Imgproc.putText(frameRGB,cocoNames.get(idGuy) + " " + intConf + "%",box.tl(), 0, 2, new Scalar(255,255,0),2);
                        Imgproc.rectangle(frameRGB, box.tl(), box.br(), new Scalar(255, 0, 0), 2);
                    }
                }
                Utils.matToBitmap(frameRGB, dcBitmap);
                try{
                    Utils.matToBitmap(frameThermal, msxBitmap);
                }
                catch (Exception e){
                    Log.e("TAG MatToBitmap Error", e.toString());
                }
            }
            else
                Log.e("Loading", "Not Loaded");
            try {
                framesBuffer.put(new FrameDataHolder(msxBitmap,dcBitmap));
            } catch (InterruptedException e) {
                //if interrupted while waiting for adding a new item in the queue
                Log.e(TAG,"images(), unable to add incoming images to frames buffer, exception:"+e);
            }

            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Log.d(TAG,"framebuffer size:"+framesBuffer.size());
                    FrameDataHolder poll = framesBuffer.poll();
                    msxImage.setImageBitmap(poll.msxBitmap);
                    photoImage.setImageBitmap(poll.dcBitmap);
                }
            });

        }
    };

    /**
     * Camera Discovery thermalImageStreamListener, is notified if a new camera was found during a active discovery phase
     * <p>
     * Note that callbacks are received on a non-ui thread so have to eg use {@link #runOnUiThread(Runnable)} to interact view UI components
     */
    private DiscoveryEventListener cameraDiscoveryListener = new DiscoveryEventListener() {
        @Override
        public void onCameraFound(Identity identity) {
            Log.d(TAG, "onCameraFound identity:" + identity);
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    cameraHandler.add(identity);
                }
            });
        }

        @Override
        public void onDiscoveryError(CommunicationInterface communicationInterface, ErrorCode errorCode) {
            Log.d(TAG, "onDiscoveryError communicationInterface:" + communicationInterface + " errorCode:" + errorCode);

            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    stopDiscovery();
                    MainActivity.this.showMessage.show("onDiscoveryError communicationInterface:" + communicationInterface + " errorCode:" + errorCode);
                }
            });
        }
    };

    private ShowMessage showMessage = new ShowMessage() {
        @Override
        public void show(String message) {
            Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show();
        }
    };

    private void showSDKversion(String version) {
        TextView sdkVersionTextView = findViewById(R.id.sdk_version);
        String sdkVersionText = getString(R.string.sdk_version_text, version);
        sdkVersionTextView.setText(sdkVersionText);
    }

    private void setupViews() {
        connectionStatus = findViewById(R.id.connection_status_text);
        discoveryStatus = findViewById(R.id.discovery_status);

        msxImage = findViewById(R.id.msx_image);
        photoImage = findViewById(R.id.photo_image);
    }

}
