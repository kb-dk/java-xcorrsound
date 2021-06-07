package dk.kb.xcorrsound;

import dk.kb.xcorrsound.search.FingerprintDBSearcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;

import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.Callable;

//https://picocli.info/#_introduction
@CommandLine.Command()
public class CommandQueryIndex implements Callable<Integer> {
    private static final Logger log = LoggerFactory.getLogger(CommandQueryIndex.class);
    
    @CommandLine.Option(names = {"-q", "--query"}, required = true)
    public String queryFile;
    
    @CommandLine.Option(names = {"-d", "--dbname"}, required = true)
    public String dbfile;
    
    @CommandLine.Option(names = {"-c", "--criteria"}, required = false)
    public Double criteria;
    
    
    public static void main(String... args) {
        CommandLine app = new CommandLine(new CommandQueryIndex());
        int exitCode = app.execute(args);
        
        System.exit(exitCode);
    }
    
    @Override
    public Integer call() throws Exception {
        //Default value if criteria not set
        criteria = Optional.ofNullable(criteria).orElse(FingerprintDBSearcher.DEFAULT_CRITERIA);
        
        FingerprintDBSearcher searcher = new FingerprintDBSearcher();
        searcher.open(dbfile);
    
        try (PrintWriter resultWriter = new PrintWriter(System.out, true, StandardCharsets.UTF_8)) {
            searcher.query_scan(queryFile, criteria, resultWriter);
        }
        return 0;
    }
}
