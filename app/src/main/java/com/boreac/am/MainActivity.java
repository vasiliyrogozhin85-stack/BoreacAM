package com.boreac.am;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.boreac.am.modem.AudioReceiver;
import com.boreac.am.modem.AudioTransmitter;
import com.boreac.am.modem.ProtocolException;
import com.boreac.am.modem.QamCodec;
import com.boreac.am.modem.ReceiverDecoder;
import com.boreac.am.ui.WaveformView;
import com.boreac.am.util.IoUtils;
import com.boreac.am.util.PcmFile;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public final class MainActivity extends Activity {
    private static final int REQUEST_OPEN_SEND = 1001;
    private static final int REQUEST_CREATE_RECEIVE = 1002;
    private static final int REQUEST_RECORD_AUDIO = 1003;

    private LinearLayout sendCard;
    private LinearLayout receiveCard;
    private TextView sendTitle;
    private TextView sendHint;
    private TextView receiveTitle;
    private TextView receiveHint;
    private TextView statusText;
    private TextView detailText;
    private ProgressBar progressBar;
    private WaveformView waveform;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final AtomicBoolean sendCancelled = new AtomicBoolean(false);

    private boolean sending;
    private boolean decoding;
    private AudioReceiver receiver;
    private Uri receiveOutputUri;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(0xff050a0f);
        getWindow().setNavigationBarColor(0xff050a0f);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_main);

        bindViews();

        if (!QamCodec.selfTest()) {
            setStatus("Ошибка самотеста QAM", "Кодек не прошёл цифровой loopback.", true);
            setCardsEnabled(false);
        } else {
            setStatus(getString(R.string.ready), getString(R.string.protocol), false);
        }

        sendCard.setOnClickListener(v -> {
            if (sending) {
                sendCancelled.set(true);
                setStatus("Остановка передачи…", "Ожидайте завершения AudioTrack.", false);
            } else if (!decoding && (receiver == null || !receiver.isRunning())) {
                chooseSendFile();
            }
        });

        receiveCard.setOnClickListener(v -> {
            if (receiver != null && receiver.isRunning()) {
                stopReceiveAndDecode();
            } else if (!sending && !decoding) {
                chooseReceiveDestination();
            }
        });
    }

    private void bindViews() {
        sendCard = findViewById(R.id.sendCard);
        receiveCard = findViewById(R.id.receiveCard);
        sendTitle = findViewById(R.id.sendTitle);
        sendHint = findViewById(R.id.sendHint);
        receiveTitle = findViewById(R.id.receiveTitle);
        receiveHint = findViewById(R.id.receiveHint);
        statusText = findViewById(R.id.statusText);
        detailText = findViewById(R.id.detailText);
        progressBar = findViewById(R.id.progressBar);
        waveform = findViewById(R.id.waveform);
    }

    private void chooseSendFile() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        startActivityForResult(intent, REQUEST_OPEN_SEND);
    }

    private void chooseReceiveDestination() {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/octet-stream");
        intent.putExtra(Intent.EXTRA_TITLE, "BoreacAM_received.bin");
        startActivityForResult(intent, REQUEST_CREATE_RECEIVE);
    }

    @Override
    protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (result != RESULT_OK || data == null || data.getData() == null) return;

        if (request == REQUEST_OPEN_SEND) {
            startSend(data.getData());
        } else if (request == REQUEST_CREATE_RECEIVE) {
            receiveOutputUri = data.getData();
            ensureMicrophoneAndStart();
        }
    }

    private void startSend(Uri uri) {
        sending = true;
        sendCancelled.set(false);
        setCardsEnabled(false);
        sendCard.setEnabled(true);
        sendTitle.setText(R.string.stop_send);
        sendHint.setText("Нажмите ещё раз для отмены");
        waveform.setActive(true);
        progressBar.setProgress(0);

        String displayName = IoUtils.displayName(getContentResolver(), uri);
        setStatus("Подготовка файла…", displayName, false);

        executor.execute(() -> {
            File cached = null;
            try {
                cached = IoUtils.copyUriToCache(
                        this,
                        uri,
                        "send_" + System.currentTimeMillis() + "_" + IoUtils.safeFileName(displayName)
                );

                File finalCached = cached;
                runOnUiThread(() -> setStatus(
                        "Передача через динамик…",
                        displayName + " • " + humanSize(finalCached.length()),
                        false
                ));

                AudioTransmitter.send(cached, sendCancelled, (progress, detail) ->
                        runOnUiThread(() -> {
                            progressBar.setProgress((int) Math.round(progress * 1000));
                            setStatus("Передача через динамик…", detail, false);
                        })
                );

                runOnUiThread(() -> {
                    setStatus("Передача завершена", displayName, false);
                    Toast.makeText(this, "Файл передан.", Toast.LENGTH_SHORT).show();
                });
            } catch (Exception e) {
                runOnUiThread(() -> setStatus(
                        sendCancelled.get() ? "Передача отменена" : "Ошибка передачи",
                        safeMessage(e),
                        true
                ));
            } finally {
                if (cached != null) cached.delete();
                runOnUiThread(this::finishSendUi);
            }
        });
    }

    private void finishSendUi() {
        sending = false;
        sendCancelled.set(false);
        sendTitle.setText(R.string.send_file);
        sendHint.setText(R.string.send_file_hint);
        waveform.setActive(false);
        setCardsEnabled(true);
    }

    private void ensureMicrophoneAndStart() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            startReceive();
        } else {
            requestPermissions(new String[] {Manifest.permission.RECORD_AUDIO}, REQUEST_RECORD_AUDIO);
        }
    }

    @Override
    public void onRequestPermissionsResult(int request, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(request, permissions, results);
        if (request != REQUEST_RECORD_AUDIO) return;

        if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) {
            startReceive();
        } else {
            receiveOutputUri = null;
            setStatus("Нет доступа к микрофону", getString(R.string.record_permission_needed), true);
            if (!shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)) {
                Toast.makeText(this, "Разрешение можно включить в настройках приложения.", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void startReceive() {
        try {
            File dir = new File(getCacheDir(), "boreacam");
            if (!dir.exists() && !dir.mkdirs()) throw new IllegalStateException("Не создана временная папка.");
            File raw = new File(dir, "rx_" + System.currentTimeMillis() + ".pcm");

            receiver = new AudioReceiver(this, raw);
            receiver.start(new AudioReceiver.Listener() {
                @Override
                public void onLevel(double level) {
                    runOnUiThread(() -> waveform.setLevel(level));
                }

                @Override
                public void onError(Exception error) {
                    runOnUiThread(() -> {
                        setStatus("Ошибка микрофона", safeMessage(error), true);
                        resetReceiveUi();
                    });
                }
            });

            progressBar.setProgress(0);
            receiveTitle.setText(R.string.stop_receive);
            receiveHint.setText("После окончания звука нажмите эту кнопку");
            waveform.setActive(true);
            setCardsEnabled(false);
            receiveCard.setEnabled(true);
            setStatus("Приём запущен", "Ожидаю акустическую передачу AudioModem 3.2.3…", false);
        } catch (Exception e) {
            setStatus("Не удалось начать приём", safeMessage(e), true);
            resetReceiveUi();
        }
    }

    private void stopReceiveAndDecode() {
        if (receiver == null || !receiver.isRunning()) return;

        setStatus("Остановка записи…", "Подготовка к декодированию.", false);
        receiveCard.setEnabled(false);

        executor.execute(() -> {
            File raw;
            try {
                raw = receiver.stop();
            } catch (Exception e) {
                runOnUiThread(() -> {
                    setStatus("Ошибка записи", safeMessage(e), true);
                    resetReceiveUi();
                });
                return;
            }

            decoding = true;
            runOnUiThread(() -> {
                setStatus("Декодирование…", "Ищу кадры, проверяю CRC.", false);
                progressBar.setProgress(0);
            });

            File decoded = new File(raw.getParentFile(), "decoded_" + System.currentTimeMillis() + ".bin");
            try {
                short[] samples = PcmFile.readAll(raw);
                ReceiverDecoder.Result result = ReceiverDecoder.decode(
                        samples,
                        decoded,
                        (progress, detail) -> runOnUiThread(() -> {
                            progressBar.setProgress((int) Math.round(progress * 1000));
                            setStatus("Декодирование…", detail, false);
                        })
                );

                IoUtils.copyFileToUri(this, decoded, receiveOutputUri);

                runOnUiThread(() -> {
                    setStatus(
                            "Файл получен",
                            humanSize(result.fileSize) + " • CRC "
                                    + String.format("%08X", result.fileCrc),
                            false
                    );
                    Toast.makeText(this, "Файл сохранён.", Toast.LENGTH_LONG).show();
                });
            } catch (Exception e) {
                runOnUiThread(() -> setStatus("Ошибка декодирования", safeMessage(e), true));
            } finally {
                raw.delete();
                decoded.delete();
                decoding = false;
                receiveOutputUri = null;
                runOnUiThread(this::resetReceiveUi);
            }
        });
    }

    private void resetReceiveUi() {
        receiveTitle.setText(R.string.receive_file);
        receiveHint.setText(R.string.receive_file_hint);
        waveform.setActive(false);
        waveform.setLevel(0.18);
        receiver = null;
        setCardsEnabled(!sending && !decoding);
    }

    private void setCardsEnabled(boolean enabled) {
        sendCard.setEnabled(enabled);
        receiveCard.setEnabled(enabled);
        sendCard.setAlpha(enabled ? 1f : 0.48f);
        receiveCard.setAlpha(enabled ? 1f : 0.48f);
    }

    private void setStatus(String title, String detail, boolean error) {
        statusText.setText(title);
        statusText.setTextColor(error ? 0xffff6b6b : 0xff18e0c1);
        detailText.setText(detail == null ? "" : detail);
    }

    private static String safeMessage(Throwable error) {
        String message = error.getMessage();
        return (message == null || message.isBlank()) ? error.getClass().getSimpleName() : message;
    }

    private static String humanSize(long size) {
        if (size < 1024) return size + " Б";
        double value = size / 1024.0;
        if (value < 1024) return String.format("%.1f КБ", value);
        value /= 1024.0;
        if (value < 1024) return String.format("%.1f МБ", value);
        return String.format("%.2f ГБ", value / 1024.0);
    }

    @Override
    protected void onDestroy() {
        sendCancelled.set(true);
        if (receiver != null && receiver.isRunning()) {
            try {
                receiver.stop();
            } catch (Exception ignored) {
            }
        }
        executor.shutdownNow();
        super.onDestroy();
    }
}
