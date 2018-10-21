package com.spinningnoodle.audiolistener.service;

import lombok.Builder;
import lombok.Data;


@Data
@Builder
public class Configuration {
    final String listenerMixerName;
    final String monitorMixerName;
    @Builder.Default private float sampleRate = 44100f;
    @Builder.Default private int sampleSize = 16;
    @Builder.Default private int numberOfChannels = 1;
    @Builder.Default private boolean bigEndian = true;
    @Builder.Default private int priorSeconds = 1;
    @Builder.Default private int maxSeconds = 5;
    @Builder.Default private int quietPeriodSeconds = 1;
    private int percentThreshold ;
    private String folder;
    private boolean displayPercent;

    private static final int CHUNK=20; // 1/20th of a second


    public int calculateChunkSize() {
        return (int) (sampleRate  * numberOfChannels * sampleSize / (20 * 8) );
    }

    public int calculateBufferSize(int seconds) {
        return (int) (sampleRate * numberOfChannels * sampleSize * seconds / 8);
    }

    public int calculateQuietPeriodCounts() {
        // by chunks.
        return CHUNK * quietPeriodSeconds;
    }

    public double calculateThreshold() {
        return percentThreshold / 100d * Short.MAX_VALUE;
    }
}
