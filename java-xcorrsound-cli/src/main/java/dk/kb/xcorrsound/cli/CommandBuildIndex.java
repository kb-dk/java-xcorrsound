package dk.kb.xcorrsound.cli;

import dk.kb.xcorrsound.index.FingerprintDBIndexer;
import picocli.CommandLine;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;

//https://picocli.info/#_introduction
@CommandLine.Command()
public class CommandBuildIndex implements Callable<Integer> {
    
    
    @CommandLine.Option(names = {"-d", "--dbname"}, description = "Database name", required = true)
    public String dbfile;
    
    @CommandLine.Option(names = {"-f", "--file"},
                        description = "File with names of wav files for bulk insertion, this ignores the --input option")
    public String listFile;
    
    @CommandLine.Option(names = {"-i", "--input"},
                        description = "Single wav file, use '-' as this value if the file is given on stdin")
    public String input;
    
    @CommandLine.Option(names = {"-n", "--name"}, description = "Name if input is on stdin")
    public String name;
    
    
    public static void main(String[] args) {
        CommandLine app = new CommandLine(new CommandBuildIndex());
        int exitCode = app.execute(args);
        
        System.exit(exitCode);
    }
    
    @Override
    public Integer call() throws Exception {
        FingerprintDBIndexer ismir = new FingerprintDBIndexer(dbfile);
        if (listFile != null) {
            //read list of input files
            List<String> mp3Files = Files.readAllLines(Path.of(listFile), StandardCharsets.UTF_8);
            for (String mp3File : mp3Files) {
                ismir.insert(mp3File, mp3File);
            }
        } else if (input != null) {
            if (input.trim().equals("-")) {
                if (name == null) {
                    //error
                    throw new IllegalArgumentException("If input is -, you must specify a name");
                }
                ismir.insert(input, name); //TODO
                //read single input from std in
            } else {
                //read single input file
                ismir.insert(input, input);
            }
        } else {
            throw new IllegalArgumentException("Please specify some input");
        }
        return 0;
    }
    
}
