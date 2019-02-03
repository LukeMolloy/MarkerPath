/*
 * Copyright 2018 Google LLC. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.ar.sceneform.samples.markerpath;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.Build.VERSION_CODES;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import com.google.ar.core.Anchor;
import com.google.ar.core.AugmentedImage;
import com.google.ar.core.AugmentedImageDatabase;
import com.google.ar.core.Config;
import com.google.ar.core.Frame;
import com.google.ar.core.Pose;
import com.google.ar.core.Session;
import com.google.ar.core.TrackingState;
import com.google.ar.core.exceptions.UnavailableApkTooOldException;
import com.google.ar.core.exceptions.UnavailableArcoreNotInstalledException;
import com.google.ar.core.exceptions.UnavailableSdkTooOldException;
import com.google.ar.sceneform.AnchorNode;
import com.google.ar.sceneform.ArSceneView;
import com.google.ar.sceneform.FrameTime;
import com.google.ar.sceneform.Node;
import com.google.ar.sceneform.math.Quaternion;
import com.google.ar.sceneform.math.Vector3;
import com.google.ar.sceneform.rendering.MaterialFactory;
import com.google.ar.sceneform.rendering.ModelRenderable;
import com.google.ar.sceneform.rendering.Renderable;
import com.google.ar.sceneform.rendering.ShapeFactory;
import com.google.ar.sceneform.rendering.ViewRenderable;
import com.google.ar.sceneform.ux.ArFragment;
import com.google.ar.sceneform.ux.TransformableNode;

import org.opencv.android.OpenCVLoader;
import org.opencv.aruco.Dictionary;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;

/**
 * This is an example activity that uses the Sceneform UX package to make common AR tasks easier.
 */
public class mainactivity extends AppCompatActivity {
  private static final String TAG = mainactivity.class.getSimpleName();
  private static final double MIN_OPENGL_VERSION = 3.0;

  private ArFragment arFragment;
  private boolean sessionConfigured = false;
  private ArSceneView arSceneView;
  LinearLayout scanningMessage;
  Session mSession;
  private ModelRenderable andyRenderable, lineRenderable, sphereRenderable;
  private ViewRenderable fireEscape;
  private AnchorNode anchorNode;
  DatabaseHelper myDB;
  int averageIterator = 0;
  float[] averageTranslation = new float[3];
  float[] averageQuaternion = new float[4];
  boolean augmentedImageFound = true;
  boolean laypath = false;
//  Button btnLayPath;
  Dictionary dictionary;
  AugmentedImageDatabase imageDatabase;

  TextView Pos;

    static {
        if (!OpenCVLoader.initDebug()) {
            // Handle initialization error
        }
    }

        @Override
  @SuppressWarnings({"AndroidApiChecker", "FutureReturnValueIgnored"})
  // CompletableFuture requires api level 24
  // FutureReturnValueIgnored is not valid
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    if (!checkIsSupportedDeviceOrFinish(this)) {
      return;
    }

    setContentView(R.layout.activity_ux);

    myDB = new DatabaseHelper(this);

    arFragment = (ArFragment) getSupportFragmentManager().findFragmentById(R.id.ux_fragment);
    arFragment.getPlaneDiscoveryController().hide();
    arFragment.getPlaneDiscoveryController().setInstructionView(null);

//    btnLayPath = findViewById(R.id.btnLayPath);
    scanningMessage = findViewById(R.id.marker_scan);
    scanningMessage.setVisibility(View.GONE);

            // When you build a Renderable, Sceneform loads its resources in the background while returning
    // a CompletableFuture. Call thenAccept(), handle(), or check isDone() before calling get().
    ModelRenderable.builder()
        .setSource(this, R.raw.andy)
        .build()
        .thenAccept(renderable -> andyRenderable = renderable)
        .exceptionally(
            throwable -> {
              Toast toast =
                  Toast.makeText(this, "Unable to load andy renderable", Toast.LENGTH_LONG);
              toast.setGravity(Gravity.CENTER, 0, 0);
              toast.show();
              return null;
            });

    ViewRenderable.builder()
            .setView(this, R.layout.planet_card_view)
            .build()
            .thenAccept(r -> {
                this.fireEscape = r;
                fireEscape.setShadowCaster(false);
                fireEscape.setShadowReceiver(false);
            });

//    ModelRenderable.builder()
//            .setSource(this, R.raw.model)
//            .build()
//            .thenAccept(renderable -> arrowRenderable = renderable)
//            .exceptionally(
//                    throwable -> {
//                        Toast toast =
//                                Toast.makeText(this, "Unable to load arrow renderable", Toast.LENGTH_LONG);
//                        toast.setGravity(Gravity.CENTER, 0, 0);
//                        toast.show();
//                        return null;
//                    });
    MaterialFactory.makeOpaqueWithColor(this, new com.google.ar.sceneform.rendering.Color(0,100,0))
            .thenAccept(
                    material -> sphereRenderable = ShapeFactory.makeSphere(0.07f, new Vector3(.0f, .0f, .0f), material));

//    MaterialFactory.makeOpaqueWithColor(this, new com.google.ar.sceneform.rendering.Color(100,100,100))
//            .thenAccept(
//                    material -> lineRenderable = ShapeFactory.makeCube(new Vector3(.01f, .01f, 0.5f),
//                            Vector3.zero(), material));
      arFragment.getArSceneView().getScene().setOnUpdateListener(this::onSceneUpdate);
      arSceneView= arFragment.getArSceneView();
  }

    private void onSceneUpdate(FrameTime frameTime) {
        // Let the fragment update its state first.
        arFragment.onUpdate(frameTime);
        Frame frame = arFragment.getArSceneView().getArFrame();
        Collection<AugmentedImage> augmentedImages = frame.getUpdatedTrackables(AugmentedImage.class);

        for (AugmentedImage augmentedImage : augmentedImages){
            if (augmentedImage.getTrackingState() == TrackingState.TRACKING){

                if (augmentedImage.getName().contains("marker")) {
//                    andyString(4,augmentedImage.getCenterPose());

                    if (augmentedImageFound) {
                        Anchor imageAnchor = augmentedImage.createAnchor(augmentedImage.getCenterPose());
                        if (averageIterator == 5) {
                            scanningMessage.setVisibility(View.GONE);
                            float[] oldPosition = {0f, 0f, 0f};
                            loadPathV2(imageAnchor);
                            augmentedImageFound = false;
                            Toast.makeText(this, "Find your path", Toast.LENGTH_LONG).show();
                        } else if (averageIterator <= 5) {
                            scanningMessage.setVisibility(View.VISIBLE);
                            // Keeps a running average of the detected image's translation
                            for (int i = 0; i < augmentedImage.getCenterPose().getTranslation().length; ) {
                                averageTranslation[i] = (averageTranslation[i] + augmentedImage.getCenterPose().getTranslation()[i]);
                                i++;
                            }
                            // Keeps a running average of the detected image's rotation quaternion
                            for (int i = 0; i < augmentedImage.getCenterPose().getRotationQuaternion().length; ) {
                                averageQuaternion[i] = (averageQuaternion[i] + augmentedImage.getCenterPose().getRotationQuaternion()[i]);
                                i++;
                            }


                        }
                        averageIterator = averageIterator + 1;
                    } else if (augmentedImage.getName().contains("code")) {
                        Pose imagePose = augmentedImage.getCenterPose();
                        String imageCoords = "x: " + String.format("%.2f", imagePose.getXAxis()[0]) + "  y: " + String.format("%.2f", imagePose.getYAxis()[0]) + "  z: " + String.format("%.2f", imagePose.getZAxis()[0]) + "  rotate: " + String.format("%.2f", imagePose.getRotationQuaternion()[0]);// + imagePose.getYAxis(),imagePose.getZAxis());
                        Pos.setText(imageCoords);
                    } else if (augmentedImage.getName().contains("car")) {
                        Toast.makeText(this, "Reading", Toast.LENGTH_LONG).show();
                    } else if (augmentedImage.getName().contains("marker2")) {
                        Toast.makeText(this, "Marker2", Toast.LENGTH_LONG).show();
                    }
                }
            }
        }
    }

    private Anchor makeAnchorWithCoords(float x, float y, float z, Anchor augmentedImageAnchor){
        float[] oldtranslation = {x,y,z};
        float[] translation = augmentedImageAnchor.getPose().transformPoint(oldtranslation);
        Pose translationPose = Pose.makeTranslation(translation);

        Anchor newAnchor = arFragment.getArSceneView().getSession().createAnchor(translationPose);
        return newAnchor;
    }


    private float[] renderObject(Anchor anchor, float[] oldPosition){

        anchorNode = new AnchorNode(anchor);
        anchorNode.setRenderable(sphereRenderable);
        anchorNode.setParent(arFragment.getArSceneView().getScene());
//        ModelRenderable.builder()
//                .setSource(this, model)
//                .build()
//                .thenAccept(renderable -> addNodeToScene(fragment, anchor, renderable))
//                .exceptionally((throwable -> {
//                    AlertDialog.Builder builder = new AlertDialog.Builder(this);
//                    builder.setMessage(throwable.getMessage())
//                            .setTitle("Error!");
//                    AlertDialog dialog = builder.create();
//                    dialog.show();
//                    return null;
//                }));
        if(oldPosition[0] != 0f){
            lineBetweenPoints(oldPosition, anchor.getPose().getTranslation());
        }

        return anchor.getPose().getTranslation();
    }

    private void addNodeToScene(ArFragment fragment, Anchor anchor, Renderable renderable){
        AnchorNode anchorNode = new AnchorNode(anchor);
        TransformableNode node = new TransformableNode(fragment.getTransformationSystem());
        node.setRenderable(renderable);
        node.setParent(anchorNode);
        fragment.getArSceneView().getScene().addChild(anchorNode);
        node.select();
    }

    public void clearPath(View view){
        myDB.clearData();
    }

    public void loadPath(View view){
        myDB = new DatabaseHelper(getApplicationContext());
        Cursor res = myDB.getAllData(DatabaseHelper.TABLE_ANCHOR_DATA);
        while (res.moveToNext()) {
            Pose pose = Pose.makeTranslation(Float.parseFloat(res.getString(1)), Float.parseFloat(res.getString(2)), Float.parseFloat(res.getString(3)));
            Anchor anchor = arFragment.getArSceneView().getSession().createAnchor(pose);

            anchorNode = new AnchorNode(anchor);
            anchorNode.setRenderable(andyRenderable);
            anchorNode.setParent(arFragment.getArSceneView().getScene());

            Node node = new Node();
            node.setParent(anchorNode);
        }
    }

    public void loadPathV2(Anchor imageAnchor){
        myDB = new DatabaseHelper(getApplicationContext());
        Cursor res = myDB.getAllData(DatabaseHelper.TABLE_ANCHOR_DATA);
        Vector3 position = arFragment.getArSceneView().getScene().getCamera().getWorldPosition();
//        arFragment.getArSceneView().getScene().getCamera().setLocalRotation(angle);
        float[] oldPosition = {0f,0f,0f};
        while (res.moveToNext()) {

//            Pose pose = Pose.makeTranslation(position.x + Float.parseFloat(res.getString(1)), position.y + Float.parseFloat(res.getString(2)), position.z + Float.parseFloat(res.getString(3)));
            oldPosition = renderObject(makeAnchorWithCoords(-Float.parseFloat(res.getString(1)), -Float.parseFloat(res.getString(3)), 0.8f,  imageAnchor), oldPosition);
//            Anchor anchor = arFragment.getArSceneView().getSession().createAnchor(pose);
//
//            anchorNode = new AnchorNode(anchor);
//            anchorNode.setRenderable(andyRenderable);
//            anchorNode.setParent(arFragment.getArSceneView().getScene());
//
//            Node node = new Node();
//            node.setParent(anchorNode);

        }
    }

    public void placeObject(){
        myDB = new DatabaseHelper(getApplicationContext());
        Vector3 cameraPos = arFragment.getArSceneView().getScene().getCamera().getWorldPosition();
        Vector3 cameraForward = arFragment.getArSceneView().getScene().getCamera().getForward();
        Vector3 position = Vector3.add(cameraPos, cameraForward.scaled(0));

        Pose pose = Pose.makeTranslation(position.x, position.y, position.z);
        Anchor anchor = arFragment.getArSceneView().getSession().createAnchor(pose);

        anchorNode = new AnchorNode(anchor);
        anchorNode.setRenderable(andyRenderable);
        anchorNode.setParent(arFragment.getArSceneView().getScene());

        Node node = new Node();
        node.setParent(anchorNode);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        int action = event.getAction();
        int keyCode = event.getKeyCode();
        switch (keyCode) {
            case KeyEvent.KEYCODE_VOLUME_UP:
                if (action == KeyEvent.ACTION_DOWN) {
                    myDB.clearData();
                }
                return true;
            case KeyEvent.KEYCODE_VOLUME_DOWN:
                if (action == KeyEvent.ACTION_DOWN) {
                    if(laypath){
                        laypath = false;
                        Toast.makeText(this, "Looking for marker", Toast.LENGTH_LONG).show();
                    }else {
//                        Toast.makeText(this, "Laying path", Toast.LENGTH_LONG).show();
//                        laypath = true;
//                    }
                        Vector3 cameraPos = arFragment.getArSceneView().getScene().getCamera().getWorldPosition();
                        Vector3 cameraForward = arFragment.getArSceneView().getScene().getCamera().getForward();
                        Vector3 position = Vector3.add(cameraPos, cameraForward.scaled(0.5f));

                        Pose pose = Pose.makeTranslation(position.x, position.y, position.z);
                        Anchor anchor = arFragment.getArSceneView().getSession().createAnchor(pose);


                        anchorNode = new AnchorNode(anchor);
                        anchorNode.setRenderable(andyRenderable);
                        anchorNode.setParent(arFragment.getArSceneView().getScene());

                        Node node = new Node();
                        node.setParent(anchorNode);
                        Boolean result = myDB.insertAnchorData(Float.toString(position.x), Float.toString(position.y), Float.toString(position.z));
                        Toast.makeText(this, result.toString() + " " + Float.toString(position.x) + " " + Float.toString(position.y) + " " + Float.toString(position.z), Toast.LENGTH_LONG).show();
                    }
                }
                return true;
            default:
                return super.dispatchKeyEvent(event);
        }
    }

    public void andyString(int numAndy, Pose imagePose){
        float [] lastPosition = new float[3];
        float[] x = imagePose.getXAxis();
        float[] y = imagePose.getYAxis();
        float[] z = imagePose.getZAxis();



        lastPosition = addObject(-1f, -1f, 1f, null, sphereRenderable, imagePose);
        lastPosition = addObject(-2f, -1f, 1f, lastPosition, sphereRenderable, imagePose);
        lastPosition = addObject(-3f, -1f, 1f, lastPosition, sphereRenderable, imagePose);
        lastPosition = addObject(-3f, -1f, 0f, lastPosition, sphereRenderable, imagePose);
        lastPosition = addObject(-3f, -1f, -1f, lastPosition, sphereRenderable, imagePose);
        lastPosition = addObject(-3f, -1f, -2f, lastPosition, sphereRenderable, imagePose);
//        lastPosition = addObject(-3f, -1f, -3f, lastPosition, sphereRenderable);
//        lastPosition = addObject(-3f, -1f, -4f, lastPosition, sphereRenderable);
//        lastPosition = addObject(-2f, -1f, -4f, lastPosition, sphereRenderable);
//        lastPosition = addObject(-1f, -1f, -4f, lastPosition, sphereRenderable);
//        lastPosition = addObject(-0f, -1f, -4f, lastPosition, sphereRenderable);
//        lastPosition = addObject(1f, -1f, -4f, lastPosition, sphereRenderable);
//        lastPosition = addObject(2f, -1f, -4f, lastPosition, sphereRenderable);
//        lastPosition = addObject(3f, -1f, -4f, lastPosition, sphereRenderable);
//        lastPosition = addObject(4f, -1f, -4f, lastPosition, sphereRenderable);
//        lastPosition = addObject(5f, -1f, -4f, lastPosition, sphereRenderable);
//        lastPosition = addObject(6f, -1f, -4f, lastPosition, sphereRenderable);
//        lastPosition = addObject(7f, -1f, -4f, lastPosition, sphereRenderable);
//        lastPosition = addObject(7.8f, -1f, -4.8f, lastPosition, sphereRenderable);
//        lastPosition = addObjectWithoutLine(7.8f, -.5f, -4.8f, lastPosition, fireEscape);


    }

  /**
   * Returns false and displays an error message if Sceneform can not run, true if Sceneform can run
   * on this device.
   *
   * <p>Sceneform requires Android N on the device as well as OpenGL 3.0 capabilities.
   *
   * <p>Finishes the activity if Sceneform can not run
   */

    public float[] addObject(float x, float y, float z, float[] lastPosition, Renderable renderable, Pose imagePose){

        float[] position = averageTranslation.clone();
        float[] quaternion = averageQuaternion.clone();

        position[0] = position[0] + x;
        position[1] = position[1] + y;
        position[2] = position[2] + z;

        Pose pose = new Pose(position, quaternion);
        Anchor anchor = arFragment.getArSceneView().getSession().createAnchor(pose);
        float[] newTranslation = anchor.getPose().transformPoint(imagePose.getRotationQuaternion());

        Pose pose2 = new Pose(newTranslation, quaternion);
        Anchor anchor2 = arFragment.getArSceneView().getSession().createAnchor(pose2);

        anchorNode = new AnchorNode(anchor2);
        anchorNode.setRenderable(renderable);
        anchorNode.setParent(arFragment.getArSceneView().getScene());

        if(lastPosition != null){
            lineBetweenPoints(lastPosition,position);
        }

        return position;
    }

    public float[] addObjectWithoutLine(float x, float y, float z, float[] lastPosition, Renderable renderable){

        Vector3 point1 = makeVector3(lastPosition);
        Vector3 point2 = makeVector3(averageTranslation.clone());

        final Vector3 difference = Vector3.subtract(point1, point2);
        final Vector3 directionFromTopToBottom = difference.normalized();
        final Quaternion rotationFromAToB = Quaternion.lookRotation(directionFromTopToBottom, Vector3.up());

        float[] position = averageTranslation.clone();
//        float[] quaternion = averageQuaternion.clone();
//        Quaternion q1 = makeFloatQuaternion(quaternion);
//        Quaternion q2 = Quaternion.axisAngle(new Vector3(0, 1f, 0f), .6f);

        position[0] = position[0] + x;
        position[1] = position[1] + y;
        position[2] = position[2] + z;

//        quaternion[0] = quaternion[0] + -3f;
//        quaternion[1] = quaternion[1] + -.5f;
//        quaternion[2] = quaternion[2];
//        quaternion[3] = quaternion[3];

        Pose pose = new Pose(position, makeQuaternionFloat(rotationFromAToB));
        Anchor anchor = arFragment.getArSceneView().getSession().createAnchor(pose);

        anchorNode = new AnchorNode(anchor);
        anchorNode.setRenderable(renderable);
        anchorNode.setParent(arFragment.getArSceneView().getScene());

        return position;
    }


    public static boolean checkIsSupportedDeviceOrFinish(final Activity activity) {
        if (Build.VERSION.SDK_INT < VERSION_CODES.N) {
          Log.e(TAG, "Sceneform requires Android N or later");
          Toast.makeText(activity, "Sceneform requires Android N or later", Toast.LENGTH_LONG).show();
          activity.finish();
          return false;
        }
        String openGlVersionString =
            ((ActivityManager) activity.getSystemService(Context.ACTIVITY_SERVICE))
                .getDeviceConfigurationInfo()
                .getGlEsVersion();
        if (Double.parseDouble(openGlVersionString) < MIN_OPENGL_VERSION) {
          Log.e(TAG, "Sceneform requires OpenGL ES 3.0 later");
          Toast.makeText(activity, "Sceneform requires OpenGL ES 3.0 or later", Toast.LENGTH_LONG)
              .show();
          activity.finish();
          return false;
        }
        return true;
    }


    private boolean setupAugmentedImageDb(Config config) {
        AugmentedImageDatabase augmentedImageDatabase;
        Bitmap augmentedImageBitmapCar = loadAugmentedImage("car.jpeg");
        Bitmap augmentedImageBitmapMarker = loadAugmentedImage("marker.jpg");
        Bitmap augmentedImageBitmapMarker2 = loadAugmentedImage("marker2.jpg");
        Bitmap augmentedImageBitmapCode = loadAugmentedImage("apriltag.jpg");
        if (augmentedImageBitmapCar == null) {
            return false;
        }
        if (augmentedImageBitmapCode == null) {
            return false;
        }
        augmentedImageDatabase = new AugmentedImageDatabase(mSession);
        augmentedImageDatabase.addImage("car", augmentedImageBitmapCar);
        augmentedImageDatabase.addImage("marker", augmentedImageBitmapMarker);
        augmentedImageDatabase.addImage("marker2", augmentedImageBitmapMarker2);
        augmentedImageDatabase.addImage("code", augmentedImageBitmapCode);
        config.setAugmentedImageDatabase(augmentedImageDatabase);
        return true;
    }

    private Bitmap loadAugmentedImage(String image){
        try (InputStream is = getAssets().open(image)){
            return BitmapFactory.decodeStream(is);
        }
        catch (IOException e){
            Log.e("ImageLoad", "IO Exception while loading", e);
        }
        return null;
    }


    public void onPause() {
        super.onPause();
        if (mSession != null) {

            arSceneView.pause();
            mSession.pause();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mSession == null) {
            String message = null;
            Exception exception = null;
            try {
                mSession = new Session(this);
            } catch (UnavailableArcoreNotInstalledException
                    e) {
                message = "Please install ARCore";
                exception = e;
            } catch (UnavailableApkTooOldException e) {
                message = "Please update ARCore";
                exception = e;
            } catch (UnavailableSdkTooOldException e) {
                message = "Please update android";
                exception = e;
            } catch (Exception e) {
                message = "AR is not supported";
                exception = e;
            }

            if (message != null) {
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
                Log.e(TAG, "Exception creating session", exception);
                return;
            }
            sessionConfigured = true;

        }
        if (sessionConfigured) {
            configureSession();
            sessionConfigured = false;

            arSceneView.setupSession(mSession);
        }


    }
    private void configureSession() {
        Config config = new Config(mSession);
        if (!setupAugmentedImageDb(config)) {
            Toast.makeText(this, "Unable to setup augmented", Toast.LENGTH_SHORT).show();
        }
        config.setUpdateMode(Config.UpdateMode.LATEST_CAMERA_IMAGE);
        mSession.configure(config);
    }

    public void lineBetweenPoints(float[] position1, float[] position2) {

   /* First, find the vector extending between the two points and define a look rotation in terms of this
        Vector. */

        Vector3 point1 = makeVector3(position1);
        Vector3 point2 = makeVector3(position2);

        final Vector3 difference = Vector3.subtract(point1, point2);
        final Vector3 directionFromTopToBottom = difference.normalized();
        final Quaternion rotationFromAToB = Quaternion.lookRotation(directionFromTopToBottom, Vector3.up());

        Pose pose = new Pose(makeVectorFloat(Vector3.add(point1, point2).scaled(.5f)), makeQuaternionFloat(rotationFromAToB));
        Anchor anchor = arFragment.getArSceneView().getSession().createAnchor(pose);

        MaterialFactory.makeOpaqueWithColor(this, new com.google.ar.sceneform.rendering.Color(0,100,0))
                .thenAccept(
                        material -> {
                            lineRenderable = ShapeFactory.makeCube(new Vector3(.05f, .05f, difference.length()),
                                    Vector3.zero(), material);
                        });

        anchorNode = new AnchorNode(anchor);
        anchorNode.setRenderable(lineRenderable);
        anchorNode.setParent(arFragment.getArSceneView().getScene());

    }

    public Vector3 makeVector3(float[] position){
        Vector3 resultVector = new Vector3();
        resultVector.x = position[0];
        resultVector.y = position[1];
        resultVector.z = position[2];

        return resultVector;
    }

    public float[] makeVectorFloat(Vector3 point){
        float[] resultFloat = new float[3];
        resultFloat[0] = point.x;
        resultFloat[1] = point.y;
        resultFloat[2] = point.z;

        return resultFloat;
    }

    public float[] makeQuaternionFloat(Quaternion point){
        float[] resultFloat = new float[4];
        resultFloat[0] = point.x;
        resultFloat[1] = point.y;
        resultFloat[2] = point.z;
        resultFloat[3] = point.w;

        return resultFloat;
    }

    public Quaternion makeFloatQuaternion(float[] rotation){
        Quaternion resultQuaternion = new Quaternion();
        resultQuaternion.x = rotation[0];
        resultQuaternion.y = rotation[1];
        resultQuaternion.z = rotation[2];
        resultQuaternion.w = rotation[3];

        return resultQuaternion;
    }
}


//                    if(augmentedImageFound) {
//                        scanningMessage.setVisibility(View.GONE);
////                         here we got that image has been detected
////                         we will render our 3D asset in center of detected image
//                        Pose imagePose = augmentedImage.getCenterPose();
//
//
//                        if(averageIterator > 5){
//                            for(int i = 0; i < averageTranslation.length;){
//                                averageTranslation[i] = averageTranslation[i]/averageIterator;
//                                i++;
//                            }
//                            for(int i = 0; i < averageQuaternion.length;){
//                                averageQuaternion[i] = averageQuaternion[i]/averageIterator;
//                                i++;
//                            }
//                            float[] a = augmentedImage.getCenterPose().getTranslation();
//                            float[] b = augmentedImage.getCenterPose().getRotationQuaternion();
//                            String imageCoords = "x: " + String.format("%.2f", imagePose.getXAxis()[0]) + "  y: " + String.format("%.2f", imagePose.getYAxis()[0]) + "  z: " + String.format("%.2f", imagePose.getZAxis()[0]) + "  rotate: " + String.format("%.2f", imagePose.getRotationQuaternion()[0]);// + imagePose.getYAxis(),imagePose.getZAxis());
//
////                            andyString(4,imagePose);
//                            augmentedImageFound = false;
//                            Toast.makeText(this, "Find your path", Toast.LENGTH_LONG).show();
//                        }else if(averageIterator <= 5) {
//                            scanningMessage.setVisibility(View.VISIBLE);
//                            // Keeps a running average of the detected image's translation
//                            for (int i = 0; i < imagePose.getTranslation().length; ) {
//                                averageTranslation[i] = (averageTranslation[i] + imagePose.getTranslation()[i]);
//                                i++;
//                            }
//                            // Keeps a running average of the detected image's rotation quaternion
//                            for (int i = 0; i < imagePose.getRotationQuaternion().length; ) {
//                                averageQuaternion[i] = (averageQuaternion[i] + imagePose.getRotationQuaternion()[i]);
//                                i++;
//                            }
//
//                            averageIterator = averageIterator + 1;
//                        }
//                    }