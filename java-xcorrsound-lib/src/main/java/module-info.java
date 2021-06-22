@SuppressWarnings({ "requires-automatic"})
    //https://stackoverflow.com/a/60156568/11532838
    //https://stackoverflow.com/a/49601182/11532838
module dk.kb.xcorrsound {
    
    //Which packages other modules can access
    exports dk.kb.xcorrsound.index;
    exports dk.kb.xcorrsound.search;
    exports dk.kb.xcorrsound;
    
    //Nessesary for junit tests
    opens dk.kb.xcorrsound;
    
    //Dependencies
    
    //FFT
    requires JTransforms;
    //FFT dependency: Complex numbers
    requires commons.math3;
    
    //AudioFile parsing: javax.sound.sampled
    requires transitive java.desktop;
    
    //Format timestamp in output...
    requires transitive org.apache.commons.lang3;
    
    
    //Logging
    requires org.slf4j;
    
    //FileUtils, IOUtils
    requires org.apache.commons.io;
    
    //ffmpeg
    requires com.github.kokorin.jaffree;
    
    
}