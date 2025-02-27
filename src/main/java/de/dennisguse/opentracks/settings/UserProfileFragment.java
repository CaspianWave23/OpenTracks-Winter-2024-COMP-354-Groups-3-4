package de.dennisguse.opentracks.settings;

import static de.dennisguse.opentracks.settings.PreferencesUtils.getUnitSystem;

import android.app.AlertDialog;
import android.app.Activity;
import android.content.ContextWrapper;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.net.Uri;
import android.provider.MediaStore;
import android.util.Log;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.Intent;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.SwitchPreference;
import androidx.core.app.ActivityCompat;

import com.github.dhaval2404.imagepicker.ImagePicker;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.sql.Timestamp;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Objects;

import de.dennisguse.opentracks.R;
import de.dennisguse.opentracks.data.models.Height;
import de.dennisguse.opentracks.data.models.HeightFormatter;
import de.dennisguse.opentracks.data.models.Speed;
import de.dennisguse.opentracks.data.models.SpeedFormatter;
import de.dennisguse.opentracks.data.models.UserModel;
import de.dennisguse.opentracks.data.models.Weight;
import de.dennisguse.opentracks.data.models.WeightFormatter;

import android.app.DatePickerDialog;
import android.widget.DatePicker;
import java.util.Calendar;
import java.util.Locale;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import java.util.Arrays;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class UserProfileFragment extends PreferenceFragmentCompat {

    private static final int PERMISSION_REQUEST_CODE = 1001;
    private static final int IMAGE_PICKER_REQUEST_CODE = 101;


    SwitchPreference leaderboardSwitch;

    private void startImagePicker() {
        try {
            ImagePicker.with(this)
                    .crop()
                    .compress(1024)
                    .maxResultSize(1080, 1080)
                    .start();
        } catch (Exception e) {
            Log.e("UserProfileFragment", "Error starting image picker: " + e.getMessage());
        }
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        addPreferencesFromResource(R.xml.settings_user_profile);

        Preference editPreference = findPreference(getString(R.string.edit_profile_key));
        if (editPreference != null) {
            editPreference.setOnPreferenceClickListener(preference -> {
                showEditProfileDialog();
                return true;
            });
        }

        Preference editProfilePic = findPreference("edit_profile_pic");
        if (editProfilePic != null) {
            editProfilePic.setOnPreferenceClickListener(preference -> {
                Log.d("UserProfileFragment", "Edit profile picture button clicked");
                // Check if permission is granted
                if (ContextCompat.checkSelfPermission(getContext(), Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED ||
                        ContextCompat.checkSelfPermission(getContext(), Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                    // Permission is not granted, request it from the user
                    ActivityCompat.requestPermissions(getActivity(),
                            new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.CAMERA},
                            PERMISSION_REQUEST_CODE);
                    if (ContextCompat.checkSelfPermission(getContext(), Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED ||
                            ContextCompat.checkSelfPermission(getContext(), Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                        // Permission is already granted, start the image picker
                        Log.d("UserProfileFragment", "Calling startImagePicker()");
                        startImagePicker();
                    }
                }
                return true;
            });
        }

        // Check toggle status for leaderboard preferences
        leaderboardSwitch = findPreference("leaderboard_switch");
        assert leaderboardSwitch != null;
        leaderboardSwitch.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(@NonNull Preference preference) {
                if(leaderboardSwitch.isChecked())
                {
                    // Form to check/ uncheck shared details

                    displayCustomSharingDialog();

                }
                return false;
            }
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == PERMISSION_REQUEST_CODE) {
            boolean allPermissionsGranted = true;
            for (int grantResult : grantResults) {
                if (grantResult != PackageManager.PERMISSION_GRANTED) {
                    allPermissionsGranted = false;
                    break;
                }
            }

            if (allPermissionsGranted) {
                Log.d("UserProfileFragment", "Permissions granted, starting image picker");
                startImagePicker();
            } else {
                Toast.makeText(getContext(), "Permission denied", Toast.LENGTH_SHORT).show();
            }
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Uri imageUri = data.getData();
        if (imageUri != null) {
            // Update the profile picture with the selected image
            updateProfilePicture(imageUri);
        }
    }

    private void updateProfilePicture(Uri imageUri) {
        // Load the image from the URI and set it to the profile picture ImageView
        ImageView profilePictureImageView = requireView().findViewById(R.id.profileImageView);
        profilePictureImageView.setImageURI(imageUri);

        // Convert the image URI to a Bitmap
        Bitmap bitmap = null;
        try {
            bitmap = MediaStore.Images.Media.getBitmap(requireActivity().getContentResolver(), imageUri);
        } catch (IOException e) {
            e.printStackTrace();
        }

        // Save the Bitmap to internal storage
        if (bitmap != null) {
            saveImageToInternalStorage(bitmap);
            // Display the profile picture after saving
            displayProfilePicture();

        }
    }

    public void saveImageToInternalStorage(Bitmap bitmapImage){
        ContextWrapper cw = new ContextWrapper(requireContext());
        // Get the directory for the app's private pictures directory.
        File directory = cw.getDir("profilePicDir", Context.MODE_PRIVATE);

        Log.d("MyApp", "Directory Path: " + directory.getAbsolutePath());

        // Create imageDir
        File myPath = new File(directory, "profile.jpg");

        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(myPath);
            // Use the compress method on the Bitmap object to write image to the OutputStream
            bitmapImage.compress(Bitmap.CompressFormat.PNG, 100, fos);
        } catch (Exception e) {
            e.printStackTrace();
            Log.e("MyApp", "Error saving image: " + e.getMessage());
        } finally {
            try {
                if (fos != null)
                    fos.close();
            } catch (IOException e) {
                e.printStackTrace();
                Log.e("MyApp", "Error closing FileOutputStream: " + e.getMessage());
            }
        }

    }

    private void displayProfilePicture() {
        View view = requireView();
        if (view != null) {
            // Get the directory for the app's private pictures directory
            ContextWrapper cw = new ContextWrapper(requireContext());
            File directory = cw.getDir("profilePicDir", Context.MODE_PRIVATE);

            // Create image file
            File myPath = new File(directory, "profile.jpg");

            // Check if the file exists
            if (myPath.exists()) {
                // If the file exists, load the image from the file
                Bitmap bitmap = BitmapFactory.decodeFile(myPath.getAbsolutePath());

                // Set the loaded bitmap to the ImageView
                ImageView profileImageView = view.findViewById(R.id.profileImageView);
                if (profileImageView != null) {
                    profileImageView.setImageBitmap(bitmap);
                } else {
                    Log.e("UserProfileFragment", "ImageView not found in the layout");
                }
            } else {
                Log.e("UserProfileFragment", "Path does not exist");
            }
        } else {
            Log.e("UserProfileFragment", "Fragment view is null");
        }
    }

    private void showEditProfileDialog() {
        // Inflate the custom layout for the edit dialog.
        View formView = LayoutInflater.from(getContext()).inflate(R.layout.edit_profile_form, null);

        // Get current profile content
        TextView nicknameText = getView().findViewById(R.id.nickname);
        TextView DOBText = getView().findViewById(R.id.dateOfBirth);
        TextView heightText = getView().findViewById(R.id.userHeight);
        TextView weightText = getView().findViewById(R.id.userWeight);
//        TextView countryText = getView().findViewById(R.id.userLocation);
//        TextView genderText = getView().findViewById(R.id.gender);


        // Initialize all the EditText fields and Spinners.
        EditText editNickname = formView.findViewById(R.id.editNickname);
        EditText editDateOfBirth = formView.findViewById(R.id.editDateOfBirth);
        EditText editHeight = formView.findViewById(R.id.editHeight);
        EditText editWeight = formView.findViewById(R.id.editWeight);

        // Write user data to profile
        if (nicknameText.getText().toString().equalsIgnoreCase("Nickname")) {
            editNickname.setText("");
        }
        else
        {
            editNickname.setText(nicknameText.getText().toString());
        }
        if (DOBText.getText().toString().equalsIgnoreCase("Date of Birth")) {
            editDateOfBirth.setText("");
        }
        else
        {
            editDateOfBirth.setText(DOBText.getText().toString());
        }
        editHeight.setText(heightText.getText().toString());
        editWeight.setText(weightText.getText().toString());

        Spinner spinnerGender = formView.findViewById(R.id.spinnerGender);
        ArrayAdapter<CharSequence> genderAdapter = ArrayAdapter.createFromResource(getContext(),
                R.array.gender_options, android.R.layout.simple_spinner_item);
        genderAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerGender.setAdapter(genderAdapter);

        Spinner spinnerLocation = formView.findViewById(R.id.spinnerLocation);
        String[] isoCountryCodes = Locale.getISOCountries();
        String[] countryNames = new String[isoCountryCodes.length];

        for (int i = 0; i < isoCountryCodes.length; i++) {
            Locale locale = new Locale("", isoCountryCodes[i]);
            countryNames[i] = locale.getDisplayCountry();
        }

        Arrays.sort(countryNames); // Sort the country names alphabetically

        // Move Canada to the beginning of the array
        for (int i = 0; i < countryNames.length; i++) {
            if (countryNames[i].equalsIgnoreCase("Canada")) {
                String temp = countryNames[i];
                System.arraycopy(countryNames, 0, countryNames, 1, i);
                countryNames[0] = temp;
                break;
            }
        }

        ArrayAdapter<String> locationAdapter = new ArrayAdapter<>(getContext(),
                android.R.layout.simple_spinner_item, countryNames);
        locationAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerLocation.setAdapter(locationAdapter);


        // Set up the AlertDialog.
        new AlertDialog.Builder(getContext())
                .setTitle(R.string.edit_profile_title)
                .setView(formView)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    // Collect data from the fields.
                    String nickname = editNickname.getText().toString();
                    String dateOfBirth = editDateOfBirth.getText().toString();
                    String height = editHeight.getText().toString();
                    String weight = editWeight.getText().toString();
                    String gender = spinnerGender.getSelectedItem().toString();
                    String location = spinnerLocation.getSelectedItem().toString();

                    // Validate and save the data if valid.
                    if (validateInputs(nickname, dateOfBirth, height, weight, gender, location)) {
                        saveProfileData(nickname, dateOfBirth, height, weight, gender, location);
                        showToast("Profile updated successfully!");
                    } else {
                        showToast("Please check your inputs.");
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
        editDateOfBirth.setOnClickListener(view -> {
            // Use the current date as the default date in the picker
            final Calendar c = Calendar.getInstance();
            int year = c.get(Calendar.YEAR);
            int month = c.get(Calendar.MONTH);
            int day = c.get(Calendar.DAY_OF_MONTH);

            // Create a new instance of DatePickerDialog and return it
            DatePickerDialog datePickerDialog = new DatePickerDialog(getContext(), (view1, year1, monthOfYear, dayOfMonth) -> {
                // The month value is 0-based, so we add 1 to it for display.
                String selectedDate = String.format(Locale.getDefault(), "%02d-%02d-%04d", dayOfMonth, monthOfYear + 1, year1);
                editDateOfBirth.setText(selectedDate);
            }, year, month, day);

            // Prevent future dates from being selected
            datePickerDialog.getDatePicker().setMaxDate(System.currentTimeMillis());

            // Show the date picker dialog
            datePickerDialog.show();
        });
    }

    // A simple method to show toast messages.
    private void showToast(String message) {
        Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
    }

    // A method to validate the user inputs.
    private boolean validateInputs(String nickname, String dateOfBirth, String height, String weight, String gender, String location) {

        if (nickname.isEmpty() || gender.isEmpty() || dateOfBirth.isEmpty())
        {
            showToast("Fields cannot be empty.");
            return false;
        }
        try {
            if (Double.parseDouble(height) < 0) {
                showToast("Height cannot be negative.");
                return false;
            }
            if (Double.parseDouble(weight) < 0) {
                showToast("Weight cannot be negative.");
                return false;
            }
        } catch (NumberFormatException e) {
            showToast("Height and weight must be valid numbers.");
            return false;
        }

        return true;
    }

    /*
    * Conversion method to appropriate data types for user object
    * */
    private UserModel createUserFromInput(String nickname, String dateOfBirth, String height, String weight, String gender, String location, boolean socialAllow, String path) {

        // Get long date
        DateFormat formatter = new SimpleDateFormat("dd-MM-yyyy", Locale.getDefault());
        Timestamp DOBlong = null;
        long userDOB = 0;

        try {
            Date date = formatter.parse(dateOfBirth);
            if (date != null) {
                DOBlong = new Timestamp(date.getTime());
                userDOB = DOBlong.getTime();
            }
        } catch (ParseException e) {
            showToast("Error DOB format error");
        }

        // Height and weight conversion
        int userHeight = 0;
        int userWeight = 0;

        Pattern pattern = Pattern.compile("\\d+");
        Matcher matcherHeight = pattern.matcher(height);
        Matcher matcherWeight = pattern.matcher(weight);

        if (matcherHeight.find() && matcherWeight.find()) {
            userHeight = Integer.parseInt(matcherHeight.group());
            userWeight = Integer.parseInt(matcherWeight.group());
        }

        // Gender, Location correct format due to spinner handling

        // Create UserModel object
        UserModel userObject = new UserModel(nickname, location, userDOB, gender, userHeight, userWeight, socialAllow, path);

        return userObject;
    }

    private void displayCustomSharingDialog(){

        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());

        // Array to store user information
        String[] userInfo = new String[5];
        int[] textViewIds = {R.id.nickname, R.id.userLocation, R.id.dateOfBirth, R.id.userHeight, R.id.userWeight};

        // Array to store detail labels
        String[] detailNames = {"Nickname", "Location", "Date of Birth", "Height", "Weight"};

        StringBuilder alertMessageBuilder = new StringBuilder("Do you allow OpenTracks to store and display the following information on the leaderboard?\n\n");

        // Retrieve values from TextViews and populate user info
        for (int i = 0; i < textViewIds.length; i++) {

            TextView textView = getView().findViewById(textViewIds[i]);

            if(textView!=null) {
                userInfo[i] = textView.getText().toString();

                // Construct custom message
                alertMessageBuilder.append(detailNames[i]).append(": ").append(userInfo[i]).append("\n");

            }
        }

        String alertMessage = alertMessageBuilder.toString();

        builder.setTitle("Confirm Selection")

                .setMessage(alertMessage)
                .setPositiveButton("ALLOW", (dialog, which) -> {
//                    UserModel userModel = new UserModel(); // Assume you have a way to initialize or get the current UserModel
//                    userModel.setSocialAllow(true);
                    showToast("Updated sharing permissions and data will be shared on the leaderboard.");
                    leaderboardSwitch.setChecked(true); // Visually indicate sharing is enabled

                    // TODO: Save the updated UserModel to the database
                })
                .setNegativeButton("CANCEL", (dialog, which) -> {
//                    UserModel userModel = new UserModel(); // we need to get current UserID and initialize.
 //                   userModel.setSocialAllow(false);
                    showToast("Sharing not enabled. Data will remain private.");
                    leaderboardSwitch.setChecked(false); // Visually indicate sharing is not enabled

                    // TODO: Save the updated UserModel to the database
                })
                .show();
    }

    // TODO: Implement saving logic here.
    private void saveProfileData(String nickname, String dateOfBirth, String height, String weight, String gender, String location) {



    }

    @Override
    public void onStart() {
        super.onStart();
        ((SettingsActivity) getActivity()).getSupportActionBar().setTitle(R.string.settings_ui_title);

        final Handler handler = new Handler(Looper.getMainLooper());
        handler.postDelayed(() -> {
            TextView heightView = getView().findViewById(R.id.userHeight);
            TextView weightView = getView().findViewById(R.id.userWeight);

            UnitSystem unitSystem = getUnitSystem();

            Height height = new Height(180);
            Pair<String, String> heightStrings = HeightFormatter.Builder().setUnit(unitSystem).build(getContext()).getHeightParts(height);

            Weight weight = new Weight(80);
            Pair<String, String> weightStrings = WeightFormatter.Builder().setUnit(unitSystem).build(getContext()).getWeightParts(weight);

            heightView.setText(heightStrings.first + heightStrings.second);
            weightView.setText(weightStrings.first + weightStrings.second);

            //check if profile picture has been changed from default and load the new picture
            if (ProfilePictureExists()) {
                displayProfilePicture();
            }
        }, 50);
    }

    @Override
    public void onDisplayPreferenceDialog(Preference preference) {
        DialogFragment dialogFragment = null;

        if (preference instanceof ResetDialogPreference) {
            dialogFragment = ResetDialogPreference.ResetPreferenceDialog.newInstance(preference.getKey());
        }

        if (dialogFragment != null) {
            dialogFragment.setTargetFragment(this, 0);
            dialogFragment.show(getParentFragmentManager(), getClass().getSimpleName());
            return;
        }

        super.onDisplayPreferenceDialog(preference);
    }
    private boolean ProfilePictureExists() {
        ContextWrapper cw = new ContextWrapper(requireContext());
        File directory = cw.getDir("profilePicDir", Context.MODE_PRIVATE);
        File myPath = new File(directory, "profile.jpg");
        return myPath.exists();
    }


}
