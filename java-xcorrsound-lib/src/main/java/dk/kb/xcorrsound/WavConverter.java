package dk.kb.xcorrsound;

import com.github.kokorin.jaffree.JaffreeException;
import com.github.kokorin.jaffree.LogLevel;
import com.github.kokorin.jaffree.ffmpeg.FFmpeg;
import com.github.kokorin.jaffree.ffmpeg.FFmpegResult;
import com.github.kokorin.jaffree.ffmpeg.UrlInput;
import com.github.kokorin.jaffree.ffmpeg.UrlOutput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class WavConverter {
    
    private static Logger log = LoggerFactory.getLogger(WavConverter.class);
    
    
    protected static Path inlineConvertToWav(String filename,
                                             int frameRate,
                                             Long offsetSeconds,
                                             Long durationSeconds)
            throws IOException {
        log.info("Converting '{}' is to {}hz WAV format", filename, frameRate);
        
        Path tmpWaveFile = wavFileName(filename);
        
        
        FFmpeg fFmpeg = FFmpeg.atPath()
                              .setLogLevel(LogLevel.ERROR)
                              .addArgument("-hide_banner");
        
        fFmpeg.setProgressListener(progress -> {
        
        });
        if (offsetSeconds != null) {
            fFmpeg.addArguments("-ss", offsetSeconds.toString());
        }
        fFmpeg.addInput(UrlInput.fromUrl(filename))
                       .setOverwriteOutput(true)
                       .addArguments("-ar", frameRate + "");
        if (durationSeconds != null) {
            fFmpeg.addArguments("-t", durationSeconds.toString());
        }
        fFmpeg.addOutput(UrlOutput.toUrl(tmpWaveFile.toString()));
        
        try {
            FFmpegResult result = fFmpeg.execute();
        } catch (JaffreeException e) {
            log.error("Failed to transcode file {} to {}", filename, tmpWaveFile, e);
            Files.deleteIfExists(tmpWaveFile);
            return null;
        }
        log.info("File '{}' converted, reading audio stream from {}", filename, tmpWaveFile);
        return tmpWaveFile;
    }
    
    private static Path wavFileName(String mp3Path) {
        String actualFileName = Path.of(mp3Path).getFileName().toString();
        
        String tmpDir = getTmpDir();
        
        return Path.of(tmpDir, actualFileName + ".wav");
    }
    
    private static String getTmpDir() {
        String val = System.getenv("TmpSoundIndex");
        if (val == null || val.isBlank()) {
            return "/tmp/";
        } else {
            return val;
        }
    }
    
}
