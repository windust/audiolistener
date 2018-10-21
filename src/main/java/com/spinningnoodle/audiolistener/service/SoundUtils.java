package com.spinningnoodle.audiolistener.service;

import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Line;
import javax.sound.sampled.Mixer;
import java.util.HashMap;
import java.util.Map;

public final class SoundUtils {

 public static Map<String, Mixer.Info> getMixers(boolean hasSource) {

        Map<String, Mixer.Info> infoMap = new HashMap<>();
        Mixer.Info[] mixers = AudioSystem.getMixerInfo();
        for (Mixer.Info mixerInfo : mixers){
            if (mixerInfo.getName().contains("Unknown")) continue;
            Mixer m = AudioSystem.getMixer(mixerInfo);

            Line.Info[] lines = hasSource ?  m.getSourceLineInfo() : m.getTargetLineInfo();
            if (lines.length > 0) {
                infoMap.put(mixerInfo.getName(), mixerInfo);
            }
        }
        return infoMap;
    }
}
