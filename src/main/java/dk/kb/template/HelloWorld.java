package dk.kb.template;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HelloWorld {
    
    private final Logger log = LoggerFactory.getLogger(getClass());
    
    public HelloWorld() {
        log.debug("Running constructor HelloWorld()");    
    }
    
    public String sayHello(String from) {
        String message = "Hello world from " + from + "!"; 
        log.info("Greeting: '{}'", message);
        return message;
    }
    
}
