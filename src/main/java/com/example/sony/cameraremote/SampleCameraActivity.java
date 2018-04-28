/*
 * Copyright 2014 Sony Corporation
 */

package com.example.sony.cameraremote;

import com.example.sony.cameraremote.utils.Constants;
import com.example.sony.cameraremote.utils.DisplayHelper;
import com.squareup.picasso.Picasso;
import com.squareup.picasso.Target;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Environment;
import android.os.Handler;
import android.support.v7.app.AlertDialog;
import android.text.TextUtils;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import timber.log.Timber;

/**
 * An Activity class of Sample Camera screen.
 */
public class SampleCameraActivity extends Activity {

    private static final String TAG = SampleCameraActivity.class.getSimpleName();

    private ImageView mImagePictureWipe;

    private Spinner mSpinnerShootMode;

    private Button mButtonTakePicture;

    private Button mButtonRecStartStop;

    private Button mButtonZoomIn;

    private Button mButtonZoomOut;

    private Button mButtonContentsListMode;

    private Button mOpenLastButton;

    private String mOpenLastUrl;

    private String mLastTakenPhotoName;

    private TextView mTextCameraStatus;

    private ServerDevice mTargetServer;

    private SimpleRemoteApi mRemoteApi;

    private SimpleStreamSurfaceView mLiveviewSurface;

    private SimpleCameraEventObserver mEventObserver;

    private SimpleCameraEventObserver.ChangeListener mEventListener;

    private final Set<String> mAvailableCameraApiSet = new HashSet<String>();

    private final Set<String> mSupportedApiSet = new HashSet<String>();

    private String mPostviewImageSize = "";

    private TextView centerInformationTextview;

    private int mCounter = 0;

    private int picturesTakenCounter = 0;

    private ArrayList<String> takenPictureFilePathArrayList = new ArrayList<>();

    private ThreadPoolExecutor singleThreadExecutor = new ThreadPoolExecutor(
            1,
            1,
            0,
            TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue<Runnable>());

    private View decorView;

    // Seconds Remaining in any countdown
    private int secondsRemaining;
    // Counter which picture is being taken right now
    private int currentPicNumBeingTaken;
    // Round Button "Take four pictures!"
    private Button takeFourPicturesButton;
    // boolean that decides if zoom buttons are allowed to be shown or not
    private boolean zoomButtonsAllowedToBeShown = true;

    private TextView xyPicturesLeftTextview;
    private SharedPreferences sharedPreferences;
    public static String numberPicturesPrintedPrefsString = "NUMBER_PICTURES_PRINTED";
    public static String drawHeartInMiddlePrefsString = "DRAW_HEART_IN_MIDDLE";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        setContentView(R.layout.activity_sample_camera);

        // disable screen timeout while app is running
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        decorView = getWindow().getDecorView();

        SampleApplication app = (SampleApplication) getApplication();
        mTargetServer = app.getTargetServerDevice();
        mRemoteApi = new SimpleRemoteApi(mTargetServer);
        app.setRemoteApi(mRemoteApi);
        mEventObserver = new SimpleCameraEventObserver(getApplicationContext(), mRemoteApi);
        app.setCameraEventObserver(mEventObserver);
        mImagePictureWipe = (ImageView) findViewById(R.id.image_picture_wipe);
        mSpinnerShootMode = (Spinner) findViewById(R.id.spinner_shoot_mode);
        mButtonTakePicture = (Button) findViewById(R.id.button_take_picture);
        mButtonRecStartStop = (Button) findViewById(R.id.button_rec_start_stop);
        mButtonZoomIn = (Button) findViewById(R.id.button_zoom_in);
        mButtonZoomOut = (Button) findViewById(R.id.button_zoom_out);
        mButtonContentsListMode = (Button) findViewById(R.id.button_contents_list);
        mTextCameraStatus = (TextView) findViewById(R.id.text_camera_status);
        mOpenLastButton = findViewById(R.id.open_last_button);
        centerInformationTextview = findViewById(R.id.center_information_textview);
        centerInformationTextview.setVisibility(View.GONE);
        takeFourPicturesButton = findViewById(R.id.take_four_pictures_button);
        xyPicturesLeftTextview = findViewById(R.id.xy_pictures_left_textview);

        mOpenLastButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!TextUtils.isEmpty(mOpenLastUrl)) {
                    Timber.d("--- clicked mOpenLastUrl: " + mOpenLastUrl);
                    Intent intent = new Intent(Intent.ACTION_VIEW);
                    intent.setData(Uri.parse(mOpenLastUrl));
                    startActivity(intent);
                }
            }
        });

        mSpinnerShootMode.setEnabled(false);

        mEventListener = new SimpleCameraEventObserver.ChangeListenerTmpl() {

            @Override
            public void onShootModeChanged(String shootMode) {
                Log.d(TAG, "onShootModeChanged() called: " + shootMode);
                refreshUi();
            }

            @Override
            public void onCameraStatusChanged(String status) {
                Log.d(TAG, "onCameraStatusChanged() called: " + status);
                refreshUi();
            }

            @Override
            public void onApiListModified(List<String> apis) {
                Log.d(TAG, "onApiListModified() called");
                synchronized (mAvailableCameraApiSet) {
                    mAvailableCameraApiSet.clear();
                    for (String api : apis) {
                        mAvailableCameraApiSet.add(api);
                    }
                    if (!mEventObserver.getLiveviewStatus() //
                            && isCameraApiAvailable("startLiveview")) {
                        if (mLiveviewSurface != null && !mLiveviewSurface.isStarted()) {
                            startLiveview();
                        }
                    }
                    if (isCameraApiAvailable("actZoom")) {
                        Log.d(TAG, "onApiListModified(): prepareActZoomButtons()");
                        prepareActZoomButtons(true);
                    } else {
                        prepareActZoomButtons(false);
                    }
                }
            }

            @Override
            public void onZoomPositionChanged(int zoomPosition) {
                Log.d(TAG, "onZoomPositionChanged() called = " + zoomPosition);
                if (zoomPosition == 0) {
                    mButtonZoomIn.setEnabled(true);
                    mButtonZoomOut.setEnabled(false);
                } else if (zoomPosition == 100) {
                    mButtonZoomIn.setEnabled(false);
                    mButtonZoomOut.setEnabled(true);
                } else {
                    mButtonZoomIn.setEnabled(true);
                    mButtonZoomOut.setEnabled(true);
                }
            }

            @Override
            public void onLiveviewStatusChanged(boolean status) {
                Log.d(TAG, "onLiveviewStatusChanged() called = " + status);
            }

            @Override
            public void onStorageIdChanged(String storageId) {
                Log.d(TAG, "onStorageIdChanged() called: " + storageId);
                refreshUi();
            }
        };

        Log.d(TAG, "onCreate() completed.");
    }

    @Override
    protected void onResume() {
        super.onResume();

        mEventObserver.activate();
        mLiveviewSurface = (SimpleStreamSurfaceView) findViewById(R.id.surfaceview_liveview);
        mSpinnerShootMode.setFocusable(false);
        mButtonContentsListMode.setEnabled(false);

        mButtonTakePicture.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                takeAndFetchPicture();
            }
        });
        mButtonRecStartStop.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                if ("MovieRecording".equals(mEventObserver.getCameraStatus())) {
                    stopMovieRec();
                } else if ("IDLE".equals(mEventObserver.getCameraStatus())) {
                    startMovieRec();
                }
            }
        });

        mImagePictureWipe.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                mImagePictureWipe.setVisibility(View.INVISIBLE);
            }
        });

        mButtonZoomIn.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                actZoom("in", "1shot");
            }
        });

        mButtonZoomOut.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                actZoom("out", "1shot");
            }
        });

        mButtonZoomIn.setOnLongClickListener(new View.OnLongClickListener() {

            @Override
            public boolean onLongClick(View arg0) {
                actZoom("in", "start");
                return true;
            }
        });

        mButtonZoomOut.setOnLongClickListener(new View.OnLongClickListener() {

            @Override
            public boolean onLongClick(View arg0) {
                actZoom("out", "start");
                return true;
            }
        });

        mButtonZoomIn.setOnTouchListener(new View.OnTouchListener() {

            private long downTime = -1;

            @Override
            public boolean onTouch(View v, MotionEvent event) {

                if (event.getAction() == MotionEvent.ACTION_UP) {
                    if (System.currentTimeMillis() - downTime > 500) {
                        actZoom("in", "stop");
                    }
                }
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    downTime = System.currentTimeMillis();
                }
                return false;
            }
        });

        mButtonZoomOut.setOnTouchListener(new View.OnTouchListener() {

            private long downTime = -1;

            @Override
            public boolean onTouch(View v, MotionEvent event) {

                if (event.getAction() == MotionEvent.ACTION_UP) {
                    if (System.currentTimeMillis() - downTime > 500) {
                        actZoom("out", "stop");
                    }
                }
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    downTime = System.currentTimeMillis();
                }
                return false;
            }
        });

        mButtonContentsListMode.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                Log.d(TAG, "Clicked contents list mode button");
                prepareToStartContentsListMode();
            }
        });

        prepareOpenConnection();
        setCorrectPicturesLeftTextview();

        Log.d(TAG, "onResume() completed.");
    }

    @Override
    protected void onPause() {
        super.onPause();
        closeConnection();

        Log.d(TAG, "onPause() completed.");
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            decorView.setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        // TODO react to if printactivity is printing or if we can take a photo right away now
        showButtons();
    }

    /**
     * Create Image Collage from the last 4 taken images
     */
    private void createImageCollage() {
        Bitmap bitmap1 = BitmapFactory.decodeFile(takenPictureFilePathArrayList.get(0));
        Bitmap bitmap2 = BitmapFactory.decodeFile(takenPictureFilePathArrayList.get(1));
        Bitmap bitmap3 = BitmapFactory.decodeFile(takenPictureFilePathArrayList.get(2));
        Bitmap bitmap4 = BitmapFactory.decodeFile(takenPictureFilePathArrayList.get(3));

//        bitmap1 = Bitmap.createScaledBitmap(bitmap1,
//                bitmap1.getWidth() / 2,
//                bitmap1.getHeight() / 2,
//                true);
//        bitmap2 = Bitmap.createScaledBitmap(bitmap2,
//                bitmap2.getWidth() / 2,
//                bitmap2.getHeight() / 2,
//                true);
//        bitmap3 = Bitmap.createScaledBitmap(bitmap3,
//                bitmap3.getWidth() / 2,
//                bitmap3.getHeight() / 2,
//                true);
//        bitmap4 = Bitmap.createScaledBitmap(bitmap4,
//                bitmap4.getWidth() / 2,
//                bitmap4.getHeight() / 2,
//                true);

        Bitmap result = Bitmap.createBitmap(bitmap1.getWidth() * 2,
                bitmap1.getHeight() * 2, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(result);
        Paint paint = new Paint();
        canvas.drawBitmap(bitmap1, 0, 0, paint);
        canvas.drawBitmap(bitmap2, bitmap1.getWidth(), 0, paint);
        canvas.drawBitmap(bitmap3, 0, bitmap1.getHeight(), paint);
        canvas.drawBitmap(bitmap4, bitmap3.getWidth(), bitmap2.getHeight(), paint);

        // Add drawable in the middle if option is selected
        if (getDrawHeartInMiddleFromPrefs()) {
            Bitmap regineBerndHeart = BitmapFactory.decodeResource(getResources(),
                    R.drawable.regine_bernd_herz);
            regineBerndHeart = Bitmap.createScaledBitmap(regineBerndHeart,
                    (regineBerndHeart.getWidth()/4),
                    (regineBerndHeart.getHeight()/4),
                    true);
            int heartLeft = bitmap1.getWidth() - (regineBerndHeart.getWidth()/2);
            int heartTop = bitmap1.getHeight() - (regineBerndHeart.getHeight()/2);
            canvas.drawBitmap(regineBerndHeart, heartLeft, heartTop, paint);
        }

        // TODO: create filename such as DSC03630-03633.JPG
        // DSC03630.JPG
        String firstFilename = takenPictureFilePathArrayList.get(0);
        String startNumber = firstFilename.substring(firstFilename.length() - 9,
                firstFilename.length() - 4);
        Timber.d("--- startNumber: " + startNumber);
        String lastFilename = takenPictureFilePathArrayList.get(3);
        String endNumber = lastFilename.substring(lastFilename.length() - 9,
                lastFilename.length() - 4);
        Timber.d("--- endNumber: " + endNumber);

        final String collagesPathName = getApplicationContext()
                .getExternalFilesDir(Environment.DIRECTORY_PICTURES)
                .getPath() + "/collages/";
        File collagesPath = new File(collagesPathName);
        collagesPath.mkdirs();
        final String simpleFileName = "DSC" + startNumber + "-" + endNumber + ".JPG";
        final File file = new File(collagesPathName + simpleFileName);

        Timber.d("--- combinedPicture file: " + file.getPath());
        try {
            file.createNewFile();
            FileOutputStream ostream = new FileOutputStream(file);
            result.compress(Bitmap.CompressFormat.JPEG,100,ostream);
            ostream.close();
        }
        catch (Exception e) {
            e.printStackTrace();
        }

        // TODO start activity from main thread
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Intent intent = PrintActivity.Companion.buildPrintStartActivityIntent(
                        SampleCameraActivity.this, file.getPath(), collagesPathName, simpleFileName);
                startActivityForResult(intent, 12345);
            }
        });
    }

    private void showButtons() {
        zoomButtonsAllowedToBeShown = true;
        mButtonZoomIn.setVisibility(View.VISIBLE);
        mButtonZoomOut.setVisibility(View.VISIBLE);
        takeFourPicturesButton.setVisibility(View.VISIBLE);
    }

    private void hideButtons() {
        zoomButtonsAllowedToBeShown = false;
        mButtonZoomIn.setVisibility(View.GONE);
        mButtonZoomOut.setVisibility(View.GONE);
        takeFourPicturesButton.setVisibility(View.GONE);
    }

    /**
     * Show a count down on tablet and then take a picture using [takeAndFetchPictureThread]
     */
    private void countDownAndTakePicture() {
        secondsRemaining = 6;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                new CountDownTimer(6000, 1000) {
                    @Override
                    public void onTick(long millisUntilFinished) {
                        if (secondsRemaining > 5) {
                            centerInformationTextview.setVisibility(View.VISIBLE);
                            centerInformationTextview.setText(currentPicNumBeingTaken + ". Foto in...");
                        } else {
                            centerInformationTextview.setText(Integer.toString(secondsRemaining));
                        }
                        secondsRemaining--;
                    }

                    @Override
                    public void onFinish() {
                        centerInformationTextview.setVisibility(View.GONE);
                        currentPicNumBeingTaken++;
                        singleThreadExecutor.submit(takeAndFetchPictureThread);
                    }
                }.start();
            }
        });
    }

    /**
     * Click Handler for the "Take four pictures" Button
     * @param view
     */
    public void onClickTakeFourPicturesButton(View view) {
        hideButtons();
        currentPicNumBeingTaken = 1;
        secondsRemaining = 3;
        new CountDownTimer(3000, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                if (secondsRemaining > 0) {
                    centerInformationTextview.setVisibility(View.VISIBLE);
                    centerInformationTextview.setText(R.string.wir_machen_jetzt_4_fotos_);
                }
                secondsRemaining--;
            }

            @Override
            public void onFinish() {
                centerInformationTextview.setVisibility(View.GONE);
                singleThreadExecutor.submit(setOriginalPostviewImageSizeThread);
                countDownAndTakePicture();
            }
        }.start();
    }

    /**
     * Start Image Series
     * @param view
     */
    public void onClickStartSeries(View view) {
        // TODO disable startSeries Button

        currentPicNumBeingTaken = 1;
        secondsRemaining = 3;
        new CountDownTimer(3000, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                if (secondsRemaining > 0) {
                    centerInformationTextview.setVisibility(View.VISIBLE);
                    centerInformationTextview.setText(R.string.wir_machen_jetzt_4_fotos_);
                }
                secondsRemaining--;
            }

            @Override
            public void onFinish() {
                centerInformationTextview.setVisibility(View.GONE);
                singleThreadExecutor.submit(setOriginalPostviewImageSizeThread);
                countDownAndTakePicture();
            }
        }.start();
    }

    /**
     * Thread that takes pictures and fetches them from the server (camera). Uses Picasso to
     * load images from server (camera) and into [targetWithNextThread].
     */
    private Thread takeAndFetchPictureThread = new Thread() {
        @Override
        public void run() {
            try {
                JSONObject replyJson = mRemoteApi.actTakePicture();
                JSONArray resultsObj = replyJson.getJSONArray("result");
                JSONArray imageUrlsObj = resultsObj.getJSONArray(0);
                String postImageUrl = null;
                if (1 <= imageUrlsObj.length()) {
                    postImageUrl = imageUrlsObj.getString(0);
                }
                if (postImageUrl == null) {
                    Log.w(TAG, "takeAndFetchPictureThread: post image URL is null.");
                    DisplayHelper.toast(getApplicationContext(), //
                            R.string.msg_error_take_picture);
                    return;
                }
                // Show progress indicator
                DisplayHelper.setProgressIndicator(SampleCameraActivity.this, true);

                URL url = new URL(postImageUrl);
                mOpenLastUrl = postImageUrl;
                Timber.d("--- takeAndFetchPictureThread mOpenLastUrl: " + mOpenLastUrl + " and " + url);

                String path = url.getPath();
                mLastTakenPhotoName = path.substring(path.lastIndexOf('/') + 1);
                Timber.d("--- takeAndFetchPictureThread mLastTakenPhotoName: " + mLastTakenPhotoName);

                runOnUiThread(new Runnable() {

                    @Override
                    public void run() {
//                        mImagePictureWipe.setVisibility(View.VISIBLE);
//                        mImagePictureWipe.setImageDrawable(pictureDrawable);

                        Picasso.with(SampleCameraActivity.this).load(mOpenLastUrl).into(targetWithNextThread);
                    }
                });
            } catch (IOException e) {
                Log.w(TAG, "IOException while closing slicer: " + e.getMessage());
                DisplayHelper.toast(getApplicationContext(), //
                        R.string.msg_error_take_picture);
            } catch (JSONException e) {
                Log.w(TAG, "JSONException while closing slicer");
                DisplayHelper.toast(getApplicationContext(), //
                        R.string.msg_error_take_picture);
            } finally {
                DisplayHelper.setProgressIndicator(SampleCameraActivity.this, false);
            }
        }
    };

    /**
     * Thread that sets PostviewImageSize to Original; use it before taking pictures that will be
     * transferred from camera to tablet.
     */
    private Thread setOriginalPostviewImageSizeThread = new Thread() {
        @Override
        public void run() {
            try {
                JSONObject replyJson = mRemoteApi.setPostviewImageSize("Original");
                JSONArray resultsObj = replyJson.getJSONArray("result");
                Timber.d("--- setOriginalPostviewImageSizeThread response: " + replyJson);
            } catch (JSONException | IOException e) {
                e.printStackTrace();
            }
        }
    };

    /**
     * Button "Call Params"
     * @param view
     */
    public void onClickCallParams(View view) {
        new Thread() {
            @Override
            public void run() {
                try {
                    JSONObject replyJson = mRemoteApi.callParamsApi("getEvent", "1.0");
                    JSONArray resultsObj = replyJson.getJSONArray("result");
                    Timber.d("--- callParamsApi getEvent: " + replyJson);

                } catch (IOException e) {
                    Log.w(TAG, "IOException while closing slicer: " + e.getMessage());
                    DisplayHelper.toast(getApplicationContext(), //
                            R.string.msg_error_api_calling);
                } catch (JSONException e) {
                    Log.w(TAG, "JSONException while closing slicer");
                    DisplayHelper.toast(getApplicationContext(), //
                            R.string.msg_error_api_calling);
                } finally {
                    DisplayHelper.setProgressIndicator(SampleCameraActivity.this, false);
                }
            }
        }.start();
    }

    /**
     * Switch between 2M and Original Postview Image Size (button "Switch Size")
     * @param view
     */
    public void onClickSwitchpostviewimagesize(View view) {
        new Thread() {
            @Override
            public void run() {
                try {
                    if (TextUtils.isEmpty(mPostviewImageSize) || mPostviewImageSize.equals("2M")) {
                        mPostviewImageSize = "Original";
                    } else {
                        mPostviewImageSize = "2M";
                    }
                    JSONObject replyJson = mRemoteApi.setPostviewImageSize(mPostviewImageSize);
                    JSONArray resultsObj = replyJson.getJSONArray("result");
                    Timber.d("--- setPostviewImageSize response: " + replyJson);

                } catch (IOException e) {
                    Log.w(TAG, "IOException while closing slicer: " + e.getMessage());
                    DisplayHelper.toast(getApplicationContext(), //
                            R.string.msg_error_api_calling);
                } catch (JSONException e) {
                    Log.w(TAG, "JSONException while closing slicer");
                    DisplayHelper.toast(getApplicationContext(), //
                            R.string.msg_error_api_calling);
                } finally {
                    DisplayHelper.setProgressIndicator(SampleCameraActivity.this, false);
                }
            }
        }.start();
    }

    /**
     * Picasso Target that loads bitmap from server (camera) and writes it to file. After writing
     * it either starts the next image taking process or the collage creating process.
     */
    private Target targetWithNextThread = new Target() {
        @Override
        public void onBitmapLoaded(Bitmap bitmap, Picasso.LoadedFrom from) {
            final File file = new File(
                    getApplicationContext()
                            .getExternalFilesDir(Environment.DIRECTORY_PICTURES)
                            .getPath()
                            + "/" + mLastTakenPhotoName);
            Timber.d("--- targetWithNextThread file: " + file.getPath());
            try {
                file.createNewFile();
                FileOutputStream ostream = new FileOutputStream(file);
                bitmap.compress(Bitmap.CompressFormat.JPEG,100,ostream);
                ostream.close();
            }
            catch (Exception e) {
                e.printStackTrace();
            }

            takenPictureFilePathArrayList.add(file.getPath());
            picturesTakenCounter++;

            if (picturesTakenCounter < 4) {
                Timber.d("--- targetWithNextThread picturesTakenCounter < 4: " + picturesTakenCounter);
                countDownAndTakePicture();

            } else {
                Timber.d("--- targetWithNextThread picturesTakenCounter >= 4: " + picturesTakenCounter);
                createImageCollage();
                picturesTakenCounter = 0;
                takenPictureFilePathArrayList.clear();
            }
        }

        @Override
        public void onBitmapFailed(Drawable errorDrawable) {

        }

        @Override
        public void onPrepareLoad(Drawable placeHolderDrawable) {

        }
    };

    /**
     * Picasso target that opens the print activity. At the moment only used for single picture
     * taking purposes (and crashes, because PrintActivity starts differently now).
     */
    private Target targetWithOpenPrintActivity = new Target() {
        @Override
        public void onBitmapLoaded(final Bitmap bitmap, Picasso.LoadedFrom from) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    final File file = new File(
                            getApplicationContext()
                                    .getExternalFilesDir(Environment.DIRECTORY_PICTURES)
                                    .getPath()
                                    + "/" + mLastTakenPhotoName);
                    Timber.d("--- targetWithOpenPrintActivity file: " + file.getPath());
                    try {
                        file.createNewFile();
                        FileOutputStream ostream = new FileOutputStream(file);
                        bitmap.compress(Bitmap.CompressFormat.JPEG,100,ostream);
                        ostream.close();
                    }
                    catch (Exception e) {
                        e.printStackTrace();
                    }

                    // TODO start activity from main thread
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Intent intent = new Intent(SampleCameraActivity.this, PrintActivity.class);
                            intent.putExtra("IMAGEFILENAME", file.getPath());
                            startActivity(intent);
                        }
                    });
                }
            }).start();
        }

        @Override
        public void onBitmapFailed(Drawable errorDrawable) {}

        @Override
        public void onPrepareLoad(Drawable placeHolderDrawable) {}
    };

    // region Set & Get SharedPrefs Strings --------------------------------------------------------
    private void setCorrectPicturesLeftTextview() {
        sharedPreferences = getSharedPreferences(getString(R.string.preference_file_key),
                Context.MODE_PRIVATE);
        int numberPicturesPrinted = sharedPreferences.getInt(numberPicturesPrintedPrefsString,
                0);
        if (Constants.AMOUNT_MAX_PICTURES_IN_PRINTER - numberPicturesPrinted > 0) {
            xyPicturesLeftTextview.setText("Noch " +
                    (Constants.AMOUNT_MAX_PICTURES_IN_PRINTER - numberPicturesPrinted) +
                    " Bilder im Drucker");
        } else {
            xyPicturesLeftTextview.setText(R.string.bitte_fotopapier_nachlegen_);
        }
    }

    private void setDrawHeartInMiddlePrefs(boolean draw) {
        sharedPreferences = getSharedPreferences(getString(R.string.preference_file_key),
                Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean(drawHeartInMiddlePrefsString, draw);
        editor.apply();
        String heartStatusToast = draw ? "Drawing heart in the middle" : "Not drawing heart in middle";
        Toast.makeText(this, heartStatusToast, Toast.LENGTH_SHORT)
                .show();
    }

    private boolean getDrawHeartInMiddleFromPrefs() {
        sharedPreferences = getSharedPreferences(getString(R.string.preference_file_key),
                Context.MODE_PRIVATE);
        return sharedPreferences.getBoolean(drawHeartInMiddlePrefsString, false);
    }
    // endregion Set & Get SharedPrefs Strings -----------------------------------------------------

    // region Secret Menu --------------------------------------------------------------------------
    int secretMenuClicks = 0;
    Handler mainThreadHandler = new Handler();

    Runnable resetSecretMenuClicksRunnable = new Runnable() {
        @Override
        public void run() {
            secretMenuClicks = 0;
        }
    };

    public void onClickSecretMenuButton(View view) {
        secretMenuClicks++;
        if (secretMenuClicks >= 10) {
            secretMenuClicks = 0;
            showSecretMenu();
        }
        mainThreadHandler.removeCallbacks(resetSecretMenuClicksRunnable);
        mainThreadHandler.postDelayed(resetSecretMenuClicksRunnable, 300);
    }

    private void showSecretMenu() {
        boolean drawHeartInMiddle = getDrawHeartInMiddleFromPrefs();
        String drawHeartItem;
        if (drawHeartInMiddle) {
            drawHeartItem = "Drawing heart in middle -> select to not draw";
        } else {
            drawHeartItem = "Not drawing heart in middle -> select to draw";
        }

        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(this);

        CharSequence[] secretMenuItems = {"Reset Number Pictures Printed",
                "Set Pictures printed to 18", // TODO make user configurable
                drawHeartItem,
                "Finish SampleCameraActivity"}; // better would be "switch WiFi"

        alertDialogBuilder.setTitle("Secret Menu")
                .setItems(secretMenuItems, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        // The 'which' argument contains the index position
                        // of the selected item
                        if (which == 0) {
                            resetNumberPicturesPrinted();
                        } else if (which == 1) {
                            SharedPreferences.Editor editor = sharedPreferences.edit();
                            editor.putInt(numberPicturesPrintedPrefsString, 18);
                            editor.apply();
                            setCorrectPicturesLeftTextview();
                        } else if (which == 2) {
                            setDrawHeartInMiddlePrefs(!getDrawHeartInMiddleFromPrefs());
                        } else if (which == 3) {
                            finish();
                        }
                    }
                });

        AlertDialog alertDialog = alertDialogBuilder.create();
        alertDialog.getWindow().setFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);
        alertDialog.show();
        alertDialog.getWindow().getDecorView().setSystemUiVisibility(
                getWindow().getDecorView().getSystemUiVisibility());
        alertDialog.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);
    }

    private void showPicturesInPrinterInput() {
        // TODO
    }

    private void resetNumberPicturesPrinted() {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putInt(numberPicturesPrintedPrefsString, 0);
        editor.apply();
        setCorrectPicturesLeftTextview();
    }
    // endregion Secret Menu -----------------------------------------------------------------------

    // code below is not used for photo box --------------------------------------------------------
    private void prepareOpenConnection() {
        Log.d(TAG, "prepareToOpenConection() exec");

        setProgressBarIndeterminateVisibility(true);

        new Thread() {

            @Override
            public void run() {
                try {
                    // Get supported API list (Camera API)
                    JSONObject replyJsonCamera = mRemoteApi.getCameraMethodTypes();
                    loadSupportedApiList(replyJsonCamera);
                    Timber.d("--- " + replyJsonCamera);

                    try {
                        // Get supported API list (AvContent API)
                        JSONObject replyJsonAvcontent = mRemoteApi.getAvcontentMethodTypes();
                        loadSupportedApiList(replyJsonAvcontent);
                    } catch (IOException e) {
                        Log.d(TAG, "AvContent is not support.");
                    }

                    SampleApplication app = (SampleApplication) getApplication();
                    app.setSupportedApiList(mSupportedApiSet);

                    if (!isApiSupported("setCameraFunction")) {

                        // this device does not support setCameraFunction.
                        // No need to check camera status.

                        openConnection();

                    } else {

                        // this device supports setCameraFunction.
                        // after confirmation of camera state, open connection.
                        Log.d(TAG, "this device support set camera function");

                        if (!isApiSupported("getEvent")) {
                            Log.e(TAG, "this device is not support getEvent");
                            openConnection();
                            return;
                        }

                        // confirm current camera status
                        String cameraStatus = null;
                        JSONObject replyJson = mRemoteApi.getEvent(false);
                        JSONArray resultsObj = replyJson.getJSONArray("result");
                        JSONObject cameraStatusObj = resultsObj.getJSONObject(1);
                        String type = cameraStatusObj.getString("type");
                        if ("cameraStatus".equals(type)) {
                            cameraStatus = cameraStatusObj.getString("cameraStatus");
                        } else {
                            throw new IOException();
                        }

                        if (isShootingStatus(cameraStatus)) {
                            Log.d(TAG, "camera function is Remote Shooting.");
                            openConnection();
                        } else {
                            // set Listener
                            startOpenConnectionAfterChangeCameraState();

                            // set Camera function to Remote Shooting
                            replyJson = mRemoteApi.setCameraFunction("Remote Shooting");
                        }
                    }
                } catch (IOException e) {
                    Log.w(TAG, "prepareToStartContentsListMode: IOException: " + e.getMessage());
                    DisplayHelper.toast(getApplicationContext(), R.string.msg_error_api_calling);
                    DisplayHelper.setProgressIndicator(SampleCameraActivity.this, false);
                } catch (JSONException e) {
                    Log.w(TAG, "prepareToStartContentsListMode: JSONException: " + e.getMessage());
                    DisplayHelper.toast(getApplicationContext(), R.string.msg_error_api_calling);
                    DisplayHelper.setProgressIndicator(SampleCameraActivity.this, false);
                }
            }
        }.start();
    }

    private static boolean isShootingStatus(String currentStatus) {
        Set<String> shootingStatus = new HashSet<String>();
        shootingStatus.add("IDLE");
        shootingStatus.add("NotReady");
        shootingStatus.add("StillCapturing");
        shootingStatus.add("StillSaving");
        shootingStatus.add("MovieWaitRecStart");
        shootingStatus.add("MovieRecording");
        shootingStatus.add("MovieWaitRecStop");
        shootingStatus.add("MovieSaving");
        shootingStatus.add("IntervalWaitRecStart");
        shootingStatus.add("IntervalRecording");
        shootingStatus.add("IntervalWaitRecStop");
        shootingStatus.add("AudioWaitRecStart");
        shootingStatus.add("AudioRecording");
        shootingStatus.add("AudioWaitRecStop");
        shootingStatus.add("AudioSaving");

        return shootingStatus.contains(currentStatus);
    }

    private void startOpenConnectionAfterChangeCameraState() {
        Log.d(TAG, "startOpenConectiontAfterChangeCameraState() exec");

        runOnUiThread(new Runnable() {

            @Override
            public void run() {
                mEventObserver
                        .setEventChangeListener(new SimpleCameraEventObserver.ChangeListenerTmpl() {

                            @Override
                            public void onCameraStatusChanged(String status) {
                                Log.d(TAG, "onCameraStatusChanged:" + status);
                                if ("IDLE".equals(status) || "NotReady".equals(status)) {
                                    openConnection();
                                }
                                refreshUi();
                            }

                            @Override
                            public void onShootModeChanged(String shootMode) {
                                refreshUi();
                            }

                            @Override
                            public void onStorageIdChanged(String storageId) {
                                refreshUi();
                            }
                        });

                mEventObserver.start();
            }
        });
    }

    /**
     * Open connection to the camera device to start monitoring Camera events
     * and showing liveview.
     */
    private void openConnection() {

        mEventObserver.setEventChangeListener(mEventListener);
        new Thread() {

            @Override
            public void run() {
                Log.d(TAG, "openConnection(): exec.");

                try {
                    JSONObject replyJson = null;

                    // getAvailableApiList
                    replyJson = mRemoteApi.getAvailableApiList();
                    loadAvailableCameraApiList(replyJson);

                    // check version of the server device
                    if (isCameraApiAvailable("getApplicationInfo")) {
                        Log.d(TAG, "openConnection(): getApplicationInfo()");
                        replyJson = mRemoteApi.getApplicationInfo();
                        if (!isSupportedServerVersion(replyJson)) {
                            DisplayHelper.toast(getApplicationContext(), //
                                    R.string.msg_error_non_supported_device);
                            SampleCameraActivity.this.finish();
                            return;
                        }
                    } else {
                        // never happens;
                        return;
                    }

                    // startRecMode if necessary.
                    if (isCameraApiAvailable("startRecMode")) {
                        Log.d(TAG, "openConnection(): startRecMode()");
                        replyJson = mRemoteApi.startRecMode();

                        // Call again.
                        replyJson = mRemoteApi.getAvailableApiList();
                        loadAvailableCameraApiList(replyJson);
                    }

                    // getEvent start
                    if (isCameraApiAvailable("getEvent")) {
                        Log.d(TAG, "openConnection(): EventObserver.start()");
                        mEventObserver.start();
                    }

                    // Liveview start
                    if (isCameraApiAvailable("startLiveview")) {
                        Log.d(TAG, "openConnection(): LiveviewSurface.start()");
                        startLiveview();
                    }

                    // prepare UIs
                    if (isCameraApiAvailable("getAvailableShootMode")) {
                        Log.d(TAG, "openConnection(): prepareShootModeSpinner()");
                        prepareShootModeSpinner();
                        // Note: hide progress bar on title after this calling.
                    }

                    // prepare UIs
                    if (isCameraApiAvailable("actZoom")) {
                        Log.d(TAG, "openConnection(): prepareActZoomButtons()");
                        prepareActZoomButtons(true);
                    } else {
                        prepareActZoomButtons(false);
                    }

                    Log.d(TAG, "openConnection(): completed.");
                } catch (IOException e) {
                    Log.w(TAG, "openConnection : IOException: " + e.getMessage());
                    DisplayHelper.setProgressIndicator(SampleCameraActivity.this, false);
                    DisplayHelper.toast(getApplicationContext(), R.string.msg_error_connection);
                }
            }
        }.start();

    }

    /**
     * Stop monitoring Camera events and close liveview connection.
     */
    private void closeConnection() {

        Log.d(TAG, "closeConnection(): exec.");
        // Liveview stop
        Log.d(TAG, "closeConnection(): LiveviewSurface.stop()");
        if (mLiveviewSurface != null) {
            mLiveviewSurface.stop();
            mLiveviewSurface = null;
            stopLiveview();
        }

        // getEvent stop
        Log.d(TAG, "closeConnection(): EventObserver.release()");
        mEventObserver.release();

        Log.d(TAG, "closeConnection(): completed.");
    }

    /**
     * Refresh UI appearance along with current "cameraStatus" and "shootMode".
     */
    private void refreshUi() {
        String cameraStatus = mEventObserver.getCameraStatus();
        String shootMode = mEventObserver.getShootMode();
        List<String> availableShootModes = mEventObserver.getAvailableShootModes();

        // CameraStatus TextView
        mTextCameraStatus.setText(cameraStatus);

        // Recording Start/Stop Button
        if ("MovieRecording".equals(cameraStatus)) {
            mButtonRecStartStop.setEnabled(true);
            mButtonRecStartStop.setText(R.string.button_rec_stop);
        } else if ("IDLE".equals(cameraStatus) && "movie".equals(shootMode)) {
            mButtonRecStartStop.setEnabled(true);
            mButtonRecStartStop.setText(R.string.button_rec_start);
        } else {
            mButtonRecStartStop.setEnabled(false);
        }

        // Take picture Button
        if ("still".equals(shootMode) && "IDLE".equals(cameraStatus)) {
            mButtonTakePicture.setEnabled(true);
        } else {
            mButtonTakePicture.setEnabled(false);
        }

        // Picture wipe Image
        if (!"still".equals(shootMode)) {
            mImagePictureWipe.setVisibility(View.INVISIBLE);
        }

        // Update Shoot Modes List
        ArrayAdapter<String> adapter = (ArrayAdapter<String>) mSpinnerShootMode.getAdapter();
        if (adapter != null) {
            adapter.clear();
            for (String mode : availableShootModes) {
                if (isSupportedShootMode(mode)) {
                    adapter.add(mode);
                }
            }
            selectionShootModeSpinner(mSpinnerShootMode, shootMode);
        }

        // Shoot Mode Buttons
        if ("IDLE".equals(cameraStatus)) {
            mSpinnerShootMode.setEnabled(true);
        } else {
            mSpinnerShootMode.setEnabled(false);
        }

        // Contents List Button
        if (isApiSupported("getContentList") //
                && isApiSupported("getSchemeList") //
                && isApiSupported("getSourceList")) {
            String storageId = mEventObserver.getStorageId();
            if (storageId == null) {
                Log.d(TAG, "not update ContentsList button ");
            } else if ("No Media".equals(storageId)) {
                mButtonContentsListMode.setEnabled(false);
            } else {
                mButtonContentsListMode.setEnabled(true);
            }
        }
    }

    /**
     * Retrieve a list of APIs that are available at present.
     * 
     * @param replyJson
     */
    private void loadAvailableCameraApiList(JSONObject replyJson) {
        synchronized (mAvailableCameraApiSet) {
            mAvailableCameraApiSet.clear();
            try {
                JSONArray resultArrayJson = replyJson.getJSONArray("result");
                JSONArray apiListJson = resultArrayJson.getJSONArray(0);
                for (int i = 0; i < apiListJson.length(); i++) {
                    mAvailableCameraApiSet.add(apiListJson.getString(i));
                }
            } catch (JSONException e) {
                Log.w(TAG, "loadAvailableCameraApiList: JSON format error.");
            }
        }
    }

    /**
     * Retrieve a list of APIs that are supported by the targetWithOpenPrintActivity device.
     * 
     * @param replyJson
     */
    private void loadSupportedApiList(JSONObject replyJson) {
        synchronized (mSupportedApiSet) {
            try {
                JSONArray resultArrayJson = replyJson.getJSONArray("results");
                for (int i = 0; i < resultArrayJson.length(); i++) {
                    mSupportedApiSet.add(resultArrayJson.getJSONArray(i).getString(0));
                }
            } catch (JSONException e) {
                Log.w(TAG, "loadSupportedApiList: JSON format error.");
            }
        }
    }

    /**
     * Check if the specified API is available at present. This works correctly
     * only for Camera API.
     * 
     * @param apiName
     * @return
     */
    private boolean isCameraApiAvailable(String apiName) {
        boolean isAvailable = false;
        synchronized (mAvailableCameraApiSet) {
            isAvailable = mAvailableCameraApiSet.contains(apiName);
        }
        return isAvailable;
    }

    /**
     * Check if the specified API is supported. This is for camera and avContent
     * service API. The result of this method does not change dynamically.
     * 
     * @param apiName
     * @return
     */
    private boolean isApiSupported(String apiName) {
        boolean isAvailable = false;
        synchronized (mSupportedApiSet) {
            isAvailable = mSupportedApiSet.contains(apiName);
        }
        return isAvailable;
    }

    /**
     * Check if the version of the server is supported in this application.
     * 
     * @param replyJson
     * @return
     */
    private boolean isSupportedServerVersion(JSONObject replyJson) {
        try {
            JSONArray resultArrayJson = replyJson.getJSONArray("result");
            String version = resultArrayJson.getString(1);
            String[] separated = version.split("\\.");
            int major = Integer.valueOf(separated[0]);
            if (2 <= major) {
                return true;
            }
        } catch (JSONException e) {
            Log.w(TAG, "isSupportedServerVersion: JSON format error.");
        } catch (NumberFormatException e) {
            Log.w(TAG, "isSupportedServerVersion: Number format error.");
        }
        return false;
    }

    /**
     * Check if the shoot mode is supported in this application.
     * 
     * @param mode
     * @return
     */
    private boolean isSupportedShootMode(String mode) {
        if ("still".equals(mode) || "movie".equals(mode)) {
            return true;
        }
        return false;
    }

    /**
     * Prepare for Spinner to select "shootMode" by user.
     */
    private void prepareShootModeSpinner() {
        new Thread() {

            @Override
            public void run() {
                Log.d(TAG, "prepareShootModeSpinner(): exec.");
                JSONObject replyJson = null;
                try {
                    replyJson = mRemoteApi.getAvailableShootMode();

                    JSONArray resultsObj = replyJson.getJSONArray("result");
                    final String currentMode = resultsObj.getString(0);
                    JSONArray availableModesJson = resultsObj.getJSONArray(1);
                    final List<String> availableModes = new ArrayList<String>();

                    for (int i = 0; i < availableModesJson.length(); i++) {
                        String mode = availableModesJson.getString(i);
                        if (!isSupportedShootMode(mode)) {
                            mode = "";
                        }
                        availableModes.add(mode);
                    }
                    runOnUiThread(new Runnable() {

                        @Override
                        public void run() {
                            prepareShootModeSpinnerUi(//
                                    availableModes.toArray(new String[0]), currentMode);
                            // Hide progress indeterminately on title bar.
                            setProgressBarIndeterminateVisibility(false);
                        }
                    });
                } catch (IOException e) {
                    Log.w(TAG, "prepareShootModeRadioButtons: IOException: " + e.getMessage());
                } catch (JSONException e) {
                    Log.w(TAG, "prepareShootModeRadioButtons: JSON format error.");
                }
            };
        }.start();
    }

    /**
     * Selection for Spinner UI of Shoot Mode.
     * 
     * @param spinner
     * @param mode
     */
    private void selectionShootModeSpinner(Spinner spinner, String mode) {
        if (!isSupportedShootMode(mode)) {
            mode = "";
        }
        @SuppressWarnings("unchecked")
        ArrayAdapter<String> adapter = (ArrayAdapter<String>) spinner.getAdapter();
        if (adapter != null) {
            mSpinnerShootMode.setSelection(adapter.getPosition(mode));
        }
    }

    /**
     * Prepare for Spinner UI of Shoot Mode.
     * 
     * @param availableShootModes
     * @param currentMode
     */
    private void prepareShootModeSpinnerUi(String[] availableShootModes, String currentMode) {

        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, //
                android.R.layout.simple_spinner_item);
        for (String mode : availableShootModes) {
            adapter.add(mode);
        }
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mSpinnerShootMode.setAdapter(adapter);
        mSpinnerShootMode.setPrompt(getString(R.string.prompt_shoot_mode));
        selectionShootModeSpinner(mSpinnerShootMode, currentMode);
        mSpinnerShootMode.setOnItemSelectedListener(new OnItemSelectedListener() {
            // selected Spinner dropdown item
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                Spinner spinner = (Spinner) parent;
                if (!spinner.isFocusable()) {
                    // ignored the first call, because shoot mode has not
                    // changed
                    spinner.setFocusable(true);
                } else {
                    String mode = spinner.getSelectedItem().toString();
                    String currentMode = mEventObserver.getShootMode();
                    if (mode.isEmpty()) {
                        DisplayHelper.toast(getApplicationContext(), //
                                R.string.msg_error_no_supported_shootmode);
                        // now state that can not be changed
                        selectionShootModeSpinner(spinner, currentMode);
                    } else {
                        if ("IDLE".equals(mEventObserver.getCameraStatus()) //
                                && !mode.equals(currentMode)) {
                            setShootMode(mode);
                        } else {
                            // now state that can not be changed
                            selectionShootModeSpinner(spinner, currentMode);
                        }
                    }
                }
            }

            // not selected Spinner dropdown item
            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

    }

    /**
     * Prepare for Button to select "actZoom" by user.
     * 
     * @param flag
     */
    private void prepareActZoomButtons(final boolean flag) {
        Log.d(TAG, "prepareActZoomButtons(): exec.");
        runOnUiThread(new Runnable() {

            @Override
            public void run() {
                prepareActZoomButtonsUi(flag);
            }
        });

    }

    /**
     * Prepare for ActZoom Button UI.
     * 
     * @param flag
     */
    private void prepareActZoomButtonsUi(boolean flag) {
        // I added zoomButtonsAllowedToBeShown to not show them when I don't want to have them on screen
        if (flag && zoomButtonsAllowedToBeShown) {
            mButtonZoomOut.setVisibility(View.VISIBLE);
            mButtonZoomIn.setVisibility(View.VISIBLE);
        } else {
            mButtonZoomOut.setVisibility(View.GONE);
            mButtonZoomIn.setVisibility(View.GONE);
        }
    }

    /**
     * Call setShootMode
     * 
     * @param mode
     */
    private void setShootMode(final String mode) {
        new Thread() {

            @Override
            public void run() {
                try {
                    JSONObject replyJson = mRemoteApi.setShootMode(mode);
                    JSONArray resultsObj = replyJson.getJSONArray("result");
                    int resultCode = resultsObj.getInt(0);
                    if (resultCode == 0) {
                        // Success, but no refresh UI at the point.
                        Log.v(TAG, "setShootMode: success.");
                    } else {
                        Log.w(TAG, "setShootMode: error: " + resultCode);
                        DisplayHelper.toast(getApplicationContext(), //
                                R.string.msg_error_api_calling);
                    }
                } catch (IOException e) {
                    Log.w(TAG, "setShootMode: IOException: " + e.getMessage());
                } catch (JSONException e) {
                    Log.w(TAG, "setShootMode: JSON format error.");
                }
            }
        }.start();
    }

    /**
     * Take a picture and retrieve the image data.
     */
    private void takeAndFetchPicture() {
        if (mLiveviewSurface == null || !mLiveviewSurface.isStarted()) {
            DisplayHelper.toast(getApplicationContext(), R.string.msg_error_take_picture);
            return;
        }

        new Thread() {

            @Override
            public void run() {
                try {
                    JSONObject replyJson = mRemoteApi.actTakePicture();
                    JSONArray resultsObj = replyJson.getJSONArray("result");
                    JSONArray imageUrlsObj = resultsObj.getJSONArray(0);
                    String postImageUrl = null;
                    if (1 <= imageUrlsObj.length()) {
                        postImageUrl = imageUrlsObj.getString(0);
                    }
                    if (postImageUrl == null) {
                        Log.w(TAG, "takeAndFetchPicture: post image URL is null.");
                        DisplayHelper.toast(getApplicationContext(), //
                                R.string.msg_error_take_picture);
                        return;
                    }
                    // Show progress indicator
                    DisplayHelper.setProgressIndicator(SampleCameraActivity.this, true);

                    URL url = new URL(postImageUrl);
                    mOpenLastUrl = postImageUrl;
                    Timber.d("--- takeAndFetchPicture mOpenLastUrl: " + mOpenLastUrl + " and " + url);

                    String path = url.getPath();
                    mLastTakenPhotoName = path.substring(path.lastIndexOf('/') + 1);
                    Timber.d("--- takeAndFetchPicture mLastTakenPhotoName: " + mLastTakenPhotoName);

                    InputStream istream = new BufferedInputStream(url.openStream());
                    BitmapFactory.Options options = new BitmapFactory.Options();
                    options.inSampleSize = 4; // irresponsible value
                    final Drawable pictureDrawable =
                            new BitmapDrawable(getResources(), //
                                    BitmapFactory.decodeStream(istream, null, options));
                    istream.close();
                    runOnUiThread(new Runnable() {

                        @Override
                        public void run() {
                            mImagePictureWipe.setVisibility(View.VISIBLE);
                            mImagePictureWipe.setImageDrawable(pictureDrawable);

                            Picasso.with(SampleCameraActivity.this).load(mOpenLastUrl).into(targetWithOpenPrintActivity);
                        }
                    });

                } catch (IOException e) {
                    Log.w(TAG, "IOException while closing slicer: " + e.getMessage());
                    DisplayHelper.toast(getApplicationContext(), //
                            R.string.msg_error_take_picture);
                } catch (JSONException e) {
                    Log.w(TAG, "JSONException while closing slicer");
                    DisplayHelper.toast(getApplicationContext(), //
                            R.string.msg_error_take_picture);
                } finally {
                    DisplayHelper.setProgressIndicator(SampleCameraActivity.this, false);
                }
            }
        }.start();
    }

    /**
     * Call startMovieRec
     */
    private void startMovieRec() {
        new Thread() {

            @Override
            public void run() {
                try {
                    Log.d(TAG, "startMovieRec: exec.");
                    JSONObject replyJson = mRemoteApi.startMovieRec();
                    JSONArray resultsObj = replyJson.getJSONArray("result");
                    int resultCode = resultsObj.getInt(0);
                    if (resultCode == 0) {
                        DisplayHelper.toast(getApplicationContext(), R.string.msg_rec_start);
                    } else {
                        Log.w(TAG, "startMovieRec: error: " + resultCode);
                        DisplayHelper.toast(getApplicationContext(), //
                                R.string.msg_error_api_calling);
                    }
                } catch (IOException e) {
                    Log.w(TAG, "startMovieRec: IOException: " + e.getMessage());
                } catch (JSONException e) {
                    Log.w(TAG, "startMovieRec: JSON format error.");
                }
            }
        }.start();
    }

    /**
     * Call stopMovieRec
     */
    private void stopMovieRec() {
        new Thread() {

            @Override
            public void run() {
                try {
                    Log.d(TAG, "stopMovieRec: exec.");
                    JSONObject replyJson = mRemoteApi.stopMovieRec();
                    JSONArray resultsObj = replyJson.getJSONArray("result");
                    String thumbnailUrl = resultsObj.getString(0);
                    if (thumbnailUrl != null) {
                        DisplayHelper.toast(getApplicationContext(), R.string.msg_rec_stop);
                    } else {
                        Log.w(TAG, "stopMovieRec: error");
                        DisplayHelper.toast(getApplicationContext(), //
                                R.string.msg_error_api_calling);
                    }
                } catch (IOException e) {
                    Log.w(TAG, "stopMovieRec: IOException: " + e.getMessage());
                } catch (JSONException e) {
                    Log.w(TAG, "stopMovieRec: JSON format error.");
                }
            }
        }.start();
    }

    /**
     * Call actZoom
     * 
     * @param direction
     * @param movement
     */
    private void actZoom(final String direction, final String movement) {
        new Thread() {

            @Override
            public void run() {
                try {
                    JSONObject replyJson = mRemoteApi.actZoom(direction, movement);
                    JSONArray resultsObj = replyJson.getJSONArray("result");
                    int resultCode = resultsObj.getInt(0);
                    if (resultCode == 0) {
                        // Success, but no refresh UI at the point.
                        Log.v(TAG, "actZoom: success");
                    } else {
                        Log.w(TAG, "actZoom: error: " + resultCode);
                        DisplayHelper.toast(getApplicationContext(), //
                                R.string.msg_error_api_calling);
                    }
                } catch (IOException e) {
                    Log.w(TAG, "actZoom: IOException: " + e.getMessage());
                } catch (JSONException e) {
                    Log.w(TAG, "actZoom: JSON format error.");
                }
            }
        }.start();
    }

    private void prepareToStartContentsListMode() {
        Log.d(TAG, "prepareToStartContentsListMode() exec");
        new Thread() {

            @Override
            public void run() {
                try {
                    // set Listener
                    moveToDateListAfterChangeCameraState();

                    // set camera function to Contents Transfer
                    Log.d(TAG, "call setCameraFunction");
                    JSONObject replyJson = mRemoteApi.setCameraFunction("Contents Transfer");
                    if (SimpleRemoteApi.isErrorReply(replyJson)) {
                        Log.w(TAG, "prepareToStartContentsListMode: set CameraFunction error: ");
                        DisplayHelper.toast(getApplicationContext(), R.string.msg_error_content);
                        mEventObserver.setEventChangeListener(mEventListener);
                    }

                } catch (IOException e) {
                    Log.w(TAG, "prepareToStartContentsListMode: IOException: " + e.getMessage());
                }
            }
        }.start();

    }

    private void moveToDateListAfterChangeCameraState() {
        Log.d(TAG, "moveToDateListAfterChangeCameraState() exec");

        // set Listener
        mEventObserver.setEventChangeListener(new SimpleCameraEventObserver.ChangeListenerTmpl() {

            @Override
            public void onCameraStatusChanged(String status) {
                Log.d(TAG, "onCameraStatusChanged:" + status);
                if ("ContentsTransfer".equals(status)) {
                    // start ContentsList mode
                    Intent intent = new Intent(getApplicationContext(), DateListActivity.class);
                    startActivity(intent);
                }

                refreshUi();
            }

            @Override
            public void onShootModeChanged(String shootMode) {
                refreshUi();
            }
        });
    }

    private void startLiveview() {
        if (mLiveviewSurface == null) {
            Log.w(TAG, "startLiveview mLiveviewSurface is null.");
            return;
        }
        new Thread() {
            @Override
            public void run() {

                try {
                    JSONObject replyJson = null;
                    replyJson = mRemoteApi.startLiveview();

                    if (!SimpleRemoteApi.isErrorReply(replyJson)) {
                        JSONArray resultsObj = replyJson.getJSONArray("result");
                        if (1 <= resultsObj.length()) {
                            // Obtain liveview URL from the result.
                            final String liveviewUrl = resultsObj.getString(0);
                            runOnUiThread(new Runnable() {

                                @Override
                                public void run() {
                                    mLiveviewSurface.start(liveviewUrl, //
                                            new SimpleStreamSurfaceView.StreamErrorListener() {

                                                @Override
                                                public void onError(StreamErrorReason reason) {
                                                    stopLiveview();
                                                }
                                            });
                                }
                            });
                        }
                    }
                } catch (IOException e) {
                    Log.w(TAG, "startLiveview IOException: " + e.getMessage());
                } catch (JSONException e) {
                    Log.w(TAG, "startLiveview JSONException: " + e.getMessage());
                }
            }
        }.start();
    }

    private void stopLiveview() {
        new Thread() {
            @Override
            public void run() {
                try {
                    mRemoteApi.stopLiveview();
                } catch (IOException e) {
                    Log.w(TAG, "stopLiveview IOException: " + e.getMessage());
                }
            }
        }.start();
    }

}
