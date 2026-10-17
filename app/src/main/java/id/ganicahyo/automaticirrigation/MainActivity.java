package id.ganicahyo.automaticirrigation;

import android.app.Activity;
import android.content.ContentValues;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.AdapterView;
import android.widget.TextView;
import android.widget.Toast;

import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttAsyncClient;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;

/** Native MQTT dashboard for ganicahyo@2026 Automatic Irrigation firmware v1.3.0. */
public class MainActivity extends Activity {
    private static final int BG = Color.rgb(243, 246, 247);
    private static final int NAVY = Color.rgb(18, 51, 59);
    private static final int TEAL = Color.rgb(15, 118, 110);
    private static final int TEXT = Color.rgb(31, 48, 54);
    private static final int MUTED = Color.rgb(96, 117, 124);
    private static final int GREEN = Color.rgb(22, 116, 73);
    private static final int RED = Color.rgb(180, 45, 45);
    private static final String PREF = "irrigation_app_config";
    private static final String[] MODES = {"Manual", "Berdasarkan batas kelembapan", "Interval + durasi ON", "Interval + target kelembapan OFF", "Berdasarkan volume air", "Interval + volume air"};
    private static final String[] UNITS = {"Menit", "Jam", "Hari"};
    private static final String[] PLANTS = {"Tanaman umum","Padi","Jagung","Kedelai","Cabai","Tomat","Mentimun","Selada","Sawi","Kentang","Bawang merah","Bawang putih","Wortel","Kangkung","Bayam","Kale","Brokoli","Kembang kol","Terung","Melon","Semangka","Stroberi","Kacang tanah","Kacang hijau","Tebu","Ubi jalar","Singkong","Kopi","Kakao","Pisang","Kelapa sawit","Tembakau","Anggur","Jeruk","Apel","Buncis","Kacang panjang","Labu","Paprika","Selada romaine","Kubis","Pakcoy","Seledri","Daun bawang","Bawang bombai","Bit","Lobak","Radis","Kacang kapri","Kacang polong","Okra","Asparagus","Jagung manis","Sorgum","Gandum","Barley","Oat","Millet","Quinoa","Biji bunga matahari","Canola","Wijen","Jarak pagar","Kapas","Teh","Vanili","Lada","Cengkeh","Pala","Kayu manis","Durian","Mangga","Alpukat","Jambu biji","Jambu air","Nanas","Pepaya","Markisa","Naga","Rambutan","Manggis","Salak","Kelapa","Karet","Nilam","Serai","Kunyit","Jahe","Lengkuas","Kencur","Temulawak","Kemangi","Mint","Rosemary","Basil","Parsley","Cilantro","Microgreens"};

    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private android.content.SharedPreferences prefs;
    private SecureStore secureStore;
    private volatile MqttAsyncClient mqtt;
    private volatile boolean connected = false;
    private volatile JSONObject telemetry = new JSONObject();
    private volatile JSONObject lastSettings = new JSONObject();
    private volatile int currentMode = -1;
    private boolean settingsLoaded = false;
    private boolean applyingSettings = false;
    private int lastPlantSelection = 0;
    private String brokerHost, brokerUser, brokerPassword, commandToken, caPem;
    private int brokerPort;
    private boolean tlsEnabled;
    private String baseTopic, deviceClientId, appClientId, rootTopic;

    private TextView connectionState, moistureValue, rawValue, pumpValue, durationValue;
    private TextView timeValue, wifiValue, ipValue, sdValue, thingSpeakValue, availabilityValue, modeValue, calibrationValue, resultValue, volumeValue;
    private EditText hostField, portField, usernameField, passwordField, baseTopicField, deviceIdField, appIdField, tokenField, caField;
    private CheckBox tlsCheck;
    private Spinner modeSpinner, intervalUnitSpinner, plantSpinner;
    private EditText lowerField, upperField, targetField, targetVolumeField, intervalField, durationField, maxRuntimeField, flowRateField;
    private CheckBox restartEnabledCheck, thingSpeakEnabledCheck;
    private EditText restartHourField, restartMinuteField, plantNameField, thingSpeakIntervalField, sdLogIntervalField;
    private CheckBox staEnterpriseCheck;
    private Spinner staEapMethodSpinner, staEapPhase2Spinner;
    private EditText staSsidField, staPasswordField, staEapIdentityField, staEapUsernameField, staEapPasswordField, staEapDomainField;
    private Button connectButton, pumpOnButton, pumpOffButton, modeManualButton, modeResumeButton;
    private boolean hasMoisture = false;
    private String sdDownloadName = null;
    private StringBuilder sdDownloadBuffer = new StringBuilder();

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences(PREF, MODE_PRIVATE);
        secureStore = new SecureStore(this);
        buildUi();
        loadConnectionFields();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);

        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setPadding(dp(20), dp(22), dp(20), dp(18));
        hero.setBackgroundColor(NAVY);
        TextView appTitle = new TextView(this);
        appTitle.setText("ganicahyo@2026");
        appTitle.setTextColor(Color.WHITE);
        appTitle.setTextSize(13);
        appTitle.setTypeface(null, Typeface.BOLD);
        TextView productTitle = new TextView(this);
        productTitle.setText("Automatic Irrigation");
        productTitle.setTextColor(Color.WHITE);
        productTitle.setTextSize(24);
        productTitle.setTypeface(null, Typeface.BOLD);
        TextView subTitle = new TextView(this);
        subTitle.setText("DASHBOARD MQTT • VERSI 1.3.0");
        subTitle.setTextColor(Color.rgb(182, 222, 217));
        subTitle.setTextSize(11);
        subTitle.setPadding(0, dp(3), 0, dp(12));
        connectionState = new TextView(this);
        connectionState.setText("● Belum terhubung ke broker");
        connectionState.setTextColor(Color.rgb(255, 220, 150));
        connectionState.setTextSize(13);
        hero.addView(appTitle); hero.addView(productTitle); hero.addView(subTitle); hero.addView(connectionState);
        root.addView(hero);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(12), dp(12), dp(12), dp(24));
        scroll.addView(page);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout dash = card(page, "Pemantauan realtime");
        LinearLayout metricRow1 = horizontal(dash);
        moistureValue = metric(metricRow1, "KELEMBAPAN TANAH", "--.- %", true);
        rawValue = metric(metricRow1, "ADC ASLI", "----", false);
        LinearLayout metricRow2 = horizontal(dash);
        pumpValue = metric(metricRow2, "STATUS POMPA", "--", false);
        durationValue = metric(metricRow2, "DURASI AKTIF", "00:00:00", false);
        LinearLayout metricRow3 = horizontal(dash);
        volumeValue = metric(metricRow3, "ESTIMASI VOLUME AIR", "0.000 L", false);
        label(metricRow3, "Debit × durasi pompa", 10, false, MUTED);
        modeValue = label(dash, "Mode: menunggu data", 14, true, TEXT);
        calibrationValue = label(dash, "Sensor: menunggu telemetri", 12, false, MUTED);
        timeValue = label(dash, "Waktu ESP32: --", 12, false, MUTED);
        wifiValue = label(dash, "Wi-Fi: --", 12, false, MUTED);
        ipValue = label(dash, "IP ESP32: --", 12, false, MUTED);
        sdValue = label(dash, "microSD: --", 12, false, MUTED);
        thingSpeakValue = label(dash, "ThingSpeak: --", 12, false, MUTED);
        availabilityValue = label(dash, "ESP32 availability: --", 12, false, MUTED);

        LinearLayout controls = card(page, "Kontrol pompa");
        TextView modeDescription = label(controls, "Mode yang diizinkan firmware: Manual=0, Kelembapan=1, Interval+Durasi=2, Interval+Target=3.", 12, false, MUTED);
        modeSpinner = spinner(controls, MODES);
        LinearLayout modeButtons = horizontal(controls);
        modeManualButton = button("Aktifkan Manual", false, () -> sendCommand("SET_MODE", jsonOf("mode", 0)));
        Button setModeButton = button("Terapkan Mode", true, () -> {
            if (modeSpinner != null) sendCommand("SET_MODE", jsonOf("mode", modeSpinner.getSelectedItemPosition()));
        });
        modeButtons.addView(modeManualButton, new LinearLayout.LayoutParams(0, dp(46), 1));
        modeButtons.addView(setModeButton, new LinearLayout.LayoutParams(0, dp(46), 1));
        modeResumeButton = button("Matikan Mode Manual • kembali otomatis", true, () -> sendCommand("RESUME_AUTO", new JSONObject()));
        modeResumeButton.setEnabled(false);
        controls.addView(modeResumeButton, matchWrap());
        LinearLayout pumpButtons = horizontal(controls);
        pumpOnButton = button("Pompa ON", false, () -> {
            if (currentMode != 0) {
                new AlertDialog.Builder(this).setTitle("Aktifkan kontrol manual?")
                        .setMessage("Firmware hanya menerima PUMP_ON pada mode Manual. Aktifkan mode Manual sekarang?")
                        .setNegativeButton("Batal", null).setPositiveButton("Aktifkan", (d, w) -> sendCommand("SET_MODE", jsonOf("mode", 0))).show();
            } else sendCommand("PUMP_ON", new JSONObject());
        });
        pumpOffButton = button("Pompa OFF", true, () -> sendCommand("PUMP_OFF", new JSONObject()));
        pumpButtons.addView(pumpOnButton, new LinearLayout.LayoutParams(0, dp(48), 1));
        pumpButtons.addView(pumpOffButton, new LinearLayout.LayoutParams(0, dp(48), 1));
        pumpOnButton.setEnabled(false);
        label(controls, "PUMP_ON hanya berjalan saat Manual. Tombol Matikan Mode Manual menghentikan pompa secara aman lalu memulihkan mode otomatis terakhir tanpa mengatur ulang parameter.", 11, false, MUTED);

        LinearLayout settingCard = card(page, "Pengaturan irigasi");
        LinearLayout thresholdRow = horizontal(settingCard);
        lowerField = field(thresholdRow, "Batas bawah (%)", "30", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL, true);
        upperField = field(thresholdRow, "Batas atas (%)", "70", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL, true);
        targetField = field(settingCard, "Target kelembapan OFF (%)", "70", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL, false);
        targetVolumeField = field(settingCard, "Target volume air Mode 4/5 (liter)", "10.000", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL, false);
        intervalField = field(settingCard, "Interval penyiraman", "6", InputType.TYPE_CLASS_NUMBER, false);
        intervalUnitSpinner = spinner(settingCard, UNITS);
        LinearLayout intervalRow = horizontal(settingCard);
        durationField = field(intervalRow, "Durasi mode interval (menit)", "5", InputType.TYPE_CLASS_NUMBER, true);
        maxRuntimeField = field(intervalRow, "Maximum runtime (menit)", "30", InputType.TYPE_CLASS_NUMBER, true);
        flowRateField = field(settingCard, "Debit pompa (liter/menit)", "1.00", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL, false);
        Button saveIrrigation = button("Simpan pengaturan irigasi", false, this::saveIrrigationSettings);
        settingCard.addView(saveIrrigation, matchWrap());

        LinearLayout restartCard = card(page, "Jadwal restart otomatis");
        restartEnabledCheck = new CheckBox(this); restartEnabledCheck.setText("Aktifkan restart harian (WIB)"); restartCard.addView(restartEnabledCheck);
        LinearLayout restartRow = horizontal(restartCard);
        restartHourField = field(restartRow, "Jam (0–23)", "3", InputType.TYPE_CLASS_NUMBER, true);
        restartMinuteField = field(restartRow, "Menit (0–59)", "0", InputType.TYPE_CLASS_NUMBER, true);
        restartCard.addView(button("Simpan jadwal restart", false, () -> {
            int h = intFrom(restartHourField, -1), m = intFrom(restartMinuteField, -1);
            if (h < 0 || h > 23 || m < 0 || m > 59) { toast("Jam 0–23 dan menit 0–59"); return; }
            sendCommand("SET_RESTART", jsonOf("enabled", restartEnabledCheck.isChecked(), "hour", h, "minute", m));
        }), matchWrap());
        restartCard.addView(button("Restart ESP32 sekarang…", true, () -> new AlertDialog.Builder(this)
                .setTitle("Restart ESP32?").setMessage("Firmware akan mematikan pompa sebelum reboot. Setelah reboot, otomasi tersimpan dapat aktif kembali.")
                .setNegativeButton("Batal", null).setPositiveButton("Restart", (d, w) -> sendCommand("RESTART", new JSONObject())).show()), matchWrap());

        LinearLayout plantCard = card(page, "Tanaman");
        plantNameField = field(plantCard, "Nama tanaman / lokasi", "Tanaman 1", InputType.TYPE_CLASS_TEXT, false);
        plantSpinner = spinner(plantCard, PLANTS);
        plantSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parentView, View view, int position, long id) {
                if (!applyingSettings && position != lastPlantSelection) applyPlantPreset(position);
                lastPlantSelection = position;
            }
            @Override public void onNothingSelected(AdapterView<?> parentView) { }
        });
        plantCard.addView(button("Simpan tanaman", false, () -> {
            String name = plantNameField.getText().toString().trim();
            if (name.length() > 39) { toast("Nama maksimal 39 karakter"); return; }
            sendCommand("SET_PLANT", jsonOf("name", name, "type", plantSpinner.getSelectedItemPosition()));
        }), matchWrap());
        label(plantCard, "Jenis tanaman adalah profil referensi; batas kelembapan tetap dapat disesuaikan pada pengaturan irigasi.", 11, false, MUTED);

        LinearLayout tsCard = card(page, "Pencatatan microSD & ThingSpeak");
        label(tsCard, "Masukkan interval bebas 1–1440 menit. Jadwal memakai jam kalender WIB berbasis HH:MM, bukan waktu sejak boot; slot tersimpan sehingga restart tidak menggeser jadwal. Pastikan jam ESP32 tersinkronisasi.", 12, false, MUTED);
        sdLogIntervalField = field(tsCard, "Interval rekam microSD (menit, 1–1440)", "30", InputType.TYPE_CLASS_NUMBER, false);
        tsCard.addView(button("Simpan jadwal microSD", false, () -> {
            int min = intFrom(sdLogIntervalField, -1);
            if (min < 1 || min > 1440) { toast("Interval microSD harus 1–1440 menit"); return; }
            sendCommand("SET_SD_LOG_INTERVAL", jsonOf("intervalMin", min));
        }), matchWrap());
        thingSpeakEnabledCheck = new CheckBox(this); thingSpeakEnabledCheck.setText("Aktifkan upload ThingSpeak"); tsCard.addView(thingSpeakEnabledCheck);
        thingSpeakIntervalField = field(tsCard, "Interval upload ThingSpeak (menit, 1–1440)", "30", InputType.TYPE_CLASS_NUMBER, false);
        tsCard.addView(button("Simpan jadwal ThingSpeak", false, () -> {
            int min = intFrom(thingSpeakIntervalField, -1);
            if (min < 1 || min > 1440) { toast("Interval ThingSpeak harus 1–1440 menit"); return; }
            sendCommand("SET_THINGSPEAK", jsonOf("enabled", thingSpeakEnabledCheck.isChecked(), "uploadMin", min));
        }), matchWrap());
        label(tsCard, "API Key ThingSpeak tetap dikonfigurasi pada dashboard web ESP32. Pencatatan SD dan upload ThingSpeak memiliki jadwal terpisah.", 11, false, MUTED);

        LinearLayout wifiCard = card(page, "Wi-Fi STA Enterprise / akses jaringan institusi");
        label(wifiCard, "Gunakan untuk jaringan 802.1X EAP yang meminta identity, username, dan password (misalnya PEAP atau TTLS). Password kosong berarti menggunakan kredensial yang sudah tersimpan. CA sertifikat dan konfigurasi jaringan tingkat lanjut tersedia di dashboard web ESP32. Login SSO yang membuka browser/captive portal atau SAML/OAuth tidak didukung langsung oleh ESP32.", 12, false, MUTED);
        staSsidField = field(wifiCard, "SSID jaringan STA", "", InputType.TYPE_CLASS_TEXT, false);
        staPasswordField = field(wifiCard, "Password Wi-Fi biasa (kosong = pertahankan)", "", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD, false);
        staEnterpriseCheck = new CheckBox(this); staEnterpriseCheck.setText("Gunakan Wi-Fi Enterprise (802.1X / EAP)"); wifiCard.addView(staEnterpriseCheck);
        staEapMethodSpinner = spinner(wifiCard, new String[]{"PEAP", "TTLS"});
        staEapPhase2Spinner = spinner(wifiCard, new String[]{"TTLS Phase 2: MSCHAPv2", "TTLS Phase 2: PAP", "TTLS Phase 2: CHAP", "TTLS Phase 2: MSCHAP"});
        staEapIdentityField = field(wifiCard, "EAP Identity (mis. user@domain)", "", InputType.TYPE_CLASS_TEXT, false);
        staEapUsernameField = field(wifiCard, "Username jaringan", "", InputType.TYPE_CLASS_TEXT, false);
        staEapPasswordField = field(wifiCard, "Password EAP (kosong = pertahankan)", "", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD, false);
        staEapDomainField = field(wifiCard, "Domain sertifikat server (opsional)", "", InputType.TYPE_CLASS_TEXT, false);
        wifiCard.addView(button("Simpan & terapkan Wi-Fi STA", false, () -> {
            String ssid = staSsidField.getText().toString().trim();
            String identity = staEapIdentityField.getText().toString().trim();
            String username = staEapUsernameField.getText().toString().trim();
            String eapPassword = staEapPasswordField.getText().toString();
            String normalPassword = staPasswordField.getText().toString();
            String domain = staEapDomainField.getText().toString().trim();
            boolean enterpriseEnabled = staEnterpriseCheck.isChecked();
            if (ssid.isEmpty() || ssid.length() > 32) { toast("SSID wajib dan maksimal 32 karakter"); return; }
            if (identity.length() > 80 || username.length() > 80 || eapPassword.length() > 64 || domain.length() > 80 || normalPassword.length() > 64) { toast("Panjang kredensial Wi-Fi terlalu besar untuk perintah MQTT"); return; }
            if (enterpriseEnabled && (identity.isEmpty() || username.isEmpty())) { toast("Wi-Fi Enterprise memerlukan EAP identity dan username"); return; }
            if (!enterpriseEnabled && normalPassword.isEmpty()) { toast("Pastikan password Wi-Fi biasa sudah tersimpan pada ESP32 sebelum menerapkan."); }
            JSONObject wifi = jsonOf("ssid", ssid, "enterpriseEnabled", enterpriseEnabled, "eapMethod", staEapMethodSpinner.getSelectedItemPosition(), "eapPhase2", staEapPhase2Spinner.getSelectedItemPosition(), "identity", identity, "username", username, "eapPassword", eapPassword, "password", normalPassword, "domain", domain);
            sendCommand("SET_WIFI", wifi);
            staPasswordField.setText(""); staEapPasswordField.setText("");
        }), matchWrap());

        LinearLayout tools = card(page, "Kalibrasi, waktu, dan status");
        LinearLayout toolRow1 = horizontal(tools);
        toolRow1.addView(button("Kalibrasi 0%", true, () -> new AlertDialog.Builder(this).setTitle("Kalibrasi 0%")
                .setMessage("Pastikan sensor berada pada kondisi referensi 0% sebelum melanjutkan.")
                .setNegativeButton("Batal", null).setPositiveButton("Kalibrasi", (d, w) -> sendCommand("CALIBRATE", jsonOf("point", 0))).show()), new LinearLayout.LayoutParams(0, dp(46), 1));
        toolRow1.addView(button("Kalibrasi 100%", true, () -> new AlertDialog.Builder(this).setTitle("Kalibrasi 100%")
                .setMessage("Pastikan sensor berada pada kondisi referensi basah/jenuh yang Anda gunakan untuk kalibrasi 100%.")
                .setNegativeButton("Batal", null).setPositiveButton("Kalibrasi", (d, w) -> sendCommand("CALIBRATE", jsonOf("point", 100))).show()), new LinearLayout.LayoutParams(0, dp(46), 1));
        LinearLayout toolRow2 = horizontal(tools);
        toolRow2.addView(button("Sinkronkan waktu", false, () -> sendCommand("SYNC_TIME", jsonOf("epoch", System.currentTimeMillis() / 1000L))), new LinearLayout.LayoutParams(0, dp(46), 1));
        toolRow2.addView(button("Minta pengaturan", true, () -> sendCommand("GET_SETTINGS", new JSONObject())), new LinearLayout.LayoutParams(0, dp(46), 1));
        resultValue = label(tools, "Hasil perintah: menunggu", 12, false, MUTED);

        LinearLayout broker = card(page, "Koneksi MQTT");
        label(broker, "Samakan host, port, username/password, topik dasar, ID perangkat, dan token dengan pengaturan MQTT di dashboard web ESP32. Untuk broker internet, gunakan TLS.", 12, false, MUTED);
        hostField = field(broker, "Broker host dari EMQX Console (hostname lengkap)", "", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI, false);
        label(broker, "Deployment ID tersimpan sebagai referensi: deployment-r696f91a. ID ini bukan hostname; salin endpoint broker lengkap dari EMQX Console.", 11, false, MUTED);
        LinearLayout hostPortRow = horizontal(broker);
        portField = field(hostPortRow, "Port", "8883", InputType.TYPE_CLASS_NUMBER, true);
        tlsCheck = new CheckBox(this); tlsCheck.setText("Gunakan TLS/SSL"); tlsCheck.setChecked(true); tlsCheck.setPadding(dp(8), dp(12), 0, 0);
        hostPortRow.addView(tlsCheck, new LinearLayout.LayoutParams(0, dp(58), 1));
        usernameField = field(broker, "Username broker", "", InputType.TYPE_CLASS_TEXT, false);
        passwordField = field(broker, "Password broker", "", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD, false);
        baseTopicField = field(broker, "Topik dasar", "ganicahyo/automatic-irrigation", InputType.TYPE_CLASS_TEXT, false);
        deviceIdField = field(broker, "Client ID ESP32 (dari dashboard web)", "irrigation-esp32-01", InputType.TYPE_CLASS_TEXT, false);
        appIdField = field(broker, "Client ID aplikasi (harus unik)", defaultAppClientId(), InputType.TYPE_CLASS_TEXT, false);
        tokenField = field(broker, "Token kontrol rahasia (minimal 16 karakter)", "", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD, false);
        caField = field(broker, "Root CA PEM (opsional bila sertifikat dipercaya Android)", "", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE, false);
        caField.setSingleLine(false); caField.setMinLines(2); caField.setMaxLines(5);
        LinearLayout connectionButtons = horizontal(broker);
        connectButton = button("Simpan & Hubungkan", false, () -> { if (saveConnectionFields()) connectMqtt(); });
        connectionButtons.addView(connectButton, new LinearLayout.LayoutParams(0, dp(48), 1));
        connectionButtons.addView(button("Putuskan", true, this::disconnectMqtt), new LinearLayout.LayoutParams(0, dp(48), 1));
        broker.addView(button("Lupakan token/password tersimpan", true, () -> new AlertDialog.Builder(this)
                .setTitle("Hapus kredensial tersimpan?").setMessage("Password broker, token kontrol, dan CA akan dihapus dari penyimpanan aplikasi.")
                .setNegativeButton("Batal", null).setPositiveButton("Hapus", (d, w) -> clearSecrets()).show()), matchWrap());

        LinearLayout explorer = card(page, "Data Explorer • microSD via MQTT");
        label(explorer, "Melihat daftar file, isi CSV/log, menghapus file, dan mengunduh file dilakukan melalui MQTT. Firmware membuat file bulanan: irrigation_YYYY-MM.csv, pump_events_YYYY-MM.csv, system_YYYY-MM.log.", 12, false, MUTED);
        LinearLayout exrow = horizontal(explorer);
        exrow.addView(button("Daftar file SD", false, () -> sendCommand("LIST_SD", new JSONObject())), new LinearLayout.LayoutParams(0, dp(46), 1));
        EditText sdFileNameField = field(explorer, "Nama file untuk lihat/hapus/download", "irrigation_YYYY-MM.csv", InputType.TYPE_CLASS_TEXT, false);
        LinearLayout exrow2 = horizontal(explorer);
        exrow2.addView(button("Lihat isi", false, () -> sendCommand("VIEW_SD", jsonOf("name", sdFileNameField.getText().toString().trim()))), new LinearLayout.LayoutParams(0, dp(46), 1));
        exrow2.addView(button("Download", false, () -> startSdDownload(sdFileNameField.getText().toString().trim())), new LinearLayout.LayoutParams(0, dp(46), 1));
        exrow2.addView(button("Hapus file", true, () -> new AlertDialog.Builder(this).setTitle("Hapus file SD?").setMessage("File akan dihapus permanen dari kartu microSD. Pastikan sudah dibackup.").setNegativeButton("Batal", null).setPositiveButton("Hapus", (d,w)->sendCommand("DELETE_SD", jsonOf("name", sdFileNameField.getText().toString().trim()))).show()), new LinearLayout.LayoutParams(0, dp(46), 1));
        label(explorer, "Catatan: MQTT cocok untuk kontrol dan preview. Download file CSV/log berukuran besar tetap menggunakan dashboard web lokal ESP32.", 11, false, MUTED);

        LinearLayout manual = card(page, "Info aplikasi & manual ringkas");
        label(manual, "Aplikasi ini mengakses ESP32 melalui broker MQTT, bukan lewat halaman web lokal. ESP32 harus terhubung ke Wi-Fi STA yang dapat menjangkau broker. Topik standar: BASE/CLIENT_ID/telemetry, /settings, /availability, /command, dan /result.", 12, false, TEXT);
        label(manual, "Mode: 0 Manual; 1 batas kelembapan; 2 interval + durasi; 3 interval + target kelembapan; 4 volume air; 5 interval + volume air. Tombol Matikan Mode Manual mengembalikan mode otomatis yang tersimpan. Interval SD dan ThingSpeak 1–1440 menit berbasis slot kalender WIB HH:MM dan tidak bergeser saat restart. File SD baru dibuat setiap tanggal 1. Pump events menyimpan durasi dan estimasi volume air berdasarkan debit L/menit. Data Explorer dapat list, lihat, download bertahap, dan delete. Enterprise mendukung 802.1X EAP-PEAP/TTLS; SSO browser/captive portal bukan metode EAP dan tidak didukung langsung. Semua perintah kontrol menyertakan token rahasia. TLS tidak boleh dimatikan untuk broker publik/internet.", 12, false, MUTED);
        label(manual, "Aplikasi menampilkan telemetri yang dipublikasikan firmware (umumnya setiap 10 detik). Untuk mengubah broker atau token kontrol ESP32, gunakan kartu MQTT pada dashboard web lokal ESP32 terlebih dahulu. Jangan bagikan token kontrol.", 12, false, MUTED);

        setContentView(root);
    }

    private LinearLayout card(LinearLayout parent, String title) {
        LinearLayout outer = new LinearLayout(this);
        outer.setOrientation(LinearLayout.VERTICAL);
        outer.setPadding(dp(14), dp(13), dp(14), dp(14));
        GradientDrawable bg = new GradientDrawable(); bg.setColor(Color.WHITE); bg.setCornerRadius(dp(16)); bg.setStroke(dp(1), Color.rgb(224, 232, 233));
        outer.setBackground(bg); outer.setElevation(dp(2));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.bottomMargin = dp(12);
        parent.addView(outer, lp);
        TextView heading = label(outer, title, 17, true, NAVY); heading.setPadding(0, 0, 0, dp(9));
        return outer;
    }

    private LinearLayout horizontal(LinearLayout parent) {
        LinearLayout row = new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL); row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.bottomMargin = dp(4); parent.addView(row, lp); return row;
    }

    private TextView metric(LinearLayout row, String title, String value, boolean primary) {
        LinearLayout tile = new LinearLayout(this); tile.setOrientation(LinearLayout.VERTICAL); tile.setPadding(dp(10), dp(11), dp(8), dp(11));
        GradientDrawable gd = new GradientDrawable(); gd.setColor(primary ? Color.rgb(226, 245, 241) : Color.rgb(246, 249, 249)); gd.setCornerRadius(dp(12));
        tile.setBackground(gd);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1); lp.setMargins(dp(2), dp(2), dp(5), dp(5)); row.addView(tile, lp);
        TextView t = label(tile, title, 10, true, MUTED); t.setPadding(0, 0, 0, dp(5));
        TextView v = label(tile, value, primary ? 25 : 21, true, primary ? TEAL : NAVY);
        return v;
    }

    private TextView label(LinearLayout parent, String text, int size, boolean bold, int color) {
        TextView tv = new TextView(this); tv.setText(text); tv.setTextSize(size); tv.setTextColor(color);
        if (bold) tv.setTypeface(null, Typeface.BOLD);
        tv.setPadding(0, dp(3), 0, dp(5));
        parent.addView(tv, new LinearLayout.LayoutParams(-1, -2)); return tv;
    }

    private EditText field(LinearLayout parent, String title, String value, int inputType, boolean weightInRow) {
        LinearLayout wrap = new LinearLayout(this); wrap.setOrientation(LinearLayout.VERTICAL);
        TextView label = new TextView(this); label.setText(title); label.setTextSize(12); label.setTextColor(MUTED); label.setPadding(0, dp(5), 0, dp(3));
        wrap.addView(label);
        EditText edit = new EditText(this); edit.setText(value); edit.setTextSize(14); edit.setSingleLine(true); edit.setInputType(inputType); edit.setPadding(dp(10), dp(7), dp(10), dp(7));
        edit.setBackground(roundBackground(Color.rgb(249, 251, 251), Color.rgb(218, 228, 229), 10));
        wrap.addView(edit, new LinearLayout.LayoutParams(-1, dp(45)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(weightInRow ? 0 : -1, -2, weightInRow ? 1 : 0); lp.setMargins(dp(2), 0, dp(7), dp(8)); parent.addView(wrap, lp);
        return edit;
    }

    private Spinner spinner(LinearLayout parent, String[] entries) {
        Spinner sp = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, entries);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item); sp.setAdapter(adapter);
        sp.setPadding(dp(6), dp(4), dp(6), dp(4));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(48)); lp.bottomMargin = dp(8); parent.addView(sp, lp); return sp;
    }

    private Button button(String text, boolean secondary, Runnable action) {
        Button b = new Button(this); b.setText(text); b.setAllCaps(false); b.setTextSize(13); b.setMinHeight(dp(44)); b.setPadding(dp(10), dp(5), dp(10), dp(5));
        b.setTextColor(secondary ? NAVY : Color.WHITE);
        b.setBackground(roundBackground(secondary ? Color.rgb(232, 239, 240) : TEAL, secondary ? Color.rgb(218, 228, 229) : TEAL, 12));
        b.setOnClickListener(v -> action.run());
        return b;
    }

    private GradientDrawable roundBackground(int fill, int stroke, int radiusDp) {
        GradientDrawable gd = new GradientDrawable(); gd.setColor(fill); gd.setCornerRadius(dp(radiusDp)); gd.setStroke(dp(1), stroke); return gd;
    }

    private LinearLayout.LayoutParams matchWrap() { return new LinearLayout.LayoutParams(-1, -2); }
    private int dp(float value) { return (int) (value * getResources().getDisplayMetrics().density + 0.5f); }

    private void loadConnectionFields() {
        hostField.setText(prefs.getString("host", ""));
        portField.setText(String.valueOf(prefs.getInt("port", 8883)));
        tlsCheck.setChecked(prefs.getBoolean("tls", true));
        usernameField.setText(prefs.getString("username", ""));
        passwordField.setText(secureStore.get("password", ""));
        baseTopicField.setText(prefs.getString("baseTopic", "ganicahyo/automatic-irrigation"));
        deviceIdField.setText(prefs.getString("deviceId", "irrigation-esp32-01"));
        appIdField.setText(prefs.getString("appId", defaultAppClientId()));
        tokenField.setText(secureStore.get("token", ""));
        caField.setText(secureStore.get("ca", ""));
    }

    private String defaultAppClientId() {
        String existing = getSharedPreferences(PREF, MODE_PRIVATE).getString("appId", "");
        if (existing != null && !existing.isEmpty()) return existing;
        String id = "irrigation-app-" + UUID.randomUUID().toString().substring(0, 8);
        getSharedPreferences(PREF, MODE_PRIVATE).edit().putString("appId", id).apply();
        return id;
    }

    private boolean saveConnectionFields() {
        String host = hostField.getText().toString().trim();
        int port = intFrom(portField, 8883);
        String base = baseTopicField.getText().toString().trim().replaceAll("/+$", "");
        String device = deviceIdField.getText().toString().trim();
        String appId = appIdField.getText().toString().trim();
        if (host.contains("://") || host.contains("/") || host.contains(" ")) { toast("Host broker isi hostname saja"); return false; }
        if (port < 1 || port > 65535) { toast("Port MQTT tidak valid"); return false; }
        if (base.isEmpty() || base.contains("//") || device.isEmpty() || device.contains("/") || appId.isEmpty()) { toast("Topik dasar dan Client ID tidak valid"); return false; }
        String token = tokenField.getText().toString().trim();
        if (token.length() < 16) { toast("Token kontrol wajib minimal 16 karakter dan harus sama dengan ESP32"); return false; }
        try {
            secureStore.put("password", passwordField.getText().toString());
            secureStore.put("token", token);
            secureStore.put("ca", caField.getText().toString().trim());
        } catch (Exception e) {
            toast("Gagal menyimpan kredensial secara aman: " + e.getMessage()); return false;
        }
        prefs.edit().putString("host", host).putInt("port", port).putBoolean("tls", tlsCheck.isChecked())
                .putString("username", usernameField.getText().toString().trim()).putString("baseTopic", base)
                .putString("deviceId", device).putString("appId", appId).apply();
        brokerHost = host; brokerPort = port; tlsEnabled = tlsCheck.isChecked(); brokerUser = usernameField.getText().toString().trim();
        brokerPassword = passwordField.getText().toString(); commandToken = token; caPem = caField.getText().toString().trim();
        baseTopic = base; deviceClientId = device; appClientId = appId; rootTopic = baseTopic + "/" + deviceClientId;
        return true;
    }

    private void connectMqtt() {
        if (brokerHost == null) { toast("Simpan pengaturan broker dahulu"); return; }
        if (brokerHost.isEmpty()) { toast("Masukkan hostname broker MQTT"); return; }
        if (tlsEnabled && caPem.isEmpty()) {
            // System trust store validates mainstream public CA certificates such as those used by managed brokers.
            // Custom CA PEM can be provided for private or private-CA deployments.
        }
        setConnectionUi(false, "Menghubungkan ke " + brokerHost + ":" + brokerPort + "…");
        io.execute(() -> {
            try {
                MqttAsyncClient old = mqtt;
                if (old != null) {
                    try { if (old.isConnected()) old.disconnect(1000).waitForCompletion(1500); } catch (Exception ignored) { }
                    try { old.close(true); } catch (Exception ignored) { }
                }
                String uri = (tlsEnabled ? "ssl://" : "tcp://") + brokerHost + ":" + brokerPort;
                MqttAsyncClient client = new MqttAsyncClient(uri, appClientId, new MemoryPersistence());
                client.setCallback(new MqttCallbackExtended() {
                    @Override public void connectComplete(boolean reconnect, String serverURI) {
                        connected = true;
                        main.post(() -> setConnectionUi(true, reconnect ? "MQTT tersambung kembali" : "MQTT terhubung"));
                        subscribeTopics(client);
                        main.postDelayed(() -> sendCommand("GET_SETTINGS", new JSONObject()), 700);
                    }
                    @Override public void connectionLost(Throwable cause) {
                        connected = false;
                        main.post(() -> setConnectionUi(false, "Koneksi terputus: " + (cause == null ? "unknown" : cause.getMessage())));
                    }
                    @Override public void messageArrived(String topic, MqttMessage message) {
                        String body = new String(message.getPayload(), StandardCharsets.UTF_8);
                        handleMqttMessage(topic, body);
                    }
                    @Override public void deliveryComplete(IMqttDeliveryToken token) { }
                });
                mqtt = client;
                MqttConnectOptions options = new MqttConnectOptions();
                options.setAutomaticReconnect(true); options.setCleanSession(true); options.setConnectionTimeout(12); options.setKeepAliveInterval(30);
                if (!brokerUser.isEmpty()) { options.setUserName(brokerUser); options.setPassword(brokerPassword == null ? new char[0] : brokerPassword.toCharArray()); }
                if (tlsEnabled) options.setSocketFactory(makeSocketFactory(caPem));
                client.connect(options, null, new IMqttActionListener() {
                    @Override public void onSuccess(org.eclipse.paho.client.mqttv3.IMqttToken asyncActionToken) { }
                    @Override public void onFailure(org.eclipse.paho.client.mqttv3.IMqttToken asyncActionToken, Throwable exception) {
                        connected = false;
                        main.post(() -> setConnectionUi(false, "Gagal terhubung: " + (exception == null ? "cek broker/ACL" : exception.getMessage())));
                    }
                });
            } catch (Exception e) {
                connected = false;
                main.post(() -> setConnectionUi(false, "Error MQTT: " + e.getMessage()));
            }
        });
    }

    // Builds a verified TLS socket factory using either Android's standard trust store or a provided root CA.
    private SSLSocketFactory makeSocketFactory(String pem) throws Exception {
        SSLContext context = SSLContext.getInstance("TLS");
        if (pem == null || pem.trim().isEmpty()) {
            context.init(null, null, new SecureRandom());
            return new VerifiedTlsSocketFactory((SSLSocketFactory) context.getSocketFactory());
        }
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        X509Certificate ca = (X509Certificate) cf.generateCertificate(new ByteArrayInputStream(pem.getBytes(StandardCharsets.US_ASCII)));
        KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType()); ks.load(null); ks.setCertificateEntry("mqtt-custom-root", ca);
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()); tmf.init(ks);
        context.init(null, tmf.getTrustManagers(), new SecureRandom());
        return new VerifiedTlsSocketFactory((SSLSocketFactory) context.getSocketFactory());
    }

    private void subscribeTopics(MqttAsyncClient client) {
        try {
            String[] topics = {rootTopic + "/telemetry", rootTopic + "/settings", rootTopic + "/availability", rootTopic + "/result"};
            int[] qos = {1, 1, 1, 1};
            client.subscribe(topics, qos, null, new IMqttActionListener() {
                @Override public void onSuccess(org.eclipse.paho.client.mqttv3.IMqttToken token) {
                    main.post(() -> appendResult("Berlangganan topic telemetry/settings/availability/result."));
                }
                @Override public void onFailure(org.eclipse.paho.client.mqttv3.IMqttToken token, Throwable error) {
                    main.post(() -> appendResult("Subscribe gagal: " + (error == null ? "unknown" : error.getMessage())));
                }
            });
        } catch (MqttException e) { main.post(() -> appendResult("Subscribe MQTT error: " + e.getMessage())); }
    }

    private void disconnectMqtt() {
        connected = false;
        MqttAsyncClient client = mqtt;
        if (client == null) { setConnectionUi(false, "Tidak terhubung"); return; }
        io.execute(() -> {
            try { if (client.isConnected()) client.disconnect(1000).waitForCompletion(1500); } catch (Exception ignored) { }
            try { client.close(true); } catch (Exception ignored) { }
            if (mqtt == client) mqtt = null;
            main.post(() -> setConnectionUi(false, "Diputus oleh pengguna"));
        });
    }

    private void handleMqttMessage(String topic, String payload) {
        if (topic.equals(rootTopic + "/availability")) {
            main.post(() -> { availabilityValue.setText("ESP32 availability: " + payload); availabilityValue.setTextColor("online".equalsIgnoreCase(payload) ? GREEN : RED); });
            return;
        }
        try {
            JSONObject object = new JSONObject(payload);
            if (topic.equals(rootTopic + "/telemetry")) {
                telemetry = object; currentMode = object.optInt("mode", currentMode); hasMoisture = !object.isNull("moisture_pct") && object.optDouble("moisture_pct", -1) >= 0;
                main.post(this::renderTelemetry);
            } else if (topic.equals(rootTopic + "/settings")) {
                lastSettings = object;
                if (!settingsLoaded) { settingsLoaded = true; main.post(() -> applyRemoteSettings(object)); }
                else main.post(() -> modeValue.setText("Mode berjalan: " + modeName(object.optInt("mode", currentMode))));
            } else if (topic.equals(rootTopic + "/result")) {
                String cmd = object.optString("cmd", "COMMAND");
                if ("DOWNLOAD_SD".equals(cmd) && object.optBoolean("ok", false)) {
                    String name = object.optString("name", sdDownloadName == null ? "download.csv" : sdDownloadName);
                    if (sdDownloadName == null || !name.equals(sdDownloadName)) { sdDownloadName = name; sdDownloadBuffer.setLength(0); }
                    sdDownloadBuffer.append(object.optString("content", ""));
                    boolean done = object.optBoolean("done", true); long next = object.optLong("nextOffset", sdDownloadBuffer.length());
                    if (done) { String all = sdDownloadBuffer.toString(); main.post(() -> { saveDownloadedSdFile(name, all); appendResult("Download selesai: " + name + " (" + all.length() + " karakter)"); }); sdDownloadName=null; }
                    else { sendCommand("DOWNLOAD_SD", jsonOf("name", name, "offset", next)); }
                } else {
                    String message = cmd + " • " + (object.optBoolean("ok", false) ? "BERHASIL" : "DITOLAK/GAGAL") + ": " + object.optString("message", payload); if (object.has("content")) message += "\n" + object.optString("name", "file") + "\n" + object.optString("content", ""); if (object.has("files")) message += "\n" + object.optString("files", "");
                    main.post(() -> appendResult(message));
                }
            }
        } catch (JSONException e) { main.post(() -> appendResult("Payload JSON tidak valid pada " + topic)); }
    }

    private void renderTelemetry() {
        JSONObject t = telemetry;
        double moisture = t.optDouble("moisture_pct", -1);
        hasMoisture = moisture >= 0;
        moistureValue.setText(hasMoisture ? String.format(Locale.US, "%.1f %%", moisture) : "--.- %");
        rawValue.setText(t.has("raw_adc") ? String.valueOf(t.optInt("raw_adc", -1)) : "----");
        boolean pump = t.optBoolean("pump_on", false);
        pumpValue.setText(pump ? "ON" : "OFF"); pumpValue.setTextColor(pump ? RED : GREEN);
        durationValue.setText(formatDuration(t.optLong("pump_duration_sec", 0)));
        volumeValue.setText(String.format(Locale.US, "%.3f L", t.optDouble("pump_volume_l", 0)));
        currentMode = t.optInt("mode", currentMode);
        modeValue.setText("Mode: " + t.optString("mode_label", modeName(currentMode)));
        boolean sensorValid = t.optBoolean("sensor_valid", false);
        boolean calValid = t.optBoolean("calibration_valid", false);
        calibrationValue.setText("Sensor: " + (sensorValid ? "valid" : "periksa sensor") + " • Kalibrasi: " + (calValid ? "valid" : "belum valid"));
        calibrationValue.setTextColor(sensorValid && calValid ? GREEN : RED);
        timeValue.setText("Waktu ESP32 (WIB): " + t.optString("time", "--"));
        wifiValue.setText("Wi-Fi: " + t.optString("wifi_mode", "--") + (t.has("wifi_rssi_dbm") ? " • RSSI " + t.optInt("wifi_rssi_dbm") + " dBm" : ""));
        ipValue.setText("IP ESP32: " + t.optString("ip", "--"));
        boolean sd = t.optBoolean("sd_ready", false); sdValue.setText("microSD: " + (sd ? "OK" : "ERROR / tidak siap")); sdValue.setTextColor(sd ? GREEN : RED);
        String ts = t.optString("thingspeak_status", "--"); thingSpeakValue.setText("ThingSpeak: " + ts); thingSpeakValue.setTextColor("SUCCESS".equalsIgnoreCase(ts) ? GREEN : MUTED);
        if (currentMode == 0) { pumpOnButton.setEnabled(true); pumpOnButton.setAlpha(1f); }
        else { pumpOnButton.setEnabled(false); pumpOnButton.setAlpha(.55f); }
        modeManualButton.setText(currentMode == 0 ? "Mode Manual Aktif" : "Aktifkan Manual");
        if (modeResumeButton != null) modeResumeButton.setEnabled(currentMode == 0);
    }

    private void applyRemoteSettings(JSONObject s) {
        applyingSettings = true;
        currentMode = s.optInt("mode", currentMode);
        if (currentMode >= 0 && currentMode < MODES.length) modeSpinner.setSelection(currentMode);
        setText(lowerField, s.optString("lower", "30")); setText(upperField, s.optString("upper", "70")); setText(targetField, s.optString("target", "70")); setText(targetVolumeField, s.optString("targetVolumeLiters", "10.000"));
        setText(intervalField, s.optString("intervalValue", "6"));
        int unit = s.optInt("intervalUnit", 1); if (unit >= 0 && unit < UNITS.length) intervalUnitSpinner.setSelection(unit);
        setText(durationField, s.optString("durationMin", "5")); setText(maxRuntimeField, s.optString("maxPumpMin", "30")); setText(flowRateField, s.optString("flowRateLpm", "1.00"));
        restartEnabledCheck.setChecked(s.optBoolean("restartEnabled", false)); setText(restartHourField, s.optString("restartHour", "3")); setText(restartMinuteField, s.optString("restartMinute", "0"));
        setText(plantNameField, s.optString("plantName", "")); int plant = s.optInt("plantType", 0); if (plant >= 0 && plant < PLANTS.length) { lastPlantSelection = plant; plantSpinner.setSelection(plant); }
        thingSpeakEnabledCheck.setChecked(s.optBoolean("thingSpeakEnabled", false)); setText(thingSpeakIntervalField, s.optString("thingSpeakUploadMin", "30")); setText(sdLogIntervalField, s.optString("sdLogIntervalMin", "30"));
        setText(staSsidField, s.optString("staSsid", ""));
        staEnterpriseCheck.setChecked(s.optBoolean("staEnterpriseEnabled", false));
        int eap = s.optInt("staEapMethod", 0); if (eap >= 0 && eap < 2) staEapMethodSpinner.setSelection(eap);
        int p2 = s.optInt("staEapPhase2", 0); if (p2 >= 0 && p2 < 4) staEapPhase2Spinner.setSelection(p2);
        setText(staEapIdentityField, s.optString("staEapIdentity", ""));
        setText(staEapUsernameField, s.optString("staEapUsername", ""));
        setText(staEapDomainField, s.optString("staEapDomain", ""));
        staPasswordField.setText(""); staEapPasswordField.setText("");
        modeValue.setText("Mode tersimpan: " + modeName(currentMode));
        modeManualButton.setText(currentMode == 0 ? "Mode Manual Aktif" : "Aktifkan Manual");
        modeResumeButton.setEnabled(currentMode == 0);
        applyingSettings = false;
        appendResult("Pengaturan ESP32 dimuat dari MQTT.");
    }


    private void applyPlantPreset(int type) {
        // Presets intentionally match the v1.3.0 web dashboard; operators may edit them afterward.
        final int[][] preset = {{30,70},{55,80},{35,65},{35,65},{35,60},{35,65},{40,70},{45,70},{45,70},{45,70},{35,60},{35,60},{45,70},{50,75},{50,75},{45,70},{45,70},{45,70},{35,60},{35,65},{35,65},{50,75},{35,60},{35,60},{40,70},{40,70},{35,65},{40,65},{50,75},{50,75},{45,70},{35,60},{35,60},{40,65},{40,65},{45,70},{45,70},{40,70},{40,65},{45,70},{45,70},{45,70},{50,75},{45,70},{40,65},{45,70},{45,70},{45,70},{45,70},{45,70},{40,65},{40,65},{40,65},{30,60},{35,60},{35,60},{35,60},{30,60},{35,60},{35,60},{35,60},{30,55},{30,55},{30,55},{50,70},{50,70},{45,70},{45,70},{45,70},{45,70},{45,70},{40,65},{45,70},{40,65},{45,70},{40,65},{45,70},{45,70},{40,65},{45,70},{50,75},{45,70},{45,70},{40,65},{45,70},{40,65},{40,65},{40,65},{40,65},{40,65},{40,65},{45,70},{45,70},{35,60},{45,65},{45,70},{45,70},{50,75}};
        if (type < 0 || type >= preset.length) type = 0;
        setText(lowerField, String.valueOf(preset[type][0]));
        setText(upperField, String.valueOf(preset[type][1]));
        setText(targetField, String.valueOf(preset[type][1]));
        appendResult("Profil kelembapan referensi tanaman dipilih; periksa batas sebelum menyimpan.");
    }

    private void saveIrrigationSettings() {
        double lower = doubleFrom(lowerField, -1), upper = doubleFrom(upperField, -1), target = doubleFrom(targetField, -1), targetVolume = doubleFrom(targetVolumeField, -1);
        int interval = intFrom(intervalField, -1), unit = intervalUnitSpinner.getSelectedItemPosition();
        int duration = intFrom(durationField, -1), max = intFrom(maxRuntimeField, -1), mode = modeSpinner.getSelectedItemPosition();
        double flow = doubleFrom(flowRateField, -1);
        if (lower < 0 || upper > 100 || lower >= upper) { toast("Batas harus memenuhi 0 ≤ bawah < atas ≤ 100"); return; }
        if (target < 0 || target > 100) { toast("Target kelembapan harus 0–100%"); return; }
        if (targetVolume < 0.01 || targetVolume > 100000) { toast("Target volume harus 0,01–100000 liter"); return; }
        if (flow < 0.01 || flow > 1000 || interval < 1 || interval > 999 || duration < 1 || duration > 240 || max < 1 || max > 240) { toast("Periksa debit 0,01–1000 L/menit, interval, durasi, dan maximum runtime"); return; }
        List<JSONObject> commands = new ArrayList<>();
        commands.add(command("SET_MODE", "mode", mode));
        commands.add(command("SET_THRESHOLDS", "lower", lower, "upper", upper));
        commands.add(command("SET_TARGET", "target", target));
        commands.add(command("SET_TARGET_VOLUME", "targetVolumeLiters", targetVolume));
        commands.add(command("SET_INTERVAL", "value", interval, "unit", unit));
        commands.add(command("SET_DURATION", "durationMin", duration));
        commands.add(command("SET_MAX_RUNTIME", "maxPumpMin", max));
        commands.add(command("SET_FLOW_RATE", "flowRateLpm", flow));
        sendCommandsSequentially(commands, 350);
    }

    private JSONObject command(String cmd, Object... pairs) {
        JSONObject object = new JSONObject();
        try {
            object.put("cmd", cmd);
            for (int i = 0; i + 1 < pairs.length; i += 2) object.put(String.valueOf(pairs[i]), pairs[i + 1]);
        } catch (JSONException ignored) { }
        return object;
    }

    private void sendCommandsSequentially(List<JSONObject> commands, long gapMillis) {
        for (int i = 0; i < commands.size(); i++) {
            final JSONObject c = commands.get(i);
            main.postDelayed(() -> {
                try { String cmd = c.getString("cmd"); c.remove("cmd"); sendCommand(cmd, c); }
                catch (JSONException e) { appendResult("Gagal membentuk perintah: " + e.getMessage()); }
            }, gapMillis * i);
        }
    }

    private JSONObject jsonOf(Object... pairs) {
        JSONObject obj = new JSONObject();
        try { for (int i = 0; i + 1 < pairs.length; i += 2) obj.put(String.valueOf(pairs[i]), pairs[i + 1]); }
        catch (JSONException ignored) { }
        return obj;
    }

    private String sdFileNameSafe(String name) { return name == null ? "" : name.trim(); }
    private void startSdDownload(String name) {
        name = sdFileNameSafe(name);
        if (name.isEmpty()) { toast("Nama file kosong"); return; }
        if (!name.startsWith("irrigation_") && !name.startsWith("pump_events_") && !name.startsWith("system_")) { toast("Nama file SD tidak valid"); return; }
        sdDownloadName = name; sdDownloadBuffer.setLength(0); appendResult("Memulai download " + name + " via MQTT…");
        sendCommand("DOWNLOAD_SD", jsonOf("name", name, "offset", 0));
    }
    private void saveDownloadedSdFile(String name, String content) {
        try {
            if (android.os.Build.VERSION.SDK_INT >= 29) {
                ContentValues values = new ContentValues(); values.put(MediaStore.Downloads.DISPLAY_NAME, name); values.put(MediaStore.Downloads.MIME_TYPE, name.endsWith(".log") ? "text/plain" : "text/csv"); values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Automatic Irrigation");
                Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values); if (uri == null) throw new Exception("MediaStore gagal membuat file");
                try (OutputStream os = getContentResolver().openOutputStream(uri)) { os.write(content.getBytes(StandardCharsets.UTF_8)); }
            } else {
                File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS + "/Automatic Irrigation"); if (!dir.exists() && !dir.mkdirs()) throw new Exception("Gagal membuat folder Downloads");
                try (OutputStream os = new FileOutputStream(new File(dir, name))) { os.write(content.getBytes(StandardCharsets.UTF_8)); }
            }
            toast("File tersimpan di Downloads/Automatic Irrigation: " + name);
        } catch (Exception e) { toast("Gagal menyimpan file: " + e.getMessage()); }
    }

    private void sendCommand(String cmd, JSONObject fields) {
        if (!connected || mqtt == null || !mqtt.isConnected()) { toast("Belum terhubung ke MQTT broker"); return; }
        if (commandToken == null || commandToken.length() < 16) { toast("Isi token kontrol pada pengaturan MQTT"); return; }
        try {
            JSONObject payload = new JSONObject(fields.toString()); payload.put("cmd", cmd); payload.put("key", commandToken);
            MqttMessage msg = new MqttMessage(payload.toString().getBytes(StandardCharsets.UTF_8)); msg.setQos(1); msg.setRetained(false);
            mqtt.publish(rootTopic + "/command", msg, null, new IMqttActionListener() {
                @Override public void onSuccess(org.eclipse.paho.client.mqttv3.IMqttToken token) { main.post(() -> appendResult("Perintah " + cmd + " dikirim; menunggu hasil ESP32.")); }
                @Override public void onFailure(org.eclipse.paho.client.mqttv3.IMqttToken token, Throwable error) { main.post(() -> appendResult("Gagal mengirim " + cmd + ": " + (error == null ? "unknown" : error.getMessage()))); }
            });
        } catch (Exception e) { appendResult("Kesalahan publish: " + e.getMessage()); }
    }

    private void setConnectionUi(boolean on, String message) {
        connected = on;
        connectionState.setText((on ? "● TERHUBUNG • " : "● TIDAK TERHUBUNG • ") + message);
        connectionState.setTextColor(on ? Color.rgb(143, 238, 192) : Color.rgb(255, 220, 150));
        connectButton.setText(on ? "Tersambung" : "Simpan & Hubungkan");
        availabilityValue.setText("ESP32 availability: " + (on ? "menunggu status" : "broker offline"));
        appendResult(message);
    }

    private void appendResult(String message) {
        if (resultValue != null) resultValue.setText("Hasil/peristiwa terakhir: " + message);
    }

    private void clearSecrets() {
        try { secureStore.put("password", ""); secureStore.put("token", ""); secureStore.put("ca", ""); }
        catch (Exception e) { toast("Gagal menghapus kredensial: " + e.getMessage()); return; }
        passwordField.setText(""); tokenField.setText(""); caField.setText(""); toast("Password, token, dan CA tersimpan sudah dihapus.");
    }

    private int intFrom(EditText e, int fallback) {
        try { return Integer.parseInt(e.getText().toString().trim()); } catch (Exception ex) { return fallback; }
    }
    private double doubleFrom(EditText e, double fallback) {
        try { return Double.parseDouble(e.getText().toString().trim().replace(',', '.')); } catch (Exception ex) { return fallback; }
    }
    private void setText(EditText e, String value) { if (e != null) e.setText(value); }
    private String modeName(int code) { return code >= 0 && code < MODES.length ? MODES[code] : "--"; }
    private String formatDuration(long seconds) {
        if (seconds < 0) seconds = 0;
        long h = seconds / 3600, m = (seconds % 3600) / 60, s = seconds % 60;
        return String.format(Locale.US, "%02d:%02d:%02d", h, m, s);
    }
    private void toast(String message) { Toast.makeText(this, message, Toast.LENGTH_LONG).show(); }

    @Override protected void onDestroy() {
        super.onDestroy();
        // Intentionally disconnect on app exit so stale sessions do not occupy broker client slots.
        MqttAsyncClient c = mqtt;
        if (c != null) io.execute(() -> {
            try { if (c.isConnected()) c.disconnect(500).waitForCompletion(800); } catch (Exception ignored) { }
            try { c.close(true); } catch (Exception ignored) { }
        });
        io.shutdown();
    }
}
