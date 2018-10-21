package com.spinningnoodle.audiolistener.service;

import org.apache.commons.lang3.StringUtils;

import javax.sound.sampled.*;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class ListeningService {
    private final Configuration configuration;
    private final AudioFormat audioFormat;
    private TargetDataLine listeningLine;
    private volatile boolean shuttingDown = false;
    private SourceDataLine monitorLine = null;
    private BlockingQueue<Runnable> queue = new ArrayBlockingQueue<Runnable>(10);
    private ThreadPoolExecutor executor = new ThreadPoolExecutor(1,1,1000, TimeUnit.SECONDS, queue);


    public ListeningService(Configuration configuration) {
        this.configuration = configuration;
        audioFormat = new AudioFormat(configuration.getSampleRate(), configuration.getSampleSize(), configuration.getNumberOfChannels(), true, true);
    }


    private void setupListener() {
        listeningLine = (TargetDataLine) getLine(configuration.getListenerMixerName(), false);
        if (!StringUtils.isEmpty(configuration.getMonitorMixerName())) {
            monitorLine = (SourceDataLine) getLine(configuration.getMonitorMixerName(), true);
        }
    }

    private DataLine getLine (String name, boolean source) {
        Mixer.Info info = findMixer(name, source);
        if (info == null) {
            throw new IllegalArgumentException("Could not find Audio Interface: "+configuration.getListenerMixerName());
        }
        try {
            if (source) {
                return AudioSystem.getSourceDataLine(audioFormat, info);
            } else {
                return AudioSystem.getTargetDataLine(audioFormat, info);
            }
        } catch (LineUnavailableException ex) {
            throw new IllegalArgumentException("Line "+configuration.getListenerMixerName()+" is not available");
        }
    }

    private Mixer.Info findMixer(String mixerName, boolean source) {
        return SoundUtils.getMixers(source).get(mixerName);
    }

    public void listen()  {
        setupListener();
        executor.prestartAllCoreThreads();


        Thread listeningThread = new Thread(() -> {
            try {
                startListen();
            } catch (LineUnavailableException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        listeningThread.setDaemon(false);
        listeningThread.start();
    }

    private void startListen() throws LineUnavailableException, IOException {

        Files.createDirectories(Paths.get(configuration.getFolder()));

        byte[] listeningBuffer = new byte[configuration.calculateBufferSize(configuration.getPriorSeconds())];
        byte[] captureBuffer = new byte[configuration.calculateBufferSize(configuration.getPriorSeconds() + configuration.getMaxSeconds())];
        int maxQuietPeriodCounts = configuration.calculateQuietPeriodCounts();
        int index =0;
        int captureBufferIndex =0;
        int consecutiveQuiets = 0;
        boolean hasUsedAllBuffer = false;
        boolean shouldCapture = false;
        listeningLine.open();
        if (monitorLine != null) {
            monitorLine.open();
        }
        listeningLine.start();
        if (monitorLine != null) monitorLine.start();
        int chunkSize = configuration.calculateChunkSize();
        System.out.println("Capture buffer size :"+captureBuffer.length+" Listening Buffer Size: "+listeningBuffer.length+" chunk:"+chunkSize);
        while (!shuttingDown) {
//            byte[] data = new byte[listeningLine.getBufferSize()/8];
            try {
                listeningLine.read(listeningBuffer, index, chunkSize);
                if (monitorLine != null) monitorLine.write(listeningBuffer, index, chunkSize);
                boolean loud = isLoud(listeningBuffer, index, chunkSize);

                if (shouldCapture) {
                    System.arraycopy(listeningBuffer, index , captureBuffer, captureBufferIndex, chunkSize);
                    captureBufferIndex += chunkSize;
                    if (loud) {
                         consecutiveQuiets = 0;
                    }  else {
                        consecutiveQuiets ++;
                    }
                }

                if (captureBufferIndex == captureBuffer.length || consecutiveQuiets > maxQuietPeriodCounts) {
                    // we max out the amount to record, or got quiet
                    System.out.println("Stopping Capture");
                    shouldCapture = false;
                    consecutiveQuiets = 0;
                    byte[] captureCopy = new byte[captureBufferIndex];
                    System.arraycopy(captureBuffer, 0, captureCopy, 0, captureBufferIndex);
                    queue.add(() -> saveEvent(captureCopy));
                    captureBufferIndex = 0;
                    index =0;
                    Arrays.fill(listeningBuffer, (byte) 0);
                    hasUsedAllBuffer = false;
                }

                if (loud && !shouldCapture) {
                    // 1st. Create buffer with all the info.
                    if (hasUsedAllBuffer) {
                        System.arraycopy(listeningBuffer, index + chunkSize, captureBuffer, 0, listeningBuffer.length - index - chunkSize);
                        System.arraycopy(listeningBuffer, 0, captureBuffer, listeningBuffer.length - index - chunkSize , index+chunkSize);
                        captureBufferIndex = listeningBuffer.length;
                    } else {
                        System.arraycopy(listeningBuffer, 0, captureBuffer, 0, index+chunkSize);
                        captureBufferIndex = index + chunkSize;
                    }
                    shouldCapture = true;
                    System.out.println("Starting Capture "+new Date());
                    consecutiveQuiets = 0;
                }


                index += chunkSize;
                if (index == listeningBuffer.length) {
                    index = 0;
                    hasUsedAllBuffer = true;

                }
            } catch (Exception e) {
                throw new RuntimeException("Error when processing", e);
            }
        }
        if (monitorLine != null) monitorLine.close();
        listeningLine.stop();
        listeningLine.close();
        synchronized (this) {
            notifyAll();
        }
    }

    private void saveEvent(byte[] captureCopy) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss");
        String fileName = configuration.getFolder()+"/"+sdf.format(new Date())+".wav";
        try {
            AudioSystem.write(new AudioInputStream(new ByteArrayInputStream(captureCopy), audioFormat, captureCopy.length / audioFormat.getFrameSize()), AudioFileFormat.Type.WAVE, new File(fileName));
        } catch (IOException e) {
            System.out.println("Error when saving :"+e);
        }
    }

    private boolean isLoud(byte[] listeningBuffer, int index, int chunkSize) {
        // let's RMS
        long accumulator = 0;

        for (int i =0;i < chunkSize; i+=2) {
            int read = listeningBuffer[i+index] << 8 | listeningBuffer[i+index+1];
            accumulator += read * read;
        }
        accumulator /= chunkSize/2;

        double rms = Math.sqrt(accumulator);
        double loudPercent = rms / (double) Short.MAX_VALUE;
        if (configuration.isDisplayPercent()) {
            System.out.print(String.format("Loudness Percent %3d",(int) (loudPercent * 100))+"\r");
        }
        return loudPercent * 100d > configuration.getPercentThreshold();
    }


    public void shutdown() {
        synchronized (this) {
            this.shuttingDown = true;
            executor.shutdown();
            try {
                executor.awaitTermination(1000, TimeUnit.MILLISECONDS);
                wait();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
}
