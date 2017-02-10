package info.nightscout.android.upload.nightscout;

import android.util.Log;

import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import info.nightscout.android.model.medtronicNg.StatusEvent;
import info.nightscout.android.upload.nightscout.serializer.EntriesSerializer;

import android.support.annotation.NonNull;
import info.nightscout.api.UploadApi;
import info.nightscout.api.GlucoseEndpoints;
import info.nightscout.api.BolusEndpoints.BolusEntry;
import info.nightscout.api.GlucoseEndpoints.GlucoseEntry;
import info.nightscout.api.BolusEndpoints;
import info.nightscout.api.DeviceEndpoints;
import info.nightscout.api.DeviceEndpoints.Iob;
import info.nightscout.api.DeviceEndpoints.Battery;
import info.nightscout.api.DeviceEndpoints.PumpStatus;
import info.nightscout.api.DeviceEndpoints.PumpInfo;
import info.nightscout.api.DeviceEndpoints.DeviceStatus;

public class NightScoutUpload {

    private static final String TAG = NightscoutUploadIntentService.class.getSimpleName();
    private static final SimpleDateFormat ISO8601_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.getDefault());

    public NightScoutUpload () {

    }

    public Boolean doRESTUpload(String url,
                                String secret,
                                int uploaderBatteryLevel,
                                List<StatusEvent> records) {
        Boolean success = false;
        try {
            success = isUploaded(records, url, secret, uploaderBatteryLevel);
        } catch (Exception e) {
            Log.e(TAG, "Unable to do REST API Upload to: " + url, e);
        }
        return success;
    }


    private boolean isUploaded(List<StatusEvent> records,
                                 String baseURL,
                                 String secret,
                                 int uploaderBatteryLevel) throws Exception {

        UploadApi uploadApi = new UploadApi(baseURL, formToken(secret));

        boolean eventsUploaded = uploadEvents(uploadApi.getGlucoseEndpoints(),
                 uploadApi.getBolusApi(),
                 records );

        boolean deviceStatusUploaded = uploadDeviceStatus(uploadApi.getDeviceEndpoints(),
                uploaderBatteryLevel, records);

        return eventsUploaded && deviceStatusUploaded;
    }

    private boolean uploadEvents(GlucoseEndpoints glucoseEndpoints,
                                 BolusEndpoints bolusEndpoints,
                                 List<StatusEvent> records ) throws Exception {


        List<GlucoseEntry> glucoseEntries = new ArrayList<>();
        List<BolusEntry> bolusEntries = new ArrayList<>();

        for (StatusEvent record : records) {

            GlucoseEntry  glucoseEntry = new GlucoseEntry();

            glucoseEntry.setType("sgv");
            glucoseEntry.setDirection(EntriesSerializer.getDirectionStringStatus(record.getCgmTrend()));
            glucoseEntry.setDevice(record.getDeviceName());
            glucoseEntry.setSgv(record.getSgv());
            glucoseEntry.setDate(record.getEventDate().getTime());
            glucoseEntry.setDateString(record.getEventDate().toString());

            glucoseEntries.add(glucoseEntry);
            glucoseEndpoints.sendEntries(glucoseEntries).execute();

            BolusEntry  bolusEntry = new BolusEntry();

            bolusEntry.setType("mbg");
            bolusEntry.setDate(record.getEventDate().getTime());
            bolusEntry.setDateString(record.getEventDate().toString());
            bolusEntry.setDevice(record.getDeviceName());
            bolusEntry.setMbg(record.getBolusWizardBGL());

            bolusEntries.add(bolusEntry);
            bolusEndpoints.sendEntries(bolusEntries).execute();

        }

         return true;
    }

    private boolean uploadDeviceStatus(DeviceEndpoints deviceEndpoints,
                                       int uploaderBatteryLevel,
                                       List<StatusEvent> records) throws Exception {


        List<DeviceStatus> deviceEntries = new ArrayList<>();
        for (StatusEvent record : records) {

            Iob iob = new Iob(record.getPumpDate(), record.getActiveInsulin());
            Battery battery = new Battery(record.getBatteryPercentage());
            PumpStatus pumpstatus;
            if (record.isBolusing()) {
                pumpstatus = new PumpStatus(true, false, "");

            } else if (record.isSuspended()) {
                pumpstatus = new PumpStatus(false, true, "");
            } else {
                pumpstatus = new PumpStatus(false, false, "normal");
            }

            PumpInfo pumpInfo = new PumpInfo(
                    ISO8601_DATE_FORMAT.format(record.getPumpDate()),
                    new BigDecimal(record.getReservoirAmount()).setScale(3, BigDecimal.ROUND_HALF_UP),
                    iob,
                    battery,
                    pumpstatus
                    );

            DeviceStatus deviceStatus = new DeviceStatus(
                    uploaderBatteryLevel,
                    record.getDeviceName(),
                    ISO8601_DATE_FORMAT.format(record.getPumpDate()),
                    pumpInfo
            );

            deviceEntries.add(deviceStatus);
        }

        for (DeviceStatus status: deviceEntries) {
            deviceEndpoints.sendDeviceStatus(status).execute();
        }

        return true ;
    }

     @NonNull
    private String formToken(String secret) throws NoSuchAlgorithmException, UnsupportedEncodingException {
        MessageDigest digest = MessageDigest.getInstance("SHA-1");
        byte[] bytes = secret.getBytes("UTF-8");
        digest.update(bytes, 0, bytes.length);
        bytes = digest.digest();
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b & 0xff));
        }
        return sb.toString();
    }

}
