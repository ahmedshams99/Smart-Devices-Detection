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

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
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
import org.opencv.core.MatOfRect;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.dnn.Dnn;
import org.opencv.dnn.Net;
import org.opencv.imgproc.Imgproc;
import org.opencv.utils.Converters;
import org.opencv.imgcodecs.*;
import org.tensorflow.lite.Interpreter;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;


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

    private static final String TAG = "TAAAG";
    Net yoloRegular;
    Net yoloBlack;
    Net tinyYolo;
    Interpreter onOffModel;
    boolean isOpencvOn = false;
    boolean isModelLoaded = false;
    SeekBar seekBar;
    int seekBarValue = 0;
    boolean segmentationOn = false;
    boolean boundingBoxOn = false;
    boolean labelsOn = false;
    boolean sphereOn = false;
    boolean shapeOn = false;
    boolean discovering = false;
    double[] gammaMapping = new double [] {0.0, 15.968719422671313, 22.58317958127243, 27.65863337187866, 31.937438845342626, 35.70714214271425, 39.1152144312159, 42.24926034855521, 45.16635916254486, 47.90615826801393, 50.49752469181039, 52.96225070746144, 55.31726674375732, 57.57603668193913, 59.74947698515862, 61.84658438426491, 63.87487769068525, 65.84071688552608, 67.74953874381728, 69.60603422117941, 71.4142842854285, 73.1778655059028, 74.89993324429602, 76.58328799418317, 78.2304288624318, 79.84359711335655, 81.42481194328913, 82.97590011563598, 84.49852069711042, 85.99418584997477, 87.46427842267951, 88.91006692158093, 90.33271832508971, 91.73330910852393, 93.11283477587823, 94.47221813845593, 95.81231653602786, 97.13392816107047, 98.4377976185977, 99.72462083156798, 100.99504938362078, 102.24969437607136, 103.48912986396203, 104.71389592599446, 105.92450141492289, 107.12142642814275, 108.30512453249847, 109.47602477255009, 110.63453348751464, 111.78103595869919, 112.91589790636215, 114.03946685248927, 115.15207336387826, 116.25403218813531, 117.34564329364768, 118.42719282327012, 119.49895397031725, 120.56118778446073, 121.61414391426682, 122.65806129235861, 123.69316876852982, 124.71968569556292, 125.73782247199925, 126.74778104566565, 127.7497553813705, 128.74393189583733, 129.7304898626379, 130.70960178961604, 131.68143377105216, 132.64614581660484, 133.6038921588739, 134.5548215412588, 135.49907748763457, 136.43679855522848, 137.3681185719598, 138.2931668593933, 139.21206844235883, 140.12494424619766, 141.03191128251788, 141.93308282426617, 142.828568570857, 143.71847480404182, 144.60290453514412, 145.4819576442385, 146.3557310118056, 147.22431864335456, 148.08781178746617, 148.94629904767692, 149.79986648859204, 150.648597736587, 151.49257407543118, 152.33187453714342, 153.16657598836633, 153.99675321252718, 154.82247898803328, 155.6438241627338, 156.4608577248636, 157.27364687066932, 158.082257068907, 158.8867521223843, 159.6871942267131, 160.48364402642406, 161.27616066858735, 162.06480185407318, 162.84962388657826, 163.6306817195357, 164.40802900101932, 165.1817181167456, 165.95180023127196, 166.7183253274816, 167.48134224444226, 168.24089871371942, 168.99704139422084, 169.7498159056439, 170.49926686059385, 171.24543789543708, 171.98837169994954, 172.72811004581737, 173.4646938140439, 174.19816302131315, 174.92855684535903, 175.65591364938444, 176.38027100557477, 177.1016657177453, 177.82013384316187, 178.53571071357126, 179.2484309554759, 179.95832850968583, 180.66543665017943, 181.3697880023021, 182.07141456033125, 182.7703477044348, 183.46661821704785, 184.1602562986922, 184.8512915832616, 185.53975315279473, 186.22566955175645, 186.90906880084765, 187.5899784103618, 188.26842539310726, 188.94443627691186, 189.61803711672582, 190.28925350633966, 190.95811058973118, 191.62463307205573, 192.2888452302941, 192.95077092357005, 193.61043360315063, 194.26785632214094, 194.92306174488436, 195.57607215607945, 196.22690946962396, 196.8755952371954, 197.52215065657828, 198.1665965797465, 198.80895352071042, 199.44924166313595, 200.08748086774446, 200.72369067950103, 201.357890334598, 201.99009876724156, 202.62033461624722, 203.24861623145185, 203.87496167994735, 204.49938875214272, 205.12191496766016, 205.74255758107026, 206.36133358747225, 206.97825972792407, 207.59335249472707, 208.20662813657015, 208.81810266353824, 209.42779185198893, 210.0357112493016, 210.64187617850348, 211.24630174277607, 211.84900282984577, 212.44999411626256, 213.04929007157006, 213.6469049623701, 214.2428528562855, 214.83714762582377, 215.42980295214494, 216.0208323287363, 216.61024906499694, 217.19806628973473, 217.78429695457842, 218.36895383730717, 218.95204954510018, 219.53359651770842, 220.1136070305514, 220.6920931977401, 221.2690669750293, 221.84454016270044, 222.41852440837744, 222.99103120977756, 223.56207191739838, 224.13165773714343, 224.69979973288804, 225.26650882898682, 225.8317958127243, 226.39567133671085, 226.95814592122485, 227.51922995650278, 228.07893370497854, 228.63726730347352, 229.1942407653386, 229.7498639825495, 230.30414672775652, 230.85709865628996, 231.40872930812267, 231.95904810979027, 232.50806437627062, 233.05578731282344, 233.6022260167912, 234.14738947936192, 234.69128658729537, 235.23392612461325, 235.77531677425438, 236.31546711969574, 236.85438564654024, 237.392080744072, 237.92856070678022, 238.46383373585186, 238.9979079406345, 239.5307913400697, 240.06249186409775, 240.59301735503462, 241.12237556892146, 241.65057417684736, 242.17762076624672, 242.70352284217054, 243.22828782853364, 243.75192306933704, 244.27443582986737, 244.79583329787295, 245.31612258471722, 245.83531072651058, 246.35340468522045, 246.87041134976056, 247.38633753705963, 247.90118999310994, 248.41497539399674, 248.92770034690795, 249.43937139112583, 249.94999499899976, 250.45957757690164, 250.9681254661635, 251.4756449439985, 251.98214222440447, 252.48762345905195, 252.9920947381558, 253.4955620913313, 253.99803148843498, 254.4995088403905, 255.0};
    //Handles Android permission for eg Network
    private PermissionHandler permissionHandler;

    //Handles network camera operations
    private CameraHandler cameraHandler;

    private Identity connectedIdentity = null;

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
    public boolean onCreateOptionsMenu(Menu menu)
    {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.settings_menu, menu);
        return true;
    }
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.item1:
                boundingBoxOn = !boundingBoxOn;
                item.setChecked(boundingBoxOn);
                return true;
            case R.id.item2:
                labelsOn = !labelsOn;
                item.setChecked(labelsOn);
                return true;
            case R.id.item3:
                sphereOn = !sphereOn;
                item.setChecked(sphereOn);
                return true;
            case R.id.item4:
                shapeOn = !shapeOn;
                item.setChecked(shapeOn);
                return true;
            case R.id.item5:
                segmentationOn = !segmentationOn;
                item.setChecked(segmentationOn);
                return true;
            case R.id.itemConnect:
                connectFlirOne();
                return true;
            case R.id.itemDisconnect:
                disconnectAll();
                return true;
            case R.id.itemS:
                if(tinyYolo==yoloRegular)
                {
                    tinyYolo = yoloBlack;
                    MainActivity.this.showMessage.show("Changed to Panic Mode");
                }
                else {
                    tinyYolo = yoloRegular;
                    MainActivity.this.showMessage.show("Changed to Regular Mode");
                }
                return true;
            case R.id.itemLog:
                String currentDateTimeString = new SimpleDateFormat("yyyy/MM/dd/HH/mm/ss/SSS").format(new Date());
                String curVisualisation = boundingBoxOn? "Bounding Box":labelsOn? "Labels":sphereOn? "Sphere":shapeOn? "Shapes":segmentationOn? "Segmentation":"None";
                String data = readFromFile(this)+curVisualisation+"/"+currentDateTimeString+", ";
                writeToFile(data, this);
//                writeToFile("", this);
                Log.e("TAAAG", readFromFile(this));
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
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

//        showSDKversion(ThermalSdkAndroid.getVersion());
        seekBar=(SeekBar) findViewById(R.id.seekBar);
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {

            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                seekBarValue = progress;
                TextView displayVal = (TextView)findViewById(R.id.confThresold);
                String text = progress+"%";
                displayVal.setText(text);
            }
            public void onStartTrackingTouch(SeekBar seekBar) {}

            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        disconnectAll();
        startDiscovery();
    }

    public void connectFlirOne() {
        startDiscovery();
        connect(cameraHandler.getFlirOne());
    }
    public void disconnectAll() {
        stopDiscovery();
        disconnect();
    }

    /**
     * Handle Android permission request response for Bluetooth permissions
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, @NotNull String[] permissions, @NotNull int[] grantResults) {
        Log.d(TAG, "onRequestPermissionsResult() called with: requestCode = [" + requestCode + "], permissions = [" + Arrays.toString(permissions) + "], grantResults = [" + Arrays.toString(grantResults) + "]");
        permissionHandler.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    /**
     * Connect to a Camera
     */
    public void connect(Identity identity) {
        if(identity == null)
        {
            MainActivity.this.showMessage.show("Camera not connected / Not turned on.");
            return;
        }
        //We don't have to stop a discovery but it's nice to do if we have found the camera that we are looking for
        cameraHandler.stopDiscovery(discoveryStatusListener);
        if(connectedIdentity!=null)
        {
            MainActivity.this.showMessage.show("Already Connected");
            return;
        }
        connectedIdentity = identity;
//        updateConnectionText(identity, "CONNECTING");
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
        public void permissionGranted(@NotNull Identity identity) {
            doConnect(identity);
        }

        @Override
        public void permissionDenied(@NotNull Identity identity) {
            MainActivity.this.showMessage.show("Permission was denied for camera.");
        }

        @Override
        public void error(UsbPermissionHandler.UsbPermissionListener.ErrorType errorType, final Identity identity) {
//            MainActivity.this.showMessage.show("Error when asking for permission for FLIR ONE, error:"+errorType+ " identity:" +identity);
            MainActivity.this.showMessage.show("Camera is not connected/ Not turned on.");
        }
    };
    private void doConnect(Identity identity) {
        MainActivity.this.showMessage.show("Connecting...");
        new Thread(() -> {
            try {
                cameraHandler.connect(identity, connectionStatusListener);
                runOnUiThread(() -> {
//                    updateConnectionText(identity, "CONNECTED");
                    cameraHandler.startStream(streamDataListener);
                });
            } catch (IOException e) {
                runOnUiThread(() -> {
                    MainActivity.this.showMessage.show("Please unplug the camera the plug it in again.");
                    disconnectAll();
//                    updateConnectionText(identity, "DISCONNECTED");
                });
            }
        }).start();
    }

    /**
     * Disconnect to a camera
     */
    private void disconnect() {
//        updateConnectionText(connectedIdentity, "DISCONNECTING");
        Log.d("TAAAG", "disconnect() called with: connectedIdentity = [" + connectedIdentity + "]");
        connectedIdentity = null;
        new Thread(() -> {
            cameraHandler.disconnect();
            runOnUiThread(() -> {
//                    updateConnectionText(null, "DISCONNECTED");
            });
        }).start();
    }

//    /**
//     * Update the UI text for connection status
//     */
//    private void updateConnectionText(Identity identity, String status) {
//        String deviceId = identity != null ? identity.deviceId : "";
//        connectionStatus.setText(getString(R.string.connection_status_text, deviceId + " " + status));
//    }

    /**
     * Start camera discovery
     */
    private void startDiscovery() {
        if(discovering || connectedIdentity!=null) return;
        cameraHandler.startDiscovery(cameraDiscoveryListener, discoveryStatusListener);
        discovering = true;
    }

    /**
     * Stop camera discovery
     */
    private void stopDiscovery() {
        if(!discovering) return;
        cameraHandler.stopDiscovery(discoveryStatusListener);
        discovering = false;
    }

    /**
     * Callback for discovery status, using it to update UI
     */
    private CameraHandler.DiscoveryStatus discoveryStatusListener = new CameraHandler.DiscoveryStatus() {
        @Override
        public void started() {
        }

        @Override
        public void stopped() {
        }
    };

    /**
     * Camera connecting state thermalImageStreamListener, keeps track of if the camera is connected or not
     * <p>
     * Note that callbacks are received on a non-ui thread so have to eg use {@link #runOnUiThread(Runnable)} to interact view UI components
     */
    private ConnectionStatusListener connectionStatusListener = errorCode -> {
        Log.d(TAG, "onDisconnected errorCode:" + errorCode);

        runOnUiThread(() -> {
//                    updateConnectionText(connectedIdentity, "DISCONNECTED");
        });
    };

    private final CameraHandler.StreamDataListener streamDataListener = new CameraHandler.StreamDataListener() {

        @Override
        public void images(FrameDataHolder dataHolder) {

            runOnUiThread(() -> {
                msxImage.setImageBitmap(dataHolder.msxBitmap);
                photoImage.setImageBitmap(dataHolder.dcBitmap);
            });
        }
        @SuppressLint("SetTextI18n")
        @RequiresApi(api = Build.VERSION_CODES.O)
        @Override
        public void images(Bitmap msxBitmap, Bitmap dcBitmap) {
            long startTime = System.nanoTime();
            if(OpenCVLoader.initDebug())
                isOpencvOn = true;
            if(isOpencvOn) {
                if(!isModelLoaded)
                {
//                    Net ans = new DetectionModel( "yolov4.weights", "yolov4.cfg");
//                    ans.setInput();
//                    ans.forward();
                    String tinyYoloCfg = Environment.getExternalStorageDirectory() + "/Documents/yolov3tiny-custom.cfg" ;
//                    String yolov4Cfg = Environment.getExternalStorageDirectory() + "/Documents/yolov4tiny-custom.cfg" ;
                    String tinyYoloWeightsBlack = Environment.getExternalStorageDirectory() + "/Documents/yolov3tiny-custom_final_Black.weights";
                    String tinyYoloWeightsRegular = Environment.getExternalStorageDirectory() + "/Documents/yolov3tiny-custom_final_Extra.weights";

//                    String tinyYolo4WeightsRegular = Environment.getExternalStorageDirectory() + "/Documents/custom-yolov4-tiny-detector_best.weights";
//                    try{
////                        DetectionModel d = new DetectionModel(tinyYolo4WeightsRegular,yolov4Cfg);
//                        Net ass= Dnn.readNet(tinyYolo4WeightsRegular,yolov4Cfg);
//                        Log.e("TAAAG","SUCCESS");
//                    }
//                    catch (Exception e){
//                        Log.e("TAAAG", "FAILED");
//                        Log.e("TAAAG", e.toString());
//                    }
                    yoloBlack = Dnn.readNetFromDarknet(tinyYoloCfg, tinyYoloWeightsBlack);
                    yoloRegular = Dnn.readNetFromDarknet(tinyYoloCfg, tinyYoloWeightsRegular);
//                    try{
//
//                        yoloRegular = Dnn.readNetFromDarknet(yolov4Cfg, tinyYolo4WeightsRegular);
//                        Log.e("TAAAG","Success");
//                    }catch (Exception e) {
//                        Log.e("TAAAG", "Failed");
//                        Log.e("TAAAG", e.toString());
//                    }
                    tinyYolo = yoloRegular;
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
                        Log.e(TAG, e.toString());
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
//                Imgproc.resize(frameRGB,frameRGB, new Size(frameRGB.width()/4, frameRGB.height()/4));
//                for(int i = 0; i<frameRGB.height();i++)
//                    for(int j = 0; j<frameRGB.width();j++)
//                    {
//                        double [] pixelValue = frameRGB.get(i,j);
//                        for(int k = 0; k<frameRGB.channels(); k++)
//                            pixelValue[k] = gammaMapping[(int)pixelValue[k]];
//                            //                            pixelValue[k] = Math.pow(pixelValue[k]/255.0, 0.5)*255;
//                        frameRGB.put(i, j, pixelValue);
//                    }
//                Imgproc.resize(frameRGB,frameRGB, new Size(frameRGB.width()*4, frameRGB.height()*4));
                // Resize the image 0.81%
                Imgproc.resize(frameThermal,frameThermal, new Size(frameRGB.width()*0.81, frameRGB.height()*0.81));
                // Pad the image and shift 180, 85
                Mat warpMat = new Mat( 2, 3, CvType.CV_64FC1 );
                warpMat.put(0 ,0, 1, 0, 120, 0, 1, 185);
                Imgproc.warpAffine(frameThermal, frameThermal, warpMat, new Size(frameRGB.width(), frameRGB.height()));
                //Start of new Model
                ArrayList<Mat> newCombined = new ArrayList<>();
                ArrayList<Mat> rgbSplit = new ArrayList<>();
                Core.split(frameRGB, rgbSplit);
                Mat newG = rgbSplit.get(1).clone();
                Mat newB = rgbSplit.get(2).clone();
                Core.addWeighted(rgbSplit.get(0), 0.33, newG, 0.66,0, newG);
                Core.addWeighted(rgbSplit.get(0), 0.33, newB, 0.66,0, newB);
                Mat oneChannelThermal = newG.clone();
                Imgproc.cvtColor(frameThermal, oneChannelThermal, Imgproc.COLOR_RGB2GRAY);
                newCombined.add(oneChannelThermal); newCombined.add(newG); newCombined.add(newB);
                Mat combinedMat = frameRGB.clone();
                Core.merge(newCombined,combinedMat);
                //End of new Model
                Mat imageBlob = Dnn.blobFromImage(combinedMat, 0.00392, new Size(416,416),new Scalar(0, 0, 0),/*swapRB*/false, /*crop*/false);
                tinyYolo.setInput(imageBlob);
                java.util.List<Mat> result = new java.util.ArrayList<>(2);
                List<String> outBlobNames = new java.util.ArrayList<>();
                outBlobNames.add(0, "yolo_16");
                outBlobNames.add(1, "yolo_23");
//                outBlobNames.add(0,"yolo_30");
//                outBlobNames.add(0,"yolo_37");
                List<String> names = tinyYolo.getLayerNames();
                tinyYolo.forward(result,outBlobNames);
                float confThreshold = (float)(seekBarValue/100.0);
                List<Integer> clsIds = new ArrayList<>();
                List<Float> confs = new ArrayList<>();
                List<Rect> rects = new ArrayList<>();
                ArrayList<Mat> bgrThermal = new ArrayList<>();
                Core.split(frameThermal,bgrThermal);
                /////////////////////////////Create Segmentation///////////////////////////////////
                Mat destination = null;
                ArrayList<Mat> bgrSegm = null;
                if(segmentationOn)
                {
                    bgrSegm = new ArrayList<>();
                    Core.split(frameRGB.clone(), bgrSegm);
                    destination = bgrThermal.get(0).clone();
                    Scalar s = Core.mean(bgrThermal.get(0));
                    double thresh = (s.val[0]*1080*1440*1)/Core.countNonZero(bgrThermal.get(0));
                    Imgproc.threshold(bgrThermal.get(0), destination, thresh, 255, 3);
                }
                /////////////////////////////Create Segmentation///////////////////////////////////
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
                            left = left > 1080? 1080: Math.max(left, 0);
                            top = top > 1440? 1440: Math.max(top, 0);
                            width = width > 1440? 1440: Math.max(width, 0);
                            height = height > 1440? 1440: Math.max(height, 0);
                            width = width+left > 1080 ? 1080-left:width;
                            height = height+top > 1440 ? 1440-top:height;
                            confs.add((float)confidence);
                            Rect rectCrop = new Rect(left, top, width, height);
                            rects.add(rectCrop);

                            // Add to onOffModel
                            float [][] nameLabels = new float[1][5];
                            nameLabels[0][curClass] = 1;
                            //New representation//////////////////////////////////////////
                            if(segmentationOn)
                            {
                                Mat segInput= destination.submat(rectCrop);
                                Mat curBoxWarped = Mat.zeros(frameRGB.size(), frameRGB.type());
                                Mat warpMatTmp = new Mat( 2, 3, CvType.CV_64FC1 );
                                warpMatTmp.put(0 ,0, 1, 0, rectCrop.x, 0, 1, rectCrop.y);
                                Imgproc.warpAffine(segInput, curBoxWarped, warpMatTmp, new Size(frameRGB.width(), frameRGB.height()));

                                Mat tempNewRed = bgrSegm.get(0);
                                Core.addWeighted(bgrSegm.get(0),1, curBoxWarped,1,0, tempNewRed);
                                bgrSegm.set(0, tempNewRed);
                            }
                            //End of new rep.//////////////////////////////////////////////
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

//                ArrayList<Mat> bgrThermal = new ArrayList<Mat>(); Already Calculated
                ArrayList<Mat> bgrColor = new ArrayList<>();
//                Core.split(frameThermal,bgrThermal); Already Calculated
                Core.split(frameRGB,bgrColor);
                bgrThermal.set(1, bgrColor.get(1));
                bgrThermal.set(2, bgrColor.get(2));
                Core.merge(bgrThermal, frameThermal);
                // Resize image back for debugging
                Imgproc.resize(frameThermal,frameThermal, new Size(480, 640));
                if(segmentationOn)
                    Core.merge(bgrSegm, frameRGB);


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
                    for (int idx : ind) {
                        Rect box = boxesArray[idx];
                        int idGuy = clsIds.get(idx);
                        float conf = confs.get(idx);
                        List<String> cocoNames = Arrays.asList("Mobile Off", "Laptop Off", "Speaker Off", "Alexa Off", "Screen Off", "Mobile On", "Laptop On", "Speaker On", "Alexa On", "Screen On");
                        int intConf = (int) (conf * 100);
                        //Text
                        if (labelsOn)
                        {
                            Mat frameCopy = frameRGB.clone();
                            int boxWidth = 200;
                            int boxHeight = 400;
                            Point top = new Point((-boxWidth/2)+(box.tl().x+box.br().x)/2, box.tl().y);
                            Point bottom = new Point((boxWidth/2)+(box.tl().x+box.br().x)/2, box.tl().y-boxHeight);
                            Imgproc.rectangle(frameCopy, top, bottom, new Scalar(150, 150, 150), -1);

                            Core.addWeighted(frameRGB, 0.5, frameCopy, 0.5, 0, frameRGB);
//                            Imgproc.putText(frameRGB, cocoNames.get(idGuy) + " " + intConf + "%", box.tl(), 0, 2, new Scalar(255, 0, 0), 4);
                            int thickness = 3;
                            int fontScale = 1;
                            Scalar textColor = new Scalar(255,255,255);
                            Imgproc.putText(frameRGB, "Device:", new Point((-boxWidth/2)+(box.tl().x+box.br().x)/2, box.tl().y - boxHeight + 50), 0, fontScale, textColor, thickness);
                            Imgproc.putText(frameRGB, cocoNames.get(idGuy), new Point((-boxWidth/2)+(box.tl().x+box.br().x)/2, box.tl().y - boxHeight + 100), 0, fontScale, textColor, thickness);
                            Imgproc.putText(frameRGB, "Tracking:", new Point((-boxWidth/2)+(box.tl().x+box.br().x)/2, box.tl().y - boxHeight + 200), 0, fontScale, textColor, thickness);
                            Imgproc.putText(frameRGB, "Microphone", new Point((-boxWidth/2)+(box.tl().x+box.br().x)/2, box.tl().y - boxHeight +250), 0, fontScale, textColor, thickness);
                            if(!(idGuy == 2 || idGuy == 3 || idGuy == 7 || idGuy == 8))
                                Imgproc.putText(frameRGB, "& Camera", new Point((-boxWidth/2)+(box.tl().x+box.br().x)/2, box.tl().y - boxHeight +300), 0, fontScale, textColor, thickness);;
                            Imgproc.putText(frameRGB, "V", new Point((box.tl().x+box.br().x)/2, box.tl().y - boxHeight +380), 0, fontScale, textColor, thickness);
                        }
                        if (sphereOn) {
                            Mat ellipse = Imgcodecs.imread(Environment.getExternalStorageDirectory() + "/Documents/sphere.png");
                            double biggerDim = Math.max(box.width, box.height) * 1.3;
                            Imgproc.resize(ellipse, ellipse, new Size(biggerDim, biggerDim));
                            Mat warpMat2 = new Mat(2, 3, CvType.CV_64FC1);
                            warpMat2.put(0, 0, 1, 0, box.x - biggerDim * 0.1, 0, 1, box.y - biggerDim * 0.25);
                            Imgproc.warpAffine(ellipse, ellipse, warpMat2, new Size(frameRGB.width(), frameRGB.height()));
                            Core.addWeighted(frameRGB, 1, ellipse, 0.5, 0, frameRGB);
                        }

                        if (shapeOn) {
                            String name = (idGuy == 2 || idGuy == 3 || idGuy == 7 || idGuy == 8) ? "mic.png" : "micAndCam.png";
                            Mat shape = Imgcodecs.imread(Environment.getExternalStorageDirectory() + "/Documents/" + name);
                            double biggerDim = Math.max(box.width, box.height) * 0.3;
                            Imgproc.resize(shape, shape, new Size(biggerDim, biggerDim));
                            Mat warpMat3 = new Mat(2, 3, CvType.CV_64FC1);
                            warpMat3.put(0, 0, 1, 0, box.x + biggerDim * 1.2, 0, 1, box.y + biggerDim * 0.8);
                            Imgproc.warpAffine(shape, shape, warpMat3, new Size(frameRGB.width(), frameRGB.height()));
                            Core.addWeighted(frameRGB, 1, shape, 1, 0, frameRGB);
                        }

                        if (boundingBoxOn)
                            Imgproc.rectangle(frameRGB, box.tl(), box.br(), new Scalar(255, 0, 0), 4);
                        //Bounding box
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
            double difference = 1000000000.0/(System.nanoTime() - startTime);
            try{
                TextView frames = (TextView)findViewById(R.id.fps);
                frames.setText("FPS: "+difference);
            }
            catch (Exception e)
            {
                Log.e("TAAAG", "FPS: "+difference);
                Log.e("TAAAG", e.toString());
            }
            try {
                framesBuffer.put(new FrameDataHolder(msxBitmap,dcBitmap));
            } catch (InterruptedException e) {
                //if interrupted while waiting for adding a new item in the queue
                Log.e(TAG,"images(), unable to add incoming images to frames buffer, exception:"+e);
            }

            runOnUiThread(() -> {
                Log.d(TAG,"framebuffer size:"+framesBuffer.size());
                FrameDataHolder poll = framesBuffer.poll();
                msxImage.setImageBitmap(poll.msxBitmap);
                photoImage.setImageBitmap(poll.dcBitmap);
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
            runOnUiThread(() -> {
                cameraHandler.add(identity);
                if(identity.deviceId.equals("FLIR ONE Camera"))
                    MainActivity.this.showMessage.show("Camera is ready to connect.");
            });
        }

        @Override
        public void onDiscoveryError(CommunicationInterface communicationInterface, ErrorCode errorCode) {
//            Log.d(TAG, "onDiscoveryError communicationInterface:" + communicationInterface + " errorCode:" + errorCode);
            runOnUiThread(() -> {
                Log.e("TAAAG", "DISCOVERY ERROR");
            });
        }
    };

    private ShowMessage showMessage = message -> Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show();

//    private void showSDKversion(String version) {
//        TextView sdkVersionTextView = findViewById(R.id.sdk_version);
//        String sdkVersionText = getString(R.string.sdk_version_text, version);
//        sdkVersionTextView.setText(sdkVersionText);
//    }

    private void setupViews() {
        msxImage = findViewById(R.id.msx_image);
        photoImage = findViewById(R.id.photo_image);
    }

    @Override
    protected void onDestroy() {
        disconnectAll();
        super.onDestroy();
    }

    private void writeToFile(String data,Context context) {
        try {
            OutputStreamWriter outputStreamWriter = new OutputStreamWriter(context.openFileOutput("myLogs.txt", Context.MODE_PRIVATE));
            outputStreamWriter.write(data);
            outputStreamWriter.close();
        }
        catch (IOException e) {
            Log.e("Exception", "File write failed: " + e.toString());
        }
    }

    private String readFromFile(Context context) {

        String ret = "";

        try {
            InputStream inputStream = context.openFileInput("myLogs.txt");

            if ( inputStream != null ) {
                InputStreamReader inputStreamReader = new InputStreamReader(inputStream);
                BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
                String receiveString = "";
                StringBuilder stringBuilder = new StringBuilder();

                while ( (receiveString = bufferedReader.readLine()) != null ) {
                    stringBuilder.append("\n").append(receiveString);
                }

                inputStream.close();
                ret = stringBuilder.toString();
            }
        }
        catch (FileNotFoundException e) {
            Log.e("login activity", "File not found: " + e.toString());
        } catch (IOException e) {
            Log.e("login activity", "Can not read file: " + e.toString());
        }

        return ret;
    }
}

