package com.example.bmc.spdoorbell;


import android.content.Intent;
import android.os.Vibrator;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

/**
 *
 *  Main Menu Activity
 *  Activity interacts with Firebase real time database getting and updating values
 *  4 Functional buttons on Menu activity
 *  - Unlock Door
 *  - Start Live Video and Audio stream and view on VideoStreaming Activity
 *  - Add new face set to facial recognition dataset and retrain the trainer with new face, vibrate when new face is successfully added
 *  - Check Facial Recognition Status button - Toast message appears with the status of the facial recognition feature of IoT Doorbell
 *  - Seekbars to update timer and threshold values on the Raspberry Pi
 *
 */

public class MainActivity extends AppCompatActivity {

    // Static string for logging tag value
    private static final String TAG = "Main Activity Logging: ";
    private String check = "";

    // Firebase database instance and reference
    FirebaseDatabase database = FirebaseDatabase.getInstance();
    DatabaseReference data = database.getReference();
    Vibrator vibrator;

    // Seekbar variables
    private SeekBar timebar;
    private TextView textView;
    private SeekBar thresbar;
    private TextView thresView;

    // Setting values of threshold and timer values to use in Seekbars
    int timeMin = 0, timeMax = 20, timeCurrent = 10;
    int thresMin = 50, thresMax = 150, thresCurrent = 100;


    /**
     *  onCreate function
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialising SeekBars and text views
        textView = findViewById(R.id.textView);
        timebar = findViewById(R.id.timebar);
        thresView = findViewById(R.id.thresView);
        thresbar = findViewById(R.id.thresbar);

        // Setting values for timer seekbar
        timebar.setMax(timeMax - timeMin);
        timebar.setProgress(timeCurrent - timeMin);
        textView.setText("" + timeCurrent);

        // Setting values for threshold seekbar
        thresbar.setMax(thresMax - thresMin);
        thresbar.setProgress(thresCurrent - thresMin);
        thresView.setText("" + thresCurrent);

        // Timer seekbar onChange listener
        timebar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            // When seekbar is changed, update the textView with the new value
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                timeCurrent = progress + timeMin;
                textView.setText("" + timeCurrent);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            // When user stops moving the seekbar, update firebase database with the seekbar progress value
            public void onStopTrackingTouch(SeekBar seekBar) {
                //send it to firebase
                seekBar.getProgress();
                Log.d(TAG, "PROGRESS IS : "+seekBar.getProgress());
                data.child("doorbell").child("lock").child("timer").setValue(seekBar.getProgress());

            }
        });

        // Threshold seekbar onChange listener
        thresbar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            // When seekbar is changed, update the textView with the new value
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                thresCurrent = progress + thresMin;
                thresView.setText("" + thresCurrent);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            // When user stops moving the seekbar, update firebase database with the seekbar progress value
            public void onStopTrackingTouch(SeekBar seekBar) {
                //send it to firebase
                seekBar.getProgress();
                Log.d(TAG, "PROGRESS IS : "+seekBar.getProgress());
                data.child("doorbell").child("facial_recognition").child("threshold").setValue(seekBar.getProgress());

            }
        });

        vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        // Event Listener constantly listening for changes to Firebase database
        data.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                for(DataSnapshot ds : dataSnapshot.getChildren()){
                    Log.d(TAG, "Current Values: "+ds.child("face").child("new_state").getValue());

                    // Face string to check the value of adding a new face on Firebase
                    String face = ds.child("face").child("start_new").getValue().toString();

                    // Check string to check if facial recognition is on or off
                    check = ds.child("facial_recognition").child("is_active").getValue().toString();
                    String faceOn = "Facial Recognition: Is Active!";
                    String faceOff = "Facial Recognition: Is Inactive!";

                    // if check = 1, toast message popup with status of On and update firebase value
                    if (check.equalsIgnoreCase("1")){
                        toastMsg(faceOn);
                        data.child("doorbell").child("facial_recognition").child("is_active").setValue("Facial Recognition: On");

                    }
                    // else if check = 0, toast message popup with status of Off and update firebase value
                    else if (check.equalsIgnoreCase("0")){
                        toastMsg(faceOff);
                        data.child("doorbell").child("facial_recognition").child("is_active").setValue("Facial Recognition: Off");
                    }

                    // if face = complete on firebase then vibrate for 1.5 seconds to inform the user it is complete
                    if (face.equalsIgnoreCase("complete")){
                        vibrator.vibrate(1500);
                    }
                    break;
                }
            }

            @Override
            public void onCancelled(DatabaseError error) {
                // Cant read value
                Log.w(TAG, "Cant read value.", error.toException());
            }
        });

        // onClick listener to unlock the door and update firebase value
        ImageView unlock = findViewById(R.id.door);
        unlock.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                data.child("doorbell").child("lock").child("state").setValue(1);
            }
        });

        // onClick listener to add a new face and update firebase value
        ImageView newface = findViewById(R.id.newface);
        newface.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                data.child("doorbell").child("face").child("start_new").setValue(1);
            }
        });

        // onClick listener to go to Video Stream activity and update firebase value
        ImageView video = findViewById(R.id.video);
        video.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                data.child("doorbell").child("streaming").child("start_requested").setValue(1);
                Intent myIntent = new Intent(MainActivity.this, VideoStreaming.class);
                MainActivity.this.startActivity(myIntent);
            }
        });

    }

    // Function to print toast popup message
    public void toastMsg(String msg) {
        Toast toast = Toast.makeText(this, msg, Toast.LENGTH_LONG);
        toast.show();

    }


    // Function to display toast message of Facial recognition value from Firebase - onClick Listener for Face Check button
    public void displayToastMsg(View v) {
        toastMsg(check);
    }
}
