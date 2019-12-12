package dk.kb.template;

import java.io.IOException;
import java.util.Arrays;

import dk.kb.util.YAML;

public class TemplateMain {

    public static void main(String[] args) throws IOException {
        HelloWorld helloWorld = new HelloWorld();
        
        String applicationConfig = System.getProperty("dk.kb.applicationConfig");
        System.out.println("Application config can be found in: '" + applicationConfig + "'");
        
        System.out.println("Arguments passed by commandline is: " + Arrays.asList(args));
        
        YAML config = new YAML(applicationConfig);
        String speaker = config.getString("config.speaker");
        
        System.out.println(helloWorld.sayHello(speaker));

    }

}
