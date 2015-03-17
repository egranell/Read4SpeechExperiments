package com.aemus.Read4SpeechExperiments;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.media.MediaRecorder.AudioSource;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.StatFs;
import android.preference.PreferenceManager;
import android.provider.Settings.Secure;
import android.speech.tts.TextToSpeech;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v7.app.ActionBarActivity;
import android.text.Html;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.Locale;

public class MainActivity extends ActionBarActivity {
    private static final int RESULT_SETTINGS = 0;
    private static String tag = "Read4SpeechExperiments";
    private static ViewPager mViewPager;
    private static ArrayList<String> sentences;
    private static ArrayList<String> fileNames;

    private static int recorderSampleRate = 16000;
    private static int recorderChannels = AudioFormat.CHANNEL_IN_MONO;
    private static int recorderEncoding = AudioFormat.ENCODING_PCM_16BIT;
    private static int recorderMinBufferSize = AudioRecord.getMinBufferSize(recorderSampleRate, recorderChannels, recorderEncoding);
    private static AudioRecord recorder;
    private static MediaPlayer player;
    private static TextToSpeech tts;

    private static Boolean record = false;
    private static File rootDir;
    private static File rootDir2; // Borrar
    private static Context context;
    AlertDialog.Builder cleanDialog;
    AlertDialog.Builder thanksDialog;
    AlertDialog.Builder sendDialog;
    AlertDialog.Builder instructionsDialog;
    AlertDialog.Builder notSpaceDialog;
    SectionsPagerAdapter mSectionsPagerAdapter;
    private String corpusName = "SmartWays";
    private String ID;

    protected static float getPercentDone() {
        float percent = 0;
        for (int i = 0; i < sentences.size(); i++) {
            if (new File(rootDir + "/" + fileNames.get(i) + ".raw").exists()) {
                percent = percent + 1 / (float) sentences.size();
            }
        }
        return (float) (Math.round(percent * Math.pow(10, 3)) / Math.pow(10, 3)) * 100;
    }


    @SuppressLint("NewApi")
    protected static long getAvailableSpaceInKB() {
        final long SIZE_KB = 1024L;
        StatFs stat = new StatFs(Environment.getExternalStorageDirectory().getPath());
        long availableSpace = stat.getAvailableBlocksLong() * stat.getBlockSizeLong();
        return availableSpace / SIZE_KB;
    }

    public static void startRecording(final int position) {
        Thread streamThread = new Thread(new Runnable() {
            @Override
            public void run() {
                int sizeAudio = 0;
                RandomAccessFile cab;
                byte[] buffer = new byte[recorderMinBufferSize];
                BufferedOutputStream bos;
                try {
                    bos = new BufferedOutputStream(new BufferedOutputStream(new FileOutputStream(rootDir + "/" + fileNames.get(position - 1) + ".raw")));
                    while (record) {
                        int recorderBufferSize = recorder.read(buffer, 0, buffer.length);
                        if (recorderBufferSize < 0)
                            break;
                        sizeAudio += buffer.length;
                        try {
                            bos.write(buffer, 0, recorderBufferSize);
                            bos.flush();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                    try {
                        bos.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                    // Write WAV header
                    cab = new RandomAccessFile(rootDir + "/" + fileNames.get(position - 1) + ".cab", "rw");
                    cab.setLength(0);
                    cab.writeBytes("RIFF");
                    cab.writeInt(Integer.reverseBytes(36 + sizeAudio));
                    cab.writeBytes("WAVE");
                    cab.writeBytes("fmt ");
                    cab.writeInt(Integer.reverseBytes(16));
                    cab.writeShort(Short.reverseBytes((short) 1));
                    cab.writeShort(Short.reverseBytes((short) 1));
                    cab.writeInt(Integer.reverseBytes(recorderSampleRate));
                    cab.writeInt(Integer.reverseBytes(recorderSampleRate * 16 / 8));
                    cab.writeShort(Short.reverseBytes((short) (16 / 8)));
                    cab.writeShort(Short.reverseBytes((short) 16));
                    cab.writeBytes("data");
                    cab.writeInt(Integer.reverseBytes(sizeAudio));
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });
        streamThread.start();
    }

    @SuppressLint("NewApi")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        context = getApplicationContext();
        tts = new TextToSpeech(context, new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
            }
        });
        tts.setLanguage(new Locale("es", "ES"));

        init();
        mSectionsPagerAdapter = new SectionsPagerAdapter(getSupportFragmentManager());
        mViewPager = (ViewPager) findViewById(R.id.pager);
        mViewPager.setAdapter(mSectionsPagerAdapter);

        sendDialog = new AlertDialog.Builder(this);
        sendDialog.setTitle("Send records by email.");
        sendDialog.setIcon(R.drawable.ic_launcher);
        sendDialog.setMessage("Sentence records are not completed.\nAre you sure you want to send it anyway?");
        sendDialog.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
            }
        });

        sendDialog.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                sendData(true);
            }
        });

        cleanDialog = new AlertDialog.Builder(this);
        cleanDialog.setTitle("Clean records");
        cleanDialog.setIcon(R.drawable.ic_launcher);
        cleanDialog.setMessage("Do you want to clean the records?");
        cleanDialog.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (rootDir.exists()) {
                    for (File f : rootDir.listFiles()) {
                        if (!f.delete()) {
                            Log.e(tag, "Cannot delete file: " + f);
                        }
                    }
                    if (!rootDir.delete()) {
                        Log.e(tag, "Cannot delete file: " + rootDir);
                    }
                }
                thanksDialog.show();
            }
        });
        cleanDialog.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
            }
        });

        thanksDialog = new AlertDialog.Builder(this);
        thanksDialog.setTitle("All files deleted.");
        thanksDialog.setIcon(R.drawable.ic_launcher);
        thanksDialog.setMessage("Thanks for your collaboration.");
        thanksDialog.setNegativeButton("New record", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                init();
                mSectionsPagerAdapter.notifyDataSetChanged();
                mViewPager.setCurrentItem(0);
                ((PlaceholderFragment) mViewPager.getAdapter().instantiateItem(mViewPager, 0)).updateContent();
                ((PlaceholderFragment) mViewPager.getAdapter().instantiateItem(mViewPager, 1)).updateContent();
            }
        });

        thanksDialog.setPositiveButton("Close", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                finish();
            }
        });
        thanksDialog.setOnKeyListener(new DialogInterface.OnKeyListener() {
            @Override
            public boolean onKey(DialogInterface dialog, int keyCode, KeyEvent event) {
                if (keyCode == KeyEvent.KEYCODE_BACK) {
                    dialog.dismiss();
                    finish();
                }
                return true;
            }
        });

        notSpaceDialog = new AlertDialog.Builder(this);
        notSpaceDialog.setTitle("Not enough free space.");
        notSpaceDialog.setIcon(R.drawable.ic_launcher);
        notSpaceDialog.setPositiveButton("Close", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                finish();
            }
        });
        notSpaceDialog.setOnKeyListener(new DialogInterface.OnKeyListener() {
            @Override
            public boolean onKey(DialogInterface dialog, int keyCode, KeyEvent event) {
                if (keyCode == KeyEvent.KEYCODE_BACK) {
                    dialog.dismiss();
                    finish();
                }
                return true;
            }
        });

        SharedPreferences settings = getSharedPreferences("Preferences", 0);
        boolean show = settings.getBoolean("show", true);
        showInstructions(show);
    }

    @SuppressLint("NewApi")
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.clear:
                cleanRecords();
                return true;
            case R.id.send:
                sendData(false);
                return true;
            case R.id.settings:
                Intent i = new Intent(this, settings.class);
                startActivityForResult(i, RESULT_SETTINGS);
                return true;
            case R.id.instructions:
                showInstructions(true);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    void init() {
        SharedPreferences sharedPrefs = PreferenceManager
                .getDefaultSharedPreferences(this);
        if (!new File(sharedPrefs.getString("mandatoryFile", "NULL")).exists()) {
            Toast.makeText(getApplicationContext(), "To use this software it is necessary to define at least the mandatory sentences file to read.\nIt will be load 10 demo sentences.", Toast.LENGTH_LONG).show();
        }

        rootDir = new File(Environment.getExternalStorageDirectory().getPath() + "/Read4SpeechExperiments/" +
                sharedPrefs.getString("audioPath", "NULL") + "/" +
                (sharedPrefs.getBoolean("speaker", false) ? "Driver" : "Passenger") + "_" +
                (sharedPrefs.getBoolean("mic", false) ? "Mic" : "Phone") + "_" +
                (sharedPrefs.getBoolean("engine", false) ? "On" : "Off") + "_" +
                (sharedPrefs.getBoolean("position", false) ? "Up" : "Down"));
        ID = Secure.getString(getApplicationContext().getContentResolver(), Secure.ANDROID_ID);
        loadLines(sharedPrefs.getString("mandatoryFile", ""), sharedPrefs.getString("optionalFile", ""), Integer.valueOf(sharedPrefs.getString("OptionalFileNumber", "0")));
        //Check if there is enough space on the SD (about 300KB per phrase)
        Long spaceAvailable = getAvailableSpaceInKB();
        Long spaceNecessary = (long) sentences.size() * 300;
        if (spaceAvailable < spaceNecessary) {
            notSpaceDialog.setMessage("There is not enough free space to record all sentences.\nPlease free at least " + (spaceNecessary / 1024 + 1) + " MB before using this application.");
            notSpaceDialog.show();
        }
        if (!rootDir.exists()) {
            if (!rootDir.mkdirs()) {
                Log.e(tag, "Cannot create directory: " + rootDir);
            }
        }
        if (!new File(rootDir + "/" + corpusName + "_transcripts_" + ID + ".txt").exists())
            writeToFile(fileNames, sentences);

        // Borrar esto
        rootDir2 = new File(Environment.getExternalStorageDirectory().getPath() + "/Read4SpeechExperiments/" +
                sharedPrefs.getString("audioPath", "NULL") + "/" +
                (!sharedPrefs.getBoolean("speaker", false) ? "Driver" : "Passenger") + "_" +
                (sharedPrefs.getBoolean("mic", false) ? "Mic" : "Phone") + "_" +
                (sharedPrefs.getBoolean("engine", false) ? "On" : "Off") + "_" +
                (sharedPrefs.getBoolean("position", false) ? "Up" : "Down"));

        loadLines(sharedPrefs.getString("mandatoryFile", ""), sharedPrefs.getString("optionalFile", ""), Integer.valueOf(sharedPrefs.getString("OptionalFileNumber", "0")));
        //Check if there is enough space on the SD (about 300KB per phrase)

        if (!rootDir2.exists()) {
            if (!rootDir2.mkdirs()) {
                Log.e(tag, "Cannot create directory: " + rootDir2);
            }
        }
        if (!new File(rootDir2 + "/" + corpusName + "_transcripts_" + ID + ".txt").exists())
            writeToFile(fileNames, sentences);
        // Borrar esto
    }

    protected void loadLines(String mandatoryFile, String optionalFile, int optionalSentences) {
        sentences = new ArrayList<String>();
        fileNames = new ArrayList<String>();
        File file = new File(rootDir + "/" + corpusName + "_transcripts_" + ID + ".txt");
        if (!file.exists()) {
            try {
                BufferedReader br;
                if (!mandatoryFile.matches("/") && new File(mandatoryFile).exists())
                    br = new BufferedReader(new FileReader(mandatoryFile));
                else
                    br = new BufferedReader(new InputStreamReader(getResources().openRawResource(R.raw.adaptation)));
                try {
                    String line;

                    int l = 0;
                    while ((line = br.readLine()) != null) {
                        l++;
                        sentences.add(line);
                        fileNames.add(corpusName + "_adap_" + String.format("%02d", l) + "_" + Secure.getString(getApplicationContext().getContentResolver(), Secure.ANDROID_ID));
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }

                try {
                    br = new BufferedReader(new FileReader(optionalFile));
                } catch (FileNotFoundException e1) {
                    e1.printStackTrace();
                }

                ArrayList<Integer> numbers = new ArrayList<Integer>();
                while (numbers.size() < optionalSentences) {
                    int r = (int) Math.floor(1 + Math.random() * 100);
                    if (!numbers.contains(r)) {
                        numbers.add(r);
                    }
                }
                try {
                    String line;
                    int l = 0;
                    while ((line = br.readLine()) != null) {
                        l++;
                        if (numbers.contains(l)) {
                            sentences.add(line);
                            fileNames.add(corpusName + "_test_" + String.format("%02d", l) + "_" + Secure.getString(getApplicationContext().getContentResolver(), Secure.ANDROID_ID));
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            } catch (FileNotFoundException e1) {
                e1.printStackTrace();
            }
        } else {
            try {
                InputStream fis = new FileInputStream(file);
                BufferedReader br = new BufferedReader(new InputStreamReader(fis));
                try {
                    String line;
                    while ((line = br.readLine()) != null) {
                        fileNames.add(line.split(" ")[0]);
                        sentences.add(line.substring(line.indexOf(" ")));
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            } catch (FileNotFoundException e1) {
                e1.printStackTrace();
            }
        }
    }

    private void writeToFile(ArrayList<String> fileName, ArrayList<String> line) {
        try {
            File file = new File(rootDir + "/" + corpusName + "_transcripts_" + ID + ".txt");
            FileOutputStream fos = new FileOutputStream(file);
            PrintStream ps = new PrintStream(fos);
            // if file doesn't exists, then create it
            if (!file.exists()) {
                if (!file.createNewFile()) {
                    Log.e(tag, "Cannot create file: " + file);
                }
            }
            for (int i = 0; i < line.size(); i++) {
                ps.println(fileName.get(i) + " " + line.get(i));
            }
            ps.close();

        } catch (IOException e) {
            Log.e(tag, "File write failed: " + e.toString());
        }
    }

    @SuppressLint({"NewApi", "InflateParams"})
    void showInstructions(Boolean show) {
        SharedPreferences settings = getSharedPreferences("Preferences", 0);
        View checkboxView = getLayoutInflater().inflate(R.layout.checkbox, null);
        CheckBox checkBox = (CheckBox) checkboxView.findViewById(R.id.checkbox);
        checkBox.setChecked(settings.getBoolean("show", true));
        checkBox.setOnCheckedChangeListener(new OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                SharedPreferences settings = getSharedPreferences("Preferences", 0);
                SharedPreferences.Editor editor = settings.edit();
                editor.putBoolean("show", isChecked);
                editor.commit();
            }
        });

        instructionsDialog = new AlertDialog.Builder(this, AlertDialog.THEME_HOLO_LIGHT);
        instructionsDialog.setTitle("Instructions.");
        instructionsDialog.setIcon(R.drawable.ic_launcher);
        instructionsDialog.setMessage(Html.fromHtml(getString(R.string.instructions)));
        instructionsDialog.setView(checkboxView);

        if (show) {
            instructionsDialog.show();
        }
    }

    @SuppressLint("ShowToast")
    void cleanRecords() {
        if (getPercentDone() > 0) {
            cleanDialog.show();
        } else {
            Toast.makeText(getApplicationContext(), "There are not records to delete.", Toast.LENGTH_LONG).show();
        }
    }

    @SuppressLint("ShowToast")
    void sendData(Boolean confirmation) {
        float done = getPercentDone();
        if (done == 0) {
            Toast.makeText(getApplicationContext(), "There are not records to send.", Toast.LENGTH_LONG).show();
        } else {
            if (done < 100 && !confirmation) {
                sendDialog.show();
            } else {
                Intent itSend = new Intent();
                itSend.setType("*/*");
                itSend.setAction(android.content.Intent.ACTION_SEND_MULTIPLE);
                ArrayList<Uri> uris = new ArrayList<Uri>();
                if (new File(rootDir + "/" + corpusName + "_transcripts_" + ID + ".txt").exists()) {
                    uris.add(Uri.fromFile(new File(rootDir + "/" + corpusName + "_transcripts_" + ID + ".txt")));
                }
                for (int i = 0; i < sentences.size(); i++) {
                    if (new File(rootDir + "/" + fileNames.get(i) + ".raw").exists()) {
                        uris.add(Uri.fromFile(new File(rootDir + "/" + fileNames.get(i) + ".raw")));
                    }
                }
                itSend.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);
                startActivity(itSend);
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        switch (requestCode) {
            case RESULT_SETTINGS:
                SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);

                // Check for text files, and if there is not a mandatory file load the demo sentences.

                File mandatoryFile = new File(sharedPrefs.getString("mandatoryFile", ""));
                if (!mandatoryFile.exists() && mandatoryFile.getAbsolutePath().matches("/")) {
                    Toast.makeText(getApplicationContext(), "The mandatory sentences file: " + mandatoryFile.getAbsolutePath() + " doesn't exist.", Toast.LENGTH_LONG).show();
                    Intent i = new Intent(this, settings.class);
                    startActivityForResult(i, RESULT_SETTINGS);
                }

                File optionalFile = new File(sharedPrefs.getString("optionalFile", ""));
                if (!optionalFile.exists() && optionalFile.getAbsolutePath().matches("/")) {
                    Toast.makeText(getApplicationContext(), "The optional sentences file: " + optionalFile.getAbsolutePath() + " doesn't exist.", Toast.LENGTH_LONG).show();
                }
                File file = new File(rootDir + "/" + corpusName + "_transcripts_" + ID + ".txt");
                if (file.exists()) {
                    if (!file.delete()) {
                        Log.e(tag, "Cannot delete file: " + file);
                    }
                }

                init();

                mSectionsPagerAdapter.notifyDataSetChanged();
                int index = mViewPager.getCurrentItem();

                if (index > 0)
                    ((PlaceholderFragment) mViewPager.getAdapter().instantiateItem(mViewPager, index - 1)).updateContent();
                ((PlaceholderFragment) mViewPager.getAdapter().instantiateItem(mViewPager, index)).updateContent();
                if (index < sentences.size() - 1)
                    ((PlaceholderFragment) mViewPager.getAdapter().instantiateItem(mViewPager, index + 1)).updateContent();
                break;
        }
    }

    public static class PlaceholderFragment extends Fragment {
        private static final String ARG_SENTENCE_NUMBER = "sentence_number";
        private static final String ARG_SENTENCE = "sentence";
        private static final String ARG_RECORDING = "recording";
        private static final String ARG_HANDFREE_RECORDING = "handFreeRecording";
        private static final String ARG_PLAYING = "playing";
        public final Handler handler;
        TextView sentenceNumber;
        TextView progress;
        TextView sentence;
        ImageButton recButton;
        ImageButton playButton;
        ProgressBar progressBar;

        public PlaceholderFragment() {
            handler = new Handler() {

                // Create handleMessage function

                public void handleMessage(Message msg) {

                    String aResponse = msg.getData().getString("message");
                    int s = msg.getData().getInt("sentence");
                    int m = msg.getData().getInt("move");

                    if ((null != aResponse)) {
                        if (aResponse.matches("start recording") || aResponse.matches("stop recording"))
                            recButton.performClick();
                        else if (aResponse.matches("next sentence") || aResponse.matches("finish")) {
                            getArguments().putInt(ARG_SENTENCE_NUMBER, s + 1);
                            getArguments().putString(ARG_SENTENCE, sentences.get(s));
                            updateContent();
                            if (m != s) {
                                mViewPager.setCurrentItem(m);
                                mViewPager.getAdapter().startUpdate(mViewPager);
                                mViewPager.getAdapter().finishUpdate(mViewPager);
                            }
                        }
                    }
                }
            };
        }

        public static PlaceholderFragment newInstance(int sectionNumber) {
            PlaceholderFragment fragment = new PlaceholderFragment();
            Bundle args = new Bundle();
            args.putInt(ARG_SENTENCE_NUMBER, sectionNumber);
            args.putString(ARG_SENTENCE, sentences.get(sectionNumber - 1));
            args.putBoolean(ARG_RECORDING, false);
            args.putBoolean(ARG_HANDFREE_RECORDING, false);
            args.putBoolean(ARG_PLAYING, false);
            fragment.setArguments(args);
            return fragment;
        }

        @SuppressLint({"NewApi", "InlinedApi"})
        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
            View rootView = inflater.inflate(R.layout.fragment_main, container, false);
            sentenceNumber = (TextView) rootView.findViewById(R.id.sentenceNumber);
            sentence = (TextView) rootView.findViewById(R.id.sentence);
            recButton = (ImageButton) rootView.findViewById(R.id.rec_button);
            playButton = (ImageButton) rootView.findViewById(R.id.play_button);
            progress = (TextView) rootView.findViewById(R.id.progress);
            progressBar = (ProgressBar) rootView.findViewById(R.id.progressBar1);
            progressBar.setMax(100);
            progressBar.setProgressDrawable(getResources().getDrawable(R.drawable.progressbar));
            progressBar.setProgress((int) getPercentDone());
            progress.setText(String.format("%.2f", getPercentDone()) + "%");

            sentenceNumber.setText(getResources().getString(R.string.sentence) + " " + Integer.toString(getArguments().getInt(ARG_SENTENCE_NUMBER)) + "/" + sentences.size());
            sentence.setText(sentences.get(getArguments().getInt(ARG_SENTENCE_NUMBER) - 1));
            sentence.setOnClickListener(new OnClickListener() {
                public void onClick(View arg0) {
                    getArguments().putBoolean(ARG_HANDFREE_RECORDING, false);
                    if (!PreferenceManager.getDefaultSharedPreferences(getActivity()).getBoolean("handsFree", false)) {
                        if (getArguments().getBoolean(ARG_RECORDING)) {
                            recButton.performClick();
                        }

                    }
                    if (getArguments().getBoolean(ARG_PLAYING)) {
                        playButton.performClick();
                    }
                }
            });
            final AlertDialog.Builder recDialog = new AlertDialog.Builder(getActivity());

            recDialog.setTitle("Record new sentence.");
            recDialog.setIcon(R.drawable.ic_launcher);
            recDialog.setMessage("A sentece's record is present.\nAre you sure you want to record it anyway?");
            recDialog.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                }
            });

            recDialog.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    record = true;
                    playButton.setEnabled(false);
                    getArguments().putBoolean(ARG_RECORDING, true);
                    sentence.setBackgroundResource(R.color.recording);
                    recButton.setBackgroundResource(R.drawable.record_stop);
                    recorder = new AudioRecord(AudioSource.MIC, recorderSampleRate, recorderChannels, recorderEncoding, recorderMinBufferSize * 10);
                    recorder.startRecording();
                    startRecording(getArguments().getInt(ARG_SENTENCE_NUMBER));
                }
            });

            recButton.setOnClickListener(new OnClickListener() {
                @SuppressLint("ResourceAsColor")
                public void onClick(View arg0) {
                    if (getArguments().getBoolean(ARG_RECORDING)) {
                        if (recorder != null) {
                            if (recorder.getState() == AudioRecord.STATE_INITIALIZED) {
                                recorder.stop();
                                recorder.release();
                            }
                        }
                        if (new File(rootDir + "/" + fileNames.get(getArguments().getInt(ARG_SENTENCE_NUMBER) - 1) + ".raw").exists()) {
                            sentence.setBackgroundResource(R.color.recorded);
                            playButton.setEnabled(true);
                        } else {
                            sentence.setBackgroundResource(R.color.notRecorded);
                            playButton.setEnabled(false);
                        }
                        getArguments().putBoolean(ARG_RECORDING, false);
                        recButton.setBackgroundResource(R.drawable.record);
                        record = false;

                        int index = mViewPager.getCurrentItem();
                        if (index > 1)
                            ((PlaceholderFragment) mViewPager.getAdapter().instantiateItem(mViewPager, index - 1)).updateContent();
                        if ((mViewPager.getAdapter().instantiateItem(mViewPager, index)) != null)
                            ((PlaceholderFragment) mViewPager.getAdapter().instantiateItem(mViewPager, index)).updateContent();
                        if (index < sentences.size() - 1)
                            ((PlaceholderFragment) mViewPager.getAdapter().instantiateItem(mViewPager, index + 1)).updateContent();

                    } else {
                        if (PreferenceManager.getDefaultSharedPreferences(getActivity()).getBoolean("handsFree", false)
                                && !getArguments().getBoolean(ARG_HANDFREE_RECORDING)) {
                            getArguments().putBoolean(ARG_HANDFREE_RECORDING, true);
                            handsFreeMode();
                        } else {
                            if (new File(rootDir + "/" + fileNames.get(getArguments().getInt(ARG_SENTENCE_NUMBER) - 1) + ".raw").exists()
                                    && !getArguments().getBoolean(ARG_HANDFREE_RECORDING)) {
                                recDialog.show();
                            } else {
                                record = true;
                                playButton.setEnabled(false);
                                getArguments().putBoolean(ARG_RECORDING, true);
                                sentence.setBackgroundResource(R.color.recording);
                                recButton.setBackgroundResource(R.drawable.record_stop);
                                recorder = new AudioRecord(AudioSource.MIC, recorderSampleRate, recorderChannels, recorderEncoding, recorderMinBufferSize * 10);
                                recorder.startRecording();
                                startRecording(getArguments().getInt(ARG_SENTENCE_NUMBER));
                            }
                        }
                    }
                }
            });

            playButton.setOnClickListener(new OnClickListener() {
                @SuppressWarnings("resource")
                @SuppressLint("ResourceAsColor")
                public void onClick(View arg0) {
                    if (!getArguments().getBoolean(ARG_PLAYING)) {
                        getArguments().putBoolean(ARG_PLAYING, true);
                        playButton.setBackgroundResource(R.drawable.play_stop);
                        recButton.setEnabled(false);
                        player = new MediaPlayer();
                        player.setOnCompletionListener(new OnCompletionListener() {
                            public void onCompletion(MediaPlayer mp) {
                                getArguments().putBoolean(ARG_PLAYING, false);
                                playButton.setBackgroundResource(R.drawable.play);
                                if (player != null) {
                                    player.release();
                                    player = null;
                                }
                                recButton.setEnabled(true);
                            }
                        });
                        String fileName = rootDir + "/" + fileNames.get(getArguments().getInt(ARG_SENTENCE_NUMBER) - 1);
                        try {
                            // Concatenate WAV header with RAW file to obtain the WAV file.
                            File cab = new File(fileName + ".cab");
                            File raw = new File(fileName + ".raw");

                            InputStream iscab = new FileInputStream(cab);
                            InputStream israw = new FileInputStream(raw);

                            RandomAccessFile rawav = new RandomAccessFile(fileName + ".wav", "rw");
                            rawav.setLength(0);

                            byte[] bytes = new byte[(int) cab.length()];

                            // Read in the bytes
                            int offset = 0;
                            int numRead;
                            while (offset < bytes.length && (numRead = iscab.read(bytes, offset, bytes.length - offset)) >= 0) {
                                offset += numRead;
                            }

                            // Ensure all the bytes have been read in
                            if (offset < bytes.length) {
                                throw new IOException("Could not completely read cab file");
                            }
                            iscab.close();
                            rawav.write(bytes);
                            bytes = new byte[(int) raw.length()];

                            // Read in the bytes
                            offset = 0;
                            while (offset < bytes.length && (numRead = israw.read(bytes, offset, bytes.length - offset)) >= 0) {
                                offset += numRead;
                            }

                            // Ensure all the bytes have been read in
                            if (offset < bytes.length) {
                                throw new IOException("Could not completely read raw file");
                            }

                            israw.close();
                            rawav.write(bytes);
                            rawav.close();

                        } catch (IOException e) {
                            Log.e(tag, "Fallo al crear fichero wav");
                        }
                        try {
                            player.setDataSource(fileName + ".wav");
                            player.prepare();
                            player.start();
                        } catch (IOException e) {
                            Log.e(tag, "Fallo al reproducir el fichero");
                        }
                    } else {
                        getArguments().putBoolean(ARG_PLAYING, false);
                        playButton.setBackgroundResource(R.drawable.play);
                        recButton.setEnabled(true);
                        if (player != null) {
                            player.release();
                            player = null;
                        }
                    }
                }

            });

            if (new File(rootDir + "/" + fileNames.get(getArguments().getInt(ARG_SENTENCE_NUMBER) - 1) + ".raw").exists()) {
                sentence.setBackgroundResource(R.color.recorded);
                playButton.setEnabled(true);
            } else {
                sentence.setBackgroundResource(R.color.notRecorded);
                playButton.setEnabled(false);
            }
            return rootView;
        }

        @Override
        public void onViewCreated(View view, Bundle savedInstanceState) {
            super.onViewCreated(view, savedInstanceState);
        }

        public void updateContent() {
            if (getArguments().getBoolean(ARG_HANDFREE_RECORDING)) {
                sentence.setBackgroundColor(R.color.black_overlay);
                playButton.setEnabled(false);
            } else if (new File(rootDir + "/" + fileNames.get(getArguments().getInt(ARG_SENTENCE_NUMBER) - 1) + ".raw").exists()) {
                sentence.setBackgroundResource(R.color.recorded);
                playButton.setEnabled(true);
            } else {
                sentence.setBackgroundResource(R.color.notRecorded);
                playButton.setEnabled(false);
            }
            sentence.setText(getArguments().getString(ARG_SENTENCE));
            progressBar.setProgress((int) getPercentDone());
            progress.setText(String.format("%.2f", getPercentDone()) + "%");
            sentenceNumber.setText(getResources().getString(R.string.sentence) + " " + Integer.toString(getArguments().getInt(ARG_SENTENCE_NUMBER)) + "/" + sentences.size());
        }

        public void handsFreeMode() {
            playButton.setEnabled(false);
            sentence.setBackgroundColor(R.color.black_overlay);
            new Thread(new Runnable() {
                public void run() {
                    Looper.prepare();
                    int init = mViewPager.getCurrentItem();
                    File rootDir1BK = rootDir;
                    File rootDir2BK = rootDir2;
                    Boolean driver = false;
                    for (int i = init; i < sentences.size(); i++) {

                        if (getArguments().getBoolean(ARG_HANDFREE_RECORDING)) {
                            tts.speak(sentences.get(i), TextToSpeech.QUEUE_FLUSH, null);
                            long startTime = System.currentTimeMillis();
                            while ((System.currentTimeMillis() - startTime) < 1000) {
                            }
                            while (tts.isSpeaking()) {
                            }
                        } else {
                            i--;
                        }
                        if (getArguments().getBoolean(ARG_HANDFREE_RECORDING)) {
                            try {
                                Thread.sleep(1000);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }
                        if (getArguments().getBoolean(ARG_HANDFREE_RECORDING)) {
                            Message msgObj = new Message();
                            Bundle b = new Bundle();
                            b.putString("message", "start recording");
                            msgObj.setData(b);
                            handler.sendMessage(msgObj);
                        }
                        try {
                            if (getArguments().getBoolean(ARG_HANDFREE_RECORDING)) {
                                Thread.sleep(10000);
                                Message msgObj = new Message();
                                Bundle b = new Bundle();
                                b.putString("message", "stop recording");
                                msgObj.setData(b);
                                handler.sendMessage(msgObj);
                            }

                            if (driver)
                                rootDir = rootDir1BK;
                            else {
                                i--;
                                rootDir = rootDir2BK;
                            }

                            if (driver) {
                                if (i + 1 < sentences.size() && getArguments().getBoolean(ARG_HANDFREE_RECORDING)) {
                                    try {
                                        Thread.sleep(1000);
                                        Message msgObj = new Message();
                                        Bundle b = new Bundle();
                                        b.putString("message", "next sentence");
                                        b.putInt("sentence", i + 1);
                                        b.putInt("move", i + 1);
                                        msgObj.setData(b);
                                        handler.sendMessage(msgObj);
                                    } catch (InterruptedException e) {
                                        e.printStackTrace();
                                    }
                                } else {
                                    Message msgObj = new Message();
                                    Bundle b = new Bundle();
                                    b.putString("message", "finish");
                                    b.putInt("sentence", init);
                                    b.putInt("move", i);
                                    msgObj.setData(b);
                                    handler.sendMessage(msgObj);
                                    Log.e("move", init + " - " + i);
                                    if (!getArguments().getBoolean(ARG_HANDFREE_RECORDING)) break;
                                }
                            }
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        driver = !driver;
                    }
                    getArguments().putBoolean(ARG_HANDFREE_RECORDING, false);
                }
            }).start();
        }
    }

    public class SectionsPagerAdapter extends FragmentPagerAdapter {
        public SectionsPagerAdapter(FragmentManager fm) {
            super(fm);
        }

        @Override
        public Fragment getItem(int position) {
            return PlaceholderFragment.newInstance(position + 1);
        }

        @Override
        public int getCount() {
            return sentences.size();
        }
    }
}
