package dk.kb.xcorrsound;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;

public class FingerPrintDB implements AutoCloseable {
    
    private Logger log = LoggerFactory.getLogger(this.getClass());
    public static Integer macro_sz = 256;
    public static Integer fpSkip = 50; // skip this much into fingerprint array.
    public static Integer nearRange = 150; // how far to look around a position that
    // is significantly different from noise.
    
    protected final FingerprintStrategy fp_strategy;
    protected String dbFilename;
    protected Map<Integer, String> offsetsToFile = new TreeMap<>();
    
    private DataInputStream fin;
    
    
    public FingerPrintDB() {
        this.fp_strategy = new FingerprintStrategyIsmir();
        //this.fp_strategy = new si::fingerprint_strategy_chroma();
    }
    
    String getMapFile(String filename) {
        int idx = 0;
        for (int i = filename.length(); i > 0; --i) {
            if (filename.charAt(i - 1) == '/') {
                idx = i;
                break;
            }
        }
        String mapFilePrefix = filename.substring(0, idx);
        String mapFileSuffix = filename.substring(idx);
        StringBuilder ss = new StringBuilder();
        ss.append(mapFilePrefix).append(mapFileSuffix).append(".map");
        return ss.toString();
    }
    
    
    public void open(String filename) throws IOException {
        this.dbFilename = filename;
        
        String mapFile = getMapFile(filename);
        FileUtils.touch(new File(mapFile));
        
        try (BufferedReader fin = new BufferedReader(new InputStreamReader(new FileInputStream(mapFile)))) {
            String line = fin.readLine();
            while (line != null) {
                String[] splits = line.split("\\s+", 2);
                offsetsToFile.put(Integer.parseInt(splits[0]), splits[1]);
                line = fin.readLine();
            }
        }
        
    }
    
    protected void writeDBToDisk(String dbFilename,
                                 long[] db,
                                 String indexedName) throws IOException {
        log.info("Writing index to disk");
        
        FileUtils.touch(new File(dbFilename));
        
        long initialSize = Files.size(Path.of(dbFilename));
        long end;
        try (DataOutputStream of = new DataOutputStream(new FileOutputStream(dbFilename, true))) {
            
            for (int i = 0, dbLength = db.length; i < dbLength; i++) {
                long j = db[i];
                log.info("db[{}]={}", i, j);
                //Written as LittleEndian
                of.writeInt(Integer.reverseBytes((int) (j)));
            }
            end = (of.size() + initialSize) / 4;
        }
        
        String mapFilename = dbFilename + ".map";
        try (Writer mof = new OutputStreamWriter(new FileOutputStream(mapFilename, true))) {
            mof.write("" + end);
            mof.write(" ");
            mof.write(indexedName);
            mof.write("\n");
        }
        log.info("Index written to disk");
    }
    
    private long bytesReadFromStream = 0;
    public void openForSearching() throws FileNotFoundException {
        if (fin == null ) {
            fin = new DataInputStream(new FileInputStream(this.dbFilename));
        }
    }
    
    protected int readDBBlob(int[] buffer) throws IOException {
        log.info("Starting search in {}", dbFilename);
        
        int count = (int) Math.min(buffer.length - macro_sz,
                                   (Files.size(Path.of(this.dbFilename))-bytesReadFromStream) / Integer.BYTES);
        
        for (int i = 0; i < count; ++i) {
            buffer[i + macro_sz] = Integer.reverseBytes(fin.readInt());
            bytesReadFromStream += Integer.BYTES;
        }
        return count;
    }
    
    @Override
    public void close() throws IOException {
        if (fin != null) {
            fin.close();
        }
    }
}
