package dk.kb.xcorrsound;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;

public class FingerPrintDB implements AutoCloseable {
    
    private Logger log = LoggerFactory.getLogger(this.getClass());
    public static final Integer macro_sz = 256;
    public static final Integer fpSkip = 50; // skip this much into fingerprint array.
    public static final Integer nearRange = 150; // how far to look around a position that
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
        return mapFilePrefix + mapFileSuffix + ".map";
    }
    
    
    public void open(String filename) throws IOException {
        this.dbFilename = filename;
        
        String mapFile = getMapFile(filename);
        FileUtils.touch(new File(mapFile));
        
        try (BufferedReader fin = IOUtils.buffer(new FileReader(mapFile, StandardCharsets.UTF_8))) {
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
        try (DataOutputStream of = new DataOutputStream(IOUtils.buffer(FileUtils.openOutputStream(new File(dbFilename), true)))) {
            
            for (int i = 0, dbLength = db.length; i < dbLength; i++) {
                long j = db[i];
                log.debug("db[{}]={}", i, j);
                //Written as LittleEndian
                of.writeInt(Integer.reverseBytes((int) j));
            }
            end = (of.size() + initialSize) / 4;
        }
        
        String mapFilename = dbFilename + ".map";
        
        try (Writer mof = IOUtils.buffer(new FileWriter(mapFilename,StandardCharsets.UTF_8, true))) {
            mof.write("" + end);
            mof.write(" ");
            mof.write(indexedName);
            mof.write("\n");
        }
        log.info("Index written to disk");
    }
    
    
    public void openForSearching() throws FileNotFoundException {
        if (fin == null) {
            fin = new DataInputStream(IOUtils.buffer(new FileInputStream(this.dbFilename)));
        }
    }
    
    public int readDBBlob(int[] buffer, long bytesReadFromStream) throws IOException {
        log.debug("Starting search in {}", dbFilename);
    
        Path dbFile = Path.of(this.dbFilename);
        int count = (int) Math.min(buffer.length - macro_sz,
                                   (Files.size(dbFile) - bytesReadFromStream) / Integer.BYTES);
        
        for (int i = 0; i < count; ++i) {
            buffer[i + macro_sz] = Integer.reverseBytes(fin.readInt());
            //bytesReadFromStream += Integer.BYTES;
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
