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
import java.util.Arrays;
import java.util.List;

public class FingerprintStrategyIsmir implements FingerprintStrategy {
    
    private static Logger log = LoggerFactory.getLogger(FingerprintStrategyIsmir.class);
    
    private final int frameLength;
    
    private final int advance;
    
    private final int sampleRate;
    
    private final int bands;
    
    public FingerprintStrategyIsmir(int frameLength, int advance, int sampleRate, int bands) {
        this.frameLength = frameLength;
        this.advance     = advance;
        this.sampleRate  = sampleRate;
        this.bands       = bands;
    }
    
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
            Path tmpWavFile = WavConverter.inlineConvertToWav(filename, sampleRate);
            if (tmpWavFile != null) {
                try {
                    samples = readWavFile(tmpWavFile);
                } finally {
                    Files.deleteIfExists(tmpWavFile);
                }
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
        
        long[] fingerprintStream = generateFingerprintStream(samples, frameLength, sampleRate, advance, bands);
        return fingerprintStream;
    }
    
    
    private static short[] readWavFile(Path tmpWaveFile) throws IOException, UnsupportedAudioFileException {
        short[] samples;
        try (AudioInputStream as = AudioSystem.getAudioInputStream(tmpWaveFile.toFile())) {
            samples = readChannel(as, 0);
            log.debug("Read {} bytes from wav file {}", samples.length, tmpWaveFile);
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
    
    protected static long[] generateFingerprintStream(final short[] input,
                                                      final int frameLength,
                                                      final int sampleRate,
                                                      final int advance,
                                                      final int bands) {
        log.debug("Generating fingerprint for input of length {}", input.length);
        
        //TODO These values are static so calculate them somewhere earlier instead of here
        double[] hanningWindow = getHanningWindow(frameLength);
        int[] logScale = getLogScale(2000, frameLength, sampleRate, bands);
        
        assert logScale.length == bands+1;
        double[] prevEnergy = new double[logScale.length];
        
        List<Long> output = new ArrayList<>();
        
        int frameEnd = frameLength;
        for (int frameStart = 0;
             frameEnd < input.length;
             frameStart += advance, frameEnd += advance) {
    
            
            short[] frame = Arrays.copyOfRange(input,frameStart,frameStart+frameLength);
            
            //double norm = normalize(tmp); //?
            double[] hanningWindowedFrame = new double[frameLength];
            for (int i = 0; i < frameLength; ++i) {
                hanningWindowedFrame[i] = frame[i] * hanningWindow[i];
            }
            
            double[] fourierTransformOfFrame = computeFFT_absValue(hanningWindowedFrame);
            
            double[] energy = computeEnergyInBands(logScale, fourierTransformOfFrame);
            
            Long fingerprint = getFingerprint(prevEnergy, energy);
            //log.info("Frame at {} have fingerprint {}", frameStart, fingerprint);
            prevEnergy = energy;
            
            output.add(fingerprint);
        }
        final Long[] array = output.toArray(new Long[0]);
        return ArrayUtils.toPrimitive(array);
    }
    
    private static double[] computeEnergyInBands(int[] logScale, double[] transform) {
        double[] energy = new double[logScale.length];
        
        for (int i = 0; i < logScale.length - 1; ++i) {
            
            double absVal = 0.0;
            for (int j = logScale[i]; j < logScale[i + 1]; ++j) {
                absVal += transform[j];
            }
            
            energy[i] = absVal / (logScale[i + 1] - logScale[i]);
        }
        return energy;
    }
    
    private static double[] getHanningWindow(int windowLength) {
        double[] window = new double[windowLength];
        
        for (int i = 0; i < windowLength; ++i) {
            window[i] = (25.0 / 46.0) - (21.0 / 46.0) * Math.cos((2 * Math.PI * i) / (windowLength - 1));
        }
        return window;
    }
    
    private static int[] getLogScale(double maxFrequency, int frameLength, int sampleRate, int bands) {
        bands = bands+1;
        int[] indices = new int[bands];
        
        double logMin = Math.log(318.0) / Math.log(2);
        double logMax = Math.log(maxFrequency) / Math.log(2);
        
        double delta = (logMax - logMin) / bands; //linear increase on log scale
        
        double sum = 0.0;
        for (int i = 0; i < bands; ++i) {
            
            double hz = Math.pow(2, logMin + sum);
            
            // hz = idx*(sampleRate/frameLength)
            // => idx = ceil(hz * (frameLength / sampleRate))
            int idx = (int) hz * frameLength / sampleRate;
            indices[i] = idx;
            
            sum += delta;
        }
        return indices;
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
    
    
    private static double[] computeFFT_absValue(final double[] input) {
        DoubleFFT_1D plan;
        double[] t = new double[input.length * 2];
        
        for (int i = 0; i < input.length; i++) {
            t[i * 2] = input[i];
        }
        
        plan = new DoubleFFT_1D(input.length);
        plan.complexForward(t); // Places result in t
        
        double[] output = new double[input.length];
        for (int i = 0; i < input.length; ++i) {
            output[i] = Complex.valueOf(t[i * 2], t[i * 2 + 1]).abs();
        }
        return output;
    }
    
    private static long getFingerprint(double[] prevEnergy, double[] energy) {
        
        long fingerprint = 0;
        
        for (int bitPos = 0; bitPos < energy.length-1; ++bitPos) {
            double val = (energy[bitPos] - energy[bitPos + 1])
                         - (prevEnergy[bitPos] - prevEnergy[bitPos + 1]);
            
            long bit = (val > 0) ? 1 : 0;
            
            fingerprint = fingerprint + (bit << bitPos);
            
        }
        
        return fingerprint;
    }
    
}
