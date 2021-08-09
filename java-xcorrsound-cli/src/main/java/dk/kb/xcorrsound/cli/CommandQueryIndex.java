package dk.kb.xcorrsound.cli;

import dk.kb.xcorrsound.search.FingerprintDBSearcher;
import dk.kb.xcorrsound.search.IsmirSearchResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;

import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

//https://picocli.info/#_introduction
@CommandLine.Command()
public class CommandQueryIndex implements Callable<Integer> {
    private static final Logger log = LoggerFactory.getLogger(CommandQueryIndex.class);
    
    @CommandLine.Option(names = {"-q", "--query"}, required = true)
    public String queryFile;
    
    @CommandLine.Option(names = {"-d", "--dbname"}, required = true)
    public List<String> dbfiles;
    
    @CommandLine.Option(names = {"-c", "--criteria"}, required = false)
    public Double criteria;
    
    @CommandLine.Option(names = {"-P", "--num-procs"}, required = false, defaultValue = "12")
    public Integer processes;
    
    public static void main(String... args) {
        CommandLine app = new CommandLine(new CommandQueryIndex());
        int exitCode = app.execute(args);
        
        System.exit(exitCode);
    }
    
    @Override
    public Integer call() throws Exception {
        //Default value if criteria not set
        criteria = Optional.ofNullable(criteria).orElse(FingerprintDBSearcher.DEFAULT_CRITERIA);
        int[] fingerprints = new FingerprintDBSearcher(null).getFingerprintStrategy().getFingerprintsForFile(queryFile, null, null);
    
        ExecutorService threadPool = Executors.newFixedThreadPool(Math.min(dbfiles.size(),processes));
        List<Future<String>> results = new ArrayList<>();
        
        for (String dbfile : dbfiles) {
            Callable<String> searcher = () -> singleSearchEx(dbfile, fingerprints);
            results.add(threadPool.submit(searcher));
        }
        awaitTermination(threadPool);
    
        for (Future<String> result : results) {
            System.out.println(result.get());
        }
        return 0;
    }
    
    private String singleSearchEx(String dbfile, int[] fingerprints) {
        try {
            return singleSearch(dbfile, fingerprints);
        } catch (IOException | InterruptedException | UnsupportedAudioFileException e) {
            throw new RuntimeException(e);
        }
    }
    
    private String singleSearch(String dbfile, int[] fingerprints) throws IOException, UnsupportedAudioFileException, InterruptedException {
        FingerprintDBSearcher searcher = new FingerprintDBSearcher(dbfile);
    
            try (StringWriter resultWriter = new StringWriter()) {
                List<IsmirSearchResult> result = searcher.query_scan(fingerprints, criteria);
                result.forEach(singleResult -> resultWriter.write(singleResult.toString()));
                return resultWriter.toString();
            }
        
    }
    
    
    private void awaitTermination(ExecutorService threadPool) throws InterruptedException {
        threadPool.shutdown();
        boolean completed = threadPool.awaitTermination(30, TimeUnit.MINUTES);
        
        if (!completed) {
            threadPool.shutdownNow();
        }
    }
}
