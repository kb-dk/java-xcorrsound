package dk.kb.xcorrsound;

import com.github.kokorin.jaffree.JaffreeException;
import com.github.kokorin.jaffree.LogLevel;
import com.github.kokorin.jaffree.ffmpeg.FFmpeg;
import com.github.kokorin.jaffree.ffmpeg.FFmpegProgress;
import com.github.kokorin.jaffree.ffmpeg.FFmpegResult;
import com.github.kokorin.jaffree.ffmpeg.NullOutput;
import com.github.kokorin.jaffree.ffmpeg.ProgressListener;
import com.github.kokorin.jaffree.ffmpeg.UrlInput;
import com.github.kokorin.jaffree.ffmpeg.UrlOutput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicLong;

public class WavConverter {
    
    private static Logger log = LoggerFactory.getLogger(WavConverter.class);
    
    
    protected static Path inlineConvertToWav(String filename) throws IOException {
        log.info("File '{}' is not in wav format; Converting", filename);
    
        Path tmpWaveFile = wavFileName(filename);
    
    
        FFmpeg fFmpeg = FFmpeg.atPath()
                              .setLogLevel(LogLevel.ERROR)
                              .addArgument("-hide_banner")
                              .addInput(UrlInput.fromUrl(filename))
                              .setOverwriteOutput(true)
                              .addArguments("-ar", "5512")
                              .addOutput(UrlOutput.toUrl(tmpWaveFile.toString()));
        try {
            FFmpegResult result = fFmpeg.execute();
        } catch (JaffreeException e){
            log.error("Failed to transcode file {} to {}",filename, tmpWaveFile, e);
            Files.deleteIfExists(tmpWaveFile);
            return null;
        }
        log.info("File '{}' converted, reading audio stream from {}", filename, tmpWaveFile);
        return tmpWaveFile;
    }
    
    protected static Path convertToWav(String filename)
            throws IOException, InterruptedException {
        log.info("File '{}' is not in wav format; Converting", filename);
        Path tmpWaveFile = wavFileName(filename);
        
        int res;
        
        Files.deleteIfExists(tmpWaveFile);
        String ss = "ffmpeg -hide_banner -loglevel error -i "
                    + filename
                    + " -ar 5512 "
                    + tmpWaveFile.toAbsolutePath()
                    + "";
        Process ffmpeg = Runtime.getRuntime().exec(ss);
        ffmpeg.waitFor();
        res = ffmpeg.exitValue();
        
        
        if (res != 0) {
            Files.deleteIfExists(tmpWaveFile);
            return null;
        }
        log.info("File '{}' converted, reading audio stream from {}", filename, tmpWaveFile);
        return tmpWaveFile;
        
    }
    
    private static Path wavFileName(String mp3Path) {
        String actualFileName = Path.of(mp3Path).getFileName().toString();
        
        String tmpDir = getTmpDir();
        
        return Path.of(tmpDir,actualFileName+".wav");
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
