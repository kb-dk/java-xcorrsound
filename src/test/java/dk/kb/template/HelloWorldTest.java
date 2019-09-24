package dk.kb.template;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;


public class HelloWorldTest {

    @Tag("fast")
    @Test
    @DisplayName("Hello from Mr. Hyde")
    public void mrHyde() {
        HelloWorld myHello = new HelloWorld();
        String message = myHello.sayHello("Mr. Hyde");
    
        assertEquals("Hello world from Mr. Hyde!", message);
    
    }
    
    
    @Tag("slow")
    @Test
    public void sleeper() {
        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        assertTrue(true);
    }
    
}
