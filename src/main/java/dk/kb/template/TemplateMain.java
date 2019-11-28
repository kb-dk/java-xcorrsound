package dk.kb.template;

import java.util.Arrays;

public class TemplateMain {

    public static void main(String[] args) {
        HelloWorld helloWorld = new HelloWorld();
        
        String applicationConfig = System.getProperty("dk.kb.applicationConfig");
        System.out.println("Application config can be found in: '" + applicationConfig + "'");
        
        System.out.println("Arguments passed by commandline is: " + Arrays.asList(args));
        
        System.out.println(helloWorld.sayHello("Dr. Jekyll"));

    }

}
