package com.flir.flironeexampleapplication;

import com.flir.flironeexampleapplication.util.SystemUiHider;

import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.PorterDuff;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.util.Log;
import android.content.Context;
import android.app.Activity;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.view.MotionEvent;
import android.view.OrientationEventListener;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.ToggleButton;

import com.flir.flironesdk.Device;
import com.flir.flironesdk.Frame;
import com.flir.flironesdk.FrameProcessor;
import com.flir.flironesdk.RenderedImage;
import com.flir.flironesdk.LoadedFrame;
import com.flir.flironesdk.SimulatedDevice;
import com.smartcam.webcam.video.JpegHandler;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Map;

/**
 * An example activity and delegate for FLIR One image streaming and device interaction.
 * Based on an example full-screen activity that shows and hides the system UI (i.e.
 * status bar and navigation/system bar) with user interaction.
 *
 * @see SystemUiHider
 * @see com.flir.flironesdk.Device.Delegate
 * @see com.flir.flironesdk.FrameProcessor.Delegate
 * @see com.flir.flironesdk.Device.StreamDelegate
 * @see com.flir.flironesdk.Device.PowerUpdateDelegate
 *
 * @author based on FLIR ONE SDK.
 * SmartCam support, temp and SharedPreferences by sven@killig.de
 */
public class PreviewActivity extends Activity implements Device.Delegate, FrameProcessor.Delegate, Device.StreamDelegate, Device.PowerUpdateDelegate{
    ImageView thermalImageView;
    private volatile boolean imageCaptureRequested = false;
    private volatile Socket streamSocket = null;
    private boolean chargeCableIsConnected = true;

    private int deviceRotation= 0;
    private OrientationEventListener orientationEventListener;


    private volatile Device flirOneDevice;
    private FrameProcessor frameProcessor;

    private String lastSavedPath;

    private Device.TuningState currentTuningState = Device.TuningState.Unknown;


    EnumSet<RenderedImage.ImageType> e;


    // Device Delegate methods

    // Called during device discovery, when a device is connected
    // During this callback, you should save a reference to device
    // You should also set the power update delegate for the device if you have one
    // Go ahead and start frame stream as soon as connected, in this use case
    // Finally we create a frame processor for rendering frames

    public void onDeviceConnected(Device device){
        Log.i("ExampleApp", "Device connected!");

        flirOneDevice = device;
        flirOneDevice.setPowerUpdateDelegate(this);
        flirOneDevice.startFrameStream(this);

        final ToggleButton chargeCableButton = (ToggleButton)findViewById(R.id.chargeCableToggle);
        if(flirOneDevice instanceof SimulatedDevice){
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    chargeCableButton.setChecked(chargeCableIsConnected);
                    chargeCableButton.setVisibility(View.VISIBLE);
                }
            });
        }else{
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    chargeCableButton.setChecked(chargeCableIsConnected);
                    chargeCableButton.setVisibility(View.INVISIBLE);
                    findViewById(R.id.connect_sim_button).setEnabled(false);
                }
            });
        }

        orientationEventListener.enable();
    }

    /**
     * Indicate to the user that the device has disconnected
     */
    public void onDeviceDisconnected(Device device){
        Log.i("ExampleApp", "Device disconnected!");

        final ToggleButton chargeCableButton = (ToggleButton)findViewById(R.id.chargeCableToggle);
        final TextView levelTextView = (TextView)findViewById(R.id.batteryLevelTextView);
        final ImageView chargingIndicator = (ImageView)findViewById(R.id.batteryChargeIndicator);
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                thermalImageView.setImageBitmap(Bitmap.createBitmap(1,1, Bitmap.Config.ALPHA_8));
                levelTextView.setText("--");
                chargeCableButton.setChecked(chargeCableIsConnected);
                chargeCableButton.setVisibility(View.INVISIBLE);
                chargingIndicator.setVisibility(View.GONE);
                thermalImageView.clearColorFilter();
                findViewById(R.id.tuningProgressBar).setVisibility(View.GONE);
                findViewById(R.id.tuningTextView).setVisibility(View.GONE);
                findViewById(R.id.connect_sim_button).setEnabled(true);
            }
        });
        flirOneDevice = null;
        orientationEventListener.disable();
    }

    /**
     * If using RenderedImage.ImageType.ThermalRadiometricKelvinImage, you should not rely on
     * the accuracy if tuningState is not Device.TuningState.Tuned
     * @param tuningState
     */
    public void onTuningStateChanged(Device.TuningState tuningState){
        Log.i("ExampleApp", "Tuning state changed changed!");

        currentTuningState = tuningState;
        if (tuningState == Device.TuningState.InProgress){
            runOnUiThread(new Thread(){
                @Override
                public void run() {
                    super.run();
                    thermalImageView.setColorFilter(Color.DKGRAY, PorterDuff.Mode.DARKEN);
                    findViewById(R.id.tuningProgressBar).setVisibility(View.VISIBLE);
                    findViewById(R.id.tuningTextView).setVisibility(View.VISIBLE);
                }
            });
        }else {
            runOnUiThread(new Thread() {
                @Override
                public void run() {
                    super.run();
                    thermalImageView.clearColorFilter();
                    findViewById(R.id.tuningProgressBar).setVisibility(View.GONE);
                    findViewById(R.id.tuningTextView).setVisibility(View.GONE);
                }
            });
        }
    }

    @Override
    public void onAutomaticTuningChanged(boolean deviceWillTuneAutomatically) {

    }
    private ColorFilter originalChargingIndicatorColor = null;
    @Override
    public void onBatteryChargingStateReceived(final Device.BatteryChargingState batteryChargingState) {
        Log.i("ExampleApp", "Battery charging state received!");

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                ImageView chargingIndicator = (ImageView)findViewById(R.id.batteryChargeIndicator);
                if (originalChargingIndicatorColor == null){
                    originalChargingIndicatorColor = chargingIndicator.getColorFilter();
                }
                switch (batteryChargingState) {
                    case FAULT:
                    case FAULT_HEAT:
                        chargingIndicator.setColorFilter(Color.RED);
                        chargingIndicator.setVisibility(View.VISIBLE);
                        break;
                    case FAULT_BAD_CHARGER:
                        chargingIndicator.setColorFilter(Color.DKGRAY);
                        chargingIndicator.setVisibility(View.VISIBLE);
                    case MANAGED_CHARGING:
                        chargingIndicator.setColorFilter(originalChargingIndicatorColor);
                        chargingIndicator.setVisibility(View.VISIBLE);
                        break;
                    case NO_CHARGING:
                    default:
                        chargingIndicator.setVisibility(View.GONE);
                        break;
                }
            }
        });
    }
    @Override
    public void onBatteryPercentageReceived(final byte percentage){
        Log.i("ExampleApp", "Battery percentage received!");

        final TextView levelTextView = (TextView)findViewById(R.id.batteryLevelTextView);
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                levelTextView.setText(String.valueOf((int) percentage) + "%");
            }
        });
    }

    private void updateThermalImageView(final Bitmap frame){
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                thermalImageView.setImageBitmap(frame);
                tempTextView.setText(String.format("%.01f °C - %.01f °C / %.01f °C", tempMin, tempMax, tempAvg));
            }
        });
    }

    // StreamDelegate method
    public void onFrameReceived(Frame frame){
        Log.v("ExampleApp", "Frame received!");

        if (currentTuningState != Device.TuningState.InProgress){
            frameProcessor.processFrame(frame);
//            Map<RenderedImage.ImageType,RenderedImage> m=frameProcessor.getProcessedFrames(frame);
//            onFrameProcessed(m.get(RenderedImage.ImageType.ThermalRadiometricKelvinImage));
        }
    }

    private Bitmap thermalBitmap = null;

    final JpegHandler jpegHandler = new JpegHandler();
    byte[] headerData;

    private boolean headerWritten;
    float tempMin, tempMax, tempAvg;
    int oldWidth, oldHeight;
    SharedPreferences sharedPref;
    TextView tempTextView;

    // Frame Processor Delegate method, will be called each time a rendered frame is produced
    public void onFrameProcessed(final RenderedImage renderedImage){
        Log.v("ExampleApp", "Frame processed! imageType:" + renderedImage.imageType()+"  width:"+renderedImage.width()+"  height:"+renderedImage.height());
        if(renderedImage.imageType()==RenderedImage.ImageType.ThermalRadiometricKelvinImage) {
            byte[] b=renderedImage.pixelData();
            tempMin=Float.MAX_VALUE;
            tempMax=Float.MIN_VALUE;
            tempAvg=0;
            for(int i=0; i<b.length/2; i++) {
                float temp=(float)((b[i*2]&0xFF)+256*(b[i*2+1]&0xFF))/100f-273.15f;
                tempMin=Math.min(tempMin, temp);
                tempMax=Math.max(tempMax, temp);
                tempAvg+=temp;
//                Log.v("ExampleApp", "temp="+temp);
//                Log.v("ExampleApp", "b["+i+"]="+(int)(b[i]&0xFF));
//                float f = ByteBuffer.wrap(b,i*2,2).order(ByteOrder.LITTLE_ENDIAN).getFloat();
//                Log.v("ExampleApp", "f="+f);
//                float fb = ByteBuffer.wrap(b,i*2,2).order(ByteOrder.BIG_ENDIAN).getFloat();
//                Log.v("ExampleApp", "fb="+fb);
            }
            tempAvg/=b.length/2;
            Log.v("ExampleApp", "b.length/2="+b.length/2+"  tempMin=" + tempMin + " tempMax=" + tempMax + " tempAvg=" + tempAvg);
        }
        boolean processThermalRadiometricKelvinImage=e.size()==1 || renderedImage.imageType()!=RenderedImage.ImageType.ThermalRadiometricKelvinImage;
        if(processThermalRadiometricKelvinImage) {
            thermalBitmap = renderedImage.getBitmap();
            updateThermalImageView(thermalBitmap);
        }


        /*
        Capture this image if requested.
        */
        if (this.imageCaptureRequested) {
            imageCaptureRequested = false;
            final Context context = this;
            new Thread(new Runnable() {
                public void run() {
                    String path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).toString();
                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ssZ", Locale.getDefault());
                    String formatedDate = sdf.format(new Date());
                    String fileName = "FLIROne-" + formatedDate + ".jpg";
                    try{
                        lastSavedPath = path+ "/" + fileName;
                        renderedImage.getFrame().save(new File(lastSavedPath), RenderedImage.Palette.Iron, RenderedImage.ImageType.BlendedMSXRGBA8888Image);

                        MediaScannerConnection.scanFile(context,
                                new String[]{path + "/" + fileName}, null,
                                new MediaScannerConnection.OnScanCompletedListener() {
                                    @Override
                                    public void onScanCompleted(String path, Uri uri) {
                                        Log.i("ExternalStorage", "Scanned " + path + ":");
                                        Log.i("ExternalStorage", "-> uri=" + uri);
                                    }

                                });

                    }catch (Exception e){
                        e.printStackTrace();
                    }
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {

                            thermalImageView.animate().setDuration(50).scaleY(0).withEndAction((new Runnable() {
                                public void run() {
                                    thermalImageView.animate().setDuration(50).scaleY(1);
                                }
                            }));
                        }
                    });
                }
            }).start();
        }

        if (streamSocket != null && streamSocket.isConnected() && processThermalRadiometricKelvinImage) {
            try {
                // send PNG file over socket in another thread
                final OutputStream outputStream = streamSocket.getOutputStream();
                // make a output stream so we can get the size of the PNG
                final ByteArrayOutputStream bufferStream = new ByteArrayOutputStream();

//                thermalBitmap.compress(Bitmap.CompressFormat./*WEBP*/JPEG, /*100*/90, bufferStream);

//C:\Docs\Java\FLIRONE2SmartCam\jni>\progs\android-ndk\ndk-build NDK_DEBUG=1 & copy /y ..\libs\armeabi-v7a\libjpeghandler.so ..\app\src\main\jniLibs\armeabi-v7a
//copy /y C:\Docs\Java\FLIRONE2SmartCam\market\app-release.apk Z:\home\public\de\killig\sven\android\FLIRONE2SmartCam\FLIRONE2SmartCam.apk
                if(renderedImage.width()!=oldWidth || renderedImage.height()!=oldHeight) {
                    oldWidth=renderedImage.width();
                    oldHeight=renderedImage.height();
                    headerWritten = false;
                    // TODO update when format changes
                    headerData = jpegHandler.initRgb(thermalBitmap.getWidth() * thermalBitmap.getHeight() * 4, thermalBitmap.getWidth(), thermalBitmap.getHeight());
                }
                Log.d("STREAM", "sending frame with size " + /*array.length +*/ " config " + thermalBitmap.getConfig());

                if(renderedImage.imageType()==RenderedImage.ImageType.VisualJPEGImage) {
                    headerWritten = false;
                    bufferStream.write(renderedImage.pixelData());
                } else if(renderedImage.imageType()==RenderedImage.ImageType.ThermalLinearFlux14BitImage || renderedImage.imageType()==RenderedImage.ImageType.ThermalRadiometricKelvinImage) {
////calculate how many bytes our image consists of.
                    int bytes = thermalBitmap.getByteCount();
////or we can calculate bytes this way. Use a different value than 4 if you don't use 32bit images.
////int bytes = b.getWidth()*b.getHeight()*4;
//
                    ByteBuffer buffer = ByteBuffer.allocate(bytes); //Create a new buffer
                    thermalBitmap.copyPixelsToBuffer(buffer); //Move the byte data to the buffer

//                    byte[] array = buffer.array(); //Get the underlying array containing the data.
                    bufferStream.write(jpegHandler.encodeRGB24(buffer.array()));
                } else {
                    bufferStream.write(jpegHandler.encodeRGB24(renderedImage.pixelData()));
                }


                bufferStream.flush();
                (new Thread() {
                    @Override
                    public void run() {
                        super.run();
                        try {
                            /*
                             * Header is 6 bytes indicating the length of the image data and rotation
                             * of the device
                             * This could be expanded upon by adding bytes to have more metadata
                             * such as image format
                             */
//                            byte[] headerBytes = ByteBuffer.allocate((Integer.SIZE + Short.SIZE) / 8).putInt(bufferStream.size()).putShort((short)deviceRotation).array();
                            synchronized (streamSocket) {
//                                outputStream.write(headerBytes);
                                if(!headerWritten) {
                                    headerWritten=true;
                                    outputStream.write(headerData);
                                }
                                bufferStream.writeTo(outputStream);
                                outputStream.flush();
                            }
                            bufferStream.close();


                        } catch (IOException ex) {
                            Log.e("STREAM", "Error sending frame: " + ex.toString());
                        }
                    }
                }).start();
            } catch (Exception ex){
                   Log.e("STREAM", "Error creating PNG: "+ex.getMessage());

            }

        }


    }




    /**
     * Whether or not the system UI should be auto-hidden after
     * {@link #AUTO_HIDE_DELAY_MILLIS} milliseconds.
     */
    private static final boolean AUTO_HIDE = true;

    /**
     * If {@link #AUTO_HIDE} is set, the number of milliseconds to wait after
     * user interaction before hiding the system UI.
     */
    private static final int AUTO_HIDE_DELAY_MILLIS = 3000;

    /**
     * If set, will toggle the system UI visibility upon interaction. Otherwise,
     * will show the system UI visibility upon interaction.
     */
    private static final boolean TOGGLE_ON_CLICK = true;

    /**
     * The flags to pass to {@link SystemUiHider#getInstance}.
     */
    private static final int HIDER_FLAGS = SystemUiHider.FLAG_HIDE_NAVIGATION;

    /**
     * The instance of the {@link SystemUiHider} for this activity.
     */
    private SystemUiHider mSystemUiHider;
    public void onTuneClicked(View v){
        if (flirOneDevice != null){
            flirOneDevice.performTuning();
        }

    }
    public void onCaptureImageClicked(View v){


        // if nothing's connected, let's load an image instead?

        if(flirOneDevice == null && lastSavedPath != null) {
            // load!
            File file = new File(lastSavedPath);


            LoadedFrame frame = new LoadedFrame(file);

            // load the frame
            onFrameReceived(frame);
        } else {
            this.imageCaptureRequested = true;
        }
    }
    public void onConnectSimClicked(View v){
        if(flirOneDevice == null){
            try {
                flirOneDevice = new SimulatedDevice(this, getResources().openRawResource(R.raw.sampleframes), 10);
                flirOneDevice.setPowerUpdateDelegate(this);
                chargeCableIsConnected = true;
            } catch(Exception ex) {
                flirOneDevice = null;
                Log.w("FLIROneExampleApp", "IO EXCEPTION");
                ex.printStackTrace();
            }
        }else if(flirOneDevice instanceof SimulatedDevice) {
            flirOneDevice.close();
            flirOneDevice = null;
        }
    }

    public void onSimulatedChargeCableToggleClicked(View v){
        if(flirOneDevice instanceof SimulatedDevice){
            chargeCableIsConnected = !chargeCableIsConnected;
            ((SimulatedDevice)flirOneDevice).setChargeCableState(chargeCableIsConnected);
        }
    }
    public void onRotateClicked(View v){
        ToggleButton theSwitch = (ToggleButton)v;
        if (theSwitch.isChecked()){
            thermalImageView.setRotation(180);
        }else{
            thermalImageView.setRotation(0);
        }
    }
    public void onChangeViewClicked(View v){
        if (frameProcessor == null){
            ((ToggleButton)v).setChecked(false);
            return;
        }
        ListView paletteListView = (ListView)findViewById(R.id.paletteListView);
        ListView imageTypeListView = (ListView)findViewById(R.id.imageTypeListView);
        if (((ToggleButton)v).isChecked()){
            // only show palette list if selected image type is colorized
            paletteListView.setVisibility(View.INVISIBLE);
            for (RenderedImage.ImageType imageType : frameProcessor.getImageTypes()){
                if (imageType.isColorized()) {
                    paletteListView.setVisibility(View.VISIBLE);
                    break;
                }
            }
            imageTypeListView.setVisibility(View.VISIBLE);
            findViewById(R.id.imageTypeListContainer).setVisibility(View.VISIBLE);
        }else{
            findViewById(R.id.imageTypeListContainer).setVisibility(View.GONE);
        }


    }

// duplicated in onCreate()
//    public void onImageTypeListViewClicked(View v){
//        int index = ((ListView) v).getSelectedItemPosition();
//        RenderedImage.ImageType imageType = RenderedImage.ImageType.values()[index];
//        e=EnumSet.of(imageType);
//        e.add(RenderedImage.ImageType.ThermalRadiometricKelvinImage);
//        frameProcessor.setImageTypes(/*EnumSet.of(imageType)*/e);
//        int paletteVisibility = (imageType.isColorized()) ? View.VISIBLE : View.GONE;
//        findViewById(R.id.paletteListView).setVisibility(paletteVisibility);
//    }
//
//    public void onPaletteListViewClicked(View v){
//        RenderedImage.Palette pal = (RenderedImage.Palette )(((ListView)v).getSelectedItem());
//        frameProcessor.setImagePalette(pal);
//    }

    public static final String PREFS_KEY="address";

    /**
     * Example method of starting/stopping a frame stream to a host
     * @param v The toggle button pushed
     */
    public void onNetStreamClicked(View v){
        final ToggleButton button = (ToggleButton)v;
        button.setChecked(false);

        if (streamSocket == null || streamSocket.isClosed()){
            AlertDialog.Builder alert = new AlertDialog.Builder(this);

            alert.setTitle("Start Network Stream");
            alert.setMessage("hostname:port of SmartCam server?");

            // Set an EditText view to get user input
            final EditText input = new EditText(this);
            input.setText(sharedPref.getString(PREFS_KEY,"192.168.30.2:9361"));

            alert.setView(input);

            alert.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int whichButton) {
                    String value = input.getText().toString();
                    SharedPreferences.Editor editor=sharedPref.edit();
                    editor.putString(PREFS_KEY,value);
                    editor.apply();

                    final String[] parts = value.split(":");
                    (new Thread(){
                        @Override
                        public void run() {
                            super.run();
                            try {
                                streamSocket = new Socket(parts[0], Integer.parseInt(parts[1], 10));
                                runOnUiThread(new Thread(){
                                    @Override
                                    public void run() {
                                        super.run();
                                        button.setChecked(streamSocket.isConnected());
                                    }
                                });

                            }catch (Exception ex){
                                Log.e("CONNECT",ex.getMessage());
                            }
                        }
                    }).start();

                }
            });

            alert.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int whichButton) {
                    // Canceled.
                }
            });

            alert.show();
        }else{
            try {
                streamSocket.close();
            }catch (Exception ex){

            }
            button.setChecked(streamSocket != null && streamSocket.isConnected());
        }

        headerWritten=false;
        oldWidth=0;
        oldHeight=0;
    }

    @Override
    protected void onStart(){
        super.onStart();
        thermalImageView = (ImageView) findViewById(R.id.imageView);
        try {
            Device.startDiscovery(this, this);
        }catch(IllegalStateException e){
            // it's okay if we've already started discovery
        }
    }

    ScaleGestureDetector mScaleDetector;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);


        setContentView(R.layout.activity_preview);

        final View controlsView = findViewById(R.id.fullscreen_content_controls);
        final View controlsViewTop = findViewById(R.id.fullscreen_content_controls_top);
        final View contentView = findViewById(R.id.fullscreen_content);


        String[] imageTypeNames = new String[RenderedImage.ImageType.values().length];
        // Massage the type names for display purposes
        for (RenderedImage.ImageType t : RenderedImage.ImageType.values()){
            String name = t.name().replaceAll("(RGBA)|(YCbCr)|(8)","").replaceAll("([a-z])([A-Z])", "$1 $2");

            if (name.contains("YCbCr888")){
                name = name.replace("YCbCr888", "Aligned");
            }
            imageTypeNames[t.ordinal()] = name;
        }
        RenderedImage.ImageType defaultImageType = RenderedImage.ImageType.BlendedMSXRGBA8888Image;
        e=EnumSet.of(defaultImageType);
        e.add(RenderedImage.ImageType.ThermalRadiometricKelvinImage);
        frameProcessor = new FrameProcessor(this, this, /*EnumSet.of(defaultImageType)*/e);

        ListView imageTypeListView = ((ListView)findViewById(R.id.imageTypeListView));
        imageTypeListView.setAdapter(new ArrayAdapter<>(this,R.layout.emptytextview,imageTypeNames));
        imageTypeListView.setSelection(defaultImageType.ordinal());
        imageTypeListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (frameProcessor != null) {
                    RenderedImage.ImageType imageType = RenderedImage.ImageType.values()[position];
                    e=EnumSet.of(imageType);
                    e.add(RenderedImage.ImageType.ThermalRadiometricKelvinImage);
                    frameProcessor.setImageTypes(/*EnumSet.of(imageType)*/e);
                    if (imageType.isColorized()){
                        findViewById(R.id.paletteListView).setVisibility(View.VISIBLE);
                    }else{
                        findViewById(R.id.paletteListView).setVisibility(View.INVISIBLE);
                    }
                }
            }
        });
        imageTypeListView.setDivider(null);

        // Palette List View Setup
        ListView paletteListView = ((ListView)findViewById(R.id.paletteListView));
        paletteListView.setDivider(null);
        paletteListView.setAdapter(new ArrayAdapter<>(this, R.layout.emptytextview, RenderedImage.Palette.values()));
        paletteListView.setSelection(frameProcessor.getImagePalette().ordinal());
        paletteListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (frameProcessor != null){
                    frameProcessor.setImagePalette(RenderedImage.Palette.values()[position]);
                }
            }
        });
        // Set up an instance of SystemUiHider to control the system UI for
        // this activity.

        mSystemUiHider = SystemUiHider.getInstance(this, contentView, HIDER_FLAGS);
        mSystemUiHider.setup();

        mSystemUiHider
                .setOnVisibilityChangeListener(new SystemUiHider.OnVisibilityChangeListener() {
                    // Cached values.
                    int mControlsHeight;
                    int mShortAnimTime;

                    @Override
                    @TargetApi(Build.VERSION_CODES.HONEYCOMB_MR2)
                    public void onVisibilityChange(boolean visible) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB_MR2) {
                            // If the ViewPropertyAnimator API is available
                            // (Honeycomb MR2 and later), use it to animate the
                            // in-layout UI controls at the bottom of the
                            // screen.
                            if (mControlsHeight == 0) {
                                mControlsHeight = controlsView.getHeight();
                            }
                            if (mShortAnimTime == 0) {
                                mShortAnimTime = getResources().getInteger(
                                        android.R.integer.config_shortAnimTime);
                            }
                            controlsView.animate()
                                    .translationY(visible ? 0 : mControlsHeight)
                                    .setDuration(mShortAnimTime);
                            controlsViewTop.animate().translationY(visible ? 0 : -1 * mControlsHeight).setDuration(mShortAnimTime);
                        } else {
                            // If the ViewPropertyAnimator APIs aren't
                            // available, simply show or hide the in-layout UI
                            // controls.
                            controlsView.setVisibility(visible ? View.VISIBLE : View.GONE);
                            controlsViewTop.setVisibility(visible ? View.VISIBLE : View.GONE);
                        }

                        if (visible && !((ToggleButton)findViewById(R.id.change_view_button)).isChecked() && AUTO_HIDE) {
                            // Schedule a hide().
                            delayedHide(AUTO_HIDE_DELAY_MILLIS);
                        }

                        tempTextView.setVisibility(visible?View.INVISIBLE:View.VISIBLE);
                    }
                });

        // Set up the user interaction to manually show or hide the system UI.
        contentView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (TOGGLE_ON_CLICK) {
                    mSystemUiHider.toggle();
                } else {
                    mSystemUiHider.show();
                }
            }
        });

        // Upon interacting with UI controls, delay any scheduled hide()
        // operations to prevent the jarring behavior of controls going away
        // while interacting with the UI.
        findViewById(R.id.change_view_button).setOnTouchListener(mDelayHideTouchListener);


        orientationEventListener = new OrientationEventListener(this) {
            @Override
            public void onOrientationChanged(int orientation) {
                deviceRotation = orientation;
            }
        };
        mScaleDetector = new ScaleGestureDetector(this, new ScaleGestureDetector.OnScaleGestureListener() {
            @Override
            public void onScaleEnd(ScaleGestureDetector detector) {
            }
            @Override
            public boolean onScaleBegin(ScaleGestureDetector detector) {
                return true;
            }
            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                Log.d("ZOOM", "zoom ongoing, scale: " + detector.getScaleFactor());
                frameProcessor.setMSXDistance(detector.getScaleFactor());
                return false;
            }
        });

        findViewById(R.id.fullscreen_content).setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                mScaleDetector.onTouchEvent(event);
                return true;
            }
        });

        sharedPref=PreferenceManager.getDefaultSharedPreferences(this);
        tempTextView=((TextView)findViewById(R.id.tempTextView));
    }

    @Override
    public void onRestart(){
        try {
            Device.startDiscovery(this, this);
        } catch (IllegalStateException e) {
            Log.e("PreviewActivity", "Somehow we've started discovery twice");
            e.printStackTrace();
        }
        super.onRestart();
    }

    @Override
    public void onStop() {
        // We must unregister our usb receiver, otherwise we will steal events from other apps
        Log.e("PreviewActivity", "onStop, stopping discovery!");
        Device.stopDiscovery();
        super.onStop();
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);

        // Trigger the initial hide() shortly after the activity has been
        // created, to briefly hint to the user that UI controls
        // are available.
        delayedHide(100);
    }


    /**
     * Touch listener to use for in-layout UI controls to delay hiding the
     * system UI. This is to prevent the jarring behavior of controls going away
     * while interacting with activity UI.
     */
    View.OnTouchListener mDelayHideTouchListener = new View.OnTouchListener() {
        @Override
        public boolean onTouch(View view, MotionEvent motionEvent) {
            if (AUTO_HIDE) {
                delayedHide(AUTO_HIDE_DELAY_MILLIS);
            }
            return false;
        }
    };

    Handler mHideHandler = new Handler();
    Runnable mHideRunnable = new Runnable() {
        @Override
        public void run() {
            mSystemUiHider.hide();
        }
    };

    /**
     * Schedules a call to hide() in [delay] milliseconds, canceling any
     * previously scheduled calls.
     */
    private void delayedHide(int delayMillis) {
        mHideHandler.removeCallbacks(mHideRunnable);
        mHideHandler.postDelayed(mHideRunnable, delayMillis);
    }
}
