package dk.kb.xcorrsound;

import org.apache.commons.io.IOUtils;
import org.apache.commons.io.input.BoundedInputStream;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.math3.complex.Complex;
import org.jtransforms.fft.DoubleFFT_1D;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
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
    
    
    protected static long[] generateFingerprintStream(final short[] input,
                                                      final int frameLength,
                                                      final int sampleRate,
                                                      final int advance,
                                                      final int bands) {
        log.debug("Generating fingerprint for input of length {}", input.length);
        
        //TODO These values are static so calculate them somewhere earlier instead of here
        double[] hanningWindow = getHanningWindow(frameLength);
        int[] logScale = getLogScale(2000, frameLength, sampleRate, bands);
        
        assert logScale.length == bands + 1;
        double[] prevEnergy = new double[logScale.length];
        
        List<Long> output = new ArrayList<>();
        
        int frameEnd = frameLength;
        for (int frameStart = 0;
             frameEnd < input.length;
             frameStart += advance, frameEnd += advance) {
            
            
            short[] frame = Arrays.copyOfRange(input, frameStart, frameStart + frameLength);
            
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
        bands = bands + 1;
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
        
        for (int bitPos = 0; bitPos < energy.length - 1; ++bitPos) {
            double val = (energy[bitPos] - energy[bitPos + 1])
                         - (prevEnergy[bitPos] - prevEnergy[bitPos + 1]);
            
            long bit = (val > 0) ? 1 : 0;
            
            fingerprint = fingerprint + (bit << bitPos);
            
        }
        
        return fingerprint;
    }
    
    @Override
    public int getSampleRate() {
        return sampleRate;
    }
    
    @Override
    public int getAdvance() {
        return advance;
    }
    
    public long[] getFingerprintsForFile(String filename, Long offsetSeconds, Long lengthSeconds)
            throws IOException, UnsupportedAudioFileException {
        
        // if filename is not wav file, start by converting to 5512hz stereo wav file
        // if filename is on stdin, assume it conforms to assumption above.
        // otherwise assume the file is 5512hz wav file.
        short[] samples = null;
        if ("-".equals(filename)) {
            log.info("Filename is -, reading standard input");
            // use stdin wav file reader.
            try (AudioInputStream as = AudioSystem.getAudioInputStream(System.in)) {
                samples = readSamples("StdIn",
                                      offsetSeconds,
                                      lengthSeconds,
                                      as);
            }
            
        } else {
            File inputFile = Path.of(filename).toFile();
            if (isAcceptableFormat(inputFile)) {
                try (AudioInputStream audioInputStream = AudioSystem.getAudioInputStream(inputFile)) {
                    samples = readSamples(inputFile.getName(),
                                          null,
                                          null,
                                          audioInputStream);
                }
            } else {
                Path tmpWavFile = WavConverter.inlineConvertToWav(filename, sampleRate, offsetSeconds, lengthSeconds);
                if (tmpWavFile != null) {
                    try (AudioInputStream audioInputStream = AudioSystem.getAudioInputStream(tmpWavFile.toFile())) {
                        samples = readSamples(tmpWavFile.toAbsolutePath().toString(),
                                              null,
                                              null,
                                              audioInputStream);
                    } finally {
                        Files.deleteIfExists(tmpWavFile);
                    }
                }
            }
        }
        
        if (samples == null) {
            return null;
        }
        
        long[] fingerprintStream = generateFingerprintStream(samples, frameLength, sampleRate, advance, bands);
        return fingerprintStream;
    }
    
    private boolean isAcceptableFormat(File inputFile) throws IOException {
        try {
            AudioFileFormat fileFormat = AudioSystem.getAudioFileFormat(inputFile);
            return fileFormat.getType() == AudioFileFormat.Type.WAVE
                   && fileFormat.getFormat().getSampleRate() == sampleRate;
        } catch (UnsupportedAudioFileException e) {
            return false;
        }
    }
    
    private short[] readSamples(String sourceFileName,
                                Long offsetSeconds,
                                Long durationSeconds,
                                AudioInputStream audioInputStream)
            throws IOException {
        AudioFormat format = audioInputStream.getFormat();
        int frameSize = format.getFrameSize();
        int numChannels = format.getChannels();
        boolean bigEndian = format.isBigEndian();
        float sampleRate = format.getSampleRate();
        if (sampleRate != this.sampleRate) {
            throw new IllegalArgumentException("The given file '"
                                               + sourceFileName
                                               + "' have sampleRate '"
                                               + sampleRate
                                               + "' but the index requires "
                                               + "sampleRate of '"
                                               + this.sampleRate
                                               + "'");
        }
        long lengthInFrames = audioInputStream.getFrameLength();
        float totalDurationSeconds = lengthInFrames / sampleRate;
        
        float bytesPerSecond = frameSize * sampleRate;
        
        long durationAsBytes;
        long durationAsFrames;
        if (durationSeconds != null && durationSeconds < totalDurationSeconds) {
            durationAsBytes  = (long) (bytesPerSecond * durationSeconds);
            durationAsFrames = (long) Math.min(sampleRate * durationSeconds, lengthInFrames);
        } else {
            durationAsBytes  = lengthInFrames * frameSize;
            durationAsFrames = lengthInFrames;
        }
        
        if (offsetSeconds != null) {
            long skipOffsets = (long) (bytesPerSecond * offsetSeconds);
            IOUtils.skipFully(audioInputStream, skipOffsets);
        }
        
        short[] samples;
        try (final BoundedInputStream boundedInputStream = new BoundedInputStream(audioInputStream, durationAsBytes)) {
            samples = readChannel(boundedInputStream,
                                  0,
                                  frameSize,
                                  numChannels,
                                  bigEndian,
                                  durationAsFrames);
            log.debug("Read {} bytes from wav file {}", samples.length, sourceFileName);
        }
        return samples;
    }
    
    private static short[] readChannel(InputStream as,
                                       int channelToRead,
                                       int frameSize,
                                       int numChannels,
                                       boolean bigEndian,
                                       long lengthInFrames) throws IOException {
        int singleSampleSize = frameSize / numChannels;
        short[] samples = new short[(int) lengthInFrames];
        byte[] sample = new byte[singleSampleSize * numChannels];
        for (int i = 0; i < samples.length; i++) {
            int bytes_read = as.read(sample);
            for (int j = 0; j < numChannels; j++) {
                if (j == channelToRead) {
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
    
}
