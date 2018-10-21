package com.spinningnoodle.audiolistener;

import com.spinningnoodle.audiolistener.service.Configuration;
import com.spinningnoodle.audiolistener.service.ListeningService;
import com.spinningnoodle.audiolistener.service.SoundUtils;
import org.apache.commons.cli.*;
import org.apache.commons.lang3.StringUtils;

import javax.sound.sampled.*;
import java.util.HashMap;
import java.util.Map;

public class Main {

    public static void main(String[] args) throws LineUnavailableException, InterruptedException, ParseException {
        Options options = new Options();
        options.addOption("i",true,"Listening interface");
        options.addOption("m",true,"Monitoring Interface (optional)");
        options.addOption("t", true,"Threshold in percentage, like 20");
        options.addOption("p", true,"# of seconds to record prior to event");
        options.addOption("a",true, "# of seconds of quiet before stopping recording");
        options.addOption("f", true,"Folder to put recordings");
        options.addOption("d","Display current percent loudness level");

        CommandLineParser parser = new DefaultParser();
        CommandLine cmd = parser.parse(options, args);
        HelpFormatter formatter = new HelpFormatter();


        Configuration configuration = Configuration.builder()
                .listenerMixerName(cmd.getOptionValue("i"))
                .monitorMixerName(cmd.getOptionValue("m"))
                .displayPercent(cmd.hasOption("d"))
                .percentThreshold(Integer.parseInt(cmd.getOptionValue("t","5")))
                .folder(cmd.getOptionValue("f","."))

                .build();
        try {
            ListeningService service = new ListeningService(configuration);
            service.listen();
            Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
                @Override
                public void run() {
                    System.out.print("Shutting Down");
                    service.shutdown();
                }
            }));
        } catch (IllegalArgumentException ex) {
            System.out.println( "There was an issue with configuration :"+ex.getMessage());
            formatter.printHelp("audiolistener", options);
            System.out.println("\n\nAvailable Listening Interfaces");
            SoundUtils.getMixers(false).keySet().stream().sorted().forEach(e -> System.out.println(" - " + e));
            System.out.println("\n\nAvailable Monitoring Interfaces");
            SoundUtils.getMixers(true).keySet().stream().sorted().forEach(e -> System.out.println(" - " + e));

        }


//        Runtime.getRuntime().addShutdownHook(new Thread(() -> System.out.println("Shutting down!")));
//        Thread.sleep(1_000_000);



//
//        Line.Info[] sourceLines = AudioSystem.getSourceLineInfo(new DataLine.Info(SourceDataLine.class, null));
//        for (Line.Info sourceLine : sourceLines) {
//            System.out.println(sourceLine);
//        }
//        System.out.println("Hello world!");
    }

}
