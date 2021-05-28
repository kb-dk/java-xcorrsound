package dk.kb.xcorrsound;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.math3.complex.Complex;
import org.jtransforms.fft.DoubleFFT_1D;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class FingerprintStrategyIsmir implements FingerprintStrategy {
    
    private static Logger log = LoggerFactory.getLogger(FingerprintStrategyIsmir.class);
    
    private final int frameLength = 2048;
    
    private final int advance = 64;
    
    private final int sampleRate = 5512;
    
    
    public int getFrameLength() {
        return frameLength;
    }
    
    public int getAdvance() {
        return advance;
    }
    
    public int getSampleRate() {
        return sampleRate;
    }
    
    public long[] getFingerprintsForFile(String filename)
            throws IOException, UnsupportedAudioFileException, InterruptedException {
        
        // if filename is not wav file, start by converting to 5512hz stereo wav file
        // if filename is on stdin, assume it conforms to assumption above.
        // otherwise assume the file is 5512hz wav file.
        short[] samples = null;
        if ("-".equals(filename)) {
            log.info("Filename is -, reading standard input");
            // use stdin wav file reader.
            
            //TODO read wave file from stdIN...
            //wavStdinReader wsr;
            //wsr.getSamplesForChannel(0, samples);
            
        } else if (!"wav".equals(filename.substring(filename.length() - 3))) {
            Path tmpWavFile = convertToWav(filename);
            try {
                samples = readWavFile(tmpWavFile);
            } finally {
                Files.deleteIfExists(tmpWavFile);
            }
        } else {
            samples = readWavFile(Path.of(filename));
        }
        
        if (samples == null) {
            return null;
        }
        
        //for (int i = 0; i < samples.length; i++) {
        //    if (samples[i] != 0){
        //        System.out.println("Found value "+samples[i]+" at index "+i);
        //    }
        //}
        //
        
        long[] fingerprintStream = generateFingerprintStream(samples, frameLength, sampleRate, advance);
        return fingerprintStream;
    }
    
    
    private static Path convertToWav(String filename)
            throws IOException, InterruptedException {
        log.info("File '{}' is not in wav format; Converting", filename);
        int idx = 0;
        for (int i = filename.length(); i > 0; --i) {
            if (filename.charAt(i - 1) == '/') {
                idx = i;
                break;
            }
        }
        
        String tmpWaveFile = constructTmpWaveFileName(filename, idx);
        
        int res;
        
        Files.deleteIfExists(Path.of(tmpWaveFile));
        String ss = "ffmpeg -hide_banner -loglevel error -i "
                    + filename
                    + " -ar 5512 "
                    + tmpWaveFile
                    + "";
        Process ffmpeg = Runtime.getRuntime().exec(ss);
        ffmpeg.waitFor();
        res = ffmpeg.exitValue();
        
        
        if (res != 0) {
            Files.deleteIfExists(Path.of(tmpWaveFile));
            return null;
        }
        log.info("File '{}' converted, reading audio stream", filename);
        return Path.of(tmpWaveFile);
        
    }
    
    private static String constructTmpWaveFileName(String filename, int idx) {
        StringBuilder tmpWaveFileBuilder = new StringBuilder();
        String tmpDir = getTmpDir();
        
        tmpWaveFileBuilder.append(tmpDir)
                          .append(filename.substring(idx))
                          .append(".wav");
        String tmpWaveFile = tmpWaveFileBuilder.toString();
        return tmpWaveFile;
    }
    
    private static String getTmpDir() {
        String val = System.getenv("TmpSoundIndex");
        if (val == null) {
            return "/tmp/";
        } else {
            return "" + val;
        }
    }
    
    private static short[] readWavFile(Path tmpWaveFile) throws IOException, UnsupportedAudioFileException {
        short[] samples;
        try (AudioInputStream as = AudioSystem.getAudioInputStream(tmpWaveFile.toFile())) {
            samples = readChannel(as, 0);
            log.info("Read {} bytes from wav file {}", samples.length, tmpWaveFile);
        }
        return samples;
    }
    
    private static short[] readChannel(AudioInputStream as, int _channel) throws IOException {
        int singleSampleSize = as.getFormat().getFrameSize() / as.getFormat().getChannels();
        short[] samples = new short[(int) (as.getFrameLength())];
        boolean bigEndian = as.getFormat().isBigEndian();
        byte[] sample = new byte[singleSampleSize * as.getFormat().getChannels()];
        for (int i = 0; i < samples.length; i++) {
            int bytes_read = as.read(sample);
            for (int j = 0; j < as.getFormat().getChannels(); j++) {
                if (j == _channel) {
                    if (bigEndian) {
                        samples[i] = convertTwoBytesToShort(sample[j + 1], sample[j]);
                    } else {
                        samples[i] = convertTwoBytesToShort(sample[j], sample[j + 1]);
                    }
                }
            }
        }
        return samples;
    }
    
    private static short convertTwoBytesToShort(byte a, byte b) {
        //little endian. a is the least significant byte.
        short res = (short) (b & 0xFF);
        res = (short) (res << 8);
        res = (short) (res | (a & 0xFF));
        return res;
    }
    
    protected static long[] generateFingerprintStream(short[] input, int frameLength, int sampleRate, int advance) {
        log.info("Generating fingerprint for input of length {}", input.length);
        double[] hanningWindow = getHanningWindow(frameLength);
        int[] logScale = getLogScale(2000, frameLength, sampleRate);
        
        List<Long> output = new ArrayList<>();
        
        double[] prevEnergy = new double[logScale.length];
        
        int frameEnd = frameLength;
        for (int frameStart = 0;
             frameEnd < input.length;
             frameStart += advance, frameEnd += advance) {
            
            double[] tmp = new double[frameLength];
            for (int i = 0; i < frameLength; ++i) {
                tmp[i] = input[frameStart + i];
            }
            double norm = normalize(tmp); //?
            for (int i = 0; i < frameLength; ++i) {
                tmp[i] = tmp[i] * hanningWindow[i];
            }
            
            Complex[] transform = computeFFT(tmp);
            
            double[] energy = new double[logScale.length];
            
            for (int i = 0; i < logScale.length - 1; ++i) {
                
                double absVal = 0.0;
                for (int j = logScale[i]; j < logScale[i + 1]; ++j) {
                    absVal += transform[j].abs();
                }
                
                energy[i] = absVal / (logScale[i + 1] - logScale[i]);
            }
            
            Long fingerprint = getFingerprint(prevEnergy, energy);
            //log.info("Frame at {} have fingerprint {}", frameStart, fingerprint);
            prevEnergy = energy;
            
            output.add(fingerprint);
        }
        return ArrayUtils.toPrimitive(output.toArray(new Long[0]));
    }
    
    private static double[] getHanningWindow(int windowLength) {
        double[] window = new double[windowLength];
        
        for (int i = 0; i < windowLength; ++i) {
            window[i] = (25.0 / 46.0) - (21.0 / 46.0) * Math.cos((2 * Math.PI * i) / (windowLength - 1));
        }
        return window;
    }
    
    private static int[] getLogScale(double maxFrequency, int frameLength, int sampleRate) {
        int bands = 33;
        int[] indices = new int[bands];
        
        double logMin = Math.log(318.0) / Math.log(2);
        double logMax = Math.log(maxFrequency) / Math.log(2);
        
        double delta = (logMax - logMin) / bands; //linear increase on log scale
        
        double sum = 0.0;
        for (int i = 0; i < bands; ++i) {
            
            double hz = Math.pow(2, logMin + sum);
            
            indices[i] = getIndexFromHz(hz, frameLength, sampleRate);
            
            sum += delta;
        }
        return indices;
    }
    
    private static int getIndexFromHz(double hz, int frameLength, int sampleRate) {
        
        // hz = idx*(sampleRate/frameLength)
        // => idx = ceil(hz * (frameLength / sampleRate))
        int idx = (int) hz * frameLength / sampleRate;
        return idx;
    }
    
    private static double normalize(double[] samples) {
        
        double rootMeanSquare = 0.0;
        for (double sample : samples) {
            rootMeanSquare += sample * sample;
        }
        
        rootMeanSquare /= samples.length;
        
        return Math.sqrt(rootMeanSquare);
        
    }
    
    private static Complex[] computeFFT(final double[] input) {
        DoubleFFT_1D plan;
        double[] t = new double[input.length * 2];
        
        for (int i = 0; i < input.length; i++) {
            t[i * 2] = input[i];
        }
        
        plan = new DoubleFFT_1D(input.length);
        plan.complexForward(t); // Places result in t
        
        Complex[] output = new Complex[input.length];
        for (int i = 0; i < input.length; ++i) {
            output[i] = new Complex(t[i * 2], t[i * 2 + 1]);
        }
        return output;
    }
    
    
    private static long getFingerprint(double[] prevEnergy, double[] energy) {
        
        long fingerprint = 0;
        
        for (int bitPos = 0; bitPos < 32; ++bitPos) {
            double val = energy[bitPos] - energy[bitPos + 1] - (prevEnergy[bitPos] - prevEnergy[bitPos + 1]);
            
            long bit = (val > 0) ? 1 : 0;
            
            fingerprint = fingerprint + (bit << bitPos);
            
        }
        
        return fingerprint;
    }
   
}
