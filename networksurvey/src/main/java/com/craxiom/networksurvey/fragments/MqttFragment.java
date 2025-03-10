package com.craxiom.networksurvey.fragments;

import static com.craxiom.networksurvey.util.PreferenceUtils.populatePrefsFromMqttConnectionSettings;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewStub;
import android.widget.Button;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;
import androidx.navigation.Navigation;

import com.craxiom.mqttlibrary.connection.BrokerConnectionInfo;
import com.craxiom.mqttlibrary.ui.AConnectionFragment;
import com.craxiom.networksurvey.NetworkSurveyActivity;
import com.craxiom.networksurvey.R;
import com.craxiom.networksurvey.constants.NetworkSurveyConstants;
import com.craxiom.networksurvey.fragments.model.MqttConnectionSettings;
import com.craxiom.networksurvey.mqtt.MqttConnectionInfo;
import com.craxiom.networksurvey.services.NetworkSurveyService;

import timber.log.Timber;

/**
 * A fragment for allowing the user to connect to an MQTT broker. This fragment handles
 * the UI portion of the connection and delegates the actual connection logic to {@link NetworkSurveyService}.
 *
 * @since 0.1.1
 */
public class MqttFragment extends AConnectionFragment<NetworkSurveyService.SurveyServiceBinder>
{
    private static final int ACCESS_BLUETOOTH_PERMISSION_REQUEST_ID = 30;

    private SwitchCompat cellularStreamToggleSwitch;
    private SwitchCompat wifiStreamToggleSwitch;
    private SwitchCompat bluetoothStreamToggleSwitch;
    private SwitchCompat gnssStreamToggleSwitch;
    private SwitchCompat deviceStatusStreamToggleSwitch;

    private boolean cellularStreamEnabled = true;
    private boolean wifiStreamEnabled = true;
    private boolean bluetoothStreamEnabled = true;
    private boolean gnssStreamEnabled = true;
    private boolean deviceStatusStreamEnabled = true;

    private final ActivityResultLauncher<String> cameraPermissionRequestLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted)
                {
                    Navigation.findNavController(requireActivity(), getId())
                            .navigate(MqttFragmentDirections.actionMqttConnectionFragmentToScannerFragment()
                                    .setMqttConnectionSettings(getCurrentMqttConnectionSettings()));
                } else
                {
                    Toast.makeText(getContext(), getString(R.string.grant_camera_permission), Toast.LENGTH_LONG).show();
                }
            });

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
    {
        // The mqttConnectionSettings will be passed as an argument if the QR code scanner fragment
        // was just displayed. A nuance to this is that even if the user did not scan a QR code, we
        // will still get mqttConnectionSettings. This is because when the user clicks on the "Scan
        // Code" button we don't want them to lose all of the settings they have already entered in
        // the event they immediately cancel the QR code scan, so we might just be getting the
        // settings that were already entered instead of new QR scanned settings.
        MqttConnectionSettings mqttConnectionSettings =
                MqttFragmentArgs.fromBundle(getArguments()).getMqttConnectionSettings();
        if (mqttConnectionSettings != null)
        {
            populatePrefsFromMqttConnectionSettings(mqttConnectionSettings, getContext());
        }

        return super.onCreateView(inflater, container, savedInstanceState);
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void inflateAdditionalFieldsViewStub(LayoutInflater layoutInflater, ViewStub viewStub)
    {
        viewStub.setLayoutResource(R.layout.fragment_stream_options);
        View inflatedStub = viewStub.inflate();

        cellularStreamToggleSwitch = inflatedStub.findViewById(R.id.streamCellularToggleSwitch);
        wifiStreamToggleSwitch = inflatedStub.findViewById(R.id.streamWifiToggleSwitch);
        bluetoothStreamToggleSwitch = inflatedStub.findViewById(R.id.streamBluetoothToggleSwitch);
        gnssStreamToggleSwitch = inflatedStub.findViewById(R.id.streamGnssToggleSwitch);
        deviceStatusStreamToggleSwitch = inflatedStub.findViewById(R.id.streamDeviceStatusToggleSwitch);

        bluetoothStreamToggleSwitch.setOnClickListener((buttonView) -> {
            if (buttonView.isPressed())
            {
                SwitchCompat switchCompat = (SwitchCompat) buttonView;
                if (switchCompat.isChecked() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && missingBluetoothPermissions())
                {
                    switchCompat.setChecked(false);
                    showBluetoothPermissionRationaleAndRequestPermissions();
                }
            }
        });
        bluetoothStreamToggleSwitch.setOnTouchListener((buttonView, motionEvent) -> motionEvent.getActionMasked() == 2);

        Button scanCodeButton = inflatedStub.findViewById(R.id.code_scan_button);
        scanCodeButton.setOnClickListener(v -> {
            if (hasCameraPermission())
            {
                Navigation.findNavController(requireActivity(), getId())
                        .navigate(MqttFragmentDirections.actionMqttConnectionFragmentToScannerFragment()
                                .setMqttConnectionSettings(getCurrentMqttConnectionSettings()));
            } else
            {
                cameraPermissionRequestLauncher.launch(Manifest.permission.CAMERA);
            }
        });
    }

    @Override
    protected Context getApplicationContext()
    {
        return requireActivity().getApplicationContext();
    }

    @Override
    protected Class<?> getServiceClass()
    {
        return NetworkSurveyService.class;
    }

    @Override
    protected void readMdmConfigAdditionalProperties(Bundle mdmProperties)
    {
        cellularStreamEnabled = mdmProperties.getBoolean(NetworkSurveyConstants.PROPERTY_MQTT_CELLULAR_STREAM_ENABLED, NetworkSurveyConstants.DEFAULT_MQTT_CELLULAR_STREAM_SETTING);
        wifiStreamEnabled = mdmProperties.getBoolean(NetworkSurveyConstants.PROPERTY_MQTT_WIFI_STREAM_ENABLED, NetworkSurveyConstants.DEFAULT_MQTT_WIFI_STREAM_SETTING);
        bluetoothStreamEnabled = mdmProperties.getBoolean(NetworkSurveyConstants.PROPERTY_MQTT_BLUETOOTH_STREAM_ENABLED, NetworkSurveyConstants.DEFAULT_MQTT_BLUETOOTH_STREAM_SETTING);
        gnssStreamEnabled = mdmProperties.getBoolean(NetworkSurveyConstants.PROPERTY_MQTT_GNSS_STREAM_ENABLED, NetworkSurveyConstants.DEFAULT_MQTT_GNSS_STREAM_SETTING);
        deviceStatusStreamEnabled = mdmProperties.getBoolean(NetworkSurveyConstants.PROPERTY_MQTT_DEVICE_STATUS_STREAM_ENABLED, NetworkSurveyConstants.DEFAULT_MQTT_DEVICE_STATUS_STREAM_SETTING);
    }

    /**
     * Update the UI fields from the instance variables in this class.
     *
     * @since 0.1.5
     */
    @Override
    protected void updateUiFieldsFromStoredValues()
    {
        super.updateUiFieldsFromStoredValues();

        cellularStreamToggleSwitch.setChecked(cellularStreamEnabled);
        wifiStreamToggleSwitch.setChecked(wifiStreamEnabled);
        bluetoothStreamToggleSwitch.setChecked(bluetoothStreamEnabled);
        gnssStreamToggleSwitch.setChecked(gnssStreamEnabled);
        deviceStatusStreamToggleSwitch.setChecked(deviceStatusStreamEnabled);
    }

    @Override
    protected void readUIAdditionalFields()
    {
        cellularStreamEnabled = cellularStreamToggleSwitch.isChecked();
        wifiStreamEnabled = wifiStreamToggleSwitch.isChecked();
        bluetoothStreamEnabled = bluetoothStreamToggleSwitch.isChecked();
        gnssStreamEnabled = gnssStreamToggleSwitch.isChecked();
        deviceStatusStreamEnabled = deviceStatusStreamToggleSwitch.isChecked();
    }

    @Override
    protected void storeAdditionalParameters(SharedPreferences.Editor editor)
    {
        editor.putBoolean(NetworkSurveyConstants.PROPERTY_MQTT_CELLULAR_STREAM_ENABLED, cellularStreamEnabled);
        editor.putBoolean(NetworkSurveyConstants.PROPERTY_MQTT_WIFI_STREAM_ENABLED, wifiStreamEnabled);
        editor.putBoolean(NetworkSurveyConstants.PROPERTY_MQTT_BLUETOOTH_STREAM_ENABLED, bluetoothStreamEnabled);
        editor.putBoolean(NetworkSurveyConstants.PROPERTY_MQTT_GNSS_STREAM_ENABLED, gnssStreamEnabled);
        editor.putBoolean(NetworkSurveyConstants.PROPERTY_MQTT_DEVICE_STATUS_STREAM_ENABLED, deviceStatusStreamEnabled);
    }

    @Override
    protected void restoreAdditionalParameters(SharedPreferences sharedPreferences)
    {
        cellularStreamEnabled = sharedPreferences.getBoolean(NetworkSurveyConstants.PROPERTY_MQTT_CELLULAR_STREAM_ENABLED, NetworkSurveyConstants.DEFAULT_MQTT_CELLULAR_STREAM_SETTING);
        wifiStreamEnabled = sharedPreferences.getBoolean(NetworkSurveyConstants.PROPERTY_MQTT_WIFI_STREAM_ENABLED, NetworkSurveyConstants.DEFAULT_MQTT_WIFI_STREAM_SETTING);
        bluetoothStreamEnabled = sharedPreferences.getBoolean(NetworkSurveyConstants.PROPERTY_MQTT_BLUETOOTH_STREAM_ENABLED, NetworkSurveyConstants.DEFAULT_MQTT_BLUETOOTH_STREAM_SETTING);
        gnssStreamEnabled = sharedPreferences.getBoolean(NetworkSurveyConstants.PROPERTY_MQTT_GNSS_STREAM_ENABLED, NetworkSurveyConstants.DEFAULT_MQTT_GNSS_STREAM_SETTING);
        deviceStatusStreamEnabled = sharedPreferences.getBoolean(NetworkSurveyConstants.PROPERTY_MQTT_DEVICE_STATUS_STREAM_ENABLED, NetworkSurveyConstants.DEFAULT_MQTT_DEVICE_STATUS_STREAM_SETTING);
    }

    @Override
    protected void setConnectionInputFieldsEditable(boolean editable, boolean force)
    {
        super.setConnectionInputFieldsEditable(editable, force);

        cellularStreamToggleSwitch.setEnabled(editable);
        wifiStreamToggleSwitch.setEnabled(editable);
        bluetoothStreamToggleSwitch.setEnabled(editable);
        gnssStreamToggleSwitch.setEnabled(editable);
        deviceStatusStreamToggleSwitch.setEnabled(editable);
    }

    @Override
    protected BrokerConnectionInfo getBrokerConnectionInfo()
    {
        return new MqttConnectionInfo(host,
                portNumber,
                tlsEnabled,
                deviceName,
                mqttUsername,
                mqttPassword,
                cellularStreamEnabled,
                wifiStreamEnabled,
                bluetoothStreamEnabled,
                gnssStreamEnabled,
                deviceStatusStreamEnabled,
                topicPrefix);
    }

    @Override
    protected void onMdmOverride(boolean mdmOverride)
    {
        // If the user has turned on or off the MDM override setting, we want to send out a device
        // status message ASAP so that the receiving MQTT broker can know it has been enabled or
        // disabled. There is of course a race condition in this single message because it is
        // possible the user turns on the MDM override and then immediately stops the MQTT
        // connection before this message is actually sent. There are ways around this but this
        // race condition is acceptable.
        ((NetworkSurveyService) service).sendSingleDeviceStatus();

        super.onMdmOverride(mdmOverride);
    }

    /**
     * Read current values from the MQTT Connection Fragment and return an instance of {@link MqttConnectionSettings}
     * object with those values.
     *
     * @since 1.7.0
     */
    private MqttConnectionSettings getCurrentMqttConnectionSettings()
    {
        int portNumber;
        try
        {
            portNumber = Integer.parseInt(mqttPortNumberEdit.getText().toString());
        } catch (NumberFormatException e)
        {
            portNumber = 1883;
        }

        return new MqttConnectionSettings.Builder()
                .host(mqttHostAddressEdit.getText().toString())
                .port(portNumber)
                .tlsEnabled(tlsToggleSwitch.isChecked())
                .deviceName(deviceNameEdit.getText().toString())
                .mqttUsername(usernameEdit.getText().toString())
                .mqttPassword(passwordEdit.getText().toString())
                .mqttTopicPrefix(topicPrefix)
                .build();
    }

    /**
     * @return True if the {@link Manifest.permission#CAMERA} permission has been granted. False otherwise.
     * @since 1.7.0
     */
    private boolean hasCameraPermission()
    {
        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED)
        {
            Timber.w("The CAMERA permission has not been granted");
            return false;
        }

        return true;
    }

    /**
     * Check to see if we should show the rationale for any of the Bluetooth permissions. If so,
     * then display a dialog that explains what permissions we need for bluetooth to work properly.
     * <p>
     * If we should not show the rationale, then just request the permissions.
     */
    private void showBluetoothPermissionRationaleAndRequestPermissions()
    {
        final FragmentActivity activity = getActivity();
        if (activity == null) return;

        final Context context = getContext();
        if (context == null) return;

        if (missingBluetoothPermissions())
        {
            AlertDialog.Builder alertBuilder = new AlertDialog.Builder(context);
            alertBuilder.setCancelable(true);
            alertBuilder.setTitle(getString(R.string.bluetooth_permissions_rationale_title));
            alertBuilder.setMessage(getText(R.string.bluetooth_permissions_rationale));
            alertBuilder.setPositiveButton(android.R.string.ok, (dialog, which) -> requestBluetoothPermissions());

            AlertDialog permissionsExplanationDialog = alertBuilder.create();
            permissionsExplanationDialog.show();
        }
    }

    /**
     * @return True if any of the Bluetooth permissions have been denied. False if all the permissions
     * have been granted.
     */
    private boolean missingBluetoothPermissions()
    {
        final Context context = getContext();
        if (context == null) return true;
        for (String permission : NetworkSurveyActivity.BLUETOOTH_PERMISSIONS)
        {
            if (ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED)
            {
                Timber.i("Missing the permission: %s", permission);
                return true;
            }
        }

        return false;
    }

    /**
     * Request the permissions needed for bluetooth if any of them have not yet been granted.  If all of the permissions
     * are already granted then don't request anything.
     */
    private void requestBluetoothPermissions()
    {
        if (missingBluetoothPermissions())
        {
            ActivityCompat.requestPermissions(getActivity(), NetworkSurveyActivity.BLUETOOTH_PERMISSIONS, ACCESS_BLUETOOTH_PERMISSION_REQUEST_ID);
        }
    }
}
